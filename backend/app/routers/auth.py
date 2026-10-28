import socket

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .. import __version__
from ..config import Settings, get_settings
from ..db import DeviceToken, get_session
from ..motion.extractor import motion_available
from ..schemas import (
    DeviceOut, LoginRequest, PairingCodeResponse, PairRequest, PairResponse, ServerInfo, SessionInfo,
)
from ..security import (
    COOKIE_NAME, Owner, authenticate, is_loopback, login_limiter, new_token, pairing, require_owner,
)

router = APIRouter(tags=["auth"])

COOKIE_MAX_AGE = 180 * 24 * 3600


def _client_key(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def _set_cookie(response: Response, request: Request, token: str) -> None:
    secure = request.url.scheme == "https" or request.headers.get("x-forwarded-proto") == "https"
    response.set_cookie(
        COOKIE_NAME, token, max_age=COOKIE_MAX_AGE, httponly=True, samesite="strict", secure=secure, path="/",
    )


async def _issue(session: AsyncSession, name: str, kind: str) -> str:
    token, digest = new_token()
    session.add(DeviceToken(name=name.strip()[:80] or kind, kind=kind, token_sha256=digest))
    await session.commit()
    return token


def _browser_name(request: Request) -> str:
    ua = request.headers.get("user-agent", "")
    for marker, label in (("Edg/", "Edge"), ("Chrome/", "Chrome"), ("Firefox/", "Firefox"), ("Safari/", "Safari")):
        if marker in ua:
            platform = "Android" if "Android" in ua else "iPhone" if "iPhone" in ua else "Windows" if "Windows" in ua else ""
            return f"{label} {platform}".strip()
    return "Browser"


@router.post("/api/v1/auth/login", status_code=204)
async def login(
    body: LoginRequest,
    request: Request,
    response: Response,
    settings: Settings = Depends(get_settings),
    session: AsyncSession = Depends(get_session),
) -> None:
    """Web sign-in with the owner token. The browser gets its own revocable device token in a cookie."""
    key = _client_key(request)
    login_limiter.check(key)
    owner = await authenticate(body.token.strip(), settings, session) if settings.owner_token_sha256 else None
    if owner is None:
        login_limiter.fail(key)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token")
    _set_cookie(response, request, await _issue(session, _browser_name(request), "web"))


@router.post("/api/v1/auth/pair", response_model=PairResponse)
async def pair(
    body: PairRequest,
    request: Request,
    response: Response,
    settings: Settings = Depends(get_settings),
    session: AsyncSession = Depends(get_session),
) -> PairResponse:
    """Exchanges the 6-digit pairing code shown on the computer for a device token."""
    key = _client_key(request)
    login_limiter.check(key)
    if not settings.owner_token_sha256 or not pairing.use_pairing_code(body.code):
        login_limiter.fail(key)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired pairing code")
    if body.kind == "web":
        _set_cookie(response, request, await _issue(session, body.device_name or _browser_name(request), "web"))
        return PairResponse(token=None)
    return PairResponse(token=await _issue(session, body.device_name or "Phone", "phone"))


@router.get("/auth/local", include_in_schema=False)
async def local_login(code: str, request: Request, session: AsyncSession = Depends(get_session)) -> Response:
    """One-click sign-in for the desktop launcher. Only works from this computer, once, within two minutes."""
    if not is_loopback(request) or not pairing.use_local_code(code):
        return RedirectResponse("/#/login", status_code=303)
    response = RedirectResponse("/", status_code=303)
    _set_cookie(response, request, await _issue(session, "This computer", "web"))
    return response


@router.post("/api/v1/auth/logout", status_code=204)
async def logout(
    request: Request, response: Response, owner: Owner = Depends(require_owner), session: AsyncSession = Depends(get_session)
) -> None:
    if owner.device_id is not None and request.cookies.get(COOKIE_NAME):
        device = await session.get(DeviceToken, owner.device_id)
        if device is not None:
            device.revoked = True
            await session.commit()
    response.delete_cookie(COOKIE_NAME, path="/")


@router.get("/api/v1/auth/session", response_model=SessionInfo)
async def session_info(request: Request, owner: Owner = Depends(require_owner)) -> SessionInfo:
    return SessionInfo(device_id=owner.device_id, device_name=owner.device_name, on_server_computer=is_loopback(request))


@router.post("/api/v1/auth/pairing-code", response_model=PairingCodeResponse, dependencies=[Depends(require_owner)])
async def pairing_code() -> PairingCodeResponse:
    code, expires_at = pairing.new_pairing_code()
    return PairingCodeResponse(code=code, expires_at=int(expires_at * 1000))


@router.get("/api/v1/devices", response_model=list[DeviceOut], dependencies=[Depends(require_owner)])
async def devices(session: AsyncSession = Depends(get_session)) -> list[DeviceOut]:
    rows = (
        await session.execute(select(DeviceToken).where(DeviceToken.revoked.is_(False)).order_by(DeviceToken.created_at))
    ).scalars().all()
    return [
        DeviceOut(
            id=d.id, name=d.name, kind=d.kind,
            created_at=int(d.created_at.timestamp() * 1000),
            last_used_at=int(d.last_used_at.timestamp() * 1000) if d.last_used_at else None,
        )
        for d in rows
    ]


@router.delete("/api/v1/devices/{device_id}", status_code=204, dependencies=[Depends(require_owner)])
async def revoke_device(device_id: int, session: AsyncSession = Depends(get_session)) -> None:
    device = await session.get(DeviceToken, device_id)
    if device is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Unknown device")
    device.revoked = True
    await session.commit()


def lan_addresses() -> list[str]:
    """IPv4 addresses of this computer that other devices on the network can reach."""
    found: list[str] = []
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))  # no packet is sent; picks the default interface
            found.append(s.getsockname()[0])
    except OSError:
        pass
    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            if ip not in found:
                found.append(ip)
    except OSError:
        pass
    return [ip for ip in found if not ip.startswith("127.")]


@router.get("/api/v1/server/info", response_model=ServerInfo, dependencies=[Depends(require_owner)])
async def server_info(request: Request, settings: Settings = Depends(get_settings)) -> ServerInfo:
    port = request.url.port or (443 if request.url.scheme == "https" else 80)
    return ServerInfo(
        version=__version__,
        lan_urls=[f"http://{ip}:{port}" for ip in lan_addresses()],
        ai_configured=settings.ai_configured,
        motion_available=motion_available(settings.pose_model_path),
    )

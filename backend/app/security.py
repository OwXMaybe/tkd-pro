"""Single-owner authentication.

There is no registration and no user table: the backend serves exactly one owner. Access is granted by
- the owner token, whose SHA-256 digest is configured on the server (the token itself is never stored), or
- a device token issued to one of the owner's devices through pairing (only its SHA-256 is stored).

Phones send the token as a bearer header; the web app keeps it in an HttpOnly, SameSite=Strict cookie.
"""

import hashlib
import hmac
import secrets
import time
from dataclasses import dataclass, field

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .config import Settings, get_settings
from .db import DeviceToken, get_session, utcnow

_bearer = HTTPBearer(auto_error=False)

COOKIE_NAME = "tkd_owner"


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def new_token() -> tuple[str, str]:
    """Returns (token, sha256_hex). 32 random bytes, URL-safe."""
    token = secrets.token_urlsafe(32)
    return token, hash_token(token)


def verify_token(token: str, expected_sha256: str) -> bool:
    if not token or not expected_sha256:
        return False
    return hmac.compare_digest(hash_token(token), expected_sha256.strip().lower())


@dataclass
class Owner:
    """The authenticated caller: always the owner, through the owner token or one of their devices."""

    device_id: int | None
    device_name: str

    @property
    def source(self) -> str:
        return f"device:{self.device_id}" if self.device_id is not None else "owner"


def presented_token(request: Request, credentials: HTTPAuthorizationCredentials | None) -> str:
    if credentials is not None:
        return credentials.credentials
    return request.cookies.get(COOKIE_NAME, "")


async def authenticate(token: str, settings: Settings, session: AsyncSession) -> Owner | None:
    if not token:
        return None
    if verify_token(token, settings.owner_token_sha256):
        return Owner(device_id=None, device_name="owner")
    device = (
        await session.execute(
            select(DeviceToken).where(DeviceToken.token_sha256 == hash_token(token), DeviceToken.revoked.is_(False))
        )
    ).scalar_one_or_none()
    if device is None:
        return None
    device.last_used_at = utcnow()
    await session.commit()
    return Owner(device_id=device.id, device_name=device.name)


async def require_owner(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    settings: Settings = Depends(get_settings),
    session: AsyncSession = Depends(get_session),
) -> Owner:
    if not settings.owner_token_sha256:
        # Refuse to run open: an unconfigured server must not accept anyone.
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Owner token is not configured on the server")
    owner = await authenticate(presented_token(request, credentials), settings, session)
    if owner is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or missing owner token")
    return owner


def is_loopback(request: Request) -> bool:
    host = request.client.host if request.client else ""
    return host in ("127.0.0.1", "::1", "localhost")


class RateLimiter:
    """Small in-memory limiter for login and pairing attempts (per client address)."""

    def __init__(self, max_failures: int, window_s: float) -> None:
        self.max_failures = max_failures
        self.window_s = window_s
        self._failures: dict[str, list[float]] = {}

    def check(self, key: str) -> None:
        now = time.monotonic()
        recent = [t for t in self._failures.get(key, []) if now - t < self.window_s]
        self._failures[key] = recent
        if len(recent) >= self.max_failures:
            raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Too many attempts, try again later")

    def fail(self, key: str) -> None:
        self._failures.setdefault(key, []).append(time.monotonic())

    def reset(self) -> None:
        self._failures.clear()


@dataclass
class _Code:
    value: str
    expires_at: float
    attempts: int = 0


@dataclass
class PairingCodes:
    """Short-lived one-time codes.

    - pairing code: 6 digits, shown in the signed-in web app, entered on a new device (phone app or a browser).
    - local login code: random, created by the desktop launcher and only accepted from this computer.
    """

    ttl_s: float = 300
    max_attempts: int = 5
    _pairing: _Code | None = None
    _local: dict[str, float] = field(default_factory=dict)

    def new_pairing_code(self) -> tuple[str, float]:
        code = f"{secrets.randbelow(1_000_000):06d}"
        self._pairing = _Code(code, time.time() + self.ttl_s)
        return code, self._pairing.expires_at

    def use_pairing_code(self, code: str) -> bool:
        current = self._pairing
        if current is None or time.time() > current.expires_at:
            self._pairing = None
            return False
        if hmac.compare_digest(current.value, code.strip()):
            self._pairing = None
            return True
        current.attempts += 1
        if current.attempts >= self.max_attempts:
            self._pairing = None
        return False

    def new_local_code(self) -> str:
        code = secrets.token_urlsafe(24)
        self._local[code] = time.time() + 120
        return code

    def use_local_code(self, code: str) -> bool:
        expires = self._local.pop(code, None)
        return expires is not None and time.time() <= expires


pairing = PairingCodes()
login_limiter = RateLimiter(max_failures=10, window_s=300)

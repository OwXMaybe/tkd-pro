import mimetypes
import sys
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, Request
from fastapi.staticfiles import StaticFiles

from . import __version__
from .config import Settings, get_settings
from .db import create_schema, dispose_engine, init_engine
from .motion.extractor import motion_available
from .routers import auth, coach, motion, sync
from .schemas import HealthResponse
from .security import require_owner


# Windows registries often lack these, which would serve them as text/plain.
mimetypes.add_type("application/manifest+json", ".webmanifest")
mimetypes.add_type("text/javascript", ".js")
mimetypes.add_type("text/css", ".css")
mimetypes.add_type("image/svg+xml", ".svg")


def web_dir() -> Path:
    """The web app shipped next to the package (or inside the PyInstaller bundle)."""
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
    return base / "web"


# Map tiles come from OpenFreeMap; everything else is served by this server.
CSP = (
    "default-src 'self'; "
    "script-src 'self'; "
    "style-src 'self' 'unsafe-inline'; "
    "img-src 'self' data: blob: https://tiles.openfreemap.org; "
    "font-src 'self' https://tiles.openfreemap.org; "
    "connect-src 'self' https://tiles.openfreemap.org; "
    "worker-src 'self' blob:; child-src blob:; "
    "frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_engine()
    await create_schema()
    yield
    await dispose_engine()


def create_app(use_lifespan: bool = True) -> FastAPI:
    app = FastAPI(
        title="TKD PRO Personal Backend",
        version=__version__,
        lifespan=lifespan if use_lifespan else None,
        # No public docs: this is a single-owner private API.
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        response = await call_next(request)
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("Referrer-Policy", "no-referrer")
        response.headers.setdefault("X-Frame-Options", "DENY")
        if not request.url.path.startswith("/api/"):
            response.headers.setdefault("Content-Security-Policy", CSP)
            # The shell must revalidate so updates reach installed apps; the service worker handles offline.
            response.headers.setdefault("Cache-Control", "no-cache")
        else:
            response.headers.setdefault("Cache-Control", "no-store")
        return response

    @app.get("/api/v1/health", response_model=HealthResponse, dependencies=[Depends(require_owner)])
    async def health(settings: Settings = Depends(get_settings)) -> HealthResponse:
        return HealthResponse(
            status="ok",
            ai_configured=settings.ai_configured,
            motion_available=motion_available(settings.pose_model_path),
            version=__version__,
        )

    app.include_router(auth.router)
    app.include_router(coach.router)
    app.include_router(sync.router)
    app.include_router(motion.router)

    static = web_dir()
    if static.is_dir():
        app.mount("/", StaticFiles(directory=static, html=True), name="web")
    return app


app = create_app()

import os

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app import db
from app.config import Settings, get_settings
from app.main import create_app
from app.security import hash_token

TOKEN = "test-owner-token"


@pytest.fixture
def settings() -> Settings:
    return Settings(owner_token_sha256=hash_token(TOKEN), database_url="sqlite+aiosqlite://", openai_api_key="", pose_model_path="/nonexistent")


@pytest_asyncio.fixture
async def client(settings: Settings):
    db.init_engine("sqlite+aiosqlite://")
    await db.create_schema()
    app = create_app(use_lifespan=False)
    app.dependency_overrides[get_settings] = lambda: settings
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c
    await db.dispose_engine()


@pytest.fixture
def auth() -> dict[str, str]:
    return {"Authorization": f"Bearer {TOKEN}"}


os.environ.setdefault("OWNER_TOKEN_SHA256", "")

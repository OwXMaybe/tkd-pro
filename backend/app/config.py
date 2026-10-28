from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables or a .env file."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    owner_token_sha256: str = ""
    database_url: str = "postgresql+asyncpg://tkdpro:tkdpro@localhost:5432/tkdpro"
    openai_api_key: str = ""
    openai_model: str = "gpt-4.1-mini"
    pose_model_path: str = "./models/pose_landmarker_full.task"
    max_upload_mb: int = 200
    # Upper bound on the JSON context the app may send to the AI endpoints.
    max_context_chars: int = 120_000

    @property
    def ai_configured(self) -> bool:
        return bool(self.openai_api_key.strip())


@lru_cache
def get_settings() -> Settings:
    return Settings()

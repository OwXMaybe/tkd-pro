from typing import Any, Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    ai_configured: bool
    motion_available: bool
    version: str


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=8000)


class CoachChatRequest(BaseModel):
    messages: list[ChatTurn] = Field(min_length=1, max_length=40)
    context: dict[str, Any]
    locale: str = "en"


class CoachChatResponse(BaseModel):
    reply: str
    model: str


class WeeklyReportRequest(BaseModel):
    context: dict[str, Any]
    locale: str = "en"


class WeeklyReportResponse(BaseModel):
    report: str
    model: str


class MotionInterpretationRequest(BaseModel):
    technique: str = Field(max_length=120)
    result: dict[str, Any]
    locale: str = "en"


class MotionInterpretationResponse(BaseModel):
    interpretation: str
    model: str


# Entities the devices may sync. Anything else is rejected.
# `run_routes` and `combination_steps` are keyed by their parent id and carry the whole child list.
SYNC_ENTITIES = {
    "athlete_profile", "belt_records", "personal_goals", "competitions",
    "training_sessions", "techniques", "technique_records",
    "combinations", "combination_steps", "combination_practices",
    "matches", "match_rounds", "match_events", "scoring_presets",
    "running_sessions", "run_routes", "reaction_tests", "performance_metrics", "recovery_entries",
    "motion_analyses", "chat_messages", "ai_reports",
}


class SyncItem(BaseModel):
    entity: str = Field(max_length=64)
    entity_id: int
    op: Literal["UPSERT", "DELETE"]
    payload: dict[str, Any]
    client_updated_at: int


class SyncPushRequest(BaseModel):
    items: list[SyncItem] = Field(max_length=500)


class SyncPushResponse(BaseModel):
    accepted: int


class SyncChange(BaseModel):
    entity: str
    entity_id: int
    op: Literal["UPSERT", "DELETE"]
    payload: dict[str, Any]
    client_updated_at: int
    revision: int


class SyncChangesResponse(BaseModel):
    items: list[SyncChange]
    # Pass as `after` in the next call. Equal to the request's `after` when nothing changed.
    next_after: int
    has_more: bool


class LoginRequest(BaseModel):
    token: str = Field(min_length=1, max_length=200)


class PairRequest(BaseModel):
    code: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")
    device_name: str = Field(default="", max_length=80)
    kind: Literal["phone", "web"] = "phone"


class PairResponse(BaseModel):
    # Returned only to the phone app; browsers receive an HttpOnly cookie instead.
    token: str | None


class PairingCodeResponse(BaseModel):
    code: str
    expires_at: int


class SessionInfo(BaseModel):
    device_id: int | None
    device_name: str
    on_server_computer: bool


class DeviceOut(BaseModel):
    id: int
    name: str
    kind: str
    created_at: int
    last_used_at: int | None


class ServerInfo(BaseModel):
    version: str
    lan_urls: list[str]
    ai_configured: bool
    motion_available: bool

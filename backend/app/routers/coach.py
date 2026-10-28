import json

from fastapi import APIRouter, Depends, HTTPException, status

from ..ai.client import AiClient
from ..ai.prompts import coach_system_prompt, motion_prompt, weekly_report_prompt
from ..config import Settings, get_settings
from ..schemas import (
    CoachChatRequest,
    CoachChatResponse,
    MotionInterpretationRequest,
    MotionInterpretationResponse,
    WeeklyReportRequest,
    WeeklyReportResponse,
)
from ..security import require_owner

router = APIRouter(prefix="/api/v1", dependencies=[Depends(require_owner)], tags=["ai"])


def get_ai(settings: Settings = Depends(get_settings)) -> AiClient:
    return AiClient(settings)


def _check_size(payload: dict, settings: Settings) -> None:
    if len(json.dumps(payload, ensure_ascii=False)) > settings.max_context_chars:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "Context too large")


@router.post("/coach/chat", response_model=CoachChatResponse)
async def coach_chat(body: CoachChatRequest, ai: AiClient = Depends(get_ai), settings: Settings = Depends(get_settings)):
    _check_size(body.context, settings)
    if body.messages[-1].role != "user":
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "The last message must be from the user")
    result = await ai.complete(
        coach_system_prompt(body.context, body.locale),
        [m.model_dump() for m in body.messages],
    )
    return CoachChatResponse(reply=result.text, model=result.model)


@router.post("/coach/weekly-report", response_model=WeeklyReportResponse)
async def weekly_report(body: WeeklyReportRequest, ai: AiClient = Depends(get_ai), settings: Settings = Depends(get_settings)):
    _check_size(body.context, settings)
    system, user = weekly_report_prompt(body.context, body.locale)
    result = await ai.complete(system, [{"role": "user", "content": user}], max_tokens=1800)
    return WeeklyReportResponse(report=result.text, model=result.model)


@router.post("/ai/motion-interpretation", response_model=MotionInterpretationResponse)
async def motion_interpretation(
    body: MotionInterpretationRequest, ai: AiClient = Depends(get_ai), settings: Settings = Depends(get_settings)
):
    _check_size(body.result, settings)
    quality = body.result.get("quality", {})
    if quality and not quality.get("passed", False):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Analysis did not pass the quality check")
    system, user = motion_prompt(body.technique, body.result, body.locale)
    result = await ai.complete(system, [{"role": "user", "content": user}])
    return MotionInterpretationResponse(interpretation=result.text, model=result.model)

import asyncio
import os
import tempfile

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from ..config import Settings, get_settings
from ..motion.analyzer import MotionAnalyzer
from ..motion.extractor import MotionUnavailable, VideoTooLong, extract
from ..security import require_owner

router = APIRouter(prefix="/api/v1/motion", dependencies=[Depends(require_owner)], tags=["motion"])


@router.post("/analyze")
async def analyze(
    video: UploadFile = File(...),
    technique: str = Form(""),
    side: str = Form(""),
    settings: Settings = Depends(get_settings),
):
    """Analyses an uploaded technique video. The upload is processed in a temp file and deleted afterwards."""
    max_bytes = settings.max_upload_mb * 1024 * 1024
    suffix = os.path.splitext(video.filename or "")[1] or ".mp4"
    fd, path = tempfile.mkstemp(suffix=suffix)
    try:
        size = 0
        with os.fdopen(fd, "wb") as out:
            while chunk := await video.read(1024 * 1024):
                size += len(chunk)
                if size > max_bytes:
                    raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "Video too large")
                out.write(chunk)
        try:
            info, frames = await asyncio.to_thread(extract, path, settings.pose_model_path)
        except MotionUnavailable as exc:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, str(exc)) from exc
        except VideoTooLong as exc:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Video longer than 30 seconds") from exc
        except ValueError as exc:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, str(exc)) from exc
        result = MotionAnalyzer().analyze(info, frames, side.upper() or None)
        return {"result": result, "frames": frames}
    finally:
        if os.path.exists(path):
            os.remove(path)

"""MediaPipe Pose Landmarker + OpenCV frame extraction for server-side analysis.

Heavy dependencies are imported lazily so the rest of the backend runs without them.
"""

from __future__ import annotations

import os

from .analyzer import Frame, VideoInfo

TARGET_FPS = 20.0
MAX_FRAME_SIDE = 640
MAX_DURATION_MS = 30_000


class MotionUnavailable(RuntimeError):
    pass


class VideoTooLong(ValueError):
    pass


def motion_available(model_path: str) -> bool:
    try:
        import cv2  # noqa: F401
        import mediapipe  # noqa: F401
    except ImportError:
        return False
    return os.path.isfile(model_path)


def extract(video_path: str, model_path: str) -> tuple[VideoInfo, list[Frame]]:
    try:
        import cv2
        import mediapipe as mp
        from mediapipe.tasks.python import BaseOptions
        from mediapipe.tasks.python.vision import PoseLandmarker, PoseLandmarkerOptions, RunningMode
    except ImportError as exc:
        raise MotionUnavailable("mediapipe/opencv are not installed") from exc
    if not os.path.isfile(model_path):
        raise MotionUnavailable(f"Pose model not found at {model_path}")

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise ValueError("Unsupported or corrupt video")
    try:
        native_fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        duration_ms = int(frame_count / native_fps * 1000) if native_fps > 0 else 0
        if duration_ms > MAX_DURATION_MS:
            raise VideoTooLong()
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fps = min(native_fps, TARGET_FPS)
        step = max(native_fps / fps, 1.0)

        options = PoseLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=model_path),
            running_mode=RunningMode.VIDEO,
            num_poses=1,
            min_pose_detection_confidence=0.5,
            min_pose_presence_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        frames: list[Frame] = []
        with PoseLandmarker.create_from_options(options) as landmarker:
            index = 0
            next_sample = 0.0
            while True:
                ok, bgr = cap.read()
                if not ok:
                    break
                if index + 1e-6 >= next_sample:
                    t_ms = int(index / native_fps * 1000)
                    h, w = bgr.shape[:2]
                    scale = MAX_FRAME_SIDE / max(h, w)
                    if scale < 1:
                        bgr = cv2.resize(bgr, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
                    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
                    result = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), t_ms)
                    landmarks = []
                    if result.pose_landmarks:
                        landmarks = [
                            {"x": lm.x, "y": lm.y, "z": lm.z, "visibility": float(lm.visibility or 0.0)}
                            for lm in result.pose_landmarks[0]
                        ]
                    frames.append({"timestampMs": t_ms, "landmarks": landmarks})
                    next_sample += step
                index += 1
        return VideoInfo(duration_ms, width, height, len(frames), fps), frames
    finally:
        cap.release()

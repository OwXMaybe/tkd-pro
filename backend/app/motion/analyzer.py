"""Server-side port of the app's MotionAnalyzer (core/domain/motion).

Produces exactly the same JSON shape (camelCase keys) as the Kotlin implementation, so results from
either engine are stored and displayed identically. All metrics are 2D estimates of visible movement;
nothing here measures force, impact speed, scoring validity or injury risk.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from statistics import mean, stdev
from typing import Any

# MediaPipe 33-landmark indices
L_SHOULDER, R_SHOULDER = 11, 12
L_HIP, R_HIP = 23, 24
L_KNEE, R_KNEE = 25, 26
L_ANKLE, R_ANKLE = 27, 28
KEY_LANDMARKS = [L_SHOULDER, R_SHOULDER, L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE]
LANDMARK_COUNT = 33

# Quality thresholds (identical to VideoQualityChecker.kt)
MIN_DURATION_MS = 1000
MIN_FRAMES = 15
MIN_FPS = 10.0
GOOD_FPS = 24.0
MIN_SHORT_SIDE_PX = 360
MIN_DETECTION_RATE = 0.6
GOOD_DETECTION_RATE = 0.85
MIN_VISIBILITY = 0.5
GOOD_VISIBILITY = 0.7
MAX_OUT_OF_FRAME_RATE = 0.3


@dataclass
class VideoInfo:
    durationMs: int
    width: int
    height: int
    sampledFrames: int
    sampledFps: float


Frame = dict[str, Any]  # {"timestampMs": int, "landmarks": [{"x","y","z","visibility"}]}


def has_pose(f: Frame) -> bool:
    return len(f.get("landmarks", [])) >= LANDMARK_COUNT


def check_quality(info: VideoInfo, frames: list[Frame]) -> dict[str, Any]:
    blocking: list[str] = []
    warnings: list[str] = []
    if info.durationMs < MIN_DURATION_MS:
        blocking.append("TOO_SHORT")
    if info.sampledFrames < MIN_FRAMES:
        blocking.append("TOO_FEW_FRAMES")
    if info.sampledFps < MIN_FPS:
        blocking.append("LOW_FRAME_RATE")
    elif info.sampledFps < GOOD_FPS:
        warnings.append("LOW_FRAME_RATE")
    if min(info.width, info.height) < MIN_SHORT_SIDE_PX:
        warnings.append("LOW_RESOLUTION")

    detected = [f for f in frames if has_pose(f)]
    rate = len(detected) / len(frames) if frames else 0.0
    if rate < MIN_DETECTION_RATE:
        blocking.append("POSE_RARELY_DETECTED")
    elif rate < GOOD_DETECTION_RATE:
        warnings.append("POSE_RARELY_DETECTED")

    vis = [f["landmarks"][i]["visibility"] for f in detected for i in KEY_LANDMARKS]
    mean_vis = mean(vis) if vis else 0.0
    if mean_vis < MIN_VISIBILITY:
        blocking.append("KEY_LANDMARKS_LOW_VISIBILITY")
    elif mean_vis < GOOD_VISIBILITY:
        warnings.append("KEY_LANDMARKS_LOW_VISIBILITY")

    out = sum(
        1 for f in detected
        if any(not (-0.02 <= f["landmarks"][i]["x"] <= 1.02 and -0.02 <= f["landmarks"][i]["y"] <= 1.02) for i in KEY_LANDMARKS)
    )
    out_rate = out / len(detected) if detected else 0.0
    if out_rate > MAX_OUT_OF_FRAME_RATE:
        blocking.append("BODY_OUT_OF_FRAME")
    elif out_rate > 0.1:
        warnings.append("BODY_OUT_OF_FRAME")

    return {
        "passed": not blocking,
        "blocking": blocking,
        "warnings": warnings,
        "poseDetectionRate": rate,
        "meanKeyLandmarkVisibility": mean_vis,
        "outOfFrameRate": out_rate,
    }


class Kinematics:
    def __init__(self, width: int, height: int):
        self.w = width
        self.h = height

    def p(self, lm: dict) -> tuple[float, float]:
        return lm["x"] * self.w, lm["y"] * self.h

    def angle(self, a: dict, b: dict, c: dict) -> float:
        ax, ay = self.p(a)
        bx, by = self.p(b)
        cx, cy = self.p(c)
        v1 = (ax - bx, ay - by)
        v2 = (cx - bx, cy - by)
        n1 = math.hypot(*v1)
        n2 = math.hypot(*v2)
        if n1 == 0 or n2 == 0:
            return math.nan
        cos = max(-1.0, min(1.0, (v1[0] * v2[0] + v1[1] * v2[1]) / (n1 * n2)))
        return math.degrees(math.acos(cos))

    def knee(self, f: Frame, side: str) -> float:
        l = f["landmarks"]
        return self.angle(l[L_HIP], l[L_KNEE], l[L_ANKLE]) if side == "LEFT" else self.angle(l[R_HIP], l[R_KNEE], l[R_ANKLE])

    def hip(self, f: Frame, side: str) -> float:
        l = f["landmarks"]
        return self.angle(l[L_SHOULDER], l[L_HIP], l[L_KNEE]) if side == "LEFT" else self.angle(l[R_SHOULDER], l[R_HIP], l[R_KNEE])

    def _mid(self, a: dict, b: dict) -> tuple[float, float]:
        ax, ay = self.p(a)
        bx, by = self.p(b)
        return (ax + bx) / 2, (ay + by) / 2

    def torso(self, f: Frame) -> float:
        l = f["landmarks"]
        hx, hy = self._mid(l[L_HIP], l[R_HIP])
        sx, sy = self._mid(l[L_SHOULDER], l[R_SHOULDER])
        return math.hypot(hx - sx, hy - sy)

    def trunk_lean(self, f: Frame) -> float:
        l = f["landmarks"]
        hx, hy = self._mid(l[L_HIP], l[R_HIP])
        sx, sy = self._mid(l[L_SHOULDER], l[R_SHOULDER])
        return abs(math.degrees(math.atan2(sx - hx, hy - sy)))

    def hip_tilt(self, f: Frame) -> float:
        l = f["landmarks"]
        lx, ly = self.p(l[L_HIP])
        rx, ry = self.p(l[R_HIP])
        deg = abs(math.degrees(math.atan2(ry - ly, rx - lx)))
        return 180 - deg if deg > 90 else deg

    def ankle_height(self, f: Frame, side: str) -> float:
        t = self.torso(f)
        if t <= 0:
            return math.nan
        l = f["landmarks"]
        _, ay = self.p(l[L_ANKLE] if side == "LEFT" else l[R_ANKLE])
        _, hy = self._mid(l[L_HIP], l[R_HIP])
        return (hy - ay) / t

    def hip_mid_x(self, f: Frame) -> float:
        t = self.torso(f)
        if t <= 0:
            return math.nan
        l = f["landmarks"]
        x, _ = self._mid(l[L_HIP], l[R_HIP])
        return x / t


def _finite(v: float) -> float | None:
    return v if math.isfinite(v) else None


def stats(values: list[float]) -> dict[str, Any] | None:
    v = [x for x in values if math.isfinite(x)]
    if not v:
        return None
    m = mean(v)
    cv = (stdev(v) / abs(m)) if len(v) >= 2 and m != 0 else None
    return {"mean": m, "min": min(v), "max": max(v), "cv": cv}


class MotionAnalyzer:
    def __init__(self, rise_threshold: float = 0.35, min_peak_height: float = -0.6, min_rep_duration_ms: int = 150, visibility_threshold: float = 0.5):
        self.rise = rise_threshold
        self.min_peak = min_peak_height
        self.min_rep_ms = min_rep_duration_ms
        self.vis = visibility_threshold

    def _key_visible(self, f: Frame) -> bool:
        return all(f["landmarks"][i]["visibility"] >= self.vis for i in KEY_LANDMARKS)

    def analyze(self, info: VideoInfo, frames: list[Frame], preferred_side: str | None = None) -> dict[str, Any]:
        quality = check_quality(info, frames)
        video = info.__dict__.copy()
        if not quality["passed"]:
            return {"video": video, "quality": quality, "repetitions": [], "sides": [], "keyframes": [], "timeline": [], "hipSwayStd": None, "notes": []}

        k = Kinematics(info.width, info.height)
        usable = [f for f in frames if has_pose(f) and self._key_visible(f)]
        timeline = [
            {
                "timestampMs": f["timestampMs"],
                "leftKnee": _finite(k.knee(f, "LEFT")),
                "rightKnee": _finite(k.knee(f, "RIGHT")),
                "trunkLean": _finite(k.trunk_lean(f)),
                "leftAnkleHeight": _finite(k.ankle_height(f, "LEFT")),
                "rightAnkleHeight": _finite(k.ankle_height(f, "RIGHT")),
            }
            for f in usable
        ]
        sides = [preferred_side] if preferred_side in ("LEFT", "RIGHT") else ["LEFT", "RIGHT"]
        reps = sorted((r for s in sides for r in self._segment(usable, s, k)), key=lambda r: r["startMs"])
        for i, r in enumerate(reps):
            r["index"] = i + 1

        notes: list[str] = []
        if not reps:
            notes.append("NO_REPETITIONS")
        if len(reps) == 1:
            notes.append("SINGLE_REPETITION")

        rep_frames = [f for f in usable if any(r["startMs"] <= f["timestampMs"] <= r["endMs"] for r in reps)]
        xs = [x for x in (k.hip_mid_x(f) for f in rep_frames) if math.isfinite(x)]
        sway = stdev(xs) if len(xs) >= 2 else None

        keyframes = []
        for r in reps[:3]:
            keyframes += [
                {"timestampMs": r["startMs"], "label": "START", "repetition": r["index"]},
                {"timestampMs": r["peakMs"], "label": "PEAK", "repetition": r["index"]},
                {"timestampMs": r["endMs"], "label": "END", "repetition": r["index"]},
            ]

        side_summaries = []
        for s in ("LEFT", "RIGHT"):
            rs = [r for r in reps if r["side"] == s]
            if rs:
                side_summaries.append({
                    "side": s,
                    "repetitions": len(rs),
                    "peakAnkleHeight": stats([r["peakAnkleHeight"] for r in rs]),
                    "kickKneeAngleAtPeak": stats([r["kickKneeAngleAtPeak"] for r in rs]),
                    "supportKneeAngleAtPeak": stats([r["supportKneeAngleAtPeak"] for r in rs]),
                    "trunkLeanAtPeak": stats([r["trunkLeanAtPeak"] for r in rs]),
                    "timeToPeakMs": stats([float(r["timeToPeakMs"]) for r in rs]),
                })

        return {
            "video": video,
            "quality": quality,
            "repetitions": reps,
            "sides": side_summaries,
            "keyframes": keyframes,
            "timeline": timeline,
            "hipSwayStd": sway,
            "notes": notes,
        }

    def _segment(self, frames: list[Frame], side: str, k: Kinematics) -> list[dict[str, Any]]:
        if len(frames) < 3:
            return []
        heights = [k.ankle_height(f, side) for f in frames]
        finite = sorted(h for h in heights if math.isfinite(h))
        if not finite:
            return []
        baseline = finite[min(int(len(finite) * 0.2), len(finite) - 1)]
        threshold = baseline + self.rise
        support = "RIGHT" if side == "LEFT" else "LEFT"
        reps: list[dict[str, Any]] = []
        i = 0
        n = len(frames)
        while i < n:
            if math.isfinite(heights[i]) and heights[i] > threshold:
                start = max(i - 1, 0)
                peak = i
                j = i
                while j < n and (not math.isfinite(heights[j]) or heights[j] > threshold):
                    if math.isfinite(heights[j]) and heights[j] > heights[peak]:
                        peak = j
                    j += 1
                end = min(j, n - 1)
                start_ms = frames[start]["timestampMs"]
                end_ms = frames[end]["timestampMs"]
                if end_ms - start_ms >= self.min_rep_ms and heights[peak] >= self.min_peak:
                    f = frames[peak]
                    reps.append({
                        "index": 0,
                        "side": side,
                        "startMs": start_ms,
                        "peakMs": f["timestampMs"],
                        "endMs": end_ms,
                        "peakAnkleHeight": heights[peak],
                        "kickKneeAngleAtPeak": k.knee(f, side),
                        "kickHipAngleAtPeak": k.hip(f, side),
                        "supportKneeAngleAtPeak": k.knee(f, support),
                        "trunkLeanAtPeak": k.trunk_lean(f),
                        "hipTiltAtPeak": k.hip_tilt(f),
                        "timeToPeakMs": f["timestampMs"] - start_ms,
                        "retractionMs": end_ms - f["timestampMs"],
                    })
                i = j + 1
            else:
                i += 1
        return reps

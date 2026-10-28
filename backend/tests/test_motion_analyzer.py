import math

from app.motion.analyzer import (
    L_ANKLE, L_HIP, L_KNEE, L_SHOULDER, LANDMARK_COUNT, R_ANKLE, R_HIP, R_KNEE, R_SHOULDER,
    MotionAnalyzer, VideoInfo,
)


def lm(x, y, vis=0.95):
    return {"x": x, "y": y, "z": 0.0, "visibility": vis}


def frames(count=150, fps=30.0, kicks=3, vis=0.95):
    """Synthetic fixture mirroring MotionAnalyzerTest.kt: the right ankle rises in `kicks` bumps."""
    out = []
    for i in range(count):
        t = int(i * 1000 / fps)
        phase = i / count * kicks
        bump = max(0.0, math.sin(phase * 2 * math.pi)) ** 2
        l = [lm(0.5, 0.5, vis) for _ in range(LANDMARK_COUNT)]
        l[L_SHOULDER] = lm(0.45, 0.30, vis)
        l[R_SHOULDER] = lm(0.55, 0.30, vis)
        l[L_HIP] = lm(0.46, 0.50, vis)
        l[R_HIP] = lm(0.54, 0.50, vis)
        l[L_KNEE] = lm(0.46, 0.65, vis)
        l[L_ANKLE] = lm(0.46, 0.80, vis)
        ay = 0.80 - bump * 0.35
        ax = 0.54 + bump * 0.25
        l[R_KNEE] = lm((0.54 + ax) / 2 + 0.02, (0.50 + ay) / 2, vis)
        l[R_ANKLE] = lm(ax, ay, vis)
        out.append({"timestampMs": t, "landmarks": l})
    return out


def info(n=150, fps=30.0):
    return VideoInfo(int(n / fps * 1000), 1080, 1920, n, fps)


def test_detects_repetitions_like_kotlin():
    r = MotionAnalyzer().analyze(info(), frames())
    assert r["quality"]["passed"]
    assert len(r["repetitions"]) == 3
    assert all(rep["side"] == "RIGHT" for rep in r["repetitions"])
    assert len(r["keyframes"]) == 9
    right = r["sides"][0]
    assert right["repetitions"] == 3
    assert abs(right["peakAnkleHeight"]["mean"] - 0.25) < 0.02


def test_poor_visibility_blocks_without_metrics():
    r = MotionAnalyzer().analyze(info(), frames(vis=0.2))
    assert not r["quality"]["passed"]
    assert "KEY_LANDMARKS_LOW_VISIBILITY" in r["quality"]["blocking"]
    assert r["repetitions"] == [] and r["timeline"] == []


def test_output_uses_app_schema_keys():
    r = MotionAnalyzer().analyze(info(), frames())
    assert set(r) == {"video", "quality", "repetitions", "sides", "keyframes", "timeline", "hipSwayStd", "notes"}
    assert set(r["video"]) == {"durationMs", "width", "height", "sampledFrames", "sampledFps"}

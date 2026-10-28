package com.tkdpro.core.domain.motion

import kotlinx.serialization.Serializable

@Serializable
enum class QualityIssue {
    TOO_SHORT,
    TOO_FEW_FRAMES,
    LOW_FRAME_RATE,
    LOW_RESOLUTION,
    POSE_RARELY_DETECTED,
    KEY_LANDMARKS_LOW_VISIBILITY,
    BODY_OUT_OF_FRAME,
}

@Serializable
data class QualityReport(
    val passed: Boolean,
    /** Issues that block reliable analysis. */
    val blocking: List<QualityIssue>,
    /** Issues that reduce reliability but still allow analysis. */
    val warnings: List<QualityIssue>,
    val poseDetectionRate: Double,
    val meanKeyLandmarkVisibility: Double,
    val outOfFrameRate: Double,
)

/**
 * Checks whether a video and its pose detections are good enough for movement analysis.
 * When it fails, the report explains why, and no metrics are produced.
 */
object VideoQualityChecker {
    const val MIN_DURATION_MS = 1_000L
    const val MIN_FRAMES = 15
    const val MIN_FPS = 10.0
    const val GOOD_FPS = 24.0
    const val MIN_SHORT_SIDE_PX = 360
    const val MIN_DETECTION_RATE = 0.6
    const val GOOD_DETECTION_RATE = 0.85
    const val MIN_VISIBILITY = 0.5
    const val GOOD_VISIBILITY = 0.7
    const val MAX_OUT_OF_FRAME_RATE = 0.3

    fun check(info: VideoInfo, frames: List<PoseFrame>): QualityReport {
        val blocking = mutableListOf<QualityIssue>()
        val warnings = mutableListOf<QualityIssue>()

        if (info.durationMs < MIN_DURATION_MS) blocking += QualityIssue.TOO_SHORT
        if (info.sampledFrames < MIN_FRAMES) blocking += QualityIssue.TOO_FEW_FRAMES
        when {
            info.sampledFps < MIN_FPS -> blocking += QualityIssue.LOW_FRAME_RATE
            info.sampledFps < GOOD_FPS -> warnings += QualityIssue.LOW_FRAME_RATE
        }
        if (minOf(info.width, info.height) < MIN_SHORT_SIDE_PX) warnings += QualityIssue.LOW_RESOLUTION

        val detected = frames.filter { it.hasPose }
        val detectionRate = if (frames.isEmpty()) 0.0 else detected.size.toDouble() / frames.size
        when {
            detectionRate < MIN_DETECTION_RATE -> blocking += QualityIssue.POSE_RARELY_DETECTED
            detectionRate < GOOD_DETECTION_RATE -> warnings += QualityIssue.POSE_RARELY_DETECTED
        }

        val visibilities = detected.flatMap { f -> PoseIndex.KEY_LANDMARKS.map { f.landmarks[it].visibility.toDouble() } }
        val meanVis = if (visibilities.isEmpty()) 0.0 else visibilities.average()
        when {
            meanVis < MIN_VISIBILITY -> blocking += QualityIssue.KEY_LANDMARKS_LOW_VISIBILITY
            meanVis < GOOD_VISIBILITY -> warnings += QualityIssue.KEY_LANDMARKS_LOW_VISIBILITY
        }

        val outOfFrame = detected.count { f ->
            PoseIndex.KEY_LANDMARKS.any { i ->
                val l = f.landmarks[i]
                l.x < -0.02f || l.x > 1.02f || l.y < -0.02f || l.y > 1.02f
            }
        }
        val outRate = if (detected.isEmpty()) 0.0 else outOfFrame.toDouble() / detected.size
        if (outRate > MAX_OUT_OF_FRAME_RATE) blocking += QualityIssue.BODY_OUT_OF_FRAME
        else if (outRate > 0.1) warnings += QualityIssue.BODY_OUT_OF_FRAME

        return QualityReport(
            passed = blocking.isEmpty(),
            blocking = blocking,
            warnings = warnings,
            poseDetectionRate = detectionRate,
            meanKeyLandmarkVisibility = meanVis,
            outOfFrameRate = outRate,
        )
    }
}

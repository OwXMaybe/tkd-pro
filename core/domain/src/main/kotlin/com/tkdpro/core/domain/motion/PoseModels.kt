package com.tkdpro.core.domain.motion

import kotlinx.serialization.Serializable

/** A single MediaPipe pose landmark in normalised image coordinates (0..1). */
@Serializable
data class Landmark(val x: Float, val y: Float, val z: Float, val visibility: Float)

/** Pose landmarks for one video frame. [landmarks] is empty when no pose was detected. */
@Serializable
data class PoseFrame(val timestampMs: Long, val landmarks: List<Landmark>) {
    val hasPose: Boolean get() = landmarks.size >= PoseIndex.COUNT
}

/** MediaPipe Pose Landmarker indices (33-point topology). */
object PoseIndex {
    const val COUNT = 33
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT = 31
    const val RIGHT_FOOT = 32

    /** Landmarks required for lower-body kick analysis. */
    val KEY_LANDMARKS = listOf(
        LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE,
    )

    /** Skeleton edges used to draw the overlay. */
    val CONNECTIONS = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER, LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
        LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP, LEFT_HIP to RIGHT_HIP,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, LEFT_ANKLE to LEFT_HEEL, LEFT_HEEL to LEFT_FOOT, LEFT_ANKLE to LEFT_FOOT,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE, RIGHT_ANKLE to RIGHT_HEEL, RIGHT_HEEL to RIGHT_FOOT, RIGHT_ANKLE to RIGHT_FOOT,
    )
}

enum class Side { LEFT, RIGHT }

/** Facts read directly from the video container/decoder. These are measured, not estimated. */
@Serializable
data class VideoInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sampledFrames: Int,
    val sampledFps: Double,
)

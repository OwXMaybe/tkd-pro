package com.tkdpro.core.domain.motion

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** Per-repetition observations. Every angle/height is a 2D computer-vision estimate. */
@Serializable
data class RepetitionMetrics(
    val index: Int,
    val side: String,
    val startMs: Long,
    val peakMs: Long,
    val endMs: Long,
    val peakAnkleHeight: Double,
    val kickKneeAngleAtPeak: Double,
    val kickHipAngleAtPeak: Double,
    val supportKneeAngleAtPeak: Double,
    val trunkLeanAtPeak: Double,
    val hipTiltAtPeak: Double,
    val timeToPeakMs: Long,
    val retractionMs: Long,
)

@Serializable
data class MetricStats(val mean: Double, val min: Double, val max: Double, val cv: Double?)

@Serializable
data class SideSummary(
    val side: String,
    val repetitions: Int,
    val peakAnkleHeight: MetricStats?,
    val kickKneeAngleAtPeak: MetricStats?,
    val supportKneeAngleAtPeak: MetricStats?,
    val trunkLeanAtPeak: MetricStats?,
    val timeToPeakMs: MetricStats?,
)

@Serializable
data class TimelinePoint(
    val timestampMs: Long,
    val leftKnee: Double?,
    val rightKnee: Double?,
    val trunkLean: Double?,
    val leftAnkleHeight: Double?,
    val rightAnkleHeight: Double?,
)

@Serializable
data class Keyframe(val timestampMs: Long, val label: String, val repetition: Int)

@Serializable
data class MotionResult(
    val video: VideoInfo,
    val quality: QualityReport,
    val repetitions: List<RepetitionMetrics>,
    val sides: List<SideSummary>,
    val keyframes: List<Keyframe>,
    val timeline: List<TimelinePoint>,
    /** Horizontal hip sway (std-dev in torso lengths) during repetitions — a balance-related movement pattern. */
    val hipSwayStd: Double?,
    val notes: List<String>,
)

/**
 * Turns pose frames into observable movement metrics for kicking techniques.
 *
 * Repetitions are segmented on the ankle-height signal: a repetition starts when an ankle rises above
 * [riseThreshold] torso lengths relative to its baseline, peaks, and ends when it returns below it.
 * Nothing here measures force, impact speed or scoring validity.
 */
class MotionAnalyzer(
    private val riseThreshold: Double = 0.35,
    private val minPeakHeight: Double = -0.6,
    private val minRepDurationMs: Long = 150,
    private val visibilityThreshold: Float = 0.5f,
) {

    fun analyze(info: VideoInfo, frames: List<PoseFrame>, preferredSide: Side? = null): MotionResult {
        val quality = VideoQualityChecker.check(info, frames)
        if (!quality.passed) {
            return MotionResult(info, quality, emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList())
        }
        val k = Kinematics(info.width, info.height)
        val usable = frames.filter { it.hasPose && keyVisible(it) }
        val timeline = usable.map { f ->
            TimelinePoint(
                timestampMs = f.timestampMs,
                leftKnee = k.kneeAngle(f, Side.LEFT).finiteOrNull(),
                rightKnee = k.kneeAngle(f, Side.RIGHT).finiteOrNull(),
                trunkLean = k.trunkLean(f).finiteOrNull(),
                leftAnkleHeight = k.ankleHeight(f, Side.LEFT).finiteOrNull(),
                rightAnkleHeight = k.ankleHeight(f, Side.RIGHT).finiteOrNull(),
            )
        }

        val sides = preferredSide?.let { listOf(it) } ?: Side.entries
        val reps = sides.flatMap { side -> segment(usable, side, k) }
            .sortedBy { it.startMs }
            .mapIndexed { i, r -> r.copy(index = i + 1) }

        val notes = mutableListOf<String>()
        if (reps.isEmpty()) notes += NOTE_NO_REPETITIONS
        if (reps.size == 1) notes += NOTE_SINGLE_REPETITION

        val repFrames = usable.filter { f -> reps.any { f.timestampMs in it.startMs..it.endMs } }
        val sway = repFrames.map { k.hipMidX(it) }.filter { it.isFinite() }.std()

        val keyframes = reps.take(MAX_KEYFRAME_REPS).flatMap { r ->
            listOf(
                Keyframe(r.startMs, KF_START, r.index),
                Keyframe(r.peakMs, KF_PEAK, r.index),
                Keyframe(r.endMs, KF_END, r.index),
            )
        }

        return MotionResult(
            video = info,
            quality = quality,
            repetitions = reps,
            sides = Side.entries.mapNotNull { side -> summarize(side, reps.filter { it.side == side.name }) },
            keyframes = keyframes,
            timeline = timeline,
            hipSwayStd = sway,
            notes = notes,
        )
    }

    private fun keyVisible(f: PoseFrame) = PoseIndex.KEY_LANDMARKS.all { f.landmarks[it].visibility >= visibilityThreshold }

    private fun segment(frames: List<PoseFrame>, side: Side, k: Kinematics): List<RepetitionMetrics> {
        if (frames.size < 3) return emptyList()
        val heights = frames.map { k.ankleHeight(it, side) }
        val finite = heights.filter { it.isFinite() }
        if (finite.isEmpty()) return emptyList()
        // Baseline = low percentile of ankle height (standing / guard position).
        val baseline = finite.sorted()[(finite.size * 0.2).toInt().coerceAtMost(finite.size - 1)]
        val threshold = baseline + riseThreshold
        val reps = mutableListOf<RepetitionMetrics>()
        var i = 0
        while (i < frames.size) {
            if (heights[i].isFinite() && heights[i] > threshold) {
                val start = (i - 1).coerceAtLeast(0)
                var peak = i
                var j = i
                while (j < frames.size && (!heights[j].isFinite() || heights[j] > threshold)) {
                    if (heights[j].isFinite() && heights[j] > heights[peak]) peak = j
                    j++
                }
                val end = j.coerceAtMost(frames.size - 1)
                val startMs = frames[start].timestampMs
                val endMs = frames[end].timestampMs
                if (endMs - startMs >= minRepDurationMs && heights[peak] >= minPeakHeight) {
                    val f = frames[peak]
                    val support = if (side == Side.LEFT) Side.RIGHT else Side.LEFT
                    reps += RepetitionMetrics(
                        index = 0,
                        side = side.name,
                        startMs = startMs,
                        peakMs = f.timestampMs,
                        endMs = endMs,
                        peakAnkleHeight = heights[peak],
                        kickKneeAngleAtPeak = k.kneeAngle(f, side),
                        kickHipAngleAtPeak = k.hipAngle(f, side),
                        supportKneeAngleAtPeak = k.kneeAngle(f, support),
                        trunkLeanAtPeak = k.trunkLean(f),
                        hipTiltAtPeak = k.hipLineTilt(f),
                        timeToPeakMs = f.timestampMs - startMs,
                        retractionMs = endMs - f.timestampMs,
                    )
                }
                i = j + 1
            } else {
                i++
            }
        }
        return reps
    }

    private fun summarize(side: Side, reps: List<RepetitionMetrics>): SideSummary? {
        if (reps.isEmpty()) return null
        return SideSummary(
            side = side.name,
            repetitions = reps.size,
            peakAnkleHeight = stats(reps.map { it.peakAnkleHeight }),
            kickKneeAngleAtPeak = stats(reps.map { it.kickKneeAngleAtPeak }),
            supportKneeAngleAtPeak = stats(reps.map { it.supportKneeAngleAtPeak }),
            trunkLeanAtPeak = stats(reps.map { it.trunkLeanAtPeak }),
            timeToPeakMs = stats(reps.map { it.timeToPeakMs.toDouble() }),
        )
    }

    companion object {
        const val NOTE_NO_REPETITIONS = "NO_REPETITIONS"
        const val NOTE_SINGLE_REPETITION = "SINGLE_REPETITION"
        const val KF_START = "START"
        const val KF_PEAK = "PEAK"
        const val KF_END = "END"
        private const val MAX_KEYFRAME_REPS = 3

        fun stats(values: List<Double>): MetricStats? {
            val v = values.filter { it.isFinite() }
            if (v.isEmpty()) return null
            val mean = v.average()
            val cv = if (v.size >= 2 && mean != 0.0) (v.std() ?: 0.0) / kotlin.math.abs(mean) else null
            return MetricStats(mean, v.min(), v.max(), cv)
        }
    }
}

private fun Double.finiteOrNull(): Double? = if (isFinite()) this else null

internal fun List<Double>.std(): Double? {
    if (size < 2) return null
    val m = average()
    return sqrt(sumOf { (it - m) * (it - m) } / (size - 1))
}

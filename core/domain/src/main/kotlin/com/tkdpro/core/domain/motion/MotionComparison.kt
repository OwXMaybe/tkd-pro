package com.tkdpro.core.domain.motion

/** Difference between two analyses of the same technique, side by side. */
data class MetricDelta(val metric: String, val side: String, val before: Double, val after: Double) {
    val delta: Double get() = after - before
}

object MotionComparison {
    const val PEAK_ANKLE_HEIGHT = "peakAnkleHeight"
    const val KICK_KNEE_ANGLE = "kickKneeAngleAtPeak"
    const val SUPPORT_KNEE_ANGLE = "supportKneeAngleAtPeak"
    const val TRUNK_LEAN = "trunkLeanAtPeak"
    const val TIME_TO_PEAK = "timeToPeakMs"

    /** Compares only metrics present in both analyses; missing values are skipped, never filled. */
    fun compare(before: MotionResult, after: MotionResult): List<MetricDelta> {
        val out = mutableListOf<MetricDelta>()
        for (a in before.sides) {
            val b = after.sides.firstOrNull { it.side == a.side } ?: continue
            fun add(name: String, x: MetricStats?, y: MetricStats?) {
                if (x != null && y != null) out += MetricDelta(name, a.side, x.mean, y.mean)
            }
            add(PEAK_ANKLE_HEIGHT, a.peakAnkleHeight, b.peakAnkleHeight)
            add(KICK_KNEE_ANGLE, a.kickKneeAngleAtPeak, b.kickKneeAngleAtPeak)
            add(SUPPORT_KNEE_ANGLE, a.supportKneeAngleAtPeak, b.supportKneeAngleAtPeak)
            add(TRUNK_LEAN, a.trunkLeanAtPeak, b.trunkLeanAtPeak)
            add(TIME_TO_PEAK, a.timeToPeakMs, b.timeToPeakMs)
        }
        return out
    }

    /** Left vs right differences inside one analysis, when both sides were recorded. */
    fun leftRight(result: MotionResult): List<MetricDelta> {
        val l = result.sides.firstOrNull { it.side == Side.LEFT.name } ?: return emptyList()
        val r = result.sides.firstOrNull { it.side == Side.RIGHT.name } ?: return emptyList()
        val out = mutableListOf<MetricDelta>()
        fun add(name: String, x: MetricStats?, y: MetricStats?) {
            if (x != null && y != null) out += MetricDelta(name, "LEFT_VS_RIGHT", x.mean, y.mean)
        }
        add(PEAK_ANKLE_HEIGHT, l.peakAnkleHeight, r.peakAnkleHeight)
        add(KICK_KNEE_ANGLE, l.kickKneeAngleAtPeak, r.kickKneeAngleAtPeak)
        add(SUPPORT_KNEE_ANGLE, l.supportKneeAngleAtPeak, r.supportKneeAngleAtPeak)
        add(TRUNK_LEAN, l.trunkLeanAtPeak, r.trunkLeanAtPeak)
        add(TIME_TO_PEAK, l.timeToPeakMs, r.timeToPeakMs)
        return out
    }
}

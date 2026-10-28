package com.tkdpro.core.domain.run

import com.tkdpro.core.domain.gps.GeoMath

/** An accepted, stored GPS point of a run. [segment] increments after every pause/resume or signal gap. */
data class RoutePoint(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float,
    val segment: Int,
)

data class Split(
    val index: Int,
    /** Distance covered in this split (full split distance, or less for the final partial split). */
    val distanceM: Double,
    val durationMs: Long,
    val elevationGainM: Double,
) {
    val paceSecPerKm: Double? get() = Pace.secondsPerKm(distanceM, durationMs)
}

/**
 * Pure calculations over a stored route. Distance and time only accumulate within a segment,
 * so the gap between a pause and a resume never counts.
 */
object RouteMetrics {

    fun totalDistanceM(points: List<RoutePoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> if (a.segment == b.segment) dist(a, b) else 0.0 }

    fun movingTimeMs(points: List<RoutePoint>): Long =
        points.zipWithNext().sumOf { (a, b) -> if (a.segment == b.segment) b.timeMs - a.timeMs else 0L }

    /**
     * Elevation gain with a hysteresis threshold that suppresses GPS altitude noise.
     * Returns null when the route carries no altitude data.
     */
    fun elevationGainM(points: List<RoutePoint>, thresholdM: Double = 3.0): Double? {
        val alts = points.mapNotNull { it.altitudeM }
        if (alts.size < 2) return null
        var gain = 0.0
        var reference = alts.first()
        for (alt in alts.drop(1)) {
            val diff = alt - reference
            if (diff >= thresholdM) {
                gain += diff
                reference = alt
            } else if (diff <= -thresholdM) {
                reference = alt
            }
        }
        return gain
    }

    /** Splits (default 1 km) with linear interpolation at split boundaries. */
    fun splits(points: List<RoutePoint>, splitDistanceM: Double = 1000.0): List<Split> {
        if (points.size < 2) return emptyList()
        val result = mutableListOf<Split>()
        var splitDist = 0.0
        var splitTime = 0.0
        var splitGain = 0.0
        for ((a, b) in points.zipWithNext()) {
            if (a.segment != b.segment) continue
            var segDist = dist(a, b)
            var segTime = (b.timeMs - a.timeMs).toDouble()
            var segGain = if (a.altitudeM != null && b.altitudeM != null) {
                (b.altitudeM - a.altitudeM).coerceAtLeast(0.0)
            } else {
                0.0
            }
            while (segDist > 0 && splitDist + segDist >= splitDistanceM) {
                val needed = splitDistanceM - splitDist
                val fraction = needed / segDist
                splitTime += segTime * fraction
                splitGain += segGain * fraction
                result += Split(result.size + 1, splitDistanceM, splitTime.toLong(), splitGain)
                segDist -= needed
                segTime -= segTime * fraction
                segGain -= segGain * fraction
                splitDist = 0.0
                splitTime = 0.0
                splitGain = 0.0
            }
            splitDist += segDist
            splitTime += segTime
            splitGain += segGain
        }
        if (splitDist >= MIN_PARTIAL_SPLIT_M) {
            result += Split(result.size + 1, splitDist, splitTime.toLong(), splitGain)
        }
        return result
    }

    /**
     * Fastest moving time needed to cover [targetM] within this run (best effort),
     * using a sliding window over cumulative moving distance with interpolation.
     * Returns null when the run is shorter than [targetM].
     */
    fun bestEffortMs(points: List<RoutePoint>, targetM: Double): Long? {
        if (points.size < 2) return null
        val n = points.size
        val cumD = DoubleArray(n)
        val cumT = DoubleArray(n)
        for (i in 1 until n) {
            val a = points[i - 1]
            val b = points[i]
            val same = a.segment == b.segment
            cumD[i] = cumD[i - 1] + if (same) dist(a, b) else 0.0
            cumT[i] = cumT[i - 1] + if (same) (b.timeMs - a.timeMs).toDouble() else 0.0
        }
        if (cumD[n - 1] < targetM) return null
        var best = Double.MAX_VALUE
        var j = 1
        for (i in 0 until n - 1) {
            val goal = cumD[i] + targetM
            if (goal > cumD[n - 1]) break
            if (j <= i) j = i + 1
            while (j < n - 1 && cumD[j] < goal) j++
            val span = cumD[j] - cumD[j - 1]
            val frac = if (span > 0) ((goal - cumD[j - 1]) / span).coerceIn(0.0, 1.0) else 0.0
            val tAtGoal = cumT[j - 1] + (cumT[j] - cumT[j - 1]) * frac
            best = minOf(best, tAtGoal - cumT[i])
        }
        return if (best == Double.MAX_VALUE) null else best.toLong()
    }

    private const val MIN_PARTIAL_SPLIT_M = 10.0

    private fun dist(a: RoutePoint, b: RoutePoint) =
        GeoMath.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}

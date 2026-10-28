package com.tkdpro.core.domain.run

import kotlin.math.roundToLong

object Pace {
    /** Below this distance a pace value is too noisy to be shown. */
    const val MIN_DISTANCE_FOR_PACE_M = 50.0

    fun secondsPerKm(distanceM: Double, durationMs: Long): Double? {
        if (distanceM < MIN_DISTANCE_FOR_PACE_M || durationMs <= 0) return null
        return (durationMs / 1000.0) / (distanceM / 1000.0)
    }

    fun kmh(distanceM: Double, durationMs: Long): Double? {
        if (durationMs <= 0 || distanceM <= 0) return null
        return (distanceM / 1000.0) / (durationMs / 3_600_000.0)
    }

    /** Formats a pace like 5:07 (min:sec per km). Returns null for an undefined pace. */
    fun format(secPerKm: Double?): String? {
        if (secPerKm == null || !secPerKm.isFinite() || secPerKm <= 0) return null
        val total = secPerKm.roundToLong()
        return "%d:%02d".format(total / 60, total % 60)
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

package com.tkdpro.core.domain.load

/**
 * Session-RPE training load: duration (minutes) × RPE (1–10).
 * This is a simple, subjective training-load estimate, NOT a physiological measurement.
 */
object TrainingLoad {
    fun sessionLoad(durationMin: Int, rpe: Int?): Int? {
        if (rpe == null || rpe !in 1..10 || durationMin <= 0) return null
        return durationMin * rpe
    }

    data class LoadItem(val epochDay: Long, val durationMin: Int, val rpe: Int?)

    data class WeeklyLoad(
        val weekStartEpochDay: Long,
        val totalLoad: Int,
        val sessions: Int,
        /** Sessions that have no RPE and therefore contribute no load. They are never guessed. */
        val sessionsWithoutRpe: Int,
    )

    fun weekly(items: List<LoadItem>): List<WeeklyLoad> =
        items.groupBy { weekStart(it.epochDay) }
            .map { (week, list) ->
                WeeklyLoad(
                    weekStartEpochDay = week,
                    totalLoad = list.sumOf { sessionLoad(it.durationMin, it.rpe) ?: 0 },
                    sessions = list.size,
                    sessionsWithoutRpe = list.count { sessionLoad(it.durationMin, it.rpe) == null },
                )
            }
            .sortedBy { it.weekStartEpochDay }

    /** Monday of the ISO week containing [epochDay]. 1970-01-01 was a Thursday. */
    fun weekStart(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 3, 7L)
}

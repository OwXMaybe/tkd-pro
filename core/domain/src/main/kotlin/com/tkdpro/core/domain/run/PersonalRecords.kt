package com.tkdpro.core.domain.run

/** Summary of a completed, valid run used to compute personal records. */
data class RunSummaryInput(
    val runId: Long,
    val distanceM: Double,
    val movingTimeMs: Long,
    /** Best effort times keyed by distance in metres, as computed from the stored route. */
    val bestEffortsMs: Map<Int, Long>,
)

data class PersonalRecord(val kind: Kind, val runId: Long, val value: Double) {
    enum class Kind { LONGEST_DISTANCE, LONGEST_DURATION, FASTEST_1K, FASTEST_5K, FASTEST_10K }
}

object PersonalRecords {
    val BEST_EFFORT_DISTANCES = listOf(1000, 5000, 10000)

    private val kindByDistance = mapOf(
        1000 to PersonalRecord.Kind.FASTEST_1K,
        5000 to PersonalRecord.Kind.FASTEST_5K,
        10000 to PersonalRecord.Kind.FASTEST_10K,
    )

    /** Computes records only from the provided runs. Never invents a value that no run produced. */
    fun compute(runs: List<RunSummaryInput>): List<PersonalRecord> {
        val valid = runs.filter { it.distanceM > 0 && it.movingTimeMs > 0 }
        if (valid.isEmpty()) return emptyList()
        val out = mutableListOf<PersonalRecord>()
        valid.maxByOrNull { it.distanceM }?.let {
            out += PersonalRecord(PersonalRecord.Kind.LONGEST_DISTANCE, it.runId, it.distanceM)
        }
        valid.maxByOrNull { it.movingTimeMs }?.let {
            out += PersonalRecord(PersonalRecord.Kind.LONGEST_DURATION, it.runId, it.movingTimeMs.toDouble())
        }
        for ((distance, kind) in kindByDistance) {
            valid.mapNotNull { r -> r.bestEffortsMs[distance]?.let { r.runId to it } }
                .minByOrNull { it.second }
                ?.let { out += PersonalRecord(kind, it.first, it.second.toDouble()) }
        }
        return out
    }
}

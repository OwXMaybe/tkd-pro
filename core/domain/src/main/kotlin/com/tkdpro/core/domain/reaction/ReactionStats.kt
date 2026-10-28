package com.tkdpro.core.domain.reaction

import kotlin.math.sqrt

data class ReactionTrial(val reactionMs: Long?, val falseStart: Boolean)

enum class InvalidReason { FALSE_START, ANTICIPATION, LAPSE, NO_RESPONSE }

data class EvaluatedTrial(
    val index: Int,
    val reactionMs: Long?,
    val valid: Boolean,
    val invalidReason: InvalidReason?,
)

data class ReactionSummary(
    val totalTrials: Int,
    val validTrials: Int,
    val averageMs: Double?,
    val bestMs: Long?,
    val medianMs: Double?,
    val standardDeviationMs: Double?,
    val trials: List<EvaluatedTrial>,
)

/**
 * Evaluates simple reaction-time trials measured on a phone.
 * Responses faster than [DEFAULT_ANTICIPATION_MS] are implausible as a reaction to the stimulus and
 * are treated as anticipations; responses slower than [DEFAULT_LAPSE_MS] are treated as attention lapses.
 * Phone touch and audio latency are included in every value; results are only comparable on the same device.
 */
object ReactionStats {
    const val DEFAULT_ANTICIPATION_MS = 100L
    const val DEFAULT_LAPSE_MS = 1000L

    fun evaluate(
        trials: List<ReactionTrial>,
        anticipationMs: Long = DEFAULT_ANTICIPATION_MS,
        lapseMs: Long = DEFAULT_LAPSE_MS,
    ): ReactionSummary {
        val evaluated = trials.mapIndexed { i, t ->
            val reason = when {
                t.falseStart -> InvalidReason.FALSE_START
                t.reactionMs == null -> InvalidReason.NO_RESPONSE
                t.reactionMs < anticipationMs -> InvalidReason.ANTICIPATION
                t.reactionMs > lapseMs -> InvalidReason.LAPSE
                else -> null
            }
            EvaluatedTrial(i + 1, t.reactionMs, reason == null, reason)
        }
        val values = evaluated.filter { it.valid }.mapNotNull { it.reactionMs }
        val avg = if (values.isEmpty()) null else values.average()
        val sd = if (values.size < 2 || avg == null) {
            null
        } else {
            sqrt(values.sumOf { (it - avg) * (it - avg) } / (values.size - 1))
        }
        val median = if (values.isEmpty()) {
            null
        } else {
            val s = values.sorted()
            if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
        }
        return ReactionSummary(
            totalTrials = trials.size,
            validTrials = values.size,
            averageMs = avg,
            bestMs = values.minOrNull(),
            medianMs = median,
            standardDeviationMs = sd,
            trials = evaluated,
        )
    }
}

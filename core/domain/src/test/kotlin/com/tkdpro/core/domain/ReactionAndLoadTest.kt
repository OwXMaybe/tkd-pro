package com.tkdpro.core.domain

import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.domain.reaction.InvalidReason
import com.tkdpro.core.domain.reaction.ReactionStats
import com.tkdpro.core.domain.reaction.ReactionTrial
import com.tkdpro.core.domain.time.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReactionAndLoadTest {

    @Test
    fun reactionStatsFilterInvalidAttempts() {
        val summary = ReactionStats.evaluate(
            listOf(
                ReactionTrial(250, false),
                ReactionTrial(80, false),    // anticipation
                ReactionTrial(null, true),   // false start
                ReactionTrial(300, false),
                ReactionTrial(1500, false),  // lapse
                ReactionTrial(275, false),
            ),
        )
        assertEquals(6, summary.totalTrials)
        assertEquals(3, summary.validTrials)
        assertEquals(275.0, summary.averageMs!!, 0.001)
        assertEquals(250L, summary.bestMs)
        assertEquals(25.0, summary.standardDeviationMs!!, 0.001)
        assertEquals(InvalidReason.ANTICIPATION, summary.trials[1].invalidReason)
        assertEquals(InvalidReason.FALSE_START, summary.trials[2].invalidReason)
        assertEquals(InvalidReason.LAPSE, summary.trials[4].invalidReason)
    }

    @Test
    fun noValidTrialsGivesNoNumbers() {
        val s = ReactionStats.evaluate(listOf(ReactionTrial(50, false)))
        assertNull(s.averageMs)
        assertNull(s.bestMs)
    }

    @Test
    fun sessionLoad() {
        assertEquals(360, TrainingLoad.sessionLoad(60, 6))
        assertNull(TrainingLoad.sessionLoad(60, null))
        assertNull(TrainingLoad.sessionLoad(0, 5))
    }

    @Test
    fun weeklyLoadGroupsByMonday() {
        val mon = LocalDate.of(2026, 10, 19).toEpochDay()
        val sun = LocalDate.of(2026, 10, 25).toEpochDay()
        val nextMon = LocalDate.of(2026, 10, 26).toEpochDay()
        val weeks = TrainingLoad.weekly(
            listOf(
                TrainingLoad.LoadItem(mon, 60, 5),
                TrainingLoad.LoadItem(sun, 30, null),
                TrainingLoad.LoadItem(nextMon, 45, 8),
            ),
        )
        assertEquals(2, weeks.size)
        assertEquals(mon, weeks[0].weekStartEpochDay)
        assertEquals(300, weeks[0].totalLoad)
        assertEquals(1, weeks[0].sessionsWithoutRpe)
        assertEquals(360, weeks[1].totalLoad)
    }

    @Test
    fun dateRanges() {
        val w = DateRange.week(LocalDate.of(2026, 10, 25))
        assertEquals(LocalDate.of(2026, 10, 19), w.start)
        assertEquals(LocalDate.of(2026, 10, 12), w.previous().start)
        assertEquals(31, DateRange.month(LocalDate.of(2026, 10, 3)).days())
    }
}

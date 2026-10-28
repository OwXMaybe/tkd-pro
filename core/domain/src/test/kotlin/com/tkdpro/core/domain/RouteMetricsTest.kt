package com.tkdpro.core.domain

import com.tkdpro.core.domain.run.Pace
import com.tkdpro.core.domain.run.PersonalRecord
import com.tkdpro.core.domain.run.PersonalRecords
import com.tkdpro.core.domain.run.RoutePoint
import com.tkdpro.core.domain.run.RouteMetrics
import com.tkdpro.core.domain.run.RunSummaryInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMetricsTest {
    private val metersPerDegLat = 111_195.0

    /** Straight north-bound route: one point every [stepM] metres, every [stepMs] ms. */
    private fun straightRoute(totalM: Double, stepM: Double, stepMs: Long, segment: Int = 0, startT: Long = 0, startM: Double = 0.0) =
        (0..(totalM / stepM).toInt()).map { i ->
            RoutePoint(startT + i * stepMs, 41.0 + (startM + i * stepM) / metersPerDegLat, 29.0, 100.0, 5f, segment)
        }

    @Test
    fun distanceAndMovingTime() {
        val pts = straightRoute(2000.0, 10.0, 3000) // 10 m every 3 s = 5:00 /km
        assertEquals(2000.0, RouteMetrics.totalDistanceM(pts), 2.0)
        assertEquals(600_000L, RouteMetrics.movingTimeMs(pts))
    }

    @Test
    fun pauseGapIsNotCounted() {
        val a = straightRoute(500.0, 10.0, 3000, segment = 0)
        val b = straightRoute(500.0, 10.0, 3000, segment = 1, startT = a.last().timeMs + 600_000, startM = 2000.0)
        val pts = a + b
        assertEquals(1000.0, RouteMetrics.totalDistanceM(pts), 2.0)
        assertEquals(300_000L, RouteMetrics.movingTimeMs(pts))
    }

    @Test
    fun kilometreSplits() {
        val pts = straightRoute(2500.0, 10.0, 3000)
        val splits = RouteMetrics.splits(pts)
        assertEquals(3, splits.size)
        assertEquals(1000.0, splits[0].distanceM, 0.01)
        assertEquals(300.0, splits[0].paceSecPerKm!!, 1.0)
        assertEquals(500.0, splits[2].distanceM, 2.0)
    }

    @Test
    fun bestEffortFindsFasterSection() {
        val slow = straightRoute(1000.0, 10.0, 4000) // 6:40 /km
        val fast = straightRoute(1000.0, 10.0, 2400, startT = slow.last().timeMs, startM = 1000.0).drop(1) // 4:00 /km
        val best = RouteMetrics.bestEffortMs(slow + fast, 1000.0)!!
        assertEquals(240_000.0, best.toDouble(), 3000.0)
        assertNull(RouteMetrics.bestEffortMs(slow, 5000.0))
    }

    @Test
    fun elevationGainUsesHysteresis() {
        val alts = listOf(100.0, 101.0, 100.5, 104.0, 103.0, 108.0, 102.0, 106.0)
        val pts = alts.mapIndexed { i, a -> RoutePoint(i * 1000L, 41.0, 29.0, a, 5f, 0) }
        // 100 -> 104 (+4), 104 -> 108 (+4), drop to 102, 102 -> 106 (+4)
        assertEquals(12.0, RouteMetrics.elevationGainM(pts)!!, 0.001)
    }

    @Test
    fun paceFormatting() {
        assertEquals("5:07", Pace.format(307.0))
        assertNull(Pace.format(null))
        assertNull(Pace.secondsPerKm(10.0, 10_000))
        assertEquals("1:02:03", Pace.formatDuration(3_723_000))
    }

    @Test
    fun personalRecordsComeOnlyFromRuns() {
        assertTrue(PersonalRecords.compute(emptyList()).isEmpty())
        val runs = listOf(
            RunSummaryInput(1, 5200.0, 1_600_000, mapOf(1000 to 290_000L, 5000 to 1_550_000L)),
            RunSummaryInput(2, 3000.0, 800_000, mapOf(1000 to 260_000L)),
        )
        val prs = PersonalRecords.compute(runs).associateBy { it.kind }
        assertEquals(1L, prs[PersonalRecord.Kind.LONGEST_DISTANCE]!!.runId)
        assertEquals(2L, prs[PersonalRecord.Kind.FASTEST_1K]!!.runId)
        assertEquals(1L, prs[PersonalRecord.Kind.FASTEST_5K]!!.runId)
        assertNull(prs[PersonalRecord.Kind.FASTEST_10K])
    }
}

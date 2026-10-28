package com.tkdpro.core.domain

import com.tkdpro.core.domain.gps.GpsPointFilter
import com.tkdpro.core.domain.gps.GpsPointFilter.Decision
import com.tkdpro.core.domain.gps.GpsPointFilter.RejectReason
import com.tkdpro.core.domain.gps.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsPointFilterTest {
    // ~0.000009 deg latitude ≈ 1 m
    private fun sample(t: Long, latOffsetM: Double, acc: Float = 5f) =
        LocationSample(t, 41.0 + latOffsetM * 0.000009, 29.0, 10.0, acc)

    @Test
    fun firstAccurateSampleIsAccepted() {
        val f = GpsPointFilter()
        assertTrue(f.evaluate(sample(0, 0.0)) is Decision.Accepted)
    }

    @Test
    fun lowAccuracyIsRejected() {
        val f = GpsPointFilter()
        assertEquals(Decision.Rejected(RejectReason.LOW_ACCURACY), f.evaluate(sample(0, 0.0, acc = 60f)))
    }

    @Test
    fun duplicateTimestampIsRejected() {
        val f = GpsPointFilter()
        f.evaluate(sample(1000, 0.0))
        assertEquals(Decision.Rejected(RejectReason.DUPLICATE_OR_OUT_OF_ORDER), f.evaluate(sample(1000, 10.0)))
    }

    @Test
    fun gpsJumpIsRejected() {
        val f = GpsPointFilter()
        f.evaluate(sample(0, 0.0))
        // 200 m in 1 s = 200 m/s
        assertEquals(Decision.Rejected(RejectReason.IMPLAUSIBLE_SPEED), f.evaluate(sample(1000, 200.0)))
    }

    @Test
    fun jitterIsRejectedButRealMovementAccepted() {
        val f = GpsPointFilter()
        f.evaluate(sample(0, 0.0))
        assertEquals(Decision.Rejected(RejectReason.STATIONARY_JITTER), f.evaluate(sample(1000, 1.0)))
        val d = f.evaluate(sample(4000, 12.0))
        assertTrue(d is Decision.Accepted)
        assertEquals(12.0, (d as Decision.Accepted).distanceFromPreviousM, 0.5)
    }
}

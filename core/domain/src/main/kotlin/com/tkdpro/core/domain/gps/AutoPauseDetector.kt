package com.tkdpro.core.domain.gps

/**
 * Detects when the athlete has stopped moving, based only on accepted GPS movement.
 * Paused after no qualifying movement for [stillWindowMs]; resumed on the next accepted movement
 * whose implied speed is at least [resumeSpeedMps].
 */
class AutoPauseDetector(
    private val stillWindowMs: Long = 10_000,
    private val resumeSpeedMps: Double = 1.0,
) {
    var isAutoPaused: Boolean = false
        private set

    private var lastMovementMs: Long? = null

    /** Returns true when this movement ended an auto-pause. */
    fun onAcceptedMovement(timeMs: Long, distanceM: Double, dtMs: Long): Boolean {
        val speed = if (dtMs > 0) distanceM / (dtMs / 1000.0) else 0.0
        if (speed >= resumeSpeedMps) {
            lastMovementMs = timeMs
            if (isAutoPaused) {
                isAutoPaused = false
                return true
            }
        }
        return false
    }

    /** Call periodically. Returns true when the detector entered the auto-paused state. */
    fun onTick(nowMs: Long): Boolean {
        val last = lastMovementMs ?: run {
            lastMovementMs = nowMs
            return false
        }
        if (!isAutoPaused && nowMs - last >= stillWindowMs) {
            isAutoPaused = true
            return true
        }
        return false
    }

    fun reset(nowMs: Long) {
        lastMovementMs = nowMs
        isAutoPaused = false
    }
}

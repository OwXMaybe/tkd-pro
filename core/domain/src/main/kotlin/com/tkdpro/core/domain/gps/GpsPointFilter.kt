package com.tkdpro.core.domain.gps

/**
 * A raw location reading as delivered by the platform location provider.
 * Nothing in this type is synthesised: every value comes from the device.
 */
data class LocationSample(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val horizontalAccuracyM: Float,
    val speedMps: Float? = null,
)

/**
 * Decides whether a raw [LocationSample] should become part of a recorded route.
 *
 * - readings with poor horizontal accuracy are rejected,
 * - readings that are not strictly newer than the last accepted one are rejected (duplicates / reordering),
 * - readings implying an implausible on-foot speed are rejected (GPS jumps),
 * - readings inside the jitter radius of the last accepted point are treated as stationary noise.
 *
 * The filter never moves or invents coordinates; it only accepts or rejects.
 */
class GpsPointFilter(
    private val maxAccuracyM: Float = DEFAULT_MAX_ACCURACY_M,
    private val maxSpeedMps: Double = DEFAULT_MAX_SPEED_MPS,
    private val minDistanceM: Double = DEFAULT_MIN_DISTANCE_M,
) {
    sealed interface Decision {
        data class Accepted(val distanceFromPreviousM: Double, val dtMs: Long) : Decision
        data class Rejected(val reason: RejectReason) : Decision
    }

    enum class RejectReason { LOW_ACCURACY, DUPLICATE_OR_OUT_OF_ORDER, IMPLAUSIBLE_SPEED, STATIONARY_JITTER }

    private var last: LocationSample? = null

    /** Restarts filtering, e.g. after resume. Pass the last stored point to keep continuity checks. */
    fun reset(lastAccepted: LocationSample? = null) {
        last = lastAccepted
    }

    fun evaluate(sample: LocationSample): Decision {
        if (sample.horizontalAccuracyM <= 0f || sample.horizontalAccuracyM > maxAccuracyM) {
            return Decision.Rejected(RejectReason.LOW_ACCURACY)
        }
        val previous = last
        if (previous == null) {
            last = sample
            return Decision.Accepted(0.0, 0)
        }
        if (sample.timeMs <= previous.timeMs) {
            return Decision.Rejected(RejectReason.DUPLICATE_OR_OUT_OF_ORDER)
        }
        val distance = GeoMath.haversineMeters(
            previous.latitude, previous.longitude, sample.latitude, sample.longitude,
        )
        val dtMs = sample.timeMs - previous.timeMs
        if (distance / (dtMs / 1000.0) > maxSpeedMps) {
            return Decision.Rejected(RejectReason.IMPLAUSIBLE_SPEED)
        }
        val jitterRadius = maxOf(
            minDistanceM,
            minOf(sample.horizontalAccuracyM, previous.horizontalAccuracyM) * JITTER_ACCURACY_FACTOR,
        )
        if (distance < jitterRadius) {
            return Decision.Rejected(RejectReason.STATIONARY_JITTER)
        }
        last = sample
        return Decision.Accepted(distance, dtMs)
    }

    companion object {
        const val DEFAULT_MAX_ACCURACY_M = 25f
        /** ~43 km/h: faster than any human sprint, so anything above is a GPS jump. */
        const val DEFAULT_MAX_SPEED_MPS = 12.0
        const val DEFAULT_MIN_DISTANCE_M = 2.0
        private const val JITTER_ACCURACY_FACTOR = 0.5
    }
}

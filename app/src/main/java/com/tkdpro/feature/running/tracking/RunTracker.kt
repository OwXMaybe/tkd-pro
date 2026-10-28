package com.tkdpro.feature.running.tracking

import android.os.SystemClock
import com.tkdpro.core.database.entity.GpsPoint
import com.tkdpro.core.database.entity.RunStatus
import com.tkdpro.core.database.entity.RunType
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.domain.gps.AutoPauseDetector
import com.tkdpro.core.domain.gps.GpsPointFilter
import com.tkdpro.core.domain.gps.LocationSample
import com.tkdpro.core.domain.run.Pace
import com.tkdpro.core.domain.run.RoutePoint
import com.tkdpro.core.domain.run.RouteMetrics
import com.tkdpro.core.domain.run.Split
import com.tkdpro.data.repository.RunningRepository
import com.tkdpro.data.repository.toRoutePoint
import com.tkdpro.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class TrackerStatus { IDLE, ACTIVE, PAUSED, AUTO_PAUSED }

enum class GpsSignal { NONE, GOOD, WEAK, LOST }

data class LiveRunState(
    val status: TrackerStatus = TrackerStatus.IDLE,
    val runId: Long? = null,
    val type: RunType = RunType.EASY,
    val movingMs: Long = 0,
    val distanceM: Double = 0.0,
    val currentPaceSecPerKm: Double? = null,
    val currentSpeedKmh: Double? = null,
    val elevationGainM: Double? = null,
    val points: List<RoutePoint> = emptyList(),
    val splits: List<Split> = emptyList(),
    val lastAccuracyM: Float? = null,
    val signal: GpsSignal = GpsSignal.NONE,
    val recovered: Boolean = false,
    val rejectedPoints: Int = 0,
) {
    val averagePaceSecPerKm: Double? get() = Pace.secondsPerKm(distanceM, movingMs)
    val averageSpeedKmh: Double? get() = Pace.kmh(distanceM, movingMs)
    val isRecording: Boolean get() = status != TrackerStatus.IDLE
}

/**
 * Owns the live run. Every accepted GPS point is written to Room immediately, so a run survives
 * process death and can be recovered. Distance only ever comes from accepted points.
 */
@Singleton
class RunTracker @Inject constructor(
    private val repo: RunningRepository,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LiveRunState())
    val state: StateFlow<LiveRunState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var filter = GpsPointFilter()
    private var autoPause: AutoPauseDetector? = null
    private var segment = 0
    private var accumulatedMs = 0L
    private var activeSince: Long? = null
    private var lastAcceptedAt: Long = 0
    private var tickJob: Job? = null

    /** Loads an ACTIVE/PAUSED run left behind by a crash or process death, in paused state. */
    suspend fun recoverIfNeeded(): Boolean = mutex.withLock {
        if (_state.value.isRecording) return@withLock false
        val run = repo.unfinishedRun() ?: return@withLock false
        val points = repo.points(run.id).map { it.toRoutePoint() }
        segment = (points.maxOfOrNull { it.segment } ?: 0) + 1
        accumulatedMs = run.movingTimeMs
        activeSince = null
        filter = GpsPointFilter(maxAccuracyM = settings.current().gpsMaxAccuracyM.toFloat())
        _state.value = LiveRunState(
            status = TrackerStatus.PAUSED,
            runId = run.id,
            type = run.type,
            movingMs = accumulatedMs,
            distanceM = RouteMetrics.totalDistanceM(points),
            elevationGainM = RouteMetrics.elevationGainM(points),
            points = points,
            splits = RouteMetrics.splits(points),
            recovered = true,
        )
        startTicker()
        true
    }

    suspend fun start(type: RunType, customName: String?) = mutex.withLock {
        if (_state.value.isRecording) return@withLock
        val s = settings.current()
        val id = repo.startRun(type, customName)
        filter = GpsPointFilter(maxAccuracyM = s.gpsMaxAccuracyM.toFloat())
        autoPause = if (s.gpsAutoPause) AutoPauseDetector() else null
        autoPause?.reset(SystemClock.elapsedRealtime())
        segment = 0
        accumulatedMs = 0
        activeSince = SystemClock.elapsedRealtime()
        lastAcceptedAt = SystemClock.elapsedRealtime()
        _state.value = LiveRunState(status = TrackerStatus.ACTIVE, runId = id, type = type)
        startTicker()
    }

    fun pause() = scope.launch {
        mutex.withLock {
            if (_state.value.status != TrackerStatus.ACTIVE && _state.value.status != TrackerStatus.AUTO_PAUSED) return@withLock
            stopClock()
            _state.update { it.copy(status = TrackerStatus.PAUSED, movingMs = accumulatedMs) }
            persistProgress()
        }
    }

    fun resume() = scope.launch {
        mutex.withLock {
            if (_state.value.status != TrackerStatus.PAUSED) return@withLock
            if (autoPause == null && settings.current().gpsAutoPause) autoPause = AutoPauseDetector()
            // New segment: the gap between pause and resume never counts as distance.
            segment++
            filter.reset(null)
            autoPause?.reset(SystemClock.elapsedRealtime())
            activeSince = SystemClock.elapsedRealtime()
            lastAcceptedAt = SystemClock.elapsedRealtime()
            _state.update { it.copy(status = TrackerStatus.ACTIVE, recovered = false) }
        }
    }

    suspend fun onLocation(sample: LocationSample) = mutex.withLock {
        val st = _state.value
        _state.update { it.copy(lastAccuracyM = sample.horizontalAccuracyM) }
        if (st.status == TrackerStatus.IDLE || st.status == TrackerStatus.PAUSED) return@withLock
        val runId = st.runId ?: return@withLock
        when (val d = filter.evaluate(sample)) {
            is GpsPointFilter.Decision.Rejected -> _state.update { it.copy(rejectedPoints = it.rejectedPoints + 1) }
            is GpsPointFilter.Decision.Accepted -> {
                val now = SystemClock.elapsedRealtime()
                lastAcceptedAt = now
                if (autoPause?.onAcceptedMovement(now, d.distanceFromPreviousM, d.dtMs) == true && st.status == TrackerStatus.AUTO_PAUSED) {
                    activeSince = now
                }
                val point = RoutePoint(sample.timeMs, sample.latitude, sample.longitude, sample.altitudeM, sample.horizontalAccuracyM, segment)
                repo.addPoint(
                    GpsPoint(
                        runId = runId, timeMs = sample.timeMs, latitude = sample.latitude, longitude = sample.longitude,
                        altitudeM = sample.altitudeM, accuracyM = sample.horizontalAccuracyM, speedMps = sample.speedMps, segment = segment,
                    ),
                )
                val points = st.points + point
                val sameSegment = st.points.lastOrNull()?.segment == segment
                val newStatus = if (st.status == TrackerStatus.AUTO_PAUSED && autoPause?.isAutoPaused == false) TrackerStatus.ACTIVE else st.status
                _state.update {
                    it.copy(
                        status = newStatus,
                        points = points,
                        distanceM = it.distanceM + if (sameSegment) d.distanceFromPreviousM else 0.0,
                        elevationGainM = RouteMetrics.elevationGainM(points),
                        splits = RouteMetrics.splits(points),
                        currentPaceSecPerKm = rollingPace(points),
                        currentSpeedKmh = sample.speedMps?.let { s -> s * 3.6 } ?: rollingPace(points)?.let { p -> 3600.0 / p },
                        signal = GpsSignal.GOOD,
                    )
                }
            }
        }
    }

    /** Finishes the run, computes the final summary from stored points and returns the run id. */
    suspend fun finish(): Long? = mutex.withLock {
        val st = _state.value
        val id = st.runId ?: return@withLock null
        stopClock()
        tickJob?.cancel()
        repo.finishRun(id, accumulatedMs)
        _state.value = LiveRunState()
        id
    }

    suspend fun discard() = mutex.withLock {
        val id = _state.value.runId
        stopClock()
        tickJob?.cancel()
        id?.let { repo.discardRun(it) }
        _state.value = LiveRunState()
    }

    private fun stopClock() {
        activeSince?.let { accumulatedMs += SystemClock.elapsedRealtime() - it }
        activeSince = null
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(1000)
                mutex.withLock { tick() }
                if (++ticks % PERSIST_EVERY_TICKS == 0) mutex.withLock { persistProgress() }
            }
        }
    }

    private fun tick() {
        val st = _state.value
        val now = SystemClock.elapsedRealtime()
        if (st.status == TrackerStatus.ACTIVE && autoPause?.onTick(now) == true) {
            // Moving time stops at the last real movement, not at detection time.
            stopClock()
            _state.update { it.copy(status = TrackerStatus.AUTO_PAUSED, currentPaceSecPerKm = null, currentSpeedKmh = 0.0) }
        }
        val running = activeSince?.let { now - it } ?: 0L
        val sinceFix = now - lastAcceptedAt
        val signal = when {
            st.status != TrackerStatus.ACTIVE -> st.signal
            sinceFix > LOST_AFTER_MS -> GpsSignal.LOST
            sinceFix > WEAK_AFTER_MS -> GpsSignal.WEAK
            else -> GpsSignal.GOOD
        }
        _state.update { it.copy(movingMs = accumulatedMs + running, signal = signal) }
    }

    private suspend fun persistProgress() {
        val st = _state.value
        val id = st.runId ?: return
        val status = if (st.status == TrackerStatus.PAUSED) RunStatus.PAUSED else RunStatus.ACTIVE
        repo.updateProgress(id, status, st.movingMs, st.distanceM)
    }

    /** Pace over the last [PACE_WINDOW_MS] of the current segment, or null if too little distance. */
    private fun rollingPace(points: List<RoutePoint>): Double? {
        val last = points.lastOrNull() ?: return null
        val window = points.takeLastWhile { it.segment == last.segment && last.timeMs - it.timeMs <= PACE_WINDOW_MS }
        if (window.size < 2) return null
        val dist = RouteMetrics.totalDistanceM(window)
        val time = window.last().timeMs - window.first().timeMs
        return if (dist < MIN_PACE_WINDOW_M) null else (time / 1000.0) / (dist / 1000.0)
    }

    private companion object {
        const val PACE_WINDOW_MS = 30_000L
        const val MIN_PACE_WINDOW_M = 20.0
        const val WEAK_AFTER_MS = 10_000L
        const val LOST_AFTER_MS = 30_000L
        const val PERSIST_EVERY_TICKS = 10
    }
}

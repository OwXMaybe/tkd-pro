package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.RunningDao
import com.tkdpro.core.database.entity.GpsPoint
import com.tkdpro.core.database.entity.RunStatus
import com.tkdpro.core.database.entity.RunType
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.RunningSplit
import com.tkdpro.core.domain.run.PersonalRecord
import com.tkdpro.core.domain.run.PersonalRecords
import com.tkdpro.core.domain.run.RoutePoint
import com.tkdpro.core.domain.run.RouteMetrics
import com.tkdpro.core.domain.run.RunSummaryInput
import com.tkdpro.data.sync.RunRoutePayload
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.data.sync.of
import com.tkdpro.di.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

fun GpsPoint.toRoutePoint() = RoutePoint(timeMs, latitude, longitude, altitudeM, accuracyM, segment)

@Singleton
class RunningRepository @Inject constructor(
    private val dao: RunningDao,
    private val sync: SyncTracker,
    private val clock: Clock,
) {
    val completedRuns: Flow<List<RunningSession>> = dao.observeCompleted()

    /** Personal records computed from valid completed sessions only. */
    val personalRecords: Flow<List<PersonalRecord>> = completedRuns.map { runs ->
        PersonalRecords.compute(
            runs.map { r ->
                RunSummaryInput(
                    runId = r.id,
                    distanceM = r.distanceM,
                    movingTimeMs = r.movingTimeMs,
                    bestEffortsMs = buildMap {
                        r.best1kMs?.let { put(1000, it) }
                        r.best5kMs?.let { put(5000, it) }
                        r.best10kMs?.let { put(10000, it) }
                    },
                )
            },
        )
    }

    fun run(id: Long): Flow<RunningSession?> = dao.observe(id)
    fun splits(id: Long): Flow<List<RunningSplit>> = dao.observeSplits(id)
    suspend fun points(id: Long): List<GpsPoint> = dao.points(id)
    suspend fun unfinishedRun(): RunningSession? = dao.unfinished()
    suspend fun lastPoint(runId: Long): GpsPoint? = dao.lastPoint(runId)
    suspend fun completedIn(from: Long, to: Long) = dao.completedInRange(from, to)

    suspend fun startRun(type: RunType, customName: String?): Long {
        val now = clock.now()
        return dao.insert(
            RunningSession(type = type, customName = customName, status = RunStatus.ACTIVE, startTime = now, createdAt = now, updatedAt = now),
        )
    }

    suspend fun addPoint(p: GpsPoint) {
        dao.insertPoint(p)
    }

    suspend fun updateProgress(runId: Long, status: RunStatus, movingTimeMs: Long, distanceM: Double) {
        val run = dao.get(runId) ?: return
        dao.update(run.copy(status = status, movingTimeMs = movingTimeMs, distanceM = distanceM, updatedAt = clock.now()))
    }

    /**
     * Finalises a run from its stored points: distance, elevation, splits and best efforts are
     * all recomputed from the filtered route. A run without at least two points has no distance.
     */
    suspend fun finishRun(runId: Long, movingTimeMs: Long): RunningSession? {
        val run = dao.get(runId) ?: return null
        val route = dao.points(runId).map { it.toRoutePoint() }
        val splits = RouteMetrics.splits(route)
        val finished = run.copy(
            status = RunStatus.COMPLETED,
            endTime = clock.now(),
            movingTimeMs = movingTimeMs,
            distanceM = RouteMetrics.totalDistanceM(route),
            elevationGainM = RouteMetrics.elevationGainM(route),
            best1kMs = RouteMetrics.bestEffortMs(route, 1000.0),
            best5kMs = RouteMetrics.bestEffortMs(route, 5000.0),
            best10kMs = RouteMetrics.bestEffortMs(route, 10000.0),
            updatedAt = clock.now(),
        )
        dao.update(finished)
        val splitRows = splits.map {
            RunningSplit(runId = runId, idx = it.index, distanceM = it.distanceM, durationMs = it.durationMs, elevationGainM = it.elevationGainM)
        }
        dao.replaceSplits(runId, splitRows)
        sync.upsert("running_sessions", runId, finished, RunningSession.serializer())
        // The route goes to the owner's computer so the run map is visible there too (never to the AI).
        sync.upsert("run_routes", runId, RunRoutePayload.of(dao.points(runId), splitRows), RunRoutePayload.serializer())
        return finished
    }

    suspend fun updateDetails(run: RunningSession) {
        val toSave = run.copy(updatedAt = clock.now())
        dao.update(toSave)
        sync.upsert("running_sessions", run.id, toSave, RunningSession.serializer())
    }

    suspend fun discardRun(runId: Long) {
        dao.delete(runId)
    }

    suspend fun deleteRun(runId: Long) {
        dao.delete(runId)
        sync.delete("running_sessions", runId)
    }
}

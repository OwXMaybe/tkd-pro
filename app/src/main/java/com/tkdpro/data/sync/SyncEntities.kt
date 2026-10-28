package com.tkdpro.data.sync

import com.tkdpro.core.database.dao.SyncApplyDao
import com.tkdpro.core.database.entity.AiReport
import com.tkdpro.core.database.entity.AthleteProfile
import com.tkdpro.core.database.entity.BeltRecord
import com.tkdpro.core.database.entity.BodySide
import com.tkdpro.core.database.entity.ChatMessage
import com.tkdpro.core.database.entity.Combination
import com.tkdpro.core.database.entity.CombinationPractice
import com.tkdpro.core.database.entity.CombinationStep
import com.tkdpro.core.database.entity.Competition
import com.tkdpro.core.database.entity.GpsPoint
import com.tkdpro.core.database.entity.Match
import com.tkdpro.core.database.entity.MatchEvent
import com.tkdpro.core.database.entity.MatchRound
import com.tkdpro.core.database.entity.MotionAnalysis
import com.tkdpro.core.database.entity.PerformanceMetric
import com.tkdpro.core.database.entity.PersonalGoal
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.RunningSplit
import com.tkdpro.core.database.entity.ScoringPreset
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Whole route of a finished run, synced as one record keyed by the run id (entity `run_routes`). */
@Serializable
data class RunRoutePayload(val points: List<RoutePointDto>, val splits: List<SplitDto>)

@Serializable
data class RoutePointDto(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val acc: Float,
    val speed: Float? = null,
    val seg: Int,
)

@Serializable
data class SplitDto(val idx: Int, val distanceM: Double, val durationMs: Long, val elevationGainM: Double)

/** Ordered steps of a combination, synced as one record keyed by the combination id (entity `combination_steps`). */
@Serializable
data class CombinationStepsPayload(val steps: List<StepDto>)

@Serializable
data class StepDto(val techniqueId: Long, val side: BodySide)

fun RunRoutePayload.Companion.of(points: List<GpsPoint>, splits: List<RunningSplit>) = RunRoutePayload(
    points = points.map { RoutePointDto(it.timeMs, it.latitude, it.longitude, it.altitudeM, it.accuracyM, it.speedMps, it.segment) },
    splits = splits.map { SplitDto(it.idx, it.distanceM, it.durationMs, it.elevationGainM) },
)

fun RunRoutePayload.toPoints(runId: Long) = points.map {
    GpsPoint(runId = runId, timeMs = it.t, latitude = it.lat, longitude = it.lon, altitudeM = it.alt, accuracyM = it.acc, speedMps = it.speed, segment = it.seg)
}

fun RunRoutePayload.toSplits(runId: Long) = splits.map {
    RunningSplit(runId = runId, idx = it.idx, distanceM = it.distanceM, durationMs = it.durationMs, elevationGainM = it.elevationGainM)
}

fun CombinationStepsPayload.Companion.of(steps: List<CombinationStep>) =
    CombinationStepsPayload(steps.sortedBy { it.position }.map { StepDto(it.techniqueId, it.side) })

/**
 * One synced table: how to snapshot it, and how to apply an incoming copy from another device.
 * [Sync.ENTITIES] is ordered parents-first so foreign keys exist when children arrive.
 */
class SyncEntity<T>(
    val name: String,
    val serializer: KSerializer<T>,
    val idOf: (T) -> Long,
    val snapshot: suspend SyncApplyDao.() -> List<Pair<Long, T>>,
    val upsert: suspend SyncApplyDao.(id: Long, value: T) -> Unit,
    val delete: suspend SyncApplyDao.(id: Long) -> Unit,
)

private fun <T> table(
    name: String,
    serializer: KSerializer<T>,
    idOf: (T) -> Long,
    all: suspend SyncApplyDao.() -> List<T>,
    upsert: suspend SyncApplyDao.(T) -> Unit,
    delete: suspend SyncApplyDao.(Long) -> Unit,
) = SyncEntity(name, serializer, idOf, { all().map { idOf(it) to it } }, { _, v -> upsert(v) }, delete)

object Sync {
    val ENTITIES: List<SyncEntity<*>> = listOf(
        table("athlete_profile", AthleteProfile.serializer(), { it.id }, { allProfiles() }, { upsertProfile(it) }, { deleteProfile(it) }),
        table("belt_records", BeltRecord.serializer(), { it.id }, { allBelts() }, { upsertBelt(it) }, { deleteBelt(it) }),
        table("personal_goals", PersonalGoal.serializer(), { it.id }, { allGoals() }, { upsertGoal(it) }, { deleteGoal(it) }),
        table("competitions", Competition.serializer(), { it.id }, { allCompetitions() }, { upsertCompetition(it) }, { deleteCompetition(it) }),
        table("scoring_presets", ScoringPreset.serializer(), { it.id }, { allPresets() }, { upsertPreset(it) }, { deletePreset(it) }),
        table("techniques", TrainingTechnique.serializer(), { it.id }, { allTechniques() }, { upsertTechnique(it) }, { deleteTechnique(it) }),
        table("training_sessions", TrainingSession.serializer(), { it.id }, { allSessions() }, { upsertSession(it) }, { deleteSession(it) }),
        table("technique_records", TechniqueRecord.serializer(), { it.id }, { allTechniqueRecords() }, { upsertTechniqueRecord(it) }, { deleteTechniqueRecord(it) }),
        table("combinations", Combination.serializer(), { it.id }, { allCombinations() }, { upsertCombination(it) }, { deleteCombination(it) }),
        SyncEntity(
            "combination_steps", CombinationStepsPayload.serializer(), { 0L },
            snapshot = { allSteps().groupBy { it.combinationId }.map { (id, steps) -> id to CombinationStepsPayload.of(steps) } },
            upsert = { id, v ->
                if (combinationExists(id) > 0) {
                    replaceSteps(id, v.steps.mapIndexed { i, s -> CombinationStep(combinationId = id, position = i, techniqueId = s.techniqueId, side = s.side) })
                }
            },
            delete = { id -> clearSteps(id) },
        ),
        table("combination_practices", CombinationPractice.serializer(), { it.id }, { allPractices() }, { upsertPractice(it) }, { deletePractice(it) }),
        table("matches", Match.serializer(), { it.id }, { allMatches() }, { upsertMatch(it) }, { deleteMatch(it) }),
        table("match_rounds", MatchRound.serializer(), { it.id }, { allRounds() }, { upsertRound(it) }, { deleteRound(it) }),
        table("match_events", MatchEvent.serializer(), { it.id }, { allEvents() }, { upsertEvent(it) }, { deleteEvent(it) }),
        table("running_sessions", RunningSession.serializer(), { it.id }, { allCompletedRuns() }, { upsertRun(it) }, { deleteRun(it) }),
        SyncEntity(
            "run_routes", RunRoutePayload.serializer(), { 0L },
            snapshot = { allCompletedRuns().map { it.id to RunRoutePayload.of(points(it.id), splits(it.id)) }.filter { it.second.points.isNotEmpty() } },
            // The phone recorded its own routes; only restore a route the phone does not have.
            upsert = { id, v -> if (runExists(id) > 0 && pointCount(id) == 0) restoreRoute(id, v.toPoints(id), v.toSplits(id)) },
            delete = { },
        ),
        table("reaction_tests", ReactionTest.serializer(), { it.id }, { allReactionTests() }, { upsertReactionTest(it) }, { deleteReactionTest(it) }),
        table("performance_metrics", PerformanceMetric.serializer(), { it.id }, { allMetrics() }, { upsertMetric(it) }, { deleteMetric(it) }),
        table(
            "recovery_entries", RecoveryEntry.serializer(), { it.id }, { allRecovery() },
            upsert = { incoming ->
                // One check-in per day: a check-in for the same day made on another device replaces the older one.
                val local = recoveryForDay(incoming.epochDay)
                if (local == null || local.id == incoming.id || local.updatedAt <= incoming.updatedAt) {
                    if (local != null && local.id != incoming.id) deleteRecovery(local.id)
                    upsertRecovery(incoming)
                }
            },
            delete = { deleteRecovery(it) },
        ),
        table(
            "motion_analyses", MotionAnalysis.serializer(), { it.id }, { allAnalyses() },
            upsert = { upsertAnalysis(it.copy(posePath = posePath(it.id))) },
            delete = { deleteAnalysis(it) },
        ),
        table("chat_messages", ChatMessage.serializer(), { it.id }, { allChat() }, { upsertChat(it) }, { deleteChat(it) }),
        table("ai_reports", AiReport.serializer(), { it.id }, { allReports() }, { upsertReport(it) }, { deleteReport(it) }),
    )

    val byName: Map<String, SyncEntity<*>> = ENTITIES.associateBy { it.name }
    val order: Map<String, Int> = ENTITIES.withIndex().associate { (i, e) -> e.name to i }
}

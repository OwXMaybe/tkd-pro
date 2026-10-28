package com.tkdpro.data.ai

import com.tkdpro.core.database.dao.MatchDao
import com.tkdpro.core.database.dao.MotionDao
import com.tkdpro.core.database.dao.PerformanceDao
import com.tkdpro.core.database.dao.ProfileDao
import com.tkdpro.core.database.dao.ReactionDao
import com.tkdpro.core.database.dao.RecoveryDao
import com.tkdpro.core.database.dao.RunningDao
import com.tkdpro.core.database.dao.TrainingDao
import com.tkdpro.core.database.dao.GoalDao
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.totalReps
import com.tkdpro.core.datastore.AiPermissions
import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.domain.motion.MotionResult
import com.tkdpro.core.domain.run.Pace
import com.tkdpro.data.repository.MatchTotals
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A visible reference to the saved data an AI answer or report was based on. */
@Serializable
data class DataSource(val category: String, val records: Int, val fromMs: Long, val toMs: Long)

data class CoachContext(val json: JsonObject, val sources: List<DataSource>, val missing: List<String>)

/**
 * Builds the data context sent to the AI Coach. Only categories the owner has explicitly permitted
 * are included; everything else is listed as "not shared" so the model can say what is missing.
 * Personal identifiers (name, club, coach, photo) are never included.
 */
@Singleton
class CoachContextBuilder @Inject constructor(
    private val trainingDao: TrainingDao,
    private val techniqueDao: com.tkdpro.core.database.dao.TechniqueDao,
    private val runningDao: RunningDao,
    private val matchDao: MatchDao,
    private val motionDao: MotionDao,
    private val recoveryDao: RecoveryDao,
    private val reactionDao: ReactionDao,
    private val performanceDao: PerformanceDao,
    private val profileDao: ProfileDao,
    private val goalDao: GoalDao,
    private val json: Json,
) {
    suspend fun build(permissions: AiPermissions, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): CoachContext {
        val sources = mutableListOf<DataSource>()
        val missing = mutableListOf<String>()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

        val obj = buildJsonObject {
            put("today", LocalDate.now(zone).toString())
            put("period_start", day(from))
            put("period_end", day(to - 1))
            put(
                "notes",
                "All values come from the athlete's saved records. Manual observations are labelled 'manual'. " +
                    "Motion metrics are 2D computer-vision estimates. Session load = duration(min) x RPE, a simple subjective estimate.",
            )

            if (permissions.training) {
                val sessions = trainingDao.sessions(from, to)
                val records = trainingDao.allRecords().groupBy { it.sessionId }
                val techniqueNames = techniqueDao.all().associate { it.id to it.name }
                putJsonArray("training_sessions") {
                    sessions.take(MAX_ITEMS).forEach { s ->
                        addJsonObject {
                            put("date", day(s.startTime))
                            put("name", s.name)
                            put("category", s.category.name)
                            put("status", s.status.name)
                            put("duration_min", s.durationMin)
                            s.rpe?.let { put("rpe", it) }
                            TrainingLoad.sessionLoad(s.durationMin, s.rpe)?.let { put("session_load_estimate", it) }
                            s.difficulty?.let { put("difficulty_1_5", it) }
                            putJsonArray("techniques") {
                                records[s.id].orEmpty().forEach { r ->
                                    addJsonObject {
                                        put("technique", techniqueNames[r.techniqueId] ?: "unknown")
                                        put("total_reps", r.totalReps())
                                        r.rightReps?.let { put("right_reps", it) }
                                        r.leftReps?.let { put("left_reps", it) }
                                        r.sets?.let { put("sets", it) }
                                    }
                                }
                            }
                            if (s.notes.isNotBlank()) put("notes", s.notes.take(NOTE_LIMIT))
                        }
                    }
                }
                val usage = trainingDao.observeTechniqueUsage(from, to).first()
                putJsonArray("technique_totals") {
                    usage.forEach { u ->
                        addJsonObject {
                            put("technique", u.name)
                            put("sessions", u.sessions)
                            put("total_reps", u.totalReps)
                            put("right_reps", u.rightReps)
                            put("left_reps", u.leftReps)
                        }
                    }
                }
                sources += DataSource("training", sessions.size, from, to)
                if (sessions.none { it.status == SessionStatus.COMPLETED }) missing += "training: no completed sessions in period"
            } else {
                missing += "training: not shared"
            }

            if (permissions.running) {
                val runs = runningDao.completedInRange(from, to)
                putJsonArray("running_sessions") {
                    runs.take(MAX_ITEMS).forEach { r ->
                        addJsonObject {
                            put("date", day(r.startTime))
                            put("type", r.type.name)
                            put("distance_km", Math.round(r.distanceM) / 1000.0)
                            put("moving_time_min", r.movingTimeMs / 60000.0)
                            Pace.secondsPerKm(r.distanceM, r.movingTimeMs)?.let { put("avg_pace_sec_per_km", Math.round(it)) }
                            r.elevationGainM?.let { put("elevation_gain_m", Math.round(it)) }
                            r.avgHeartRate?.let { put("avg_heart_rate_manual", it) }
                        }
                    }
                }
                sources += DataSource("running", runs.size, from, to)
                if (runs.isEmpty()) missing += "running: no runs in period"
            } else {
                missing += "running: not shared"
            }

            if (permissions.matches) {
                val matches = matchDao.inRange(from, to)
                putJsonArray("matches") {
                    matches.take(MAX_ITEMS).forEach { m ->
                        val totals = MatchTotals.of(m.rounds)
                        addJsonObject {
                            put("date", day(m.match.date))
                            put("competition", m.match.competitionName)
                            put("stage", m.match.stage)
                            put("result", m.match.result.name)
                            m.match.scoreFor?.let { put("score_for", it) }
                            m.match.scoreAgainst?.let { put("score_against", it) }
                            put("rounds_recorded", m.rounds.size)
                            putJsonObject("manual_round_totals") {
                                totals.attackAttempts?.let { put("attack_attempts", it) }
                                totals.successfulAttacks?.let { put("successful_attacks", it) }
                                totals.defensiveActions?.let { put("defensive_actions", it) }
                                totals.counterattacks?.let { put("counterattacks", it) }
                            }
                            if (m.match.strengths.isNotBlank()) put("strengths_manual", m.match.strengths.take(NOTE_LIMIT))
                            if (m.match.improvements.isNotBlank()) put("improvements_manual", m.match.improvements.take(NOTE_LIMIT))
                            val tactical = m.rounds.map { it.tacticalNotes }.filter { it.isNotBlank() }
                            if (tactical.isNotEmpty()) put("tactical_notes_manual", tactical.joinToString(" | ").take(NOTE_LIMIT))
                        }
                    }
                }
                sources += DataSource("matches", matches.size, from, to)
                if (matches.isEmpty()) missing += "matches: none in period"
            } else {
                missing += "matches: not shared"
            }

            if (permissions.motion) {
                val analyses = motionDao.inRange(from, to)
                putJsonArray("motion_analyses") {
                    analyses.take(MAX_ITEMS).forEach { a ->
                        val result = runCatching { json.decodeFromString(MotionResult.serializer(), a.resultJson) }.getOrNull()
                        addJsonObject {
                            put("date", day(a.createdAt))
                            put("technique", a.techniqueName)
                            put("status", a.status.name)
                            if (result != null) {
                                put("repetitions", result.repetitions.size)
                                put("quality_warnings", buildJsonArray { result.quality.warnings.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) } })
                                putJsonArray("sides") {
                                    result.sides.forEach { s ->
                                        addJsonObject {
                                            put("side", s.side)
                                            put("repetitions", s.repetitions)
                                            s.peakAnkleHeight?.let { put("peak_ankle_height_torso_lengths_mean", it.mean) }
                                            s.kickKneeAngleAtPeak?.let { put("kick_knee_angle_at_peak_deg_mean", it.mean) }
                                            s.supportKneeAngleAtPeak?.let { put("support_knee_angle_at_peak_deg_mean", it.mean) }
                                            s.trunkLeanAtPeak?.let { put("trunk_lean_at_peak_deg_mean", it.mean) }
                                            s.timeToPeakMs?.let { put("time_to_peak_ms_mean", it.mean) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                sources += DataSource("motion", analyses.size, from, to)
            } else {
                missing += "motion analyses: not shared"
            }

            if (permissions.recovery) {
                val fromDay = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().toEpochDay()
                val toDay = Instant.ofEpochMilli(to - 1).atZone(zone).toLocalDate().toEpochDay()
                val entries = recoveryDao.range(fromDay, toDay)
                putJsonArray("recovery_checkins") {
                    entries.forEach { e ->
                        addJsonObject {
                            put("date", LocalDate.ofEpochDay(e.epochDay).toString())
                            e.sleepHours?.let { put("sleep_hours", it) }
                            e.sleepQuality?.let { put("sleep_quality_1_5", it) }
                            e.energy?.let { put("energy_1_5", it) }
                            e.fatigue?.let { put("fatigue_1_5", it) }
                            e.soreness?.let { put("soreness_1_5", it) }
                            e.recovery?.let { put("perceived_recovery_1_5", it) }
                            e.restingHeartRate?.let { put("resting_hr", it) }
                        }
                    }
                }
                sources += DataSource("recovery", entries.size, from, to)
            } else {
                missing += "recovery: not shared"
            }

            if (permissions.reaction) {
                val tests = reactionDao.inRange(from, to)
                val perf = performanceDao.inRange(from, to)
                putJsonArray("reaction_tests") {
                    tests.forEach { t ->
                        addJsonObject {
                            put("date", day(t.date))
                            put("type", t.type.name)
                            put("valid_trials", t.validTrials)
                            put("total_trials", t.totalTrials)
                            t.averageMs?.let { put("average_ms", Math.round(it)) }
                            t.bestMs?.let { put("best_ms", it) }
                            t.standardDeviationMs?.let { put("sd_ms", Math.round(it)) }
                            put("device", t.deviceModel)
                        }
                    }
                }
                putJsonArray("performance_tests") {
                    perf.forEach { p ->
                        addJsonObject {
                            put("date", day(p.date))
                            put("test", p.testType.name)
                            put("value", p.value)
                            put("unit", p.unit)
                            p.side?.let { put("side", it.name) }
                            if (p.method.isNotBlank()) put("method", p.method)
                        }
                    }
                }
                sources += DataSource("reaction_and_performance_tests", tests.size + perf.size, from, to)
            } else {
                missing += "reaction/performance tests: not shared"
            }

            if (permissions.profile) {
                val p = profileDao.get()
                putJsonObject("athlete") {
                    p?.startedTrainingYear?.let { put("training_since_year", it) }
                    p?.currentBelt?.takeIf { it.isNotBlank() }?.let { put("belt", it) }
                    p?.weightCategory?.takeIf { it.isNotBlank() }?.let { put("weight_category", it) }
                    p?.dominantSide?.let { put("dominant_side", it.name) }
                    p?.birthYear?.let { put("age_years_approx", LocalDate.now(zone).year - it) }
                }
                val goals = goalDao.all()
                putJsonArray("goals") {
                    goals.forEach { g ->
                        addJsonObject {
                            put("title", g.title)
                            put("category", g.category.name)
                            put("status", g.status.name)
                            g.targetDate?.let { put("target_date", day(it)) }
                        }
                    }
                }
                sources += DataSource("profile_and_goals", goals.size + (if (p != null) 1 else 0), from, to)
            } else {
                missing += "profile/goals: not shared"
            }

            putJsonArray("missing_or_not_shared") { missing.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
        return CoachContext(obj, sources, missing)
    }

    private companion object {
        const val MAX_ITEMS = 150
        const val NOTE_LIMIT = 400
    }
}

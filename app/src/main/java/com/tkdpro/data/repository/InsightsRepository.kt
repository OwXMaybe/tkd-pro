package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.MatchDao
import com.tkdpro.core.database.dao.MotionDao
import com.tkdpro.core.database.dao.ReactionDao
import com.tkdpro.core.database.dao.RecoveryDao
import com.tkdpro.core.database.dao.RunningDao
import com.tkdpro.core.database.dao.TechniqueUsage
import com.tkdpro.core.database.dao.TrainingDao
import com.tkdpro.core.database.entity.MatchResult
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.domain.run.Pace
import com.tkdpro.core.domain.time.DateRange
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A labelled numeric point; label is the bucket's start date. */
data class SeriesPoint(val date: LocalDate, val value: Double)

data class PeriodTotals(
    val trainingSessions: Int,
    val trainingMinutes: Int,
    val trainingLoad: Int,
    val sessionsWithoutRpe: Int,
    val runs: Int,
    val runDistanceM: Double,
    val runMovingMs: Long,
    val matches: Int,
    val analyses: Int,
    val reactionTests: Int,
    val recoveryEntries: Int,
    val activeDays: Int,
)

data class InsightsData(
    val range: DateRange,
    val weeklyBuckets: Boolean,
    val current: PeriodTotals,
    val previous: PeriodTotals,
    val trainingMinutes: List<SeriesPoint>,
    val trainingLoad: List<SeriesPoint>,
    val categoryMinutes: Map<TrainingCategory, Int>,
    val topTechniques: List<TechniqueUsage>,
    val longestStreakDays: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val attackSuccess: List<SeriesPoint>,
    val runDistance: List<SeriesPoint>,
    val runPace: List<SeriesPoint>,
    val reactionAverage: List<SeriesPoint>,
    val sleepHours: List<SeriesPoint>,
    val perceivedRecovery: List<SeriesPoint>,
    val averageSleepHours: Double?,
    val averageEnergy: Double?,
    val averageSoreness: Double?,
) {
    val isEmpty: Boolean
        get() = current.trainingSessions == 0 && current.runs == 0 && current.matches == 0 &&
            current.analyses == 0 && current.reactionTests == 0 && current.recoveryEntries == 0
}

/** Aggregates for the Insights dashboard, computed only from stored records in the selected range. */
@Singleton
class InsightsRepository @Inject constructor(
    private val trainingDao: TrainingDao,
    private val runningDao: RunningDao,
    private val matchDao: MatchDao,
    private val motionDao: MotionDao,
    private val reactionDao: ReactionDao,
    private val recoveryDao: RecoveryDao,
) {
    suspend fun compute(range: DateRange, zone: ZoneId = ZoneId.systemDefault()): InsightsData {
        val weekly = range.days() > 31
        fun bucket(date: LocalDate): LocalDate =
            if (weekly) LocalDate.ofEpochDay(TrainingLoad.weekStart(date.toEpochDay())) else date
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        val from = range.startMillis(zone)
        val to = range.endMillisExclusive(zone)
        val sessions = trainingDao.sessions(from, to).filter { it.status == SessionStatus.COMPLETED }
        val runs = runningDao.completedInRange(from, to)
        val matches = matchDao.inRange(from, to)
        val analyses = motionDao.inRange(from, to)
        val reactions = reactionDao.inRange(from, to)
        val recovery = recoveryDao.range(range.start.toEpochDay(), range.endInclusive.toEpochDay())

        val activeDates = (sessions.map { dateOf(it.startTime) } + runs.map { dateOf(it.startTime) }).toSortedSet()
        var longest = 0
        var streak = 0
        var prev: LocalDate? = null
        for (d in activeDates) {
            streak = if (prev != null && prev.plusDays(1) == d) streak + 1 else 1
            longest = maxOf(longest, streak)
            prev = d
        }

        val buckets = generateSequence(bucket(range.start)) { if (weekly) it.plusWeeks(1) else it.plusDays(1) }
            .takeWhile { !it.isAfter(range.endInclusive) }.toList()
        fun <T> series(items: List<T>, date: (T) -> LocalDate, value: (List<T>) -> Double): List<SeriesPoint> {
            val grouped = items.groupBy { bucket(date(it)) }
            return buckets.map { b -> SeriesPoint(b, grouped[b]?.let(value) ?: 0.0) }
        }

        return InsightsData(
            range = range,
            weeklyBuckets = weekly,
            current = totals(range, zone),
            previous = totals(range.previous(), zone),
            trainingMinutes = series(sessions, { dateOf(it.startTime) }) { l -> l.sumOf { it.durationMin }.toDouble() },
            trainingLoad = series(sessions, { dateOf(it.startTime) }) { l ->
                l.sumOf { TrainingLoad.sessionLoad(it.durationMin, it.rpe) ?: 0 }.toDouble()
            },
            categoryMinutes = sessions.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.durationMin } },
            topTechniques = trainingDao.observeTechniqueUsage(from, to).first().take(8),
            longestStreakDays = longest,
            wins = matches.count { it.match.result == MatchResult.WIN },
            losses = matches.count { it.match.result == MatchResult.LOSS },
            draws = matches.count { it.match.result == MatchResult.DRAW },
            attackSuccess = matches.sortedBy { it.match.date }.mapNotNull { m ->
                MatchTotals.of(m.rounds).attackSuccessRate?.let { SeriesPoint(dateOf(m.match.date), it * 100) }
            },
            runDistance = series(runs, { dateOf(it.startTime) }) { l -> l.sumOf { it.distanceM } / 1000.0 },
            runPace = runs.sortedBy { it.startTime }.mapNotNull { r ->
                Pace.secondsPerKm(r.distanceM, r.movingTimeMs)?.let { SeriesPoint(dateOf(r.startTime), it) }
            },
            reactionAverage = reactions.mapNotNull { t -> t.averageMs?.let { SeriesPoint(dateOf(t.date), it) } },
            sleepHours = recovery.mapNotNull { e -> e.sleepHours?.let { SeriesPoint(LocalDate.ofEpochDay(e.epochDay), it) } },
            perceivedRecovery = recovery.mapNotNull { e -> e.recovery?.let { SeriesPoint(LocalDate.ofEpochDay(e.epochDay), it.toDouble()) } },
            averageSleepHours = recovery.mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.average(),
            averageEnergy = recovery.mapNotNull { it.energy?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            averageSoreness = recovery.mapNotNull { it.soreness?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    suspend fun totals(range: DateRange, zone: ZoneId = ZoneId.systemDefault()): PeriodTotals {
        val from = range.startMillis(zone)
        val to = range.endMillisExclusive(zone)
        val sessions = trainingDao.sessions(from, to).filter { it.status == SessionStatus.COMPLETED }
        val runs = runningDao.completedInRange(from, to)
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return PeriodTotals(
            trainingSessions = sessions.size,
            trainingMinutes = sessions.sumOf { it.durationMin },
            trainingLoad = sessions.sumOf { TrainingLoad.sessionLoad(it.durationMin, it.rpe) ?: 0 },
            sessionsWithoutRpe = sessions.count { it.rpe == null },
            runs = runs.size,
            runDistanceM = runs.sumOf { it.distanceM },
            runMovingMs = runs.sumOf { it.movingTimeMs },
            matches = matchDao.inRange(from, to).size,
            analyses = motionDao.inRange(from, to).size,
            reactionTests = reactionDao.inRange(from, to).size,
            recoveryEntries = recoveryDao.range(range.start.toEpochDay(), range.endInclusive.toEpochDay()).size,
            activeDays = (sessions.map { dateOf(it.startTime) } + runs.map { dateOf(it.startTime) }).toSet().size,
        )
    }
}

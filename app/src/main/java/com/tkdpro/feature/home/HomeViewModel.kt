package com.tkdpro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkdpro.core.database.dao.TechniqueUsage
import com.tkdpro.core.database.entity.AiReport
import com.tkdpro.core.database.entity.AthleteProfile
import com.tkdpro.core.database.entity.Competition
import com.tkdpro.core.database.entity.MotionAnalysis
import com.tkdpro.core.database.entity.PersonalGoal
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.datastore.AppSettings
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.domain.time.DateRange
import com.tkdpro.data.repository.AiRepository
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.data.repository.PerformanceRepository
import com.tkdpro.data.repository.ProfileRepository
import com.tkdpro.data.repository.RunningRepository
import com.tkdpro.data.repository.TimelineItem
import com.tkdpro.data.repository.TimelineRepository
import com.tkdpro.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WeekVolume(
    val sessions: Int,
    val minutes: Int,
    val load: Int,
    val sessionsWithoutRpe: Int,
    val previousMinutes: Int,
    val technicalSessions: Int,
)

enum class ReminderKind { RECOVERY_CHECKIN, PLANNED_SESSION }

data class PendingReminder(val kind: ReminderKind, val title: String = "", val sessionId: Long? = null)

data class HomeState(
    val today: LocalDate,
    val profile: AthleteProfile?,
    val settings: AppSettings,
    val todaySessions: List<TrainingSession>,
    val recoveryToday: RecoveryEntry?,
    val week: WeekVolume,
    val weekRunDistanceM: Double,
    val weekRuns: Int,
    val lastRun: RunningSession?,
    val topTechniques: List<TechniqueUsage>,
    val latestReaction: ReactionTest?,
    val latestAnalysis: MotionAnalysis?,
    val nextCompetition: Competition?,
    val goals: List<PersonalGoal>,
    val weeklyReport: AiReport?,
    val recent: List<TimelineItem>,
) {
    val reminders: List<PendingReminder>
        get() = buildList {
            if (settings.recoveryReminderEnabled && recoveryToday == null) add(PendingReminder(ReminderKind.RECOVERY_CHECKIN))
            todaySessions.filter { it.status == SessionStatus.PLANNED }.forEach {
                add(PendingReminder(ReminderKind.PLANNED_SESSION, it.name, it.id))
            }
        }
}

private val TECHNICAL_CATEGORIES = setOf(TrainingCategory.TECHNICAL, TrainingCategory.COMBINATION, TrainingCategory.FOOTWORK, TrainingCategory.TACTICAL)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settings: SettingsRepository,
    profile: ProfileRepository,
    training: TrainingRepository,
    running: RunningRepository,
    performance: PerformanceRepository,
    media: MediaRepository,
    ai: AiRepository,
    timeline: TimelineRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val week = DateRange.week(today)
    private val prevWeek = week.previous()
    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private val todaySessions = training.sessions(ms(today), ms(today.plusDays(1)))
    private val weekSessions = training.sessions(week.startMillis(zone), week.endMillisExclusive(zone))
    private val prevWeekSessions = training.sessions(prevWeek.startMillis(zone), prevWeek.endMillisExclusive(zone))

    private val weekVolume: Flow<WeekVolume> = combine(weekSessions, prevWeekSessions) { cur, prev ->
        val done = cur.filter { it.status == SessionStatus.COMPLETED }
        WeekVolume(
            sessions = done.size,
            minutes = done.sumOf { it.durationMin },
            load = done.sumOf { TrainingLoad.sessionLoad(it.durationMin, it.rpe) ?: 0 },
            sessionsWithoutRpe = done.count { it.rpe == null },
            previousMinutes = prev.filter { it.status == SessionStatus.COMPLETED }.sumOf { it.durationMin },
            technicalSessions = done.count { it.category in TECHNICAL_CATEGORIES },
        )
    }

    private val runInfo = running.completedRuns.map { runs ->
        val inWeek = runs.filter { it.startTime >= week.startMillis(zone) && it.startTime < week.endMillisExclusive(zone) }
        Triple(inWeek.sumOf { it.distanceM }, inWeek.size, runs.firstOrNull())
    }

    private val groupA = combine(settings.settings, profile.profile, todaySessions, performance.recoveryForDay(today.toEpochDay()), weekVolume) { s, p, t, r, w ->
        GroupA(s, p, t, r, w)
    }

    private val groupB = combine(
        runInfo,
        training.techniqueUsage(week.startMillis(zone), week.endMillisExclusive(zone)),
        performance.latestReaction,
        media.latestAnalysis,
        profile.upcomingCompetitions(ms(today)),
    ) { run, usage, reaction, analysis, comps -> GroupB(run, usage, reaction, analysis, comps.firstOrNull()) }

    private val groupC = combine(
        profile.activeGoals,
        ai.latestWeekly,
        timeline.timeline(ms(today.minusDays(13)), ms(today.plusDays(1))),
    ) { goals, report, recent -> Triple(goals, report, recent) }

    val state: StateFlow<HomeState?> = combine(groupA, groupB, groupC) { a, b, c ->
        HomeState(
            today = today,
            profile = a.profile,
            settings = a.settings,
            todaySessions = a.today,
            recoveryToday = a.recovery,
            week = a.week,
            weekRunDistanceM = b.run.first,
            weekRuns = b.run.second,
            lastRun = b.run.third,
            topTechniques = b.usage.take(3),
            latestReaction = b.reaction,
            latestAnalysis = b.analysis,
            nextCompetition = b.competition,
            goals = c.first.take(3),
            weeklyReport = c.second,
            recent = c.third.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class GroupA(val settings: AppSettings, val profile: AthleteProfile?, val today: List<TrainingSession>, val recovery: RecoveryEntry?, val week: WeekVolume)
    private data class GroupB(
        val run: Triple<Double, Int, RunningSession?>,
        val usage: List<TechniqueUsage>,
        val reaction: ReactionTest?,
        val analysis: MotionAnalysis?,
        val competition: Competition?,
    )
}

package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.CompetitionDao
import com.tkdpro.core.database.dao.GoalDao
import com.tkdpro.core.database.dao.MatchDao
import com.tkdpro.core.database.dao.MotionDao
import com.tkdpro.core.database.dao.ProfileDao
import com.tkdpro.core.database.dao.ReactionDao
import com.tkdpro.core.database.dao.RecoveryDao
import com.tkdpro.core.database.dao.RunningDao
import com.tkdpro.core.database.dao.TrainingDao
import com.tkdpro.core.database.entity.AnalysisStatus
import com.tkdpro.core.database.entity.GoalStatus
import com.tkdpro.core.database.entity.MatchResult
import com.tkdpro.core.database.entity.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class TimelineType { TRAINING, RUN, MOTION, MATCH, REACTION, RECOVERY, ACHIEVEMENT }

/**
 * A single entry of the unified development timeline. Values are passed through from the stored
 * record; the UI formats and localises them.
 */
data class TimelineItem(
    val type: TimelineType,
    val id: Long,
    val timeMs: Long,
    val title: String,
    val detail: TimelineDetail,
)

sealed interface TimelineDetail {
    data class Training(val category: String, val durationMin: Int, val rpe: Int?, val planned: Boolean) : TimelineDetail
    data class Run(val distanceM: Double, val movingTimeMs: Long) : TimelineDetail
    data class Motion(val status: AnalysisStatus) : TimelineDetail
    data class MatchInfo(val result: MatchResult, val scoreFor: Int?, val scoreAgainst: Int?) : TimelineDetail
    data class Reaction(val averageMs: Double?, val validTrials: Int, val totalTrials: Int) : TimelineDetail
    data class Recovery(val sleepHours: Double?, val recovery: Int?) : TimelineDetail
    data class Achievement(val kind: String) : TimelineDetail
}

@Singleton
class TimelineRepository @Inject constructor(
    private val trainingDao: TrainingDao,
    private val runningDao: RunningDao,
    private val motionDao: MotionDao,
    private val matchDao: MatchDao,
    private val reactionDao: ReactionDao,
    private val recoveryDao: RecoveryDao,
    private val goalDao: GoalDao,
    private val profileDao: ProfileDao,
) {
    fun timeline(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Flow<List<TimelineItem>> {
        val training = trainingDao.observeSessions(from, to)
        val runs = runningDao.observeCompleted()
        val motion = motionDao.observeAll()
        val matches = matchDao.observeAll()
        val reaction = reactionDao.observeAll()
        val recovery = recoveryDao.observeAll()
        val goals = goalDao.observeAll()
        val belts = profileDao.observeBelts()

        val first = combine(training, runs, motion, matches) { t, r, m, ma ->
            buildList {
                t.forEach {
                    add(TimelineItem(TimelineType.TRAINING, it.id, it.startTime, it.name,
                        TimelineDetail.Training(it.category.name, it.durationMin, it.rpe, it.status == SessionStatus.PLANNED)))
                }
                r.filter { it.startTime in from until to }.forEach {
                    add(TimelineItem(TimelineType.RUN, it.id, it.startTime, it.customName ?: it.type.name,
                        TimelineDetail.Run(it.distanceM, it.movingTimeMs)))
                }
                m.filter { it.createdAt in from until to }.forEach {
                    add(TimelineItem(TimelineType.MOTION, it.id, it.createdAt, it.techniqueName, TimelineDetail.Motion(it.status)))
                }
                ma.filter { it.match.date in from until to }.forEach {
                    add(TimelineItem(TimelineType.MATCH, it.match.id, it.match.date, it.match.competitionName,
                        TimelineDetail.MatchInfo(it.match.result, it.match.scoreFor, it.match.scoreAgainst)))
                }
            }
        }
        val second = combine(reaction, recovery, goals, belts) { re, rc, g, b ->
            buildList {
                re.filter { it.date in from until to }.forEach {
                    add(TimelineItem(TimelineType.REACTION, it.id, it.date, it.type.name,
                        TimelineDetail.Reaction(it.averageMs, it.validTrials, it.totalTrials)))
                }
                rc.forEach {
                    val t = LocalDate.ofEpochDay(it.epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
                    if (t in from until to) {
                        add(TimelineItem(TimelineType.RECOVERY, it.id, t, "", TimelineDetail.Recovery(it.sleepHours, it.recovery)))
                    }
                }
                g.filter { it.status == GoalStatus.ACHIEVED && (it.achievedAt ?: 0) in from until to }.forEach {
                    add(TimelineItem(TimelineType.ACHIEVEMENT, it.id, it.achievedAt!!, it.title, TimelineDetail.Achievement("GOAL")))
                }
                b.filter { it.achievedOn in from until to }.forEach {
                    add(TimelineItem(TimelineType.ACHIEVEMENT, it.id, it.achievedOn, it.belt, TimelineDetail.Achievement("BELT")))
                }
            }
        }
        return combine(first, second) { a, b -> (a + b).sortedByDescending { it.timeMs } }
    }
}

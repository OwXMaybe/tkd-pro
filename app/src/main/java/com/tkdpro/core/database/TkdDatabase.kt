package com.tkdpro.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tkdpro.core.database.dao.AiDao
import com.tkdpro.core.database.dao.CombinationDao
import com.tkdpro.core.database.dao.CompetitionDao
import com.tkdpro.core.database.dao.GoalDao
import com.tkdpro.core.database.dao.MatchDao
import com.tkdpro.core.database.dao.MotionDao
import com.tkdpro.core.database.dao.PerformanceDao
import com.tkdpro.core.database.dao.ProfileDao
import com.tkdpro.core.database.dao.ReactionDao
import com.tkdpro.core.database.dao.RecoveryDao
import com.tkdpro.core.database.dao.RunningDao
import com.tkdpro.core.database.dao.ScoringPresetDao
import com.tkdpro.core.database.dao.SyncApplyDao
import com.tkdpro.core.database.dao.SyncDao
import com.tkdpro.core.database.dao.TechniqueDao
import com.tkdpro.core.database.dao.TrainingDao
import com.tkdpro.core.database.dao.VideoDao
import com.tkdpro.core.database.entity.AiReport
import com.tkdpro.core.database.entity.AthleteProfile
import com.tkdpro.core.database.entity.BeltRecord
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
import com.tkdpro.core.database.entity.ReactionAttempt
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.RunningSplit
import com.tkdpro.core.database.entity.ScoringPreset
import com.tkdpro.core.database.entity.SyncQueueItem
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.database.entity.VideoAsset

@Database(
    entities = [
        AthleteProfile::class, BeltRecord::class, PersonalGoal::class,
        TrainingSession::class, TrainingTechnique::class, TechniqueRecord::class,
        Combination::class, CombinationStep::class, CombinationPractice::class,
        VideoAsset::class, MotionAnalysis::class,
        Match::class, MatchRound::class, MatchEvent::class, ScoringPreset::class, Competition::class,
        RunningSession::class, GpsPoint::class, RunningSplit::class,
        ReactionTest::class, ReactionAttempt::class, PerformanceMetric::class, RecoveryEntry::class,
        AiReport::class, ChatMessage::class, SyncQueueItem::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TkdDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    abstract fun techniqueDao(): TechniqueDao
    abstract fun combinationDao(): CombinationDao
    abstract fun videoDao(): VideoDao
    abstract fun motionDao(): MotionDao
    abstract fun matchDao(): MatchDao
    abstract fun competitionDao(): CompetitionDao
    abstract fun scoringPresetDao(): ScoringPresetDao
    abstract fun runningDao(): RunningDao
    abstract fun reactionDao(): ReactionDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun recoveryDao(): RecoveryDao
    abstract fun profileDao(): ProfileDao
    abstract fun goalDao(): GoalDao
    abstract fun aiDao(): AiDao
    abstract fun syncDao(): SyncDao
    abstract fun syncApplyDao(): SyncApplyDao

    companion object {
        const val NAME = "tkdpro.db"
    }
}

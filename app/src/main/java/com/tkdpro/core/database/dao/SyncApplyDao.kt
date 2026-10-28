package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.RunningSplit
import com.tkdpro.core.database.entity.ScoringPreset
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique

/**
 * Raw access used only by sync: full snapshots for the first upload, and applying changes made on
 * the owner's other devices. Writes here never go back into the sync queue.
 */
@Dao
interface SyncApplyDao {
    @Query("SELECT * FROM athlete_profile") suspend fun allProfiles(): List<AthleteProfile>
    @Upsert suspend fun upsertProfile(v: AthleteProfile)
    @Query("DELETE FROM athlete_profile WHERE id = :id") suspend fun deleteProfile(id: Long)

    @Query("SELECT * FROM belt_records") suspend fun allBelts(): List<BeltRecord>
    @Upsert suspend fun upsertBelt(v: BeltRecord)
    @Query("DELETE FROM belt_records WHERE id = :id") suspend fun deleteBelt(id: Long)

    @Query("SELECT * FROM personal_goals") suspend fun allGoals(): List<PersonalGoal>
    @Upsert suspend fun upsertGoal(v: PersonalGoal)
    @Query("DELETE FROM personal_goals WHERE id = :id") suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM competitions") suspend fun allCompetitions(): List<Competition>
    @Upsert suspend fun upsertCompetition(v: Competition)
    @Query("DELETE FROM competitions WHERE id = :id") suspend fun deleteCompetition(id: Long)

    @Query("SELECT * FROM scoring_presets") suspend fun allPresets(): List<ScoringPreset>
    @Upsert suspend fun upsertPreset(v: ScoringPreset)
    @Query("DELETE FROM scoring_presets WHERE id = :id") suspend fun deletePreset(id: Long)

    @Query("SELECT * FROM techniques") suspend fun allTechniques(): List<TrainingTechnique>
    @Upsert suspend fun upsertTechnique(v: TrainingTechnique)
    @Query("DELETE FROM techniques WHERE id = :id") suspend fun deleteTechnique(id: Long)

    @Query("SELECT * FROM training_sessions") suspend fun allSessions(): List<TrainingSession>
    @Upsert suspend fun upsertSession(v: TrainingSession)
    @Query("DELETE FROM training_sessions WHERE id = :id") suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM technique_records") suspend fun allTechniqueRecords(): List<TechniqueRecord>
    @Upsert suspend fun upsertTechniqueRecord(v: TechniqueRecord)
    @Query("DELETE FROM technique_records WHERE id = :id") suspend fun deleteTechniqueRecord(id: Long)

    @Query("SELECT * FROM combinations") suspend fun allCombinations(): List<Combination>
    @Upsert suspend fun upsertCombination(v: Combination)
    @Query("DELETE FROM combinations WHERE id = :id") suspend fun deleteCombination(id: Long)
    @Query("SELECT COUNT(*) FROM combinations WHERE id = :id") suspend fun combinationExists(id: Long): Int

    @Query("SELECT * FROM combination_steps ORDER BY combinationId, position") suspend fun allSteps(): List<CombinationStep>
    @Query("DELETE FROM combination_steps WHERE combinationId = :combinationId") suspend fun clearSteps(combinationId: Long)
    @Insert suspend fun insertSteps(steps: List<CombinationStep>)

    @Transaction
    suspend fun replaceSteps(combinationId: Long, steps: List<CombinationStep>) {
        clearSteps(combinationId)
        insertSteps(steps)
    }

    @Query("SELECT * FROM combination_practices") suspend fun allPractices(): List<CombinationPractice>
    @Upsert suspend fun upsertPractice(v: CombinationPractice)
    @Query("DELETE FROM combination_practices WHERE id = :id") suspend fun deletePractice(id: Long)

    @Query("SELECT * FROM matches") suspend fun allMatches(): List<Match>
    @Upsert suspend fun upsertMatch(v: Match)
    @Query("DELETE FROM matches WHERE id = :id") suspend fun deleteMatch(id: Long)

    @Query("SELECT * FROM match_rounds") suspend fun allRounds(): List<MatchRound>
    @Upsert suspend fun upsertRound(v: MatchRound)
    @Query("DELETE FROM match_rounds WHERE id = :id") suspend fun deleteRound(id: Long)

    @Query("SELECT * FROM match_events") suspend fun allEvents(): List<MatchEvent>
    @Upsert suspend fun upsertEvent(v: MatchEvent)
    @Query("DELETE FROM match_events WHERE id = :id") suspend fun deleteEvent(id: Long)

    /** Unfinished runs stay on the phone until they are finished or discarded. */
    @Query("SELECT * FROM running_sessions WHERE status = 'COMPLETED'") suspend fun allCompletedRuns(): List<RunningSession>
    @Upsert suspend fun upsertRun(v: RunningSession)
    @Query("DELETE FROM running_sessions WHERE id = :id") suspend fun deleteRun(id: Long)
    @Query("SELECT COUNT(*) FROM running_sessions WHERE id = :id") suspend fun runExists(id: Long): Int

    @Query("SELECT * FROM gps_points WHERE runId = :runId ORDER BY timeMs") suspend fun points(runId: Long): List<GpsPoint>
    @Query("SELECT * FROM running_splits WHERE runId = :runId ORDER BY idx") suspend fun splits(runId: Long): List<RunningSplit>
    @Query("SELECT COUNT(*) FROM gps_points WHERE runId = :runId") suspend fun pointCount(runId: Long): Int
    @Insert suspend fun insertPoints(points: List<GpsPoint>)
    @Query("DELETE FROM running_splits WHERE runId = :runId") suspend fun clearSplits(runId: Long)
    @Insert suspend fun insertSplits(splits: List<RunningSplit>)

    @Transaction
    suspend fun restoreRoute(runId: Long, points: List<GpsPoint>, splits: List<RunningSplit>) {
        insertPoints(points)
        clearSplits(runId)
        insertSplits(splits)
    }

    @Query("SELECT * FROM reaction_tests") suspend fun allReactionTests(): List<ReactionTest>
    @Upsert suspend fun upsertReactionTest(v: ReactionTest)
    @Query("DELETE FROM reaction_tests WHERE id = :id") suspend fun deleteReactionTest(id: Long)

    @Query("SELECT * FROM performance_metrics") suspend fun allMetrics(): List<PerformanceMetric>
    @Upsert suspend fun upsertMetric(v: PerformanceMetric)
    @Query("DELETE FROM performance_metrics WHERE id = :id") suspend fun deleteMetric(id: Long)

    @Query("SELECT * FROM recovery_entries") suspend fun allRecovery(): List<RecoveryEntry>
    @Upsert suspend fun upsertRecovery(v: RecoveryEntry)
    @Query("DELETE FROM recovery_entries WHERE id = :id") suspend fun deleteRecovery(id: Long)
    @Query("SELECT * FROM recovery_entries WHERE epochDay = :epochDay") suspend fun recoveryForDay(epochDay: Long): RecoveryEntry?

    @Query("SELECT * FROM motion_analyses") suspend fun allAnalyses(): List<MotionAnalysis>
    @Upsert suspend fun upsertAnalysis(v: MotionAnalysis)
    @Query("DELETE FROM motion_analyses WHERE id = :id") suspend fun deleteAnalysis(id: Long)
    /** Pose frames stay on the phone (they are not synced), so a pulled copy keeps the local file. */
    @Query("SELECT posePath FROM motion_analyses WHERE id = :id") suspend fun posePath(id: Long): String?

    @Query("SELECT * FROM chat_messages") suspend fun allChat(): List<ChatMessage>
    @Upsert suspend fun upsertChat(v: ChatMessage)
    @Query("DELETE FROM chat_messages WHERE id = :id") suspend fun deleteChat(id: Long)

    @Query("SELECT * FROM ai_reports") suspend fun allReports(): List<AiReport>
    @Upsert suspend fun upsertReport(v: AiReport)
    @Query("DELETE FROM ai_reports WHERE id = :id") suspend fun deleteReport(id: Long)
}

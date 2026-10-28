package com.tkdpro.data.repository

import android.content.Context
import android.net.Uri
import com.tkdpro.core.database.TkdDatabase
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
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.security.SecretStore
import com.tkdpro.data.seed.Seeder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Complete, human-readable export of the owner's data (videos are referenced by file name, not embedded). */
@Serializable
data class ExportBundle(
    val format: String = "tkdpro-export",
    val version: Int = 1,
    val exportedAt: Long,
    val profile: AthleteProfile?,
    val belts: List<BeltRecord>,
    val goals: List<PersonalGoal>,
    val sessions: List<TrainingSession>,
    val techniques: List<TrainingTechnique>,
    val techniqueRecords: List<TechniqueRecord>,
    val combinations: List<Combination>,
    val combinationSteps: List<CombinationStep>,
    val combinationPractices: List<CombinationPractice>,
    val videos: List<VideoAsset>,
    val motionAnalyses: List<MotionAnalysis>,
    val matches: List<Match>,
    val matchRounds: List<MatchRound>,
    val matchEvents: List<MatchEvent>,
    val scoringPresets: List<ScoringPreset>,
    val competitions: List<Competition>,
    val runs: List<RunningSession>,
    val gpsPoints: List<GpsPoint>,
    val splits: List<RunningSplit>,
    val reactionTests: List<ReactionTest>,
    val reactionAttempts: List<ReactionAttempt>,
    val performanceMetrics: List<PerformanceMetric>,
    val recoveryEntries: List<RecoveryEntry>,
    val aiReports: List<AiReport>,
    val chatMessages: List<ChatMessage>,
)

@Singleton
class DataManagementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TkdDatabase,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val seeder: Seeder,
    private val json: Json,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val bundle = ExportBundle(
            exportedAt = System.currentTimeMillis(),
            profile = db.profileDao().get(),
            belts = db.profileDao().allBelts(),
            goals = db.goalDao().all(),
            sessions = db.trainingDao().all(),
            techniques = db.techniqueDao().all(),
            techniqueRecords = db.trainingDao().allRecords(),
            combinations = db.combinationDao().allCombinations(),
            combinationSteps = db.combinationDao().allSteps(),
            combinationPractices = db.combinationDao().allPractices(),
            videos = db.videoDao().all(),
            motionAnalyses = db.motionDao().all(),
            matches = db.matchDao().allMatches(),
            matchRounds = db.matchDao().allRounds(),
            matchEvents = db.matchDao().allEvents(),
            scoringPresets = db.scoringPresetDao().all(),
            competitions = db.competitionDao().all(),
            runs = db.runningDao().allCompleted(),
            gpsPoints = db.runningDao().allPoints(),
            splits = db.runningDao().allSplits(),
            reactionTests = db.reactionDao().all(),
            reactionAttempts = db.reactionDao().allAttempts(),
            performanceMetrics = db.performanceDao().all(),
            recoveryEntries = db.recoveryDao().all(),
            aiReports = db.aiDao().allReports(),
            chatMessages = db.aiDao().recentMessages(Int.MAX_VALUE).reversed(),
        )
        val prettyJson = Json(json) { prettyPrint = true }
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(prettyJson.encodeToString(ExportBundle.serializer(), bundle).toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open export destination")
    }

    /**
     * Permanently deletes all local data: database rows, private videos, pose files, the profile photo,
     * the stored backend token and preferences. The built-in technique library is re-created afterwards.
     */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        listOf("videos", "analysis", "profile").forEach { File(context.filesDir, it).deleteRecursively() }
        secrets.clear()
        settings.clear()
        seeder.run()
    }
}

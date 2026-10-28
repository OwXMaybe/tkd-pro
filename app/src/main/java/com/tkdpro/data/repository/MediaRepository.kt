package com.tkdpro.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.tkdpro.core.database.dao.MotionDao
import com.tkdpro.core.database.dao.VideoDao
import com.tkdpro.core.database.entity.AnalysisEngine
import com.tkdpro.core.database.entity.AnalysisStatus
import com.tkdpro.core.database.entity.MotionAnalysis
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.domain.motion.MotionAnalyzer
import com.tkdpro.core.domain.motion.MotionResult
import com.tkdpro.core.domain.motion.PoseFrame
import com.tkdpro.core.domain.motion.Side
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.network.BackendError
import com.tkdpro.core.network.MotionInterpretationRequest
import com.tkdpro.data.motion.PoseExtractor
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.di.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class AiNotPermittedException : IllegalStateException("AI use of this data category is not permitted")

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val motionDao: MotionDao,
    private val extractor: PoseExtractor,
    private val client: BackendClient,
    private val settings: SettingsRepository,
    private val json: Json,
    private val sync: SyncTracker,
    private val clock: Clock,
) {
    val analyses: Flow<List<MotionAnalysis>> = motionDao.observeAll()
    val latestAnalysis: Flow<MotionAnalysis?> = motionDao.observeLatest()
    fun analysis(id: Long): Flow<MotionAnalysis?> = motionDao.observe(id)
    fun video(id: Long): Flow<VideoAsset?> = videoDao.observe(id)
    suspend fun videoById(id: Long): VideoAsset? = videoDao.get(id)

    /** Copies a picked/recorded video into private app storage and records its real metadata. */
    suspend fun importVideo(uri: Uri, label: String = ""): VideoAsset = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "videos").apply { mkdirs() }
        val file = File(dir, "video_${clock.now()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: error("Cannot open video")
        val retriever = MediaMetadataRetriever()
        val asset = try {
            retriever.setDataSource(file.absolutePath)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotated = rotation == 90 || rotation == 270
            VideoAsset(
                path = file.absolutePath,
                label = label,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = if (rotated) h else w,
                height = if (rotated) w else h,
                sizeBytes = file.length(),
                createdAt = clock.now(),
            )
        } catch (e: RuntimeException) {
            file.delete()
            throw IllegalArgumentException("Unsupported video", e)
        } finally {
            retriever.release()
        }
        asset.copy(id = videoDao.insert(asset))
    }

    suspend fun deleteVideo(id: Long) = withContext(Dispatchers.IO) {
        videoDao.get(id)?.let { File(it.path).delete() }
        videoDao.delete(id)
    }

    /**
     * Runs the full analysis pipeline and stores the result, including failed quality checks
     * (so the athlete can see why a recording could not be analysed).
     */
    suspend fun analyze(
        video: VideoAsset,
        techniqueId: Long?,
        techniqueName: String,
        side: Side?,
        onProgress: (Float) -> Unit,
    ): Long {
        val engine = settings.current().analysisEngine
        val (result, frames) = when (engine) {
            AnalysisEngine.ON_DEVICE -> {
                val (info, poseFrames) = extractor.extract(video.path, onProgress)
                MotionAnalyzer().analyze(info, poseFrames, side) to poseFrames
            }
            AnalysisEngine.SERVER -> analyzeOnServer(video, techniqueName, side)
        }
        val posePath = savePoses(frames)
        val analysis = MotionAnalysis(
            techniqueId = techniqueId,
            techniqueName = techniqueName,
            videoAssetId = video.id,
            side = side?.name,
            status = if (result.quality.passed) AnalysisStatus.COMPLETED else AnalysisStatus.INSUFFICIENT_QUALITY,
            engine = engine,
            resultJson = json.encodeToString(MotionResult.serializer(), result),
            posePath = posePath,
            createdAt = clock.now(),
        )
        val id = motionDao.insert(analysis)
        sync.upsert("motion_analyses", id, analysis.copy(id = id, posePath = null), MotionAnalysis.serializer())
        return id
    }

    private suspend fun analyzeOnServer(video: VideoAsset, technique: String, side: Side?): Pair<MotionResult, List<PoseFrame>> {
        val part = MultipartBody.Part.createFormData(
            "video", File(video.path).name, File(video.path).asRequestBody("video/mp4".toMediaType()),
        )
        val response = client.call {
            it.analyzeMotion(part, technique.toRequestBody(), (side?.name ?: "").toRequestBody())
        }.getOrThrow()
        val result = json.decodeFromJsonElement(MotionResult.serializer(), response.result)
        val frames = json.decodeFromJsonElement(ListSerializer(PoseFrame.serializer()), response.frames)
        return result to frames
    }

    private suspend fun savePoses(frames: List<PoseFrame>): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "analysis").apply { mkdirs() }
        val file = File(dir, "poses_${clock.now()}.json")
        file.writeText(json.encodeToString(ListSerializer(PoseFrame.serializer()), frames))
        file.absolutePath
    }

    suspend fun loadPoses(path: String?): List<PoseFrame> = withContext(Dispatchers.IO) {
        if (path == null) return@withContext emptyList()
        val f = File(path)
        if (!f.exists()) emptyList() else json.decodeFromString(ListSerializer(PoseFrame.serializer()), f.readText())
    }

    fun decodeResult(a: MotionAnalysis): MotionResult? =
        runCatching { json.decodeFromString(MotionResult.serializer(), a.resultJson) }.getOrNull()

    /**
     * Requests an AI interpretation of the measured/estimated metrics. Only the numeric summary is sent —
     * never the video — and only if the owner has allowed motion data for the AI Coach.
     */
    suspend fun requestInterpretation(analysisId: Long): Result<String> {
        val ai = settings.current().ai
        if (!ai.enabled || !ai.motion) return Result.failure(AiNotPermittedException())
        val analysis = motionDao.get(analysisId) ?: return Result.failure(IllegalArgumentException("Not found"))
        val summary = json.parseToJsonElement(analysis.resultJson).jsonObject.let { obj ->
            JsonObject(obj.filterKeys { it != "timeline" })
        }
        return client.call {
            it.motionInterpretation(
                MotionInterpretationRequest(analysis.techniqueName, summary, Locale.getDefault().toLanguageTag()),
            )
        }.map { resp ->
            motionDao.update(analysis.copy(aiInterpretation = resp.interpretation, aiGeneratedAt = clock.now()))
            resp.interpretation
        }.recoverCatching { e -> throw (e as? BackendError ?: e) }
    }

    suspend fun saveNotes(analysisId: Long, notes: String) {
        motionDao.get(analysisId)?.let { motionDao.update(it.copy(notes = notes)) }
    }

    suspend fun deleteAnalysis(id: Long) = withContext(Dispatchers.IO) {
        motionDao.get(id)?.posePath?.let { File(it).delete() }
        motionDao.delete(id)
        sync.delete("motion_analyses", id)
    }
}

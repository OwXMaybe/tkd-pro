package com.tkdpro.data.motion

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.tkdpro.core.domain.motion.Landmark
import com.tkdpro.core.domain.motion.PoseFrame
import com.tkdpro.core.domain.motion.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

class VideoTooLongException(val maxMs: Long) : IllegalArgumentException("Video longer than $maxMs ms")

/**
 * On-device pose estimation with MediaPipe Pose Landmarker (VIDEO mode).
 * Frames are sampled at up to [TARGET_FPS] and downscaled for speed; landmark coordinates are
 * normalised, so downscaling does not change the geometry.
 */
@Singleton
class PoseExtractor @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun extract(path: String, onProgress: (Float) -> Unit): Pair<VideoInfo, List<PoseFrame>> =
        withContext(Dispatchers.Default) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durationMs > MAX_DURATION_MS) throw VideoTooLongException(MAX_DURATION_MS)
                val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
                val nativeFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
                val fps = minOf(nativeFps?.takeIf { it > 0 } ?: TARGET_FPS, TARGET_FPS)
                val frameCount = ((durationMs / 1000.0) * fps).toInt()

                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()

                val frames = ArrayList<PoseFrame>(frameCount)
                PoseLandmarker.createFromOptions(context, options).use { landmarker ->
                    for (i in 0 until frameCount) {
                        coroutineContext.ensureActive()
                        val tMs = (i * 1000.0 / fps).toLong()
                        val bitmap = retriever.getScaledFrameAtTime(
                            tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, MAX_FRAME_SIDE, MAX_FRAME_SIDE,
                        )
                        if (bitmap == null) {
                            frames += PoseFrame(tMs, emptyList())
                        } else {
                            val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            val result = landmarker.detectForVideo(BitmapImageBuilder(argb).build(), tMs)
                            val landmarks = result.landmarks().firstOrNull()?.map {
                                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
                            } ?: emptyList()
                            frames += PoseFrame(tMs, landmarks)
                            if (argb !== bitmap) argb.recycle()
                            bitmap.recycle()
                        }
                        onProgress((i + 1).toFloat() / frameCount.coerceAtLeast(1))
                    }
                }
                VideoInfo(durationMs, w, h, frames.size, fps) to frames
            } finally {
                retriever.release()
            }
        }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        const val TARGET_FPS = 20.0
        const val MAX_FRAME_SIDE = 640
        const val MAX_DURATION_MS = 30_000L
    }
}

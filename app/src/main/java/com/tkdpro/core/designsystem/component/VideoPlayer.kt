package com.tkdpro.core.designsystem.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tkdpro.core.domain.motion.PoseFrame
import com.tkdpro.core.domain.motion.PoseIndex
import kotlinx.coroutines.delay
import java.io.File

/** Handle to control playback from outside the player (e.g. jump to a keyframe). */
class VideoController {
    internal var player: ExoPlayer? = null
    fun seekTo(ms: Long) {
        player?.seekTo(ms)
        player?.pause()
    }
}

/**
 * Plays a private video file. When [poses] are given, the MediaPipe skeleton for the frame closest
 * to the current playback position is drawn on top (an overlay of computer-vision estimates).
 */
@Composable
fun VideoPlayer(
    path: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 9f / 16f,
    poses: List<PoseFrame> = emptyList(),
    controller: VideoController? = null,
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    var positionMs by remember { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        controller?.player = player
        onDispose {
            controller?.player = null
            player.release()
        }
    }
    LaunchedEffect(player, poses.isNotEmpty()) {
        if (poses.isEmpty()) return@LaunchedEffect
        while (true) {
            positionMs = player.currentPosition
            delay(33)
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(MaterialTheme.shapes.medium)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (poses.isNotEmpty()) {
            val frame = remember(positionMs, poses) { closestFrame(poses, positionMs) }
            if (frame != null && frame.hasPose) {
                Canvas(Modifier.fillMaxSize()) {
                    fun pt(i: Int) = Offset(frame.landmarks[i].x * size.width, frame.landmarks[i].y * size.height)
                    PoseIndex.CONNECTIONS.forEach { (a, b) ->
                        if (frame.landmarks[a].visibility > 0.5f && frame.landmarks[b].visibility > 0.5f) {
                            drawLine(accent, pt(a), pt(b), strokeWidth = 3.dp.toPx())
                        }
                    }
                    PoseIndex.KEY_LANDMARKS.forEach { i ->
                        if (frame.landmarks[i].visibility > 0.5f) drawCircle(Color.White, 4.dp.toPx(), pt(i))
                    }
                }
            }
        }
    }
}

private fun closestFrame(frames: List<PoseFrame>, t: Long): PoseFrame? {
    if (frames.isEmpty()) return null
    var lo = 0
    var hi = frames.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (frames[mid].timestampMs < t) lo = mid + 1 else hi = mid
    }
    val candidate = frames[lo]
    val prev = frames.getOrNull(lo - 1)
    return if (prev != null && kotlin.math.abs(prev.timestampMs - t) < kotlin.math.abs(candidate.timestampMs - t)) prev else candidate
}

/** Launcher for the system photo picker restricted to videos (no storage permission needed). */
@Composable
fun rememberVideoPicker(onPicked: (Uri) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }

fun videoOnlyRequest() = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

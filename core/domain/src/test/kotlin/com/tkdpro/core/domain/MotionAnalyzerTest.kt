package com.tkdpro.core.domain

import com.tkdpro.core.domain.motion.Landmark
import com.tkdpro.core.domain.motion.MotionAnalyzer
import com.tkdpro.core.domain.motion.MotionComparison
import com.tkdpro.core.domain.motion.PoseFrame
import com.tkdpro.core.domain.motion.PoseIndex
import com.tkdpro.core.domain.motion.QualityIssue
import com.tkdpro.core.domain.motion.Side
import com.tkdpro.core.domain.motion.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MotionAnalyzerTest {

    /**
     * Synthetic standing figure (test fixture only) whose right ankle rises in [kicks] bumps.
     * Coordinates are normalised; the figure fills a 1080x1920 portrait frame.
     */
    private fun frames(count: Int, fps: Double, kicks: Int, visibility: Float = 0.95f): List<PoseFrame> =
        (0 until count).map { i ->
            val t = (i * 1000.0 / fps).toLong()
            val phase = i.toDouble() / count * kicks
            val bump = maxOf(0.0, sin(phase * 2 * PI)).let { it * it }
            val lm = MutableList(PoseIndex.COUNT) { Landmark(0.5f, 0.5f, 0f, visibility) }
            lm[PoseIndex.LEFT_SHOULDER] = Landmark(0.45f, 0.30f, 0f, visibility)
            lm[PoseIndex.RIGHT_SHOULDER] = Landmark(0.55f, 0.30f, 0f, visibility)
            lm[PoseIndex.LEFT_HIP] = Landmark(0.46f, 0.50f, 0f, visibility)
            lm[PoseIndex.RIGHT_HIP] = Landmark(0.54f, 0.50f, 0f, visibility)
            lm[PoseIndex.LEFT_KNEE] = Landmark(0.46f, 0.65f, 0f, visibility)
            lm[PoseIndex.LEFT_ANKLE] = Landmark(0.46f, 0.80f, 0f, visibility)
            // Right leg swings forward and up.
            val ankleY = (0.80 - bump * 0.35).toFloat()
            val ankleX = (0.54 + bump * 0.25).toFloat()
            lm[PoseIndex.RIGHT_KNEE] = Landmark(((0.54f + ankleX) / 2) + 0.02f, ((0.50f + ankleY) / 2), 0f, visibility)
            lm[PoseIndex.RIGHT_ANKLE] = Landmark(ankleX, ankleY, 0f, visibility)
            PoseFrame(t, lm)
        }

    private fun info(frames: Int, fps: Double) = VideoInfo((frames / fps * 1000).toLong(), 1080, 1920, frames, fps)

    @Test
    fun detectsRepetitionsOnKickingSide() {
        val f = frames(150, 30.0, kicks = 3)
        val result = MotionAnalyzer().analyze(info(150, 30.0), f)
        assertTrue(result.quality.passed)
        assertEquals(3, result.repetitions.size)
        assertTrue(result.repetitions.all { it.side == Side.RIGHT.name })
        assertEquals(9, result.keyframes.size)
        val right = result.sides.single()
        assertEquals(3, right.repetitions)
        // Peak ankle at y=0.45 vs hip y=0.50 with a 384 px torso -> +0.25 torso lengths.
        assertEquals(0.25, right.peakAnkleHeight!!.mean, 0.02)
    }

    @Test
    fun poorVisibilityBlocksAnalysisWithoutMetrics() {
        val f = frames(150, 30.0, kicks = 3, visibility = 0.2f)
        val result = MotionAnalyzer().analyze(info(150, 30.0), f)
        assertFalse(result.quality.passed)
        assertTrue(QualityIssue.KEY_LANDMARKS_LOW_VISIBILITY in result.quality.blocking)
        assertTrue(result.repetitions.isEmpty())
        assertTrue(result.timeline.isEmpty())
    }

    @Test
    fun noPoseBlocksAnalysis() {
        val empty = (0 until 60).map { PoseFrame(it * 33L, emptyList()) }
        val result = MotionAnalyzer().analyze(info(60, 30.0), empty)
        assertTrue(QualityIssue.POSE_RARELY_DETECTED in result.quality.blocking)
    }

    @Test
    fun comparisonOnlyIncludesSharedMetrics() {
        val a = MotionAnalyzer().analyze(info(150, 30.0), frames(150, 30.0, 3))
        val b = MotionAnalyzer().analyze(info(150, 30.0), frames(150, 30.0, 2))
        val deltas = MotionComparison.compare(a, b)
        assertTrue(deltas.isNotEmpty())
        assertTrue(deltas.all { it.side == Side.RIGHT.name })
        assertTrue(MotionComparison.leftRight(a).isEmpty())
    }
}

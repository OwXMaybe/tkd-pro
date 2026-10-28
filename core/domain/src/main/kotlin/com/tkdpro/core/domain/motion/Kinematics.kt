package com.tkdpro.core.domain.motion

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 2D kinematics computed from pose landmarks in pixel space.
 * All angles are projections onto the camera plane and are therefore estimates: they depend on
 * camera angle and distance and are not equivalent to 3D motion-capture joint angles.
 */
class Kinematics(private val widthPx: Int, private val heightPx: Int) {

    private data class P(val x: Double, val y: Double)

    private fun p(l: Landmark) = P(l.x.toDouble() * widthPx, l.y.toDouble() * heightPx)

    /** Inner angle at [b] formed by a-b-c, degrees in 0..180. */
    fun angle(a: Landmark, b: Landmark, c: Landmark): Double {
        val pa = p(a)
        val pb = p(b)
        val pc = p(c)
        val v1x = pa.x - pb.x
        val v1y = pa.y - pb.y
        val v2x = pc.x - pb.x
        val v2y = pc.y - pb.y
        val n1 = sqrt(v1x * v1x + v1y * v1y)
        val n2 = sqrt(v2x * v2x + v2y * v2y)
        if (n1 == 0.0 || n2 == 0.0) return Double.NaN
        val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    fun kneeAngle(f: PoseFrame, side: Side): Double = when (side) {
        Side.LEFT -> angle(f.landmarks[PoseIndex.LEFT_HIP], f.landmarks[PoseIndex.LEFT_KNEE], f.landmarks[PoseIndex.LEFT_ANKLE])
        Side.RIGHT -> angle(f.landmarks[PoseIndex.RIGHT_HIP], f.landmarks[PoseIndex.RIGHT_KNEE], f.landmarks[PoseIndex.RIGHT_ANKLE])
    }

    fun hipAngle(f: PoseFrame, side: Side): Double = when (side) {
        Side.LEFT -> angle(f.landmarks[PoseIndex.LEFT_SHOULDER], f.landmarks[PoseIndex.LEFT_HIP], f.landmarks[PoseIndex.LEFT_KNEE])
        Side.RIGHT -> angle(f.landmarks[PoseIndex.RIGHT_SHOULDER], f.landmarks[PoseIndex.RIGHT_HIP], f.landmarks[PoseIndex.RIGHT_KNEE])
    }

    private fun mid(a: Landmark, b: Landmark) = P((p(a).x + p(b).x) / 2, (p(a).y + p(b).y) / 2)

    private fun hipMid(f: PoseFrame) = mid(f.landmarks[PoseIndex.LEFT_HIP], f.landmarks[PoseIndex.RIGHT_HIP])
    private fun shoulderMid(f: PoseFrame) = mid(f.landmarks[PoseIndex.LEFT_SHOULDER], f.landmarks[PoseIndex.RIGHT_SHOULDER])

    fun torsoLengthPx(f: PoseFrame): Double {
        val h = hipMid(f)
        val s = shoulderMid(f)
        return sqrt((h.x - s.x) * (h.x - s.x) + (h.y - s.y) * (h.y - s.y))
    }

    /** Trunk deviation from vertical in degrees (0 = upright), regardless of lean direction. */
    fun trunkLean(f: PoseFrame): Double {
        val h = hipMid(f)
        val s = shoulderMid(f)
        val dx = s.x - h.x
        val dy = h.y - s.y // image y grows downwards
        return abs(Math.toDegrees(atan2(dx, dy)))
    }

    /** Angle of the hip line relative to horizontal, degrees. */
    fun hipLineTilt(f: PoseFrame): Double {
        val l = p(f.landmarks[PoseIndex.LEFT_HIP])
        val r = p(f.landmarks[PoseIndex.RIGHT_HIP])
        val deg = abs(Math.toDegrees(atan2(r.y - l.y, r.x - l.x)))
        return if (deg > 90) 180 - deg else deg
    }

    /**
     * Ankle height relative to the hip midpoint, in torso lengths.
     * 0 = ankle at hip height, positive = above the hip, negative = below.
     */
    fun ankleHeight(f: PoseFrame, side: Side): Double {
        val torso = torsoLengthPx(f)
        if (torso <= 0) return Double.NaN
        val ankle = p(f.landmarks[if (side == Side.LEFT) PoseIndex.LEFT_ANKLE else PoseIndex.RIGHT_ANKLE])
        return (hipMid(f).y - ankle.y) / torso
    }

    /** Horizontal position of the hip midpoint, in torso lengths (for sway / balance pattern). */
    fun hipMidX(f: PoseFrame): Double {
        val torso = torsoLengthPx(f)
        return if (torso <= 0) Double.NaN else hipMid(f).x / torso
    }
}

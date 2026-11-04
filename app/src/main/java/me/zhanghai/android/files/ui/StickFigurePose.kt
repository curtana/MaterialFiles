/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** A point in the unit box of a stick figure, where x goes forward (LTR) and y goes down. */
data class Joint(val x: Float, val y: Float)

/** A stick figure of a head and five lines from the neck and the hip. */
data class StickFigurePose(
    val head: Joint,
    val headRadius: Float,
    val neck: Joint,
    val hip: Joint,
    val hand1: Joint,
    val hand2: Joint,
    val foot1: Joint,
    val foot2: Joint
) {
    companion object {
        private const val HEAD_RADIUS = 0.11f
        private const val ARM_LENGTH = 0.26f
        private const val LEG_LENGTH = 0.34f

        private const val RUNNING_ARM_SWING = 0.9f
        private const val RUNNING_LEG_SWING = 0.7f
        private val RUNNING_HEAD = Joint(0.58f, 0.18f)
        private val RUNNING_NECK = Joint(0.53f, 0.33f)
        private val RUNNING_HIP = Joint(0.45f, 0.62f)

        // Angles of the arms from straight up.
        private const val CHEERING_ARM_ANGLE_DOWN = 2.8f
        private const val CHEERING_ARM_ANGLE_UP = 0.5f
        private val STANDING_HEAD = Joint(0.5f, 0.22f)
        private val STANDING_NECK = Joint(0.5f, 0.36f)
        private val STANDING_HIP = Joint(0.5f, 0.64f)
        private val STANDING_FOOT1 = Joint(0.38f, 0.96f)
        private val STANDING_FOOT2 = Joint(0.62f, 0.96f)

        // Fallen backwards, with the head towards the start.
        private val LYING = StickFigurePose(
            Joint(0.16f, 0.80f), HEAD_RADIUS, Joint(0.30f, 0.82f), Joint(0.60f, 0.85f),
            Joint(0.36f, 0.64f), Joint(0.44f, 0.96f), Joint(0.90f, 0.72f), Joint(0.93f, 0.92f)
        )

        /** A running pose at [t] of a cycle, where t is wrapped into [0, 1). */
        fun running(t: Float): StickFigurePose {
            val swing = sin(2 * PI * (t - floor(t))).toFloat()
            val neck = RUNNING_NECK
            val hip = RUNNING_HIP
            return StickFigurePose(
                RUNNING_HEAD, HEAD_RADIUS, neck, hip,
                limbDown(neck, ARM_LENGTH, RUNNING_ARM_SWING * swing),
                limbDown(neck, ARM_LENGTH, -RUNNING_ARM_SWING * swing),
                limbDown(hip, LEG_LENGTH, -RUNNING_LEG_SWING * swing),
                limbDown(hip, LEG_LENGTH, RUNNING_LEG_SWING * swing)
            )
        }

        /** From arms down at 0 to both arms raised above the head at 1. */
        fun cheering(t: Float): StickFigurePose {
            val angle = lerp(CHEERING_ARM_ANGLE_DOWN, CHEERING_ARM_ANGLE_UP, t.coerceIn(0f, 1f))
            val neck = STANDING_NECK
            val dx = ARM_LENGTH * sin(angle)
            val dy = -ARM_LENGTH * cos(angle)
            return StickFigurePose(
                STANDING_HEAD, HEAD_RADIUS, neck, STANDING_HIP, Joint(neck.x - dx, neck.y + dy),
                Joint(neck.x + dx, neck.y + dy), STANDING_FOOT1, STANDING_FOOT2
            )
        }

        /** From standing at 0 to lying at 1. */
        fun fallen(t: Float): StickFigurePose {
            val fraction = t.coerceIn(0f, 1f)
            val standing = cheering(0f)
            val lying = LYING
            return StickFigurePose(
                lerp(standing.head, lying.head, fraction),
                lerp(standing.headRadius, lying.headRadius, fraction),
                lerp(standing.neck, lying.neck, fraction), lerp(standing.hip, lying.hip, fraction),
                lerp(standing.hand1, lying.hand1, fraction),
                lerp(standing.hand2, lying.hand2, fraction),
                lerp(standing.foot1, lying.foot1, fraction),
                lerp(standing.foot2, lying.foot2, fraction)
            )
        }

        // The angle is from straight down, positive towards the front.
        private fun limbDown(from: Joint, length: Float, angle: Float): Joint =
            Joint(from.x + length * sin(angle), from.y + length * cos(angle))

        private fun lerp(start: Float, end: Float, fraction: Float): Float =
            start + (end - start) * fraction

        private fun lerp(start: Joint, end: Joint, fraction: Float): Joint =
            Joint(lerp(start.x, end.x, fraction), lerp(start.y, end.y, fraction))
    }
}

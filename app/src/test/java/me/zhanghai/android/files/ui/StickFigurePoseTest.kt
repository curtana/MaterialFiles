/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StickFigurePoseTest {
    private val ts = (0..20).map { it / 20f }

    private fun StickFigurePose.joints(): List<Joint> =
        listOf(head, neck, hip, hand1, hand2, foot1, foot2)

    private fun assertJointEquals(expected: Joint, actual: Joint) {
        assertEquals(expected.x, actual.x, EPSILON)
        assertEquals(expected.y, actual.y, EPSILON)
    }

    private fun assertPoseEquals(expected: StickFigurePose, actual: StickFigurePose) {
        assertEquals(expected.headRadius, actual.headRadius, EPSILON)
        expected.joints().zip(actual.joints()).forEach { (e, a) -> assertJointEquals(e, a) }
    }

    // U-P01
    @Test
    fun allPoses_insideUnitBox() {
        for (pose in listOf(
            StickFigurePose::running, StickFigurePose::cheering, StickFigurePose::fallen
        )) {
            for (t in ts) {
                val figure = pose(t)
                for (joint in figure.joints()) {
                    assertTrue("$joint at $t", joint.x in 0f..1f && joint.y in 0f..1f)
                }
                val head = figure.head
                val r = figure.headRadius
                assertTrue(
                    "head at $t",
                    head.x - r >= 0 && head.x + r <= 1 && head.y - r >= 0 && head.y + r <= 1
                )
            }
        }
    }

    // U-P02
    @Test
    fun running_isPeriodic() {
        assertPoseEquals(StickFigurePose.running(0f), StickFigurePose.running(1f))
        assertPoseEquals(StickFigurePose.running(0.3f), StickFigurePose.running(1.3f))
    }

    // U-P03
    @Test
    fun running_halfCycleApart_isMirrored() {
        val a = StickFigurePose.running(0.25f)
        val b = StickFigurePose.running(0.75f)
        assertJointEquals(a.hand1, b.hand2)
        assertJointEquals(a.hand2, b.hand1)
        assertJointEquals(a.foot1, b.foot2)
        assertJointEquals(a.foot2, b.foot1)
    }

    // U-P04
    @Test
    fun running_staticPose_hasStride() {
        val pose = StickFigurePose.running(0.25f)
        assertTrue(abs(pose.foot1.x - pose.foot2.x) >= 0.2f)
    }

    // U-P05
    @Test
    fun cheering_raisesArms() {
        val down = StickFigurePose.cheering(0f)
        assertTrue(down.hand1.y > down.neck.y && down.hand2.y > down.neck.y)
        val up = StickFigurePose.cheering(1f)
        assertTrue(up.hand1.y < up.head.y && up.hand2.y < up.head.y)
    }

    // U-P06
    @Test
    fun cheering_handsRiseMonotonicallyAndSymmetrically() {
        var lastY = Float.MAX_VALUE
        for (t in ts) {
            val pose = StickFigurePose.cheering(t)
            assertTrue("at $t", pose.hand1.y <= lastY)
            lastY = pose.hand1.y
            assertEquals(pose.hand1.y, pose.hand2.y, EPSILON)
            assertEquals(0.5f - pose.hand1.x, pose.hand2.x - 0.5f, EPSILON)
        }
    }

    // U-P07
    @Test
    fun fallen_fromStandingToLying() {
        val standing = StickFigurePose.fallen(0f)
        assertTrue(standing.head.y < standing.hip.y)
        assertTrue(standing.hip.y < standing.foot1.y && standing.hip.y < standing.foot2.y)
        val lying = StickFigurePose.fallen(1f)
        assertTrue(abs(lying.head.y - lying.hip.y) < 0.15f)
        assertTrue(lying.head.x < lying.hip.x)
    }

    // U-P08
    @Test
    fun cheeringAndFallen_clampT() {
        assertPoseEquals(StickFigurePose.cheering(0f), StickFigurePose.cheering(-0.5f))
        assertPoseEquals(StickFigurePose.cheering(1f), StickFigurePose.cheering(1.5f))
        assertPoseEquals(StickFigurePose.fallen(0f), StickFigurePose.fallen(-0.5f))
        assertPoseEquals(StickFigurePose.fallen(1f), StickFigurePose.fallen(1.5f))
    }

    companion object {
        private const val EPSILON = 1e-4f
    }
}

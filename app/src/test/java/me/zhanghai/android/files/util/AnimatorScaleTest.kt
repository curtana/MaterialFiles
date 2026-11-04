/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatorScaleTest {
    // U-A01
    @Test
    fun isReducedMotion_onlyZeroScale() {
        assertTrue(isReducedMotion(0f))
        for (scale in listOf(1f, 0.5f, 10f)) {
            assertFalse(isReducedMotion(scale))
        }
    }

    // U-A02
    @Test
    fun isReducedMotion_powerSaveModeOrAnimatorsDisabled() {
        assertFalse(isReducedMotion(isPowerSaveMode = false, areAnimatorsDisabled = false))
        assertTrue(isReducedMotion(isPowerSaveMode = false, areAnimatorsDisabled = true))
        assertTrue(isReducedMotion(isPowerSaveMode = true, areAnimatorsDisabled = false))
        assertTrue(isReducedMotion(isPowerSaveMode = true, areAnimatorsDisabled = true))
    }
}

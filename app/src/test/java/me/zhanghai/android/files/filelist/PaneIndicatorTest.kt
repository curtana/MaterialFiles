/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Test

class PaneIndicatorTest {
    // U-C01
    @Test
    fun getPaneIndicatorMode_dualPaneDisabled_isHidden() {
        for (isDualPane in listOf(false, true)) {
            assertEquals(PaneIndicatorMode.HIDDEN, getPaneIndicatorMode(false, isDualPane, 0, 0))
            assertEquals(PaneIndicatorMode.HIDDEN, getPaneIndicatorMode(false, isDualPane, 1, 0))
        }
    }

    // U-C02
    @Test
    fun getPaneIndicatorMode_singlePane_isSwitcher() {
        for (paneIndex in 0..1) {
            for (activePane in 0..1) {
                assertEquals(
                    PaneIndicatorMode.SWITCHER,
                    getPaneIndicatorMode(true, false, paneIndex, activePane)
                )
            }
        }
    }

    // U-C03
    @Test
    fun getPaneIndicatorMode_dualPaneActive_isActive() {
        assertEquals(PaneIndicatorMode.ACTIVE, getPaneIndicatorMode(true, true, 0, 0))
        assertEquals(PaneIndicatorMode.ACTIVE, getPaneIndicatorMode(true, true, 1, 1))
    }

    // U-C04
    @Test
    fun getPaneIndicatorMode_dualPaneInactive_isInactive() {
        assertEquals(PaneIndicatorMode.INACTIVE, getPaneIndicatorMode(true, true, 0, 1))
        assertEquals(PaneIndicatorMode.INACTIVE, getPaneIndicatorMode(true, true, 1, 0))
    }

    // U-C05
    @Test
    fun otherPaneIndex_returnsTheOtherPane() {
        assertEquals(1, otherPaneIndex(0))
        assertEquals(0, otherPaneIndex(1))
    }
}

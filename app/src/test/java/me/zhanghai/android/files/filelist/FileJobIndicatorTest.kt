/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Test

class FileJobIndicatorTest {
    // U-I01
    @Test
    fun dualPaneDisabled_isFirstPane() {
        for (isDualPane in listOf(false, true)) {
            for (activePane in 0..1) {
                assertEquals(0, getFileJobIndicatorPaneIndex(false, isDualPane, activePane))
            }
        }
    }

    // U-I02
    @Test
    fun dualPane_isEndPane() {
        for (activePane in 0..1) {
            assertEquals(1, getFileJobIndicatorPaneIndex(true, true, activePane))
        }
    }

    // U-I03
    @Test
    fun singlePane_isVisiblePane() {
        for (activePane in 0..1) {
            assertEquals(activePane, getFileJobIndicatorPaneIndex(true, false, activePane))
        }
    }
}

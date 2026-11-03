/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileJobErrorActionTest {
    // U-E01
    @Test
    fun positiveRetry_isResolved() {
        assertTrue(isFileJobErrorResolved(FileJobErrorAction.POSITIVE, true))
    }

    // U-E02
    @Test
    fun positiveSkip_isUnresolved() {
        assertFalse(isFileJobErrorResolved(FileJobErrorAction.POSITIVE, false))
    }

    // U-E03
    @Test
    fun otherActions_areUnresolved() {
        for (action in listOf(
            FileJobErrorAction.NEGATIVE, FileJobErrorAction.NEUTRAL, FileJobErrorAction.CANCELED
        )) {
            for (isPositiveRetry in listOf(false, true)) {
                assertFalse(isFileJobErrorResolved(action, isPositiveRetry))
            }
        }
    }
}

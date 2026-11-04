/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileJobCleanupFailuresTest {
    private val failures = FileJobCleanupFailures<String>()

    // U-F01
    @Test
    fun whileRunning_thenCanceled_returned() {
        assertFalse(failures.onFailed("a"))
        assertEquals(listOf("a"), failures.onJobFinished(false))
    }

    // U-F02 is the same at this level: a canceled and a failed job are both not successful.
    @Test
    fun whileRunning_thenFailed_returned() {
        assertFalse(failures.onFailed("a"))
        assertEquals(listOf("a"), failures.onJobFinished(isSuccessful = false))
    }

    // U-F03
    @Test
    fun whileRunning_thenSucceeded_dropped() {
        assertFalse(failures.onFailed("a"))
        assertTrue(failures.onJobFinished(isSuccessful = true).isEmpty())
    }

    // U-F04
    @Test
    fun writtenByRetry_dropped() {
        failures.onFailed("a")
        failures.onWritten("a")
        assertTrue(failures.onJobFinished(false).isEmpty())
    }

    // U-F05
    @Test
    fun twoItems_inOrder() {
        failures.onFailed("b")
        failures.onFailed("a")
        assertEquals(listOf("b", "a"), failures.onJobFinished(false))
    }

    // U-F06
    @Test
    fun sameItem_reportedOnce() {
        failures.onFailed("a")
        failures.onFailed("a")
        assertEquals(listOf("a"), failures.onJobFinished(false))
        assertFalse(failures.onFailed("a"))
    }

    // U-F07, U-F08
    @Test
    fun afterNotSucceeded_newItem_reportedSeparately() {
        failures.onJobFinished(false)
        assertTrue(failures.onFailed("a"))
        assertFalse(failures.onFailed("a"))
    }

    // U-F09
    @Test
    fun afterSucceeded_newItem_dropped() {
        failures.onJobFinished(true)
        assertFalse(failures.onFailed("a"))
    }
}

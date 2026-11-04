/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class FileJobServiceTest {
    // U-S01
    @Test
    fun runJobAndReport_success() {
        val results = mutableListOf<FileJobOutcome>()
        runJobAndReport({ FileJobOutcome.SUCCEEDED }) { results += it }
        assertEquals(listOf(FileJobOutcome.SUCCEEDED), results)
    }

    // U-S02
    @Test
    fun runJobAndReport_failure() {
        val results = mutableListOf<FileJobOutcome>()
        runJobAndReport({ FileJobOutcome.FAILED }) { results += it }
        assertEquals(listOf(FileJobOutcome.FAILED), results)
    }

    // U-S03
    @Test
    fun runJobAndReport_throwable_reportsFailureAndRethrows() {
        val results = mutableListOf<FileJobOutcome>()
        val error = AssertionError("Not an exception")
        val thrown = assertThrows(AssertionError::class.java) {
            runJobAndReport({ throw error }) { results += it }
        }
        assertSame(error, thrown)
        assertEquals(listOf(FileJobOutcome.FAILED), results)
    }

    // U-S04
    @Test
    fun runJobAndReport_reportsAfterRun() {
        val events = mutableListOf<String>()
        runJobAndReport({
            events += "run"
            FileJobOutcome.SUCCEEDED
        }) { events += "finished" }
        assertEquals(listOf("run", "finished"), events)
    }

    // U-S05
    @Test
    fun runJobAndReport_canceled() {
        val results = mutableListOf<FileJobOutcome>()
        runJobAndReport({ FileJobOutcome.CANCELED }) { results += it }
        assertEquals(listOf(FileJobOutcome.CANCELED), results)
    }
}

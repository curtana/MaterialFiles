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
        val results = mutableListOf<Boolean>()
        runJobAndReport({ true }) { results += it }
        assertEquals(listOf(true), results)
    }

    // U-S02
    @Test
    fun runJobAndReport_failure() {
        val results = mutableListOf<Boolean>()
        runJobAndReport({ false }) { results += it }
        assertEquals(listOf(false), results)
    }

    // U-S03
    @Test
    fun runJobAndReport_throwable_reportsFailureAndRethrows() {
        val results = mutableListOf<Boolean>()
        val error = AssertionError("Not an exception")
        val thrown = assertThrows(AssertionError::class.java) {
            runJobAndReport({ throw error }) { results += it }
        }
        assertSame(error, thrown)
        assertEquals(listOf(false), results)
    }

    // U-S04
    @Test
    fun runJobAndReport_reportsAfterRun() {
        val events = mutableListOf<String>()
        runJobAndReport({
            events += "run"
            true
        }) { events += "finished" }
        assertEquals(listOf("run", "finished"), events)
    }
}

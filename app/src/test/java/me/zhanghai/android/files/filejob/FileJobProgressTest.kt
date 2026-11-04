/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileJobProgressTest {
    private fun size(transferred: Long, size: Long) =
        FileJobProgress(FileJobProgressKind.SIZE, 1, 0, size, transferred)

    private fun count(transferred: Int, count: Int) =
        FileJobProgress(FileJobProgressKind.COUNT, count, transferred, 0, 0)

    // U-P01
    @Test
    fun size_percent() {
        assertEquals(50, size(50, 100).percent)
    }

    // U-P02
    @Test
    fun size_largerThanInt_percent() {
        assertEquals(50, size(2_500_000_000, 5_000_000_000).percent)
    }

    // U-P03
    @Test
    fun size_zeroOrNegative_indeterminate() {
        assertNull(size(0, 0).percent)
        assertNull(size(10, -1).percent)
    }

    // U-P04
    @Test
    fun size_transferredOverSize_clamped() {
        assertEquals(100, size(200, 100).percent)
    }

    // U-P05
    @Test
    fun count_percent() {
        assertEquals(30, count(3, 10).percent)
    }

    // U-P06
    @Test
    fun count_oneOrZero_indeterminate() {
        assertNull(count(0, 1).percent)
        assertNull(count(0, 0).percent)
    }

    // U-P07
    @Test
    fun scanning_indeterminate() {
        assertNull(FileJobProgress(FileJobProgressKind.SCANNING, 5, 0, 100, 0).percent)
    }

    // U-P08
    @Test
    fun currentFileIndex() {
        assertEquals(1, count(0, 10).currentFileIndex)
        assertEquals(10, count(9, 10).currentFileIndex)
        assertEquals(10, count(10, 10).currentFileIndex)
    }

    // U-P09
    @Test
    fun throttle() {
        val throttle = FileJobProgressThrottle(500)
        assertTrue(throttle.shouldReport(1000))
        assertFalse(throttle.shouldReport(1100))
        assertFalse(throttle.shouldReport(1500))
        assertTrue(throttle.shouldReport(1501))
    }
}

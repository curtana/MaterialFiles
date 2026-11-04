/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileJobEntryTableTest {
    private val info = FileJobInfo(FileJobType.COPY, "a", 1, null, false)
    private val otherInfo = FileJobInfo(FileJobType.MOVE, "b", 1, null, false)
    private val progress = FileJobProgress(FileJobProgressKind.COUNT, 2, 1, 0, 0)

    // U-T01
    @Test
    fun progress_keepsStartOrder() {
        val table = FileJobEntryTable()
        table.start(3, info, false)
        table.start(1, info, false)
        table.start(2, info, false)
        assertTrue(table.setProgress(1, progress))
        assertEquals(listOf(3, 1, 2), table.entries.map { it.id })
        assertEquals(progress, table.entries[1].progress)
    }

    // U-T02
    @Test
    fun startCollidingId_keepsFirst() {
        val table = FileJobEntryTable()
        assertNotNull(table.start(1, info, false))
        assertNull(table.start(1, otherInfo, false))
        assertEquals(info, table.entries.single().info)
    }

    // U-T03
    @Test
    fun afterFinish_updatesIgnored() {
        val table = FileJobEntryTable()
        table.start(1, info, true)
        table.finish(1)
        assertFalse(table.setProgress(1, progress))
        assertFalse(table.setRunning(1))
        assertTrue(table.entries.isEmpty())
    }

    // U-T04
    @Test
    fun finishTwiceOrUnknown_ignored() {
        val table = FileJobEntryTable()
        table.start(1, info, false)
        assertTrue(table.finish(1))
        assertFalse(table.finish(1))
        assertFalse(table.finish(2))
    }

    // U-T05
    @Test
    fun setRunning_clearsPending() {
        val table = FileJobEntryTable()
        table.start(1, info, true)
        assertTrue(table.entries.single().isPending)
        assertTrue(table.setRunning(1))
        assertFalse(table.entries.single().isPending)
        assertFalse(table.setRunning(1))
    }

    // U-T06
    @Test
    fun pendingFinish_removed() {
        val table = FileJobEntryTable()
        table.start(1, info, true)
        assertTrue(table.finish(1))
        assertTrue(table.entries.isEmpty())
    }
}

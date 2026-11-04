/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileJobListRowsTest {
    private val info = FileJobInfo(FileJobType.COPY, "a", 1, null, false)

    private fun entry(id: Int, isPending: Boolean = false) = FileJobEntry(id, info, null, isPending)

    private fun size(fileCount: Int, transferredFileCount: Int, transferred: Long, size: Long) =
        FileJobProgress(FileJobProgressKind.SIZE, fileCount, transferredFileCount, size, transferred)

    private fun count(fileCount: Int, transferredFileCount: Int) =
        FileJobProgress(FileJobProgressKind.COUNT, fileCount, transferredFileCount, 0, 0)

    // U-R01
    @Test
    fun status_pending() {
        assertEquals(
            FileJobRowStatus.Pending, fileJobRowStatusOf(true, null, null, false)
        )
    }

    // U-R02
    @Test
    fun status_runningWithoutProgress() {
        assertEquals(FileJobRowStatus.Running, fileJobRowStatusOf(false, null, null, false))
    }

    // U-R03
    @Test
    fun status_scanning() {
        val progress = FileJobProgress(FileJobProgressKind.SCANNING, 5, 0, 100, 0)
        assertEquals(
            FileJobRowStatus.Scanning(5, 100), fileJobRowStatusOf(false, progress, null, false)
        )
    }

    // U-R04
    @Test
    fun status_size() {
        assertEquals(
            FileJobRowStatus.Size(50, 50, 100, null, 1),
            fileJobRowStatusOf(false, size(1, 0, 50, 100), null, false)
        )
        assertEquals(
            FileJobRowStatus.Size(50, 50, 100, 3, 10),
            fileJobRowStatusOf(false, size(10, 2, 50, 100), null, false)
        )
    }

    // U-R05
    @Test
    fun status_count() {
        assertEquals(
            FileJobRowStatus.Running, fileJobRowStatusOf(false, count(1, 0), null, false)
        )
        assertEquals(
            FileJobRowStatus.Count(30, 4, 10), fileJobRowStatusOf(false, count(10, 3), null, false)
        )
    }

    // U-R06
    @Test
    fun status_sizeZero_running() {
        assertEquals(
            FileJobRowStatus.Running, fileJobRowStatusOf(false, size(1, 0, 0, 0), null, false)
        )
    }

    // U-R07
    @Test
    fun status_finishedWins() {
        assertEquals(
            FileJobRowStatus.Finished(FileJobOutcome.FAILED, true),
            fileJobRowStatusOf(true, count(10, 3), FileJobOutcome.FAILED, true)
        )
    }

    // U-R08
    @Test
    fun rows_initialThenStarted_inStartOrder() {
        val rows = FileJobListRows(listOf(entry(2), entry(1)))
        assertTrue(rows.onStarted(entry(3)))
        assertEquals(listOf(2, 1, 3), rows.rows.map { it.id })
    }

    // U-R09
    @Test
    fun rows_empty() {
        val rows = FileJobListRows(emptyList())
        assertTrue(rows.rows.isEmpty())
        assertTrue(rows.activeIds.isEmpty())
    }

    // U-R10
    @Test
    fun finished_ignoresProgress() {
        val rows = FileJobListRows(listOf(entry(1)))
        assertTrue(rows.onFinished(1, FileJobOutcome.SUCCEEDED))
        assertFalse(rows.onProgress(1, count(10, 3)))
        assertEquals(
            FileJobRowStatus.Finished(FileJobOutcome.SUCCEEDED, false), rows.rows.single().status
        )
    }

    // U-R11
    @Test
    fun finished_firstOutcomeWins() {
        val rows = FileJobListRows(listOf(entry(1)))
        rows.onFinished(1, FileJobOutcome.CANCELED)
        assertFalse(rows.onFinished(1, FileJobOutcome.SUCCEEDED))
        assertEquals(
            FileJobRowStatus.Finished(FileJobOutcome.CANCELED, false), rows.rows.single().status
        )
    }

    // U-R12
    @Test
    fun cleanupFailed_onFinishedRow() {
        val rows = FileJobListRows(listOf(entry(1)))
        rows.onFinished(1, FileJobOutcome.CANCELED)
        assertTrue(rows.onCleanupFailed(1))
        assertEquals(
            FileJobRowStatus.Finished(FileJobOutcome.CANCELED, true), rows.rows.single().status
        )
    }

    // U-R13
    @Test
    fun cleanupFailed_beforeFinish_keptAfterFinish() {
        val rows = FileJobListRows(listOf(entry(1)))
        assertTrue(rows.onCleanupFailed(1))
        rows.onFinished(1, FileJobOutcome.FAILED)
        assertEquals(
            FileJobRowStatus.Finished(FileJobOutcome.FAILED, true), rows.rows.single().status
        )
    }

    // U-R14
    @Test
    fun unknownRow_ignored() {
        val rows = FileJobListRows(listOf(entry(1)))
        assertFalse(rows.onProgress(2, count(10, 3)))
        assertFalse(rows.onFinished(2, FileJobOutcome.FAILED))
        assertFalse(rows.onCleanupFailed(2))
        assertFalse(rows.onRunning(2))
        assertEquals(listOf(1), rows.rows.map { it.id })
    }

    // U-R15
    @Test
    fun activeIds_excludeFinished_andIsCopy() {
        val rows = FileJobListRows(listOf(entry(1), entry(2), entry(3)))
        rows.onFinished(2, FileJobOutcome.SUCCEEDED)
        val activeIds = rows.activeIds
        assertEquals(listOf(1, 3), activeIds)
        rows.onFinished(1, FileJobOutcome.CANCELED)
        assertEquals(listOf(1, 3), activeIds)
        assertFalse(rows.rows[1].isActive)
    }

    // U-R16
    @Test
    fun startCollidingId_oneRow() {
        val rows = FileJobListRows(listOf(entry(1)))
        assertFalse(rows.onStarted(entry(1)))
        assertEquals(1, rows.rows.size)
    }

    // U-R17
    @Test
    fun pending_clearedByRunningOrProgress() {
        val rows = FileJobListRows(listOf(entry(1, true), entry(2, true)))
        assertEquals(FileJobRowStatus.Pending, rows.rows[0].status)
        assertTrue(rows.onRunning(1))
        assertTrue(rows.onProgress(2, count(10, 3)))
        assertEquals(FileJobRowStatus.Running, rows.rows[0].status)
        assertEquals(FileJobRowStatus.Count(30, 4, 10), rows.rows[1].status)
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCopyWalkTest {
    // Progress totals (#55 B2, LLD-B 2.8).

    private fun transferInfo(fileCount: Int, size: Long): TransferInfo {
        val scanInfo = ScanInfo()
        repeat(fileCount) { scanInfo.incrementFileCount() }
        scanInfo.addToSize(size)
        return TransferInfo(scanInfo, null)
    }

    @Test
    fun transferInfo_countNeverExceedsTotal() {
        val transferInfo = transferInfo(1, 0)
        repeat(3) { transferInfo.incrementTransferredFileCount() }
        assertEquals(3, transferInfo.fileCount)
        assertEquals(3, transferInfo.transferredFileCount)
    }

    @Test
    fun transferInfo_sizeNeverExceedsTotal() {
        val transferInfo = transferInfo(1, 10)
        transferInfo.addToTransferredSize(25)
        assertEquals(25, transferInfo.size)
        val progress = FileJobProgress(
            FileJobProgressKind.SIZE, transferInfo.fileCount, transferInfo.transferredFileCount,
            transferInfo.size, transferInfo.transferredSize
        )
        assertEquals(100, progress.percent)
    }

    @Test
    fun transferInfo_withinTotals_unchanged() {
        val transferInfo = transferInfo(3, 30)
        transferInfo.incrementTransferredFileCount()
        transferInfo.addToTransferredSize(10)
        assertEquals(3, transferInfo.fileCount)
        assertEquals(30, transferInfo.size)
        assertEquals(1, transferInfo.transferredFileCount)
        assertEquals(10, transferInfo.transferredSize)
    }

    @Test
    fun transferInfo_skipNeverBelowTransferred() {
        val transferInfo = transferInfo(2, 0)
        repeat(2) { transferInfo.incrementTransferredFileCount() }
        transferInfo.skipFileIgnoringSize()
        assertEquals(2, transferInfo.fileCount)
    }

    // Resuming a walk start (#55 B2, LLD-B 2.5, 2.5a).

    private fun path(path: String) = TestPaths.path(path)

    @Test
    fun resumedStart_failingBeforeEntering_keepsResume() {
        val e1 = IOException("e1")
        val e2 = IOException("e2")
        val resume = ItemWalkFailure(e1, 3, setOf(path("/s/d/a")))
        val failure = startFailureBeforeEntering(e2, resume)
        assertSame(e2, failure.error)
        assertEquals(3, failure.enteredDepth)
        assertEquals(setOf(path("/s/d/a")), failure.visitedChildren)
        val state = LinkWalkState(2)
        assertTrue(state.enterIfResumedStart(path("/s/d"), path("/s/d"), failure))
        assertEquals(3, state.currentDepth)
        assertFalse(state.markVisited(path("/s/d/a")))
    }

    @Test
    fun enterIfResumedStart_onlyForEnteredStart() {
        val e = IOException("e")
        val entered = ItemWalkFailure(e, 3, emptySet())
        val notEntered = ItemWalkFailure(e, null, emptySet())
        val cases = listOf(
            Triple(path("/s/d"), path("/s/d"), null),
            Triple(path("/s/d"), path("/s/d"), notEntered),
            Triple(path("/s/d/x"), path("/s/d"), entered)
        )
        for ((directory, start, resume) in cases) {
            val state = LinkWalkState(2)
            assertFalse(state.enterIfResumedStart(directory, start, resume))
            assertEquals(2, state.currentDepth)
            assertThrows(IllegalStateException::class.java) { state.exit() }
        }
    }

    @Test
    fun isCountedOnSkip() {
        val notEntered = startFailureBeforeEntering(IOException("e"), null)
        assertNull(notEntered.enteredDepth)
        assertTrue(notEntered.isCountedOnSkip)
        val entered = ItemWalkFailure(IOException("e1"), 2, emptySet())
        assertFalse(entered.isCountedOnSkip)
        val resumed = startFailureBeforeEntering(IOException("e2"), entered)
        assertFalse(resumed.isCountedOnSkip)
    }
}

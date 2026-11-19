/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
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
}

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

class FileJobBatchTest {
    // U-B01
    @Test
    fun startAndFinish_success() {
        val batch = FileJobBatch()
        assertTrue(batch.start(1))
        assertEquals(FileJobState(1, null), batch.state)
        assertTrue(batch.finish(1, true, true))
        assertEquals(FileJobState(0, FinishedBatch(1, true)), batch.state)
    }

    // U-B02
    @Test
    fun twoJobs_oneFailed_isFailure() {
        val batch = FileJobBatch()
        batch.start(1)
        batch.start(2)
        batch.finish(1, true, true)
        assertEquals(FileJobState(1, null), batch.state)
        batch.finish(2, false, true)
        assertEquals(FileJobState(0, FinishedBatch(1, false)), batch.state)
    }

    // U-B03
    @Test
    fun successAndCancel_eitherOrder_isFailure() {
        for (failedFirst in listOf(false, true)) {
            val batch = FileJobBatch()
            batch.start(1)
            batch.start(2)
            batch.finish(1, !failedFirst, true)
            batch.finish(2, failedFirst, true)
            assertEquals(false, batch.state.finished!!.isSuccessful)
        }
    }

    // U-B04
    @Test
    fun finishTwice_firstReportWins() {
        val batch = FileJobBatch()
        batch.start(1)
        batch.start(2)
        assertTrue(batch.finish(1, false, true))
        assertFalse(batch.finish(1, true, true))
        assertEquals(FileJobState(1, null), batch.state)
        batch.finish(2, true, true)
        assertEquals(FinishedBatch(1, false), batch.state.finished)
    }

    // U-B05
    @Test
    fun finishUnknown_isIgnored() {
        val batch = FileJobBatch()
        assertFalse(batch.finish(1, false, true))
        assertEquals(FileJobState(0, null), batch.state)
        batch.start(2)
        assertFalse(batch.finish(3, false, true))
        assertEquals(FileJobState(1, null), batch.state)
    }

    // U-B06
    @Test
    fun pendingCancel_isFailure() {
        val batch = FileJobBatch()
        batch.start(1)
        batch.finish(1, false, true)
        assertEquals(FileJobState(0, FinishedBatch(1, false)), batch.state)
    }

    // U-B07
    @Test
    fun newStart_clearsResultAndFailure() {
        val batch = FileJobBatch()
        batch.start(1)
        batch.finish(1, false, true)
        batch.start(2)
        assertEquals(FileJobState(1, null), batch.state)
        batch.finish(2, true, true)
        assertEquals(FileJobState(0, FinishedBatch(2, true)), batch.state)
    }

    // U-B08
    @Test
    fun twoBatchesWithoutConsuming_keepsLastOnly() {
        val batch = FileJobBatch()
        batch.start(1)
        batch.finish(1, true, true)
        batch.start(2)
        batch.finish(2, false, true)
        assertEquals(FileJobState(0, FinishedBatch(2, false)), batch.state)
    }

    // U-B09
    @Test
    fun consume_onlyMatchingSeq() {
        val batch = FileJobBatch()
        batch.start(1)
        batch.finish(1, true, true)
        assertFalse(batch.consume(7))
        assertEquals(FinishedBatch(1, true), batch.state.finished)
        assertTrue(batch.consume(1))
        assertNull(batch.state.finished)
        assertFalse(batch.consume(1))
    }

    // U-B10
    @Test
    fun startCollidingId_isCountedOnce() {
        val batch = FileJobBatch()
        assertTrue(batch.start(1))
        assertFalse(batch.start(1))
        assertEquals(FileJobState(1, null), batch.state)
    }

    // U-B11
    @Test
    fun finishWithResultNotKept_hasNoResult() {
        val batch = FileJobBatch()
        batch.start(1)
        assertTrue(batch.finish(1, true, false))
        assertEquals(FileJobState(0, null), batch.state)
        batch.start(2)
        batch.finish(2, true, true)
        assertEquals(FinishedBatch(2, true), batch.state.finished)
    }
}

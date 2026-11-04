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
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class FileJobEndNoticeTest {
    private val noFailures = emptyList<String>()

    // U-N01
    @Test
    fun succeeded_notifiesCompletion_shortNotice() {
        val notice = fileJobEndNoticeOf(true, FileJobOutcome.SUCCEEDED, null, noFailures)!!
        assertEquals(FileJobOutcome.SUCCEEDED, notice.outcome)
        assertFalse(notice.isLong)
    }

    // U-N02
    @Test
    fun succeeded_doesNotNotifyCompletion_noNotice() {
        assertNull(fileJobEndNoticeOf(false, FileJobOutcome.SUCCEEDED, null, noFailures))
    }

    // U-N03
    @Test
    fun succeeded_withFailures_noCleanupFailures() {
        val notice = fileJobEndNoticeOf(true, FileJobOutcome.SUCCEEDED, null, listOf("a"))!!
        assertTrue(notice.cleanupFailures.isEmpty())
    }

    // U-N04
    @Test
    fun canceled_doesNotNotifyCompletion_stillNotice() {
        val notice = fileJobEndNoticeOf(false, FileJobOutcome.CANCELED, null, noFailures)!!
        assertEquals(FileJobOutcome.CANCELED, notice.outcome)
        assertFalse(notice.isLong)
    }

    // U-N05
    @Test
    fun failed_noThrown_noticeWithoutDetail() {
        val notice = fileJobEndNoticeOf(false, FileJobOutcome.FAILED, null, noFailures)!!
        assertEquals(FileJobOutcome.FAILED, notice.outcome)
        assertNull(notice.detail)
        assertFalse(notice.isLong)
    }

    // U-N06
    @Test
    fun failed_interrupted_interruptedDetail() {
        for (thrown in listOf(InterruptedIOException(), SocketTimeoutException())) {
            val notice = fileJobEndNoticeOf(false, FileJobOutcome.FAILED, thrown, noFailures)!!
            assertEquals(FileJobFailureDetail.Interrupted, notice.detail)
            assertTrue(notice.isLong)
        }
    }

    // U-N07
    @Test
    fun failed_exception_errorDetail() {
        val thrown = IOException("Message")
        val notice = fileJobEndNoticeOf(false, FileJobOutcome.FAILED, thrown, noFailures)!!
        assertEquals(FileJobFailureDetail.Error(thrown.toString()), notice.detail)
        assertTrue(notice.isLong)
    }

    // U-N08
    @Test
    fun canceled_exception_noDetail() {
        val notice =
            fileJobEndNoticeOf(false, FileJobOutcome.CANCELED, IOException(), noFailures)!!
        assertNull(notice.detail)
    }

    // U-N09
    @Test
    fun canceledAndFailed_withFailures_longNoticeWithFailures() {
        for (outcome in listOf(FileJobOutcome.CANCELED, FileJobOutcome.FAILED)) {
            val notice = fileJobEndNoticeOf(false, outcome, null, listOf("a", "b"))!!
            assertEquals(listOf("a", "b"), notice.cleanupFailures)
            assertTrue(notice.isLong)
        }
    }
}

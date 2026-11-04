/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class FileJobRunBodyTest {
    private class TestJob(private val body: TestJob.() -> Unit = {}) : FileJob() {
        init {
            info = FileJobInfo(FileJobType.COPY, "a", 1, "b", false)
        }

        override fun run() {
            body()
        }
    }

    // U-J01
    @Test
    fun runBody_socketTimeoutWithoutFlag_failed() {
        val job = TestJob { throw SocketTimeoutException() }
        assertEquals(FileJobOutcome.FAILED, job.runBody())
    }

    // U-J02
    @Test
    fun runBody_interruptedWithFlag_canceled() {
        val job = TestJob {
            isCanceled = true
            throw InterruptedIOException()
        }
        assertEquals(FileJobOutcome.CANCELED, job.runBody())
    }

    // U-J03
    @Test
    fun runBody_dialogCancel_canceledWithFlag() {
        val job = TestJob { throw canceledByUser() }
        assertEquals(FileJobOutcome.CANCELED, job.runBody())
        assertTrue(job.isCanceled)
    }

    // U-J04
    @Test
    fun runBody_normalEnd_followsUnresolvedError() {
        assertEquals(FileJobOutcome.SUCCEEDED, TestJob().runBody())
        val job = TestJob { hasUnresolvedError = true }
        assertEquals(FileJobOutcome.FAILED, job.runBody())
    }

    // U-J05
    @Test
    fun runBody_error_isRethrown() {
        val error = AssertionError("Not an exception")
        val job = TestJob { throw error }
        assertSame(error, assertThrows(AssertionError::class.java) { job.runBody() })
    }

    // U-J06
    @Test
    fun endOf_canceledAfterBodyFinished_canceled() {
        val job = TestJob()
        job.claimEndForRun()
        val outcome = job.runBody()
        job.isCanceled = true
        val end = job.endOf(outcome)
        assertEquals(FileJobOutcome.CANCELED, end.outcome)
        assertEquals(FileJobOutcome.CANCELED, end.notice!!.outcome)
    }

    // U-J07
    @Test
    fun endOf_interruptedWithoutFlag_oneFailedToastWithInterruptedDetail() {
        val job = TestJob { throw InterruptedIOException() }
        job.claimEndForRun()
        val end = job.endOf(job.runBody())
        assertEquals(FileJobOutcome.FAILED, end.outcome)
        assertEquals(FileJobFailureDetail.Interrupted, end.notice!!.detail)
        assertTrue(end.separateFailures.isEmpty())
    }

    // U-J08
    @Test
    fun endOf_exception_oneFailedToastWithErrorDetail() {
        val exception = IOException("Message")
        val job = TestJob { throw exception }
        job.claimEndForRun()
        val end = job.endOf(job.runBody())
        assertEquals(FileJobOutcome.FAILED, end.outcome)
        assertEquals(FileJobFailureDetail.Error(exception.toString()), end.notice!!.detail)
    }

    // U-J09
    @Test
    fun endOf_exceptionWithFlag_noRawExceptionInToast() {
        val job = TestJob {
            isCanceled = true
            throw IOException("Message")
        }
        job.claimEndForRun()
        val end = job.endOf(job.runBody())
        assertEquals(FileJobOutcome.CANCELED, end.outcome)
        assertNull(end.notice!!.detail)
    }

    // U-J10
    @Test
    fun endOf_endClaimedByService_noNoticeAndSeparateFailures() {
        val path = TestPaths.path("/a/b")
        val job = TestJob()
        assertTrue(job.tryClaimEnd())
        job.claimEndForRun()
        job.isCanceled = true
        job.cleanupFailures.onFailed(path)
        val end = job.endOf(FileJobOutcome.FAILED)
        assertNull(end.notice)
        assertEquals(listOf(path), end.separateFailures)
    }

    // U-J11
    @Test
    fun runReportingResult_value_reportedOnce() {
        for (value in listOf(true, false)) {
            val results = mutableListOf<Boolean>()
            runReportingResult({ value }) { results += it }
            assertEquals(listOf(value), results)
        }
    }

    // U-J12
    @Test
    fun runReportingResult_thrown_reportsFalseOnceAndRethrowsSame() {
        val exception = InterruptedIOException()
        val results = mutableListOf<Boolean>()
        assertSame(
            exception,
            assertThrows(InterruptedIOException::class.java) {
                runReportingResult({ throw exception }) { results += it }
            }
        )
        assertEquals(listOf(false), results)

        val error = AssertionError("Not an exception")
        results.clear()
        assertSame(
            error,
            assertThrows(AssertionError::class.java) {
                runReportingResult({ throw error }) { results += it }
            }
        )
        assertEquals(listOf(false), results)
    }

    // U-J13
    @Test
    fun finishEnd_endOfThrows_doesNotThrow() {
        val job = object : FileJob() {
            override fun run() {}
        }
        // The info isn't set, so endOf() throws.
        job.claimEndForRun()
        assertEquals(FileJobOutcome.SUCCEEDED, job.finishEnd(FileJobOutcome.SUCCEEDED))
        job.isCanceled = true
        assertEquals(FileJobOutcome.CANCELED, job.finishEnd(FileJobOutcome.SUCCEEDED))
    }

    // U-J14
    @Test
    fun onceResultReporter_reportsFirstOnly() {
        val results = mutableListOf<Boolean>()
        val reporter = OnceResultReporter { results += it }
        reporter.report(false)
        reporter.report(true)
        assertEquals(listOf(false), results)
    }

    // U-C01
    @Test
    fun throwIfCanceled() {
        val job = TestJob()
        job.throwIfCanceled()
        job.isCanceled = true
        assertNotNull(assertThrows(InterruptedIOException::class.java) { job.throwIfCanceled() })
    }

    // U-C02
    @Test
    fun throwIfCanceledOrInterrupted_neither_doesNotThrow() {
        TestJob().throwIfCanceledOrInterrupted()
    }

    // U-C03
    @Test
    fun throwIfCanceledOrInterrupted_interrupted_throwsAndClears() {
        val job = TestJob()
        Thread.currentThread().interrupt()
        assertThrows(InterruptedIOException::class.java) { job.throwIfCanceledOrInterrupted() }
        assertFalse(Thread.currentThread().isInterrupted)
    }

    // U-C04
    @Test
    fun throwIfCanceledOrInterrupted_interruptConsumed_stillThrows() {
        val job = TestJob()
        job.isCanceled = true
        assertThrows(InterruptedIOException::class.java) { job.throwIfCanceledOrInterrupted() }
    }

    // U-C05
    @Test
    fun throwIfCanceledOrInterrupted_both_throwsAndClears() {
        val job = TestJob()
        job.isCanceled = true
        Thread.currentThread().interrupt()
        assertThrows(InterruptedIOException::class.java) { job.throwIfCanceledOrInterrupted() }
        assertFalse(Thread.currentThread().isInterrupted)
    }

    // U-C06
    @Test
    fun tryClaimEnd_firstOnly() {
        val job = TestJob()
        assertTrue(job.tryClaimEnd())
        assertFalse(job.tryClaimEnd())
    }
}

/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.content.Context
import java8.nio.file.Path
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set

    internal lateinit var info: FileJobInfo

    // Whether an error dialog was answered with anything other than retrying. Only accessed on the
    // job thread.
    internal var hasUnresolvedError = false

    // Whether the job was canceled by the user or the service. The service sets this before
    // interrupting the job thread, because the interrupt may be consumed in a backend.
    @Volatile
    internal var isCanceled = false

    internal val cleanupFailures = FileJobCleanupFailures<Path>()

    private val isEndClaimed = AtomicBoolean()

    // Only accessed on the job thread.
    private var isEndClaimedByRun = false

    // Only accessed on the job thread.
    private var thrown: Throwable? = null

    // Returns how the job body ended. The end is reported by finishEnd() afterwards.
    fun runOn(service: FileJobService): FileJobOutcome {
        this.service = service
        claimEndForRun()
        return try {
            runBody()
        } finally {
            service.notificationManager.cancel(id)
        }
    }

    internal fun runBody(): FileJobOutcome =
        try {
            run()
            FileJobOutcome.of(hasUnresolvedError, null, isCanceled)
        } catch (e: Exception) {
            e.printStackTrace()
            thrown = e
            FileJobOutcome.of(false, e, isCanceled)
        }

    internal fun claimEndForRun() {
        isEndClaimedByRun = tryClaimEnd()
    }

    /**
     * Decides how the job finally ended. Must be called after the job can no longer be canceled by
     * the service, so that the result agrees with what the service reported.
     */
    internal fun endOf(bodyOutcome: FileJobOutcome): FileJobEnd {
        val outcome = if (isCanceled) FileJobOutcome.CANCELED else bodyOutcome
        val failures = cleanupFailures.onJobFinished(outcome == FileJobOutcome.SUCCEEDED)
        return if (isEndClaimedByRun) {
            FileJobEnd(
                outcome, fileJobEndNoticeOf(info.notifiesCompletion, outcome, thrown, failures),
                emptyList()
            )
        } else {
            // The service has shown the toast.
            FileJobEnd(outcome, null, failures)
        }
    }

    // Must not throw, because the end of the job is reported afterwards, and it's called in a
    // finally block where it shouldn't replace what the job threw.
    internal fun finishEnd(bodyOutcome: FileJobOutcome): FileJobOutcome =
        try {
            val end = endOf(bodyOutcome)
            end.notice?.let { notifyEnd(it) }
            end.separateFailures.forEach { notifyCleanupFailure(it) }
            end.outcome
        } catch (e: Exception) {
            e.printStackTrace()
            if (isCanceled) FileJobOutcome.CANCELED else bodyOutcome
        }

    internal fun canceledByUser(): InterruptedIOException {
        isCanceled = true
        return InterruptedIOException()
    }

    @Throws(InterruptedIOException::class)
    internal fun throwIfCanceled() {
        if (isCanceled) {
            throw InterruptedIOException()
        }
    }

    @Throws(InterruptedIOException::class)
    internal fun throwIfCanceledOrInterrupted() {
        // Always clear the interrupted status first.
        if (Thread.interrupted() || isCanceled) {
            throw InterruptedIOException()
        }
    }

    // Only one of the job thread starting and the service canceling a job that may not start wins.
    internal fun tryClaimEnd(): Boolean = isEndClaimed.compareAndSet(false, true)

    // The job body doesn't run if the job is canceled while pending or before its thread starts.
    internal fun notifyCanceledWithoutRun(context: Context) {
        finishWithoutRun {
            showFileJobEndToast(
                context, info,
                fileJobEndNoticeOf<Path>(false, FileJobOutcome.CANCELED, null, emptyList())!!
            )
        }
    }

    internal fun finishWithoutRun(showToast: () -> Unit) {
        if (!tryClaimEnd()) {
            return
        }
        try {
            showToast()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onCanceledWithoutRun()
    }

    protected open fun onCanceledWithoutRun() {}

    @Throws(IOException::class)
    protected abstract fun run()
}

internal class FileJobEnd(
    val outcome: FileJobOutcome,
    val notice: FileJobEndNotice<Path>?,
    val separateFailures: List<Path>
)

/** Only the first result is reported. */
internal class OnceResultReporter(private val onResult: (Boolean) -> Unit) {
    private val isReported = AtomicBoolean()

    fun report(result: Boolean) {
        if (isReported.compareAndSet(false, true)) {
            onResult(result)
        }
    }
}

/** Reports the result of [block] once, with false if it threw. Whatever it threw is rethrown. */
internal inline fun runReportingResult(block: () -> Boolean, report: (Boolean) -> Unit) {
    val result = try {
        block()
    } catch (e: Throwable) {
        report(false)
        throw e
    }
    report(result)
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

/** The result of a batch, i.e. all the jobs from when one started with none running until none. */
data class FinishedBatch(val seq: Int, val isSuccessful: Boolean)

data class FileJobState(val runningCount: Int, val finished: FinishedBatch?)

/** Counts the running jobs and the result of the current batch. Not thread safe. */
class FileJobBatch {
    private val runningJobIds = mutableSetOf<Int>()
    private var hasFailure = false
    private var finished: FinishedBatch? = null
    private var nextSeq = 1

    val state: FileJobState
        get() = FileJobState(runningJobIds.size, finished)

    fun start(id: Int): Boolean {
        // Colliding job IDs are counted as one job.
        if (id in runningJobIds) {
            return false
        }
        if (runningJobIds.isEmpty()) {
            // A new batch hides the result of the previous one.
            finished = null
            hasFailure = false
        }
        runningJobIds += id
        return true
    }

    // The first report of a job wins, and the later ones are ignored.
    fun finish(id: Int, isSuccessful: Boolean, isResultKept: Boolean): Boolean {
        if (!runningJobIds.remove(id)) {
            return false
        }
        if (!isSuccessful) {
            hasFailure = true
        }
        if (runningJobIds.isEmpty()) {
            finished = if (isResultKept) FinishedBatch(nextSeq, !hasFailure) else null
            ++nextSeq
            hasFailure = false
        }
        return true
    }

    fun consume(seq: Int): Boolean {
        if (finished?.seq != seq) {
            return false
        }
        finished = null
        return true
    }
}

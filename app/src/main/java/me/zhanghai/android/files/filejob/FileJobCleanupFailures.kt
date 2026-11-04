/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

/**
 * The target files that a file job failed to delete after failing to write them. Failures are kept
 * while the job is running so that they can be merged into the toast for the end of the job.
 */
internal class FileJobCleanupFailures<T : Any> {
    private val pending = linkedSetOf<T>()
    private val reported = hashSetOf<T>()
    private var isFinished = false
    private var isFinishedSuccessfully = false

    /** @return Whether the failure should be reported on its own now. */
    @Synchronized
    fun onFailed(item: T): Boolean {
        if (item in reported) {
            return false
        }
        if (!isFinished) {
            pending += item
            return false
        }
        if (isFinishedSuccessfully) {
            return false
        }
        reported += item
        return true
    }

    /** The item has been fully written by a retry, so it's no longer broken. */
    @Synchronized
    fun onWritten(item: T) {
        pending -= item
    }

    /** @return The failures to report for the end of the job. */
    @Synchronized
    fun onJobFinished(isSuccessful: Boolean): List<T> {
        isFinished = true
        isFinishedSuccessfully = isSuccessful
        if (isSuccessful) {
            pending.clear()
            return emptyList()
        }
        val items = pending.toList()
        reported += items
        pending.clear()
        return items
    }
}

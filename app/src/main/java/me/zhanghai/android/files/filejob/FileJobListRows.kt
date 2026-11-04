/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

sealed interface FileJobRowStatus {
    object Pending : FileJobRowStatus

    object Running : FileJobRowStatus

    data class Scanning(val fileCount: Int, val size: Long) : FileJobRowStatus

    data class Size(
        val percent: Int,
        val transferredSize: Long,
        val size: Long,
        val fileIndex: Int?,
        val fileCount: Int
    ) : FileJobRowStatus

    data class Count(val percent: Int, val fileIndex: Int, val fileCount: Int) : FileJobRowStatus

    data class Finished(
        val outcome: FileJobOutcome,
        val hasCleanupFailure: Boolean
    ) : FileJobRowStatus
}

data class FileJobRow(val id: Int, val info: FileJobInfo, val status: FileJobRowStatus) {
    val isActive: Boolean
        get() = status !is FileJobRowStatus.Finished
}

internal fun fileJobRowStatusOf(
    isPending: Boolean,
    progress: FileJobProgress?,
    outcome: FileJobOutcome?,
    hasCleanupFailure: Boolean
): FileJobRowStatus {
    if (outcome != null) {
        return FileJobRowStatus.Finished(outcome, hasCleanupFailure)
    }
    if (isPending) {
        return FileJobRowStatus.Pending
    }
    if (progress == null) {
        return FileJobRowStatus.Running
    }
    if (progress.kind == FileJobProgressKind.SCANNING) {
        return FileJobRowStatus.Scanning(progress.fileCount, progress.size)
    }
    val percent = progress.percent ?: return FileJobRowStatus.Running
    return if (progress.kind == FileJobProgressKind.SIZE) {
        FileJobRowStatus.Size(
            percent, progress.transferredSize, progress.size,
            progress.currentFileIndex.takeIf { progress.fileCount > 1 }, progress.fileCount
        )
    } else {
        FileJobRowStatus.Count(percent, progress.currentFileIndex, progress.fileCount)
    }
}

/**
 * The rows for one time that the list is open. Rows for finished jobs stay until the list is
 * closed. All changes return whether anything has changed. Not thread safe.
 */
internal class FileJobListRows(initialEntries: List<FileJobEntry>) {
    private val states = linkedMapOf<Int, RowState>()

    init {
        initialEntries.forEach { onStarted(it) }
    }

    val rows: List<FileJobRow>
        get() =
            states.values.map {
                FileJobRow(
                    it.id, it.info,
                    fileJobRowStatusOf(it.isPending, it.progress, it.outcome, it.hasCleanupFailure)
                )
            }

    val activeIds: List<Int>
        get() = states.values.filter { it.outcome == null }.map { it.id }

    // Colliding job IDs are counted as one job.
    fun onStarted(entry: FileJobEntry): Boolean {
        if (entry.id in states) {
            return false
        }
        states[entry.id] = RowState(entry.id, entry.info, entry.isPending, entry.progress)
        return true
    }

    fun onRunning(id: Int): Boolean {
        val state = states[id]?.takeIf { it.outcome == null && it.isPending } ?: return false
        state.isPending = false
        return true
    }

    fun onProgress(id: Int, progress: FileJobProgress): Boolean {
        val state = states[id]?.takeIf { it.outcome == null } ?: return false
        state.isPending = false
        state.progress = progress
        return true
    }

    // The first report of a job wins, and the later ones are ignored.
    fun onFinished(id: Int, outcome: FileJobOutcome): Boolean {
        val state = states[id]?.takeIf { it.outcome == null } ?: return false
        state.outcome = outcome
        return true
    }

    fun onCleanupFailed(id: Int): Boolean {
        val state = states[id]?.takeIf { !it.hasCleanupFailure } ?: return false
        state.hasCleanupFailure = true
        return true
    }

    private class RowState(
        val id: Int,
        val info: FileJobInfo,
        var isPending: Boolean,
        var progress: FileJobProgress?
    ) {
        var outcome: FileJobOutcome? = null
        var hasCleanupFailure = false
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

/**
 * The state of the file jobs for the UI. Changes are always set on the main thread (instead of
 * posted), so that the start and finish of a short job aren't merged.
 */
object FileJobStateTracker {
    private val batch = FileJobBatch()

    private val _stateLiveData = MutableLiveData(batch.state)
    val stateLiveData: LiveData<FileJobState>
        get() = _stateLiveData

    private val entryTable = FileJobEntryTable()

    private val listeners = mutableListOf<Listener>()

    val runningEntries: List<FileJobEntry>
        @MainThread
        get() = entryTable.entries

    @MainThread
    fun addListener(listener: Listener) {
        listeners += listener
    }

    @MainThread
    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    @MainThread
    fun onJobStarted(id: Int, info: FileJobInfo, isPending: Boolean) {
        if (batch.start(id)) {
            _stateLiveData.value = batch.state
        }
        entryTable.start(id, info, isPending)?.let { entry ->
            forEachListener { it.onStarted(entry) }
        }
    }

    @MainThread
    fun onJobRunning(id: Int) {
        if (entryTable.setRunning(id)) {
            forEachListener { it.onRunning(id) }
        }
    }

    @MainThread
    fun onJobProgress(id: Int, progress: FileJobProgress) {
        if (entryTable.setProgress(id, progress)) {
            forEachListener { it.onProgress(id, progress) }
        }
    }

    @MainThread
    fun onJobFinished(id: Int, outcome: FileJobOutcome) {
        // The result of a batch that finished while the indicator is turned off is never shown,
        // even if no file list is started to consume it now.
        val isSuccessful = outcome == FileJobOutcome.SUCCEEDED
        if (batch.finish(id, isSuccessful, Settings.FILE_JOB_INDICATOR.valueCompat)) {
            _stateLiveData.value = batch.state
        }
        if (entryTable.finish(id)) {
            forEachListener { it.onFinished(id, outcome) }
        }
    }

    // A failure may be reported after the job has finished.
    @MainThread
    fun onJobCleanupFailed(id: Int) {
        forEachListener { it.onCleanupFailed(id) }
    }

    @MainThread
    fun consumeResult(seq: Int) {
        if (batch.consume(seq)) {
            _stateLiveData.value = batch.state
        }
    }

    private inline fun forEachListener(block: (Listener) -> Unit) {
        listeners.toList().forEach(block)
    }

    interface Listener {
        fun onStarted(entry: FileJobEntry)

        fun onRunning(id: Int)

        fun onProgress(id: Int, progress: FileJobProgress)

        fun onFinished(id: Int, outcome: FileJobOutcome)

        fun onCleanupFailed(id: Int)
    }
}

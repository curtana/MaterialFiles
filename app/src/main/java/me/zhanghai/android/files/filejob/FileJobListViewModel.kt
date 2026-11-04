/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FileJobListViewModel : ViewModel() {
    // The snapshot and the listener are on the same main thread message so no event is missed.
    private val rows = FileJobListRows(FileJobStateTracker.runningEntries)

    private val _rowsLiveData = MutableLiveData(rows.rows)
    val rowsLiveData: LiveData<List<FileJobRow>>
        get() = _rowsLiveData

    private val listener = object : FileJobStateTracker.Listener {
        override fun onStarted(entry: FileJobEntry) {
            update(rows.onStarted(entry))
        }

        override fun onRunning(id: Int) {
            update(rows.onRunning(id))
        }

        override fun onProgress(id: Int, progress: FileJobProgress) {
            update(rows.onProgress(id, progress))
        }

        override fun onFinished(id: Int, outcome: FileJobOutcome) {
            update(rows.onFinished(id, outcome))
        }

        override fun onCleanupFailed(id: Int) {
            update(rows.onCleanupFailed(id))
        }

        private fun update(isChanged: Boolean) {
            if (isChanged) {
                _rowsLiveData.value = rows.rows
            }
        }
    }

    init {
        FileJobStateTracker.addListener(listener)
    }

    override fun onCleared() {
        FileJobStateTracker.removeListener(listener)
    }

    fun cancel(id: Int) {
        FileJobService.cancelJob(id)
    }

    fun cancelAll() {
        rows.activeIds.forEach { FileJobService.cancelJob(it) }
    }
}

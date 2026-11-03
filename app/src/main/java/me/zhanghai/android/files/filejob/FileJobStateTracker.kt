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

    @MainThread
    fun onJobStarted(id: Int) {
        if (batch.start(id)) {
            _stateLiveData.value = batch.state
        }
    }

    @MainThread
    fun onJobFinished(id: Int, isSuccessful: Boolean) {
        // The result of a batch that finished while the indicator is turned off is never shown,
        // even if no file list is started to consume it now.
        if (batch.finish(id, isSuccessful, Settings.FILE_JOB_INDICATOR.valueCompat)) {
            _stateLiveData.value = batch.state
        }
    }

    @MainThread
    fun consumeResult(seq: Int) {
        if (batch.consume(seq)) {
            _stateLiveData.value = batch.state
        }
    }
}

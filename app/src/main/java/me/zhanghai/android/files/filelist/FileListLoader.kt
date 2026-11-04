/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.annotation.MainThread
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Runs the loads of a [FileListLiveData].
 *
 * A [reload] cancels the running load and starts a new one. A [requestReloadOnChange] doesn't
 * cancel the running load, because a load slower than the change notifications (e.g. a large
 * remote directory while a file is being written into it) would otherwise never finish; instead,
 * one more load starts after the running one finishes. The result of a load is delivered on
 * [mainExecutor] only if the load is still the current one, so a cancelled or superseded load
 * never delivers its result.
 *
 * All the functions here and the callbacks are on the main thread.
 *
 * @param load Loads the result on [backgroundExecutor]. It should not throw; if it does, nothing
 * is delivered for that load, but the loader is no longer loading.
 * @param onLoadStarted Called when a load starts, with whether it was started for a change.
 * @param onLoadFinished Called with the result of the current load, and whether it was started for
 * a change.
 * @param onPendingChange Called after [onLoadFinished] if a change was requested during the
 * load; it should call [requestReloadOnChange] if the change is still wanted.
 */
class FileListLoader<T>(
    private val backgroundExecutor: ExecutorService,
    private val mainExecutor: Executor,
    private val load: () -> T,
    private val onLoadStarted: (isForChange: Boolean) -> Unit,
    private val onLoadFinished: (T, isForChange: Boolean) -> Unit,
    private val onPendingChange: () -> Unit
) {
    private var future: Future<*>? = null

    // Incremented for every load and on close, so that only the current load delivers.
    private var generation = 0

    private var isChangePending = false

    private var isClosed = false

    val isLoading: Boolean
        @MainThread
        get() = future != null

    @MainThread
    fun reload() {
        start(false)
    }

    @MainThread
    fun requestReloadOnChange() {
        if (isLoading) {
            isChangePending = true
        } else {
            start(true)
        }
    }

    private fun start(isForChange: Boolean) {
        if (isClosed) {
            return
        }
        isChangePending = false
        future?.cancel(true)
        val generation = ++generation
        onLoadStarted(isForChange)
        future = backgroundExecutor.submit {
            val result = try {
                load()
            } catch (t: Throwable) {
                // Don't stay loading forever, so that later changes still reload.
                mainExecutor.execute { onFinished(generation, null, isForChange) }
                throw t
            }
            mainExecutor.execute { onFinished(generation, LoadResult(result), isForChange) }
        }
    }

    // A null result means that the load threw.
    private fun onFinished(generation: Int, result: LoadResult<T>?, isForChange: Boolean) {
        if (generation != this.generation) {
            return
        }
        future = null
        if (result != null) {
            onLoadFinished(result.value, isForChange)
        }
        if (isChangePending) {
            isChangePending = false
            onPendingChange()
        }
    }

    @MainThread
    fun close() {
        isClosed = true
        ++generation
        isChangePending = false
        future?.cancel(true)
        future = null
    }
}

// Wraps a result so that a null result can be told apart from a load that threw.
private class LoadResult<T>(val value: T)

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import me.zhanghai.android.files.provider.common.PathObservable
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Observes a path inside an archive by observing the archive file. Before an observer is notified,
 * the archive is invalidated so that the next load reads the archive file again.
 *
 * The invalidation may wait for a load holding the archive file system lock, so it is done on
 * [executor] instead of the thread notifying the observers, which is shared by all observables.
 */
internal class ArchivePathObservable(
    private val archiveFileObservable: PathObservable,
    private val invalidate: () -> Unit,
    private val executor: Executor
) : PathObservable {
    private val wrappers = mutableMapOf<() -> Unit, () -> Unit>()

    override fun addObserver(observer: () -> Unit) {
        val wrapper: () -> Unit = {
            executor.execute {
                try {
                    invalidate()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                observer()
            }
        }
        val previousWrapper = synchronized(wrappers) { wrappers.put(observer, wrapper) }
        if (previousWrapper != null) {
            archiveFileObservable.removeObserver(previousWrapper)
        }
        try {
            archiveFileObservable.addObserver(wrapper)
        } catch (e: Exception) {
            synchronized(wrappers) { wrappers.remove(observer, wrapper) }
            throw e
        }
    }

    override fun removeObserver(observer: () -> Unit) {
        val wrapper = synchronized(wrappers) { wrappers.remove(observer) } ?: return
        archiveFileObservable.removeObserver(wrapper)
    }

    @Throws(IOException::class)
    override fun close() {
        synchronized(wrappers) { wrappers.clear() }
        archiveFileObservable.close()
    }
}

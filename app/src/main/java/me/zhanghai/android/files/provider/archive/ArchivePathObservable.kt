/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import me.zhanghai.android.files.provider.common.PathObservable
import java.util.concurrent.Executor

// TEMPORARY fail-before stub (#69): the prototype behavior, invalidating on the caller thread.
internal class ArchivePathObservable(
    private val archiveFileObservable: PathObservable,
    private val invalidate: () -> Unit,
    private val executor: Executor
) : PathObservable {
    private val wrappers = mutableMapOf<() -> Unit, () -> Unit>()

    override fun addObserver(observer: () -> Unit) {
        val wrapper: () -> Unit = {
            try {
                invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            observer()
        }
        val previous = synchronized(wrappers) { wrappers.put(observer, wrapper) }
        if (previous != null) {
            archiveFileObservable.removeObserver(previous)
        }
        archiveFileObservable.addObserver(wrapper)
    }

    override fun removeObserver(observer: () -> Unit) {
        val wrapper = synchronized(wrappers) { wrappers.remove(observer) } ?: return
        archiveFileObservable.removeObserver(wrapper)
    }

    override fun close() {
        synchronized(wrappers) { wrappers.clear() }
        archiveFileObservable.close()
    }
}

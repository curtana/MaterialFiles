/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.ClosedFileSystemException

// TEMPORARY: mimics the current locking of ArchiveFileSystem (refresh() takes the lock) to
// show the tests failing before the real implementation.
internal class ArchiveEntriesCache<T : Any>(private val lock: Any) {
    @Volatile
    private var isClosed = false

    private var isRefreshNeeded = true

    private var value: T? = null

    val isOpen: Boolean
        get() = !isClosed

    fun invalidate() {
        synchronized(lock) {
            if (isClosed) {
                throw ClosedFileSystemException()
            }
            isRefreshNeeded = true
        }
    }

    fun getLocked(read: () -> T): T {
        check(Thread.holdsLock(lock))
        if (isClosed) {
            throw ClosedFileSystemException()
        }
        if (isRefreshNeeded) {
            value = read()
            isRefreshNeeded = false
        }
        return value!!
    }

    fun closeLocked(): Boolean {
        check(Thread.holdsLock(lock))
        if (isClosed) {
            return false
        }
        isRefreshNeeded = false
        value = null
        isClosed = true
        return true
    }
}

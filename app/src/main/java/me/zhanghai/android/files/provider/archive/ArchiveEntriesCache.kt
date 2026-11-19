/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.ClosedFileSystemException
import java.util.concurrent.atomic.AtomicLong

/**
 * Cached archive entries guarded by the lock of the owning [ArchiveFileSystem].
 *
 * Invalidation doesn't take the lock and only bumps the requested generation, so it never waits
 * for a read in progress. A lookup records the requested generation before reading, so an
 * invalidation during the read makes the next lookup read again.
 */
internal class ArchiveEntriesCache<T : Any>(private val lock: Any) {
    @Volatile
    private var isClosed = false

    private val requestedGeneration = AtomicLong()

    // Guarded by lock, and only meaningful when value is not null.
    private var loadedGeneration = 0L

    // Guarded by lock.
    private var value: T? = null

    val isOpen: Boolean
        get() = !isClosed

    fun invalidate() {
        if (isClosed) {
            throw ClosedFileSystemException()
        }
        requestedGeneration.incrementAndGet()
    }

    fun getLocked(read: () -> T): T {
        check(Thread.holdsLock(lock))
        if (isClosed) {
            throw ClosedFileSystemException()
        }
        // Read the generation before reading, so that an invalidation during the read isn't lost.
        val generation = requestedGeneration.get()
        val currentValue = value
        if (currentValue != null && loadedGeneration == generation) {
            return currentValue
        }
        val newValue = read()
        value = newValue
        loadedGeneration = generation
        return newValue
    }

    fun closeLocked(): Boolean {
        check(Thread.holdsLock(lock))
        if (isClosed) {
            return false
        }
        value = null
        isClosed = true
        return true
    }
}

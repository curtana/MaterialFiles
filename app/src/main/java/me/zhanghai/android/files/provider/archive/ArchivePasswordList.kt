/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.ClosedFileSystemException
import java.util.concurrent.atomic.AtomicReference

/**
 * Archive passwords of the owning [ArchiveFileSystem], as an immutable list behind an atomic
 * reference.
 *
 * Adding doesn't take the lock of the file system, so it never waits for an entry read in progress.
 * Readers get the list once under that lock and use it for the whole read.
 */
internal class ArchivePasswordList(
    private val lock: Any,
    private val isOpen: () -> Boolean
) {
    private val passwords = AtomicReference(listOf<String>())

    fun add(password: String) {
        while (true) {
            if (!isOpen()) {
                throw ClosedFileSystemException()
            }
            val currentPasswords = passwords.get()
            if (passwords.compareAndSet(currentPasswords, currentPasswords + password)) {
                return
            }
        }
    }

    fun getLocked(): List<String> {
        check(Thread.holdsLock(lock))
        return passwords.get()
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.ClosedFileSystemException

// TEMPORARY: mimics the current locking of ArchiveFileSystem.addPassword() (adding takes the lock)
// to show the tests failing before the real implementation.
internal class ArchivePasswordList(
    private val lock: Any,
    private val isOpen: () -> Boolean
) {
    private var passwords = listOf<String>()

    fun add(password: String) {
        synchronized(lock) {
            if (!isOpen()) {
                throw ClosedFileSystemException()
            }
            passwords += password
        }
    }

    fun getLocked(): List<String> {
        check(Thread.holdsLock(lock))
        return passwords
    }
}

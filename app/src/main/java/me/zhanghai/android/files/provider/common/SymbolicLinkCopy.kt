/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.NoSuchFileException
import java.io.IOException

/**
 * Creates a symbolic link while copying, replacing an existing target if requested.
 *
 * [createLink] and [deleteExisting] must throw exceptions already converted to
 * [java8.nio.file.FileSystemException]; an existing target must be reported as
 * [FileAlreadyExistsException] and a missing target as [NoSuchFileException].
 */
@Throws(IOException::class)
fun createSymbolicLinkForCopy(
    replaceExisting: Boolean,
    createLink: () -> Unit,
    deleteExisting: () -> Unit
) {
    try {
        createLink()
    } catch (e: FileAlreadyExistsException) {
        if (!replaceExisting) {
            throw e
        }
        try {
            deleteExisting()
        } catch (e2: NoSuchFileException) {
            // Ignored.
        } catch (e2: IOException) {
            e2.addSuppressed(e)
            throw e2
        }
        try {
            createLink()
        } catch (e2: IOException) {
            e2.addSuppressed(e)
            throw e2
        }
        throw e
    }
}

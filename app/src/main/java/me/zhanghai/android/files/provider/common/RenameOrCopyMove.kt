/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.FileSystemException
import java.io.IOException

/**
 * Finishes a provider move after the source and target have been checked: tries the server
 * [rename] first and, unless the move is atomic, falls back to [copy] and then [deleteSource].
 *
 * [rename] is told to overwrite only for a replace move onto an existing target. The existing
 * target is never deleted here: an overwrite that the server refuses is left to [copy], which
 * deletes the target only after checking the source (#55). If [deleteSource] fails after the copy,
 * the copy is kept and the exception is thrown (#95).
 *
 * The lambdas must throw exceptions already converted to [FileSystemException]; only those count
 * as a failed rename.
 */
@Throws(IOException::class)
internal fun moveByRenameOrCopy(
    copyOptions: CopyOptions,
    targetExists: Boolean,
    sourceSize: Long,
    rename: (overwrite: Boolean) -> Unit,
    copy: (CopyOptions) -> Unit,
    deleteSource: () -> Unit
) {
    val overwrite = targetExists && copyOptions.replaceExisting
    var renameSuccessful = false
    try {
        rename(overwrite)
        renameSuccessful = true
    } catch (e: FileSystemException) {
        if (copyOptions.atomicMove) {
            throw e
        }
        // Ignored.
    }
    if (renameSuccessful) {
        copyOptions.progressListener?.invoke(sourceSize)
        return
    }
    if (copyOptions.atomicMove) {
        throw AssertionError()
    }
    val copyOptionsForCopy = if (copyOptions.copyAttributes && copyOptions.noFollowLinks) {
        copyOptions
    } else {
        CopyOptions(
            copyOptions.replaceExisting, true, false, true, copyOptions.progressIntervalMillis,
            copyOptions.progressListener, copyOptions.cleanupFailureListener
        )
    }
    copy(copyOptionsForCopy)
    // Keep the copy if deleting the source fails: the target has the new content and the source
    // is still there, so nothing is lost.
    deleteSource()
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.NoSuchFileException
import java.io.IOException

/**
 * Writes a copy target, deleting an existing target only after the source has been opened, so
 * that a source that cannot be read never costs the existing target.
 *
 * [openSource] is called first; if it throws, nothing else is called. Then [deleteExisting] is
 * called if not null, ignoring [NoSuchFileException], and then [write] with the opened source.
 * [closeSource] is called once [openSource] has succeeded. If [deleteExisting] or [write] throws,
 * an exception from [closeSource] is added as suppressed.
 *
 * The lambdas must throw exceptions already converted to [java8.nio.file.FileSystemException], so
 * that a missing target is reported as [NoSuchFileException].
 */
@Throws(IOException::class)
fun <S, R> replaceAfterOpeningSource(
    openSource: () -> S,
    closeSource: (S) -> Unit,
    deleteExisting: (() -> Unit)?,
    write: (S) -> R
): R {
    val source = openSource()
    val result = try {
        if (deleteExisting != null) {
            try {
                deleteExisting()
            } catch (e: NoSuchFileException) {
                // Ignored.
            }
        }
        write(source)
    } catch (t: Throwable) {
        try {
            closeSource(source)
        } catch (t2: Throwable) {
            t.addSuppressed(t2)
        }
        throw t
    }
    closeSource(source)
    return result
}

/**
 * Like [replaceAfterOpeningSource], for sources that are only checked by [checkSource] (which opens
 * and closes them itself) instead of being read through an opened handle.
 */
@Throws(IOException::class)
fun <R> replaceAfterCheckingSource(
    checkSource: () -> Unit,
    deleteExisting: (() -> Unit)?,
    write: () -> R
): R = replaceAfterOpeningSource(checkSource, {}, deleteExisting) { write() }

/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

enum class FileJobErrorAction {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    CANCELED
}

// Only retrying resolves an error; skipping or canceling leaves the job unfinished. The positive
// button skips in some dialogs.
internal fun isFileJobErrorResolved(action: FileJobErrorAction, isPositiveRetry: Boolean): Boolean =
    action == FileJobErrorAction.POSITIVE && isPositiveRetry

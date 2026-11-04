/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

/** How a file job finished. */
enum class FileJobOutcome {
    SUCCEEDED,
    CANCELED,
    FAILED;

    companion object {
        /**
         * A job with the canceled flag is always canceled, so that its row and toast agree with
         * what the user asked for no matter how the job body happened to end.
         */
        fun of(
            hasUnresolvedError: Boolean,
            thrown: Throwable?,
            isCanceled: Boolean
        ): FileJobOutcome =
            when {
                isCanceled -> CANCELED
                thrown != null || hasUnresolvedError -> FAILED
                else -> SUCCEEDED
            }
    }
}

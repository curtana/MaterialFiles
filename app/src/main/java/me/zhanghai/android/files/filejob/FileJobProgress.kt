/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

enum class FileJobProgressKind {
    SCANNING,
    SIZE,
    COUNT
}

/** A copy of the values used by the notification of a file job. */
data class FileJobProgress(
    val kind: FileJobProgressKind,
    val fileCount: Int,
    val transferredFileCount: Int,
    val size: Long,
    val transferredSize: Long
) {
    /** The percentage from 0 to 100, or null if the progress is indeterminate. */
    val percent: Int?
        get() =
            when (kind) {
                FileJobProgressKind.SCANNING -> null
                FileJobProgressKind.SIZE ->
                    if (size > 0) {
                        (transferredSize * 100 / size).coerceIn(0, 100).toInt()
                    } else {
                        null
                    }
                FileJobProgressKind.COUNT ->
                    if (fileCount > 1) {
                        (transferredFileCount.toLong() * 100 / fileCount).coerceIn(0, 100).toInt()
                    } else {
                        null
                    }
            }

    val currentFileIndex: Int
        get() = (transferredFileCount + 1).coerceAtMost(fileCount)
}

/** Not thread safe. */
internal class FileJobProgressThrottle(private val intervalMillis: Long) {
    private var lastReportMillis: Long? = null

    fun shouldReport(nowMillis: Long): Boolean {
        val lastReportMillis = lastReportMillis
        if (lastReportMillis != null && lastReportMillis + intervalMillis >= nowMillis) {
            return false
        }
        this.lastReportMillis = nowMillis
        return true
    }
}

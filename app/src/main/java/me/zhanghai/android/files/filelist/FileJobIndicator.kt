/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

enum class FileJobIndicatorPhase {
    HIDDEN,
    RUNNING,
    SUCCEEDED,
    FAILED
}

// There is only one indicator on the screen, which is on the end pane in dual pane mode and on the
// visible pane in single pane mode.
internal fun getFileJobIndicatorPaneIndex(
    isDualPaneEnabled: Boolean,
    isDualPane: Boolean,
    activePane: Int
): Int =
    when {
        !isDualPaneEnabled -> 0
        isDualPane -> 1
        else -> activePane
    }

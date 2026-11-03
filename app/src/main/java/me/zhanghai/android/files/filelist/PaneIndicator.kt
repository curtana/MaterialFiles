/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

enum class PaneIndicatorMode {
    // There is no second pane.
    HIDDEN,
    // Single pane mode, where the indicator toggles the visible pane.
    SWITCHER,
    // Dual pane mode, for the active pane.
    ACTIVE,
    // Dual pane mode, for the inactive pane, where the indicator activates the pane.
    INACTIVE
}

// The hidden pane in single pane mode is also a switcher, so that it's ready to receive focus
// right after toggling.
internal fun getPaneIndicatorMode(
    isDualPaneEnabled: Boolean,
    isDualPane: Boolean,
    paneIndex: Int,
    activePane: Int
): PaneIndicatorMode =
    when {
        !isDualPaneEnabled -> PaneIndicatorMode.HIDDEN
        !isDualPane -> PaneIndicatorMode.SWITCHER
        paneIndex == activePane -> PaneIndicatorMode.ACTIVE
        else -> PaneIndicatorMode.INACTIVE
    }

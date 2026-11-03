/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Stateful

// These are file level functions so that they can be tested without Android.

/**
 * Whether a load should be silent, i.e. not show the loading state. An automatic load (for a
 * change) is silent if there is already a list shown, either from a success or kept by a failure.
 */
internal fun shouldLoadSilently(isAutomatic: Boolean, current: Stateful<*>?): Boolean =
    isAutomatic && current?.value != null

/**
 * Whether a failure should be notified with a toast. A failure without any files is shown in the
 * error text instead. A failure is notified when the view wasn't in failure already, so that
 * failing silent loads during a file job don't keep showing toasts, or when it's the result of an
 * explicit load, which the user expects an answer for even if the view didn't receive the loading
 * state (e.g. while it was inactive).
 *
 * @param previous The state the view received before, or `null` if the view is new.
 * @param isExplicitLoadResult Whether [current] is the result of an explicit load.
 */
internal fun shouldNotifyFailure(
    previous: Stateful<*>?,
    current: Stateful<*>,
    isExplicitLoadResult: Boolean
): Boolean {
    if (current !is Failure || (current.value as? Collection<*>).isNullOrEmpty()) {
        return false
    }
    // The same state may be delivered again, e.g. when a hidden pane is shown again.
    if (current === previous) {
        return false
    }
    return isExplicitLoadResult || previous !is Failure
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import java.util.concurrent.atomic.AtomicBoolean

/** Only follows whichever comes first between a remote operation completing and the remote dying. */
class RemoteCopyOutcomeWatch(private val onDied: () -> Unit) {
    private val isSettled = AtomicBoolean()

    /** @return Whether the operation completed before the remote died. */
    fun onCompleted(): Boolean = isSettled.compareAndSet(false, true)

    fun onRemoteDied() {
        if (isSettled.compareAndSet(false, true)) {
            onDied()
        }
    }
}

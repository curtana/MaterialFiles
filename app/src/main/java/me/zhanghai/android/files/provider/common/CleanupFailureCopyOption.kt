/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.CopyOption
import me.zhanghai.android.files.util.RemoteCallback
import me.zhanghai.android.files.util.readParcelable
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports that a partially written target couldn't be deleted, or that it's unknown whether it was
 * deleted. The listener is called at most once and never throws, because it's called while cleaning
 * up in a finally block. RemoteCallback.sendResult() isn't oneway, so a remote cleanup waits for the
 * listener and the listener must not block.
 */
class CleanupFailureCopyOption(listener: () -> Unit) : CopyOption, Parcelable {
    val listener: () -> Unit = safeOnce(listener)

    private constructor(source: Parcel) : this(source.readParcelable<RemoteCallback>()!!.asListener())

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(RemoteCallback { listener() }, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<CleanupFailureCopyOption> {
            override fun createFromParcel(source: Parcel): CleanupFailureCopyOption =
                CleanupFailureCopyOption(source)

            override fun newArray(size: Int): Array<CleanupFailureCopyOption?> = arrayOfNulls(size)
        }
    }
}

private fun RemoteCallback.asListener(): () -> Unit = { sendResult(Bundle()) }

internal fun safeOnce(listener: () -> Unit): () -> Unit {
    val isCalled = AtomicBoolean()
    return {
        if (isCalled.compareAndSet(false, true)) {
            try {
                listener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/** Closes this and reports the failure if [shouldReport], rethrowing what close() threw. */
internal fun Closeable.closeReportingFailure(shouldReport: () -> Boolean, listener: (() -> Unit)?) {
    try {
        close()
    } catch (e: Exception) {
        if (shouldReport()) {
            listener?.invoke()
        }
        throw e
    }
}

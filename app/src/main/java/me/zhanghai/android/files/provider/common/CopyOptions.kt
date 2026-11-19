/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import android.os.Parcelable
import java8.nio.file.CopyOption
import java8.nio.file.LinkOption
import java8.nio.file.StandardCopyOption
import kotlinx.parcelize.Parcelize

/**
 * Don't give the target any attribute of the source: no permissions when creating it, and no
 * attribute step (times, permissions, owner, xattrs) after writing it. Used by network copies
 * (#55). Takes precedence over [StandardCopyOption.COPY_ATTRIBUTES].
 */
@Parcelize
data object SkipAttributesCopyOption : CopyOption, Parcelable

class CopyOptions(
    val replaceExisting: Boolean,
    val copyAttributes: Boolean,
    val atomicMove: Boolean,
    val noFollowLinks: Boolean,
    val progressIntervalMillis: Long,
    val progressListener: ((Long) -> Unit)?,
    val cleanupFailureListener: (() -> Unit)?,
    val skipAttributes: Boolean = false
) {
    fun toArray(): Array<CopyOption> {
        val options = mutableListOf<CopyOption>()
        if (replaceExisting) {
            options += StandardCopyOption.REPLACE_EXISTING
        }
        if (copyAttributes) {
            options += StandardCopyOption.COPY_ATTRIBUTES
        }
        if (atomicMove) {
            options += StandardCopyOption.ATOMIC_MOVE
        }
        if (noFollowLinks) {
            options += LinkOption.NOFOLLOW_LINKS
        }
        if (progressListener != null) {
            options += ProgressCopyOption(progressIntervalMillis, progressListener)
        }
        if (cleanupFailureListener != null) {
            options += CleanupFailureCopyOption(cleanupFailureListener)
        }
        if (skipAttributes) {
            options += SkipAttributesCopyOption
        }
        return options.toTypedArray()
    }
}

fun Array<out CopyOption>.toCopyOptions(): CopyOptions {
    var replaceExisting = false
    var copyAttributes = false
    var atomicMove = false
    var noFollowLinks = false
    var progressIntervalMillis = 0L
    var progressListener: ((Long) -> Unit)? = null
    var cleanupFailureListener: (() -> Unit)? = null
    var skipAttributes = false
    for (option in this) {
        when {
            option is StandardCopyOption ->
                when (option) {
                    StandardCopyOption.REPLACE_EXISTING -> replaceExisting = true
                    StandardCopyOption.COPY_ATTRIBUTES -> copyAttributes = true
                    StandardCopyOption.ATOMIC_MOVE -> atomicMove = true
                    else -> throw UnsupportedOperationException(option.toString())
                }
            option === LinkOption.NOFOLLOW_LINKS -> noFollowLinks = true
            option is ProgressCopyOption -> {
                progressIntervalMillis = option.intervalMillis
                progressListener = option.listener
            }
            option is CleanupFailureCopyOption -> cleanupFailureListener = option.listener
            option is SkipAttributesCopyOption -> skipAttributes = true
            else -> {
                throw UnsupportedOperationException(option.toString())
            }
        }
    }
    return CopyOptions(
        replaceExisting, copyAttributes, atomicMove, noFollowLinks, progressIntervalMillis,
        progressListener, cleanupFailureListener, skipAttributes
    )
}

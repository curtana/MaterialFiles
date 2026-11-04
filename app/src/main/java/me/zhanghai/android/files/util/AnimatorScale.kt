/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import me.zhanghai.android.files.app.powerManager

/**
 * Whether animations should be replaced with still images, either because battery saver is on
 * (regardless of whether the device disables animators for it), or because animators don't run.
 */
fun Context.isReducedMotion(): Boolean =
    isReducedMotion(powerManager.isPowerSaveMode, areAnimatorsDisabled())

/**
 * Whether the animator duration scale is 0 (e.g. animations removed in accessibility settings), or
 * on API 26+ animators are disabled for any other reason.
 */
private fun Context.areAnimatorsDisabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        !ValueAnimator.areAnimatorsEnabled()
    } else {
        isReducedMotion(
            android.provider.Settings.Global.getFloat(
                contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        )
    }

internal fun isReducedMotion(isPowerSaveMode: Boolean, areAnimatorsDisabled: Boolean): Boolean =
    isPowerSaveMode || areAnimatorsDisabled

internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

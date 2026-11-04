/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build

/**
 * Whether animators don't run, either because the animator duration scale is 0 (e.g. animations
 * removed in accessibility settings), or on API 26+ for any other reason (e.g. battery saver).
 */
fun Context.isReducedMotion(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        !ValueAnimator.areAnimatorsEnabled()
    } else {
        isReducedMotion(
            android.provider.Settings.Global.getFloat(
                contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        )
    }

internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

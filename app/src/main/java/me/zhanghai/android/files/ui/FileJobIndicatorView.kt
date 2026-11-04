/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.AttrRes
import androidx.core.graphics.withScale
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.FileJobIndicatorPhase
import me.zhanghai.android.files.util.RuntimeBroadcastReceiver
import me.zhanghai.android.files.util.dpToDimension
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.isReducedMotion

/**
 * A stick figure that runs while file jobs are running, and cheers or falls over for the result.
 * It conveys a state, so it's exposed to accessibility services but isn't clickable.
 */
class FileJobIndicatorView : View {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToDimension(STROKE_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // The same color as the toolbar icons, from the app bar theme.
        color = context.getColorByAttr(androidx.appcompat.R.attr.colorControlNormal)
    }

    private val figureSize = context.dpToDimension(FIGURE_SIZE_DP)

    private val poseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        addUpdateListener {
            posePhase = it.animatedFraction
            invalidate()
        }
    }

    private var posePhase = 0f

    private var isReducedMotion = false

    // The last shown phase, which is still drawn while fading out.
    private var drawnPhase: FileJobIndicatorPhase? = null

    private val powerSaveModeReceiver = RuntimeBroadcastReceiver(
        IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateReducedMotion()
            }
        }, context
    )

    var phase = FileJobIndicatorPhase.HIDDEN
        set(value) {
            if (field == value) {
                return
            }
            // The new phase must be set before changing the visibility, which calls
            // onVisibilityChanged() synchronously.
            field = value
            poseAnimator.cancel()
            if (value == FileJobIndicatorPhase.HIDDEN) {
                // Cleared now so that the next description is announced again.
                contentDescription = null
                isReducedMotion = context.isReducedMotion()
                animate().cancel()
                if (isReducedMotion || !isShown) {
                    visibility = GONE
                    alpha = 1f
                } else {
                    animate()
                        .alpha(0f)
                        .setDuration(FADE_OUT_MILLIS)
                        .withEndAction {
                            visibility = GONE
                            alpha = 1f
                        }
                }
                return
            }
            animate().cancel()
            alpha = 1f
            drawnPhase = value
            isReducedMotion = context.isReducedMotion()
            posePhase = if (isReducedMotion) getStaticPosePhase(value) else 0f
            visibility = VISIBLE
            // Set after being visible, because the live region event of an invisible view is
            // dropped.
            contentDescription = context.getString(
                when (value) {
                    FileJobIndicatorPhase.RUNNING -> R.string.file_job_indicator_running_description
                    FileJobIndicatorPhase.SUCCEEDED ->
                        R.string.file_job_indicator_succeeded_description
                    else -> R.string.file_job_indicator_failed_description
                }
            )
            updatePoseAnimator(true)
            invalidate()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    init {
        isClickable = false
        isLongClickable = false
        isFocusable = false
        // Set before any description, so that setting one sends a live region event.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        contentDescription = null
        visibility = GONE
    }

    private fun getStaticPosePhase(phase: FileJobIndicatorPhase): Float =
        if (phase == FileJobIndicatorPhase.RUNNING) STATIC_RUNNING_POSE_PHASE else 1f

    private fun updatePoseAnimator(restart: Boolean) {
        val shouldRun = phase != FileJobIndicatorPhase.HIDDEN && !isReducedMotion &&
            isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (!shouldRun) {
            stopPoseAnimator()
            return
        }
        if (!restart) {
            if (poseAnimator.isStarted) {
                return
            }
            // A result that is already shown isn't animated again.
            if (phase != FileJobIndicatorPhase.RUNNING && posePhase == 1f) {
                return
            }
        }
        poseAnimator.cancel()
        when (phase) {
            FileJobIndicatorPhase.RUNNING -> poseAnimator.apply {
                duration = RUNNING_CYCLE_MILLIS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
            }
            FileJobIndicatorPhase.SUCCEEDED -> poseAnimator.apply {
                duration = CHEERING_MILLIS
                repeatCount = 0
                interpolator = FastOutSlowInInterpolator()
            }
            else -> poseAnimator.apply {
                duration = FALLING_MILLIS
                repeatCount = 0
                interpolator = FastOutSlowInInterpolator()
            }
        }
        poseAnimator.start()
    }

    private fun stopPoseAnimator() {
        poseAnimator.cancel()
        // A result that was interrupted isn't animated again from the start.
        if (phase == FileJobIndicatorPhase.SUCCEEDED || phase == FileJobIndicatorPhase.FAILED) {
            posePhase = 1f
        }
    }

    // Only changes how the current phase is drawn, so that nothing is announced again and the
    // timing rules are unaffected.
    private fun updateReducedMotion() {
        val isReducedMotion = context.isReducedMotion()
        if (this.isReducedMotion == isReducedMotion) {
            return
        }
        this.isReducedMotion = isReducedMotion
        if (phase == FileJobIndicatorPhase.HIDDEN) {
            // Canceling the fade out also removes its end action.
            if (isReducedMotion && visibility != GONE) {
                animate().cancel()
                visibility = GONE
                alpha = 1f
            }
            return
        }
        if (isReducedMotion) {
            poseAnimator.cancel()
            posePhase = getStaticPosePhase(phase)
            invalidate()
        } else {
            updatePoseAnimator(false)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Registered first so that a change after reading the state isn't missed.
        powerSaveModeReceiver.register()
        updateReducedMotion()
        updatePoseAnimator(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        powerSaveModeReceiver.unregister()
        // Not updatePoseAnimator(), because the view still appears attached and shown here.
        stopPoseAnimator()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        updatePoseAnimator(false)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)

        updatePoseAnimator(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pose = when (drawnPhase) {
            null, FileJobIndicatorPhase.HIDDEN -> return
            FileJobIndicatorPhase.RUNNING -> StickFigurePose.running(posePhase)
            FileJobIndicatorPhase.SUCCEEDED -> StickFigurePose.cheering(posePhase)
            FileJobIndicatorPhase.FAILED -> StickFigurePose.fallen(posePhase)
        }
        val centerX = width / 2f
        val centerY = height / 2f
        // Mirrored in RTL so that it runs towards the end.
        val scaleX = if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
        canvas.withScale(scaleX, 1f, centerX, centerY) {
            val left = centerX - figureSize / 2
            val top = centerY - figureSize / 2
            fun x(joint: Joint) = left + joint.x * figureSize
            fun y(joint: Joint) = top + joint.y * figureSize
            drawCircle(x(pose.head), y(pose.head), pose.headRadius * figureSize, paint)
            drawLine(x(pose.neck), y(pose.neck), x(pose.hip), y(pose.hip), paint)
            drawLine(x(pose.neck), y(pose.neck), x(pose.hand1), y(pose.hand1), paint)
            drawLine(x(pose.neck), y(pose.neck), x(pose.hand2), y(pose.hand2), paint)
            drawLine(x(pose.hip), y(pose.hip), x(pose.foot1), y(pose.foot1), paint)
            drawLine(x(pose.hip), y(pose.hip), x(pose.foot2), y(pose.foot2), paint)
        }
    }

    companion object {
        private const val FIGURE_SIZE_DP = 24
        private const val STROKE_WIDTH_DP = 2
        private const val STATIC_RUNNING_POSE_PHASE = 0.25f
        private const val RUNNING_CYCLE_MILLIS = 600L
        private const val CHEERING_MILLIS = 300L
        private const val FALLING_MILLIS = 400L
        private const val FADE_OUT_MILLIS = 150L
    }
}

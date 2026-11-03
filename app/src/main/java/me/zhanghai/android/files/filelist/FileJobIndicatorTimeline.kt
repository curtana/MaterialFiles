/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.filejob.FileJobState

/**
 * Decides the phase of the file job indicator of a screen over time. The same input always gives
 * the same output, and a result is only asked to be consumed once.
 */
class FileJobIndicatorTimeline {
    class Output(
        val phase: FileJobIndicatorPhase,
        // The time to update again at, in the same clock as the input.
        val nextUpdateMillis: Long?,
        val seqToConsume: Int?
    )

    var phase = FileJobIndicatorPhase.HIDDEN
        private set

    private var runningSince = 0L
    private var resultUntil = 0L
    private var shownSeq: Int? = null
    private var isRestarting = false

    // The time while stopped doesn't count as shown running.
    fun stop() {
        isRestarting = true
    }

    fun update(nowMillis: Long, isEnabled: Boolean, state: FileJobState): Output {
        if (!isEnabled) {
            phase = FileJobIndicatorPhase.HIDDEN
            isRestarting = false
            return Output(phase, null, state.finished?.seq)
        }
        if (isRestarting) {
            isRestarting = false
            if (phase == FileJobIndicatorPhase.RUNNING) {
                runningSince = nowMillis
            }
        }
        if (state.runningCount > 0) {
            if (phase != FileJobIndicatorPhase.RUNNING) {
                phase = FileJobIndicatorPhase.RUNNING
                runningSince = nowMillis
            }
            return Output(phase, null, null)
        }
        val finished = state.finished
        if (phase == FileJobIndicatorPhase.SUCCEEDED || phase == FileJobIndicatorPhase.FAILED) {
            if (finished != null && finished.seq != shownSeq) {
                // Another batch started and finished while stopped.
                return startRunning(nowMillis)
            }
            if (nowMillis < resultUntil) {
                return Output(phase, resultUntil, null)
            }
            phase = FileJobIndicatorPhase.HIDDEN
        }
        return when (phase) {
            FileJobIndicatorPhase.HIDDEN ->
                if (finished != null && finished.seq != shownSeq) {
                    // The batch finished while this screen wasn't shown.
                    startRunning(nowMillis)
                } else {
                    Output(phase, null, null)
                }
            FileJobIndicatorPhase.RUNNING -> {
                if (finished == null) {
                    // Consumed by another screen.
                    phase = FileJobIndicatorPhase.HIDDEN
                    return Output(phase, null, null)
                }
                if (nowMillis - runningSince < MIN_RUNNING_MILLIS) {
                    return Output(phase, runningSince + MIN_RUNNING_MILLIS, null)
                }
                phase = if (finished.isSuccessful) {
                    FileJobIndicatorPhase.SUCCEEDED
                } else {
                    FileJobIndicatorPhase.FAILED
                }
                resultUntil = nowMillis + RESULT_MILLIS
                shownSeq = finished.seq
                Output(phase, resultUntil, finished.seq)
            }
            else -> throw AssertionError(phase)
        }
    }

    private fun startRunning(nowMillis: Long): Output {
        phase = FileJobIndicatorPhase.RUNNING
        runningSince = nowMillis
        return Output(phase, nowMillis + MIN_RUNNING_MILLIS, null)
    }

    companion object {
        const val MIN_RUNNING_MILLIS = 1000L
        const val RESULT_MILLIS = 1500L
    }
}

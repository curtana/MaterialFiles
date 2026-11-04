/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.filejob.FileJobState
import me.zhanghai.android.files.filejob.FinishedBatch
import org.junit.Assert.assertEquals
import org.junit.Test

class FileJobIndicatorTimelineTest {
    private val running = FileJobState(1, null)
    private val idle = FileJobState(0, null)

    private fun succeeded(seq: Int) = FileJobState(0, FinishedBatch(seq, true))

    private fun failed(seq: Int) = FileJobState(0, FinishedBatch(seq, false))

    private fun FileJobIndicatorTimeline.assertUpdate(
        nowMillis: Long,
        state: FileJobState,
        phase: FileJobIndicatorPhase,
        nextUpdateMillis: Long?,
        seqToConsume: Int? = null,
        isEnabled: Boolean = true
    ) {
        val output = update(nowMillis, isEnabled, state)
        assertEquals("phase at $nowMillis", phase, output.phase)
        assertEquals("next update at $nowMillis", nextUpdateMillis, output.nextUpdateMillis)
        assertEquals("seq to consume at $nowMillis", seqToConsume, output.seqToConsume)
        assertEquals(phase, this.phase)
    }

    // U-L01
    @Test
    fun shortBatch_runsOneSecondThenResult() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(200, succeeded(1), FileJobIndicatorPhase.RUNNING, 1000)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        timeline.assertUpdate(2500, idle, FileJobIndicatorPhase.HIDDEN, null)
    }

    // U-L02
    @Test
    fun longBatch_resultRightAway() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(5000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 6500, 1)
    }

    // U-L03
    @Test
    fun failedBatch_isFailed() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(3000, failed(1), FileJobIndicatorPhase.FAILED, 4500, 1)
    }

    // U-L04
    @Test
    fun minimumRunning_boundary() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(999, succeeded(1), FileJobIndicatorPhase.RUNNING, 1000)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
    }

    // U-L05
    @Test
    fun newStartDuringResult_runsAgain() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        timeline.assertUpdate(1500, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(1600, succeeded(2), FileJobIndicatorPhase.RUNNING, 2500)
        timeline.assertUpdate(2500, succeeded(2), FileJobIndicatorPhase.SUCCEEDED, 4000, 2)
    }

    // U-L06
    @Test
    fun newStartDuringMinimumRunning_keepsRunningSince() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(200, succeeded(1), FileJobIndicatorPhase.RUNNING, 1000)
        timeline.assertUpdate(500, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(3000, succeeded(2), FileJobIndicatorPhase.SUCCEEDED, 4500, 2)
    }

    // U-L07
    @Test
    fun hiddenWithResult_runsFirst() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(100, succeeded(3), FileJobIndicatorPhase.RUNNING, 1100)
        timeline.assertUpdate(1100, succeeded(3), FileJobIndicatorPhase.SUCCEEDED, 2600, 3)
    }

    // U-L08
    @Test
    fun reentrantUpdateAfterConsuming_isSame() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        timeline.assertUpdate(1000, idle, FileJobIndicatorPhase.SUCCEEDED, 2500)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500)
    }

    // U-L09
    @Test
    fun consumedByAnotherScreen_isHidden() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(200, succeeded(1), FileJobIndicatorPhase.RUNNING, 1000)
        timeline.assertUpdate(300, idle, FileJobIndicatorPhase.HIDDEN, null)
    }

    // U-L10
    @Test
    fun disabled_isHiddenAndConsumes() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(
            100, running, FileJobIndicatorPhase.HIDDEN, null, isEnabled = false
        )
        timeline.assertUpdate(
            200, succeeded(2), FileJobIndicatorPhase.HIDDEN, null, 2, isEnabled = false
        )
        timeline.assertUpdate(300, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(400, succeeded(3), FileJobIndicatorPhase.RUNNING, 1300)
    }

    // U-L11
    @Test
    fun stopWhileRunning_restartsMinimumRunning() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.stop()
        timeline.assertUpdate(10000, succeeded(1), FileJobIndicatorPhase.RUNNING, 11000)
        timeline.assertUpdate(11000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 12500, 1)
    }

    // U-L12
    @Test
    fun stopDuringResult_keepsResultUntil() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        timeline.stop()
        timeline.assertUpdate(2000, idle, FileJobIndicatorPhase.SUCCEEDED, 2500)

        val other = FileJobIndicatorTimeline()
        other.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        other.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        other.stop()
        other.assertUpdate(3000, idle, FileJobIndicatorPhase.HIDDEN, null)
    }

    // U-L13
    @Test
    fun stopDuringResult_newBatchFinished_runsAgain() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        timeline.stop()
        timeline.assertUpdate(2000, failed(2), FileJobIndicatorPhase.RUNNING, 3000)
        timeline.assertUpdate(3000, failed(2), FileJobIndicatorPhase.FAILED, 4500, 2)
    }

    // U-L14
    @Test
    fun idle_isHidden() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, idle, FileJobIndicatorPhase.HIDDEN, null)
    }

    // U-L15
    @Test
    fun runningTwice_keepsRunningSince() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(400, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(500, succeeded(1), FileJobIndicatorPhase.RUNNING, 1000)
    }

    // U-L16
    @Test
    fun hiddenWithShownSeq_staysHidden() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(1000, succeeded(1), FileJobIndicatorPhase.SUCCEEDED, 2500, 1)
        // Redelivered before being consumed.
        timeline.assertUpdate(2500, succeeded(1), FileJobIndicatorPhase.HIDDEN, null)
    }

    // U-L17
    @Test
    fun stopThenStillRunning_resetsRunningSince() {
        val timeline = FileJobIndicatorTimeline()
        timeline.assertUpdate(0, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.stop()
        // Both updates are at the same time 5000, still running and then finished.
        timeline.assertUpdate(5000, running, FileJobIndicatorPhase.RUNNING, null)
        timeline.assertUpdate(5000, succeeded(1), FileJobIndicatorPhase.RUNNING, 6000)
    }
}

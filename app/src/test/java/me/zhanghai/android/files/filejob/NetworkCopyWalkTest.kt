/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java.io.IOException
import java.io.InterruptedIOException
import java8.nio.file.FileSystemLoopException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCopyWalkTest {
    // Link depth and resume state (#55 B2, LLD-B 2.4).

    @Test
    fun noLinks_deepTree_neverExceeds() {
        val state = LinkWalkState()
        repeat(100) {
            val depth = state.depthOf(false)
            assertFalse(state.isExceeded(depth))
            state.enter(depth)
        }
        assertEquals(0, state.currentDepth)
    }

    @Test
    fun eightLinks_pass_ninthExceeds() {
        val state = LinkWalkState()
        repeat(MAX_LINK_DEPTH) {
            val depth = state.depthOf(true)
            assertFalse(state.isExceeded(depth))
            state.enter(depth)
        }
        assertEquals(8, state.currentDepth)
        val ninth = state.depthOf(true)
        assertEquals(9, ninth)
        assertTrue(state.isExceeded(ninth))
    }

    @Test
    fun exit_restoresParentDepth() {
        val state = LinkWalkState()
        repeat(3) { state.enter(state.depthOf(true)) }
        assertEquals(3, state.currentDepth)
        assertEquals(3, state.exit().depth)
        assertEquals(2, state.currentDepth)
        assertEquals(2, state.exit().depth)
        assertEquals(1, state.currentDepth)
        assertEquals(1, state.exit().depth)
        assertEquals(0, state.currentDepth)
    }

    @Test
    fun nonLinkDirectory_keepsDepth() {
        val state = LinkWalkState()
        repeat(2) { state.enter(state.depthOf(true)) }
        state.enter(state.depthOf(false))
        assertEquals(2, state.currentDepth)
    }

    @Test
    fun skippedSubtree_siblingSeesParentDepth() {
        val state = LinkWalkState()
        repeat(8) { state.enter(state.depthOf(true)) }
        // A link sibling exceeding the limit is skipped without entering it.
        assertTrue(state.isExceeded(state.depthOf(true)))
        assertEquals(8, state.depthOf(false))
        assertEquals(9, state.depthOf(true))
    }

    @Test
    fun rewalk_continuesFromBaseDepth() {
        val state = LinkWalkState(5)
        assertEquals(5, state.currentDepth)
        assertEquals(6, state.depthOf(true))
        val atLimit = LinkWalkState(8)
        assertEquals(9, atLimit.depthOf(true))
        assertTrue(atLimit.isExceeded(atLimit.depthOf(true)))
    }

    @Test
    fun exit_withoutEnter_throws() {
        assertThrows(IllegalStateException::class.java) { LinkWalkState().exit() }
    }

    @Test
    fun markVisited_resumeSkipsVisitedChildren() {
        val state = LinkWalkState()
        state.enter(1, setOf(path("/s/a"), path("/s/b")))
        assertFalse(state.markVisited(path("/s/a")))
        assertTrue(state.markVisited(path("/s/c")))
        assertFalse(state.markVisited(path("/s/c")))
        assertEquals(
            setOf(path("/s/a"), path("/s/b"), path("/s/c")), state.exit().visitedChildren
        )
    }

    @Test
    fun markVisited_withoutFrame_true() {
        val state = LinkWalkState()
        assertTrue(state.markVisited(path("/s")))
        assertTrue(state.markVisited(path("/s")))
    }

    // Retry loop (#55 B2, LLD-B 2.6).

    private fun failure(name: String, depth: Int? = null, visited: Set<String> = emptySet()) =
        ItemWalkFailure(IOException(name), depth, visited.map { path(it) }.toSet())

    @Test
    fun skip_doesNotRewalk() {
        var rewalkCount = 0
        val walked = retryFailedWalk(failure("e"), { false }) {
            ++rewalkCount
            null
        }
        assertFalse(walked)
        assertEquals(0, rewalkCount)
    }

    @Test
    fun consecutiveFailures_loopNotRecurse() {
        var askCount = 0
        var rewalkCount = 0
        var isRewalking = false
        val walked = retryFailedWalk(
            failure("e0"),
            {
                assertFalse(isRewalking)
                ++askCount
                true
            }
        ) {
            isRewalking = true
            ++rewalkCount
            val next = if (rewalkCount <= 3) failure("e$rewalkCount") else null
            isRewalking = false
            next
        }
        assertTrue(walked)
        // Three new failures, then a pass: the first failure and each new one are asked about.
        assertEquals(4, askCount)
        assertEquals(4, rewalkCount)
    }

    @Test
    fun rewalk_receivesPreviousFailure() {
        val first = failure("e1", 2, setOf("/s/d/a"))
        val second = failure("e2", 2, setOf("/s/d/a", "/s/d/b"))
        val asked = mutableListOf<ItemWalkFailure>()
        val rewalked = mutableListOf<ItemWalkFailure>()
        retryFailedWalk(first, {
            asked += it
            true
        }) {
            rewalked += it
            if (rewalked.size == 1) second else null
        }
        assertEquals(listOf(first, second), asked)
        assertEquals(listOf(first, second), rewalked)
    }

    @Test
    fun cancel_propagates() {
        var askCount = 0
        var rewalkCount = 0
        assertThrows(InterruptedIOException::class.java) {
            retryFailedWalk(failure("e0"), {
                if (++askCount == 2) {
                    throw InterruptedIOException()
                }
                true
            }) {
                ++rewalkCount
                failure("e$rewalkCount")
            }
        }
        assertEquals(1, rewalkCount)
    }

    // Error decisions and ActionAllInfo (#55 B2, LLD-B 2.7).

    private class Calls {
        var userAction = 0
        var dialog = 0
    }

    private fun decide(
        actionAllInfo: ActionAllInfo,
        canRetry: Boolean,
        calls: Calls,
        userActionResult: Boolean = false,
        result: ErrorResult = ErrorResult(FileJobErrorAction.NEGATIVE, false)
    ): CopyErrorDecision =
        decideCopyError(actionAllInfo, canRetry, {
            ++calls.userAction
            userActionResult
        }) {
            ++calls.dialog
            result
        }

    @Test
    fun skipAll_set_skipsWithoutDialog() {
        val calls = Calls()
        val decision = decide(ActionAllInfo(skipCopyMoveError = true), true, calls, true)
        assertEquals(CopyErrorDecision.SKIP, decision)
        assertEquals(0, calls.userAction)
        assertEquals(0, calls.dialog)
    }

    @Test
    fun negativeAll_setsSkipAll() {
        val actionAllInfo = ActionAllInfo()
        val decision = decide(
            actionAllInfo, true, Calls(), result = ErrorResult(FileJobErrorAction.NEGATIVE, true)
        )
        assertEquals(CopyErrorDecision.SKIP, decision)
        assertTrue(actionAllInfo.skipCopyMoveError)
    }

    @Test
    fun negative_withoutAll_keepsFlag() {
        val actionAllInfo = ActionAllInfo()
        val decision = decide(
            actionAllInfo, true, Calls(), result = ErrorResult(FileJobErrorAction.NEGATIVE, false)
        )
        assertEquals(CopyErrorDecision.SKIP, decision)
        assertFalse(actionAllInfo.skipCopyMoveError)
    }

    @Test
    fun actions_mapLikeCopyOrMove() {
        val expected = mapOf(
            FileJobErrorAction.POSITIVE to CopyErrorDecision.RETRY,
            FileJobErrorAction.CANCELED to CopyErrorDecision.SKIP,
            FileJobErrorAction.NEUTRAL to CopyErrorDecision.CANCEL
        )
        for ((action, decision) in expected) {
            val actionAllInfo = ActionAllInfo()
            assertEquals(
                decision, decide(actionAllInfo, true, Calls(), result = ErrorResult(action, true))
            )
            assertFalse(actionAllInfo.skipCopyMoveError)
        }
    }

    @Test
    fun userAction_retriesBeforeDialog() {
        val calls = Calls()
        assertEquals(CopyErrorDecision.RETRY, decide(ActionAllInfo(), true, calls, true))
        assertEquals(1, calls.userAction)
        assertEquals(0, calls.dialog)
    }

    @Test
    fun noRetry_noUserAction_noPositive() {
        val calls = Calls()
        assertEquals(CopyErrorDecision.SKIP, decide(ActionAllInfo(), false, calls, true))
        assertEquals(0, calls.userAction)
        assertEquals(1, calls.dialog)
        assertThrows(IllegalStateException::class.java) {
            decide(
                ActionAllInfo(), false, Calls(),
                result = ErrorResult(FileJobErrorAction.POSITIVE, false)
            )
        }
    }

    @Test
    fun walkSkipAll_appliesToLaterItems() {
        val actionAllInfo = ActionAllInfo()
        // (1) A walk error skipped for all.
        val firstCalls = Calls()
        var firstRewalkCount = 0
        val firstWalked = retryFailedWalk(failure("e1"), {
            decide(
                actionAllInfo, true, firstCalls,
                result = ErrorResult(FileJobErrorAction.NEGATIVE, true)
            ) == CopyErrorDecision.RETRY
        }) {
            ++firstRewalkCount
            null
        }
        assertFalse(firstWalked)
        assertEquals(1, firstCalls.dialog)
        assertEquals(0, firstRewalkCount)
        // (2) A later walk error of another item.
        val secondCalls = Calls()
        var secondRewalkCount = 0
        val secondWalked = retryFailedWalk(failure("e2"), {
            decide(actionAllInfo, true, secondCalls) == CopyErrorDecision.RETRY
        }) {
            ++secondRewalkCount
            null
        }
        assertFalse(secondWalked)
        assertEquals(0, secondCalls.dialog)
        assertEquals(0, secondRewalkCount)
        // (3) A later copy error.
        val thirdCalls = Calls()
        assertEquals(CopyErrorDecision.SKIP, decide(actionAllInfo, true, thirdCalls, true))
        assertEquals(0, thirdCalls.userAction)
        assertEquals(0, thirdCalls.dialog)
    }

    // Exception (#55 B2, LLD-B 2.3).

    @Test
    fun depthExceeded_isLoopAndNamesLimit() {
        val exception: Exception = LinkDepthExceededException("/a/l")
        assertTrue(exception is FileSystemLoopException)
        val string = exception.toString()
        assertTrue(string, string.contains("/a/l"))
        assertTrue(string, string.contains("8"))
    }

    // Progress totals (#55 B2, LLD-B 2.8).

    private fun transferInfo(fileCount: Int, size: Long): TransferInfo {
        val scanInfo = ScanInfo()
        repeat(fileCount) { scanInfo.incrementFileCount() }
        scanInfo.addToSize(size)
        return TransferInfo(scanInfo, null)
    }

    @Test
    fun transferInfo_countNeverExceedsTotal() {
        val transferInfo = transferInfo(1, 0)
        repeat(3) { transferInfo.incrementTransferredFileCount() }
        assertEquals(3, transferInfo.fileCount)
        assertEquals(3, transferInfo.transferredFileCount)
    }

    @Test
    fun transferInfo_sizeNeverExceedsTotal() {
        val transferInfo = transferInfo(1, 10)
        transferInfo.addToTransferredSize(25)
        assertEquals(25, transferInfo.size)
        val progress = FileJobProgress(
            FileJobProgressKind.SIZE, transferInfo.fileCount, transferInfo.transferredFileCount,
            transferInfo.size, transferInfo.transferredSize
        )
        assertEquals(100, progress.percent)
    }

    @Test
    fun transferInfo_withinTotals_unchanged() {
        val transferInfo = transferInfo(3, 30)
        transferInfo.incrementTransferredFileCount()
        transferInfo.addToTransferredSize(10)
        assertEquals(3, transferInfo.fileCount)
        assertEquals(30, transferInfo.size)
        assertEquals(1, transferInfo.transferredFileCount)
        assertEquals(10, transferInfo.transferredSize)
    }

    @Test
    fun transferInfo_skipNeverBelowTransferred() {
        val transferInfo = transferInfo(2, 0)
        repeat(2) { transferInfo.incrementTransferredFileCount() }
        transferInfo.skipFileIgnoringSize()
        assertEquals(2, transferInfo.fileCount)
    }

    // Resuming a walk start (#55 B2, LLD-B 2.5, 2.5a).

    private fun path(path: String) = TestPaths.path(path)

    @Test
    fun resumedStart_failingBeforeEntering_keepsResume() {
        val e1 = IOException("e1")
        val e2 = IOException("e2")
        val resume = ItemWalkFailure(e1, 3, setOf(path("/s/d/a")))
        val failure = startFailureBeforeEntering(e2, resume)
        assertSame(e2, failure.error)
        assertEquals(3, failure.enteredDepth)
        assertEquals(setOf(path("/s/d/a")), failure.visitedChildren)
        val state = LinkWalkState(2)
        assertTrue(state.enterIfResumedStart(path("/s/d"), path("/s/d"), failure))
        assertEquals(3, state.currentDepth)
        assertFalse(state.markVisited(path("/s/d/a")))
    }

    @Test
    fun enterIfResumedStart_onlyForEnteredStart() {
        val e = IOException("e")
        val entered = ItemWalkFailure(e, 3, emptySet())
        val notEntered = ItemWalkFailure(e, null, emptySet())
        val cases = listOf(
            Triple(path("/s/d"), path("/s/d"), null),
            Triple(path("/s/d"), path("/s/d"), notEntered),
            Triple(path("/s/d/x"), path("/s/d"), entered)
        )
        for ((directory, start, resume) in cases) {
            val state = LinkWalkState(2)
            assertFalse(state.enterIfResumedStart(directory, start, resume))
            assertEquals(2, state.currentDepth)
            assertThrows(IllegalStateException::class.java) { state.exit() }
        }
    }

    @Test
    fun isCountedOnSkip() {
        val notEntered = startFailureBeforeEntering(IOException("e"), null)
        assertNull(notEntered.enteredDepth)
        assertTrue(notEntered.isCountedOnSkip)
        val entered = ItemWalkFailure(IOException("e1"), 2, emptySet())
        assertFalse(entered.isCountedOnSkip)
        val resumed = startFailureBeforeEntering(IOException("e2"), entered)
        assertFalse(resumed.isCountedOnSkip)
    }
}

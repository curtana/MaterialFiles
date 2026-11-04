/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCopyOutcomeWatchTest {
    private var diedCount = 0
    private val watch = RemoteCopyOutcomeWatch { ++diedCount }

    // U-W01
    @Test
    fun completedThenDied_notCalled() {
        assertTrue(watch.onCompleted())
        watch.onRemoteDied()
        assertEquals(0, diedCount)
    }

    // U-W02
    @Test
    fun diedThenCompleted_calledOnce() {
        watch.onRemoteDied()
        assertFalse(watch.onCompleted())
        assertEquals(1, diedCount)
    }

    // U-W03
    @Test
    fun diedTwice_calledOnce() {
        watch.onRemoteDied()
        watch.onRemoteDied()
        assertEquals(1, diedCount)
    }
}

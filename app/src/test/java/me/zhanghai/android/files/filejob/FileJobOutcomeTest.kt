/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.nio.channels.ClosedByInterruptException

class FileJobOutcomeTest {
    // U-O01
    @Test
    fun normalEnd_succeeded() {
        assertEquals(FileJobOutcome.SUCCEEDED, FileJobOutcome.of(false, null, false))
    }

    // U-O02
    @Test
    fun skippedError_failed() {
        assertEquals(FileJobOutcome.FAILED, FileJobOutcome.of(true, null, false))
    }

    // U-O03
    @Test
    fun interrupted_withFlag_canceled() {
        assertEquals(
            FileJobOutcome.CANCELED, FileJobOutcome.of(false, InterruptedIOException(), true)
        )
    }

    // U-O04
    @Test
    fun interrupted_withoutFlag_failed() {
        assertEquals(
            FileJobOutcome.FAILED, FileJobOutcome.of(false, InterruptedIOException(), false)
        )
    }

    // U-O05
    @Test
    fun socketTimeout_followsFlag() {
        assertEquals(
            FileJobOutcome.FAILED, FileJobOutcome.of(false, SocketTimeoutException(), false)
        )
        assertEquals(
            FileJobOutcome.CANCELED, FileJobOutcome.of(false, SocketTimeoutException(), true)
        )
    }

    // U-O06
    @Test
    fun otherExceptions_withoutFlag_failed() {
        assertEquals(FileJobOutcome.FAILED, FileJobOutcome.of(false, IOException(), false))
        assertEquals(FileJobOutcome.FAILED, FileJobOutcome.of(false, RuntimeException(), false))
    }

    // U-O07
    @Test
    fun closedByInterrupt_withFlag_canceled() {
        assertEquals(
            FileJobOutcome.CANCELED, FileJobOutcome.of(false, ClosedByInterruptException(), true)
        )
    }

    // U-O08
    @Test
    fun normalEnd_withFlag_canceled() {
        assertEquals(FileJobOutcome.CANCELED, FileJobOutcome.of(false, null, true))
        assertEquals(FileJobOutcome.CANCELED, FileJobOutcome.of(true, null, true))
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.CopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.Closeable
import java.io.IOException

class CleanupFailureCopyOptionTest {
    // U-K01
    @Test
    fun listener_calledOnce() {
        var count = 0
        val option = CleanupFailureCopyOption { ++count }
        option.listener()
        option.listener()
        assertEquals(1, count)
    }

    // U-K02
    @Test
    fun listener_throwing_doesNotThrow() {
        val option = CleanupFailureCopyOption { throw RuntimeException() }
        option.listener()
    }

    // U-K03
    @Test
    fun listener_throwing_notCalledAgain() {
        var count = 0
        val option = CleanupFailureCopyOption {
            ++count
            throw RuntimeException()
        }
        option.listener()
        option.listener()
        assertEquals(1, count)
    }

    // U-K04
    @Test
    fun toCopyOptions_acceptsOption() {
        val options: Array<CopyOption> = arrayOf(CleanupFailureCopyOption {})
        assertNotNull(options.toCopyOptions().cleanupFailureListener)
        assertNull(emptyArray<CopyOption>().toCopyOptions().cleanupFailureListener)
    }

    // U-K05
    @Test
    fun roundTrip_calledOnce() {
        var count = 0
        val options: Array<CopyOption> = arrayOf(CleanupFailureCopyOption { ++count })
        val listener = options.toCopyOptions().toArray().toCopyOptions().cleanupFailureListener!!
        listener()
        listener()
        assertEquals(1, count)
    }

    // U-K06
    @Test
    fun toArray_withoutListener_noOption() {
        assertFalse(
            emptyArray<CopyOption>().toCopyOptions().toArray()
                .any { it is CleanupFailureCopyOption }
        )
    }

    // U-K07
    @Test
    fun closeReportingFailure_closeThrows_deleteRequested_reportsAndRethrows() {
        val exception = IOException()
        var count = 0
        val closeable = Closeable { throw exception }
        assertSame(
            exception,
            assertThrows(IOException::class.java) {
                closeable.closeReportingFailure({ true }) { ++count }
            }
        )
        assertEquals(1, count)
    }

    // U-K08
    @Test
    fun closeReportingFailure_closeThrows_deleteNotRequested_rethrowsOnly() {
        val exception = IOException()
        var count = 0
        val closeable = Closeable { throw exception }
        assertSame(
            exception,
            assertThrows(IOException::class.java) {
                closeable.closeReportingFailure({ false }) { ++count }
            }
        )
        assertEquals(0, count)
    }

    // U-K09
    @Test
    fun closeReportingFailure_closeSucceeds_notReported() {
        var count = 0
        Closeable {}.closeReportingFailure({ true }) { ++count }
        assertEquals(0, count)
    }
}

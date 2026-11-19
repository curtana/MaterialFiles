/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.AccessDeniedException
import java8.nio.file.NoSuchFileException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SourceCheckedReplaceTest {
    private class FakeBackend(
        private val openFailure: IOException? = null,
        private val deleteFailure: IOException? = null,
        private val writeFailure: IOException? = null,
        private val closeFailure: IOException? = null
    ) {
        val calls = mutableListOf<String>()

        fun open(): String {
            calls += "open"
            openFailure?.let { throw it }
            return "source"
        }

        fun close(source: String) {
            assertEquals("source", source)
            calls += "close"
            closeFailure?.let { throw it }
        }

        fun delete() {
            calls += "delete"
            deleteFailure?.let { throw it }
        }

        fun write(source: String): String {
            assertEquals("source", source)
            calls += "write"
            writeFailure?.let { throw it }
            return "result"
        }

        fun run(replace: Boolean): String =
            replaceAfterOpeningSource(::open, ::close, if (replace) ::delete else null, ::write)
    }

    // U-C01
    @Test
    fun openFails_noDeleteNoWriteNoClose() {
        val exception = AccessDeniedException("s")
        val backend = FakeBackend(openFailure = exception)
        assertSame(exception, assertThrows(IOException::class.java) { backend.run(true) })
        assertEquals(listOf("open"), backend.calls)
    }

    // U-C02
    @Test
    fun replace_opensThenDeletesThenWritesThenCloses() {
        val backend = FakeBackend()
        assertEquals("result", backend.run(true))
        assertEquals(listOf("open", "delete", "write", "close"), backend.calls)
    }

    // U-C03
    @Test
    fun deleteNoSuchFile_ignored() {
        val backend = FakeBackend(deleteFailure = NoSuchFileException("t"))
        assertEquals("result", backend.run(true))
        assertEquals(listOf("open", "delete", "write", "close"), backend.calls)
    }

    // U-C04
    @Test
    fun noReplace_noDelete() {
        val backend = FakeBackend()
        assertEquals("result", backend.run(false))
        assertEquals(listOf("open", "write", "close"), backend.calls)
    }

    // U-C05
    @Test
    fun deleteFails_noWrite_sourceClosed() {
        val exception = AccessDeniedException("t")
        val backend = FakeBackend(deleteFailure = exception)
        assertSame(exception, assertThrows(IOException::class.java) { backend.run(true) })
        assertEquals(listOf("open", "delete", "close"), backend.calls)
    }

    // U-C06
    @Test
    fun writeFails_sourceClosed_closeFailureSuppressed() {
        val writeException = IOException("write")
        val closeException = IOException("close")
        val backend = FakeBackend(writeFailure = writeException, closeFailure = closeException)
        val exception = assertThrows(IOException::class.java) { backend.run(true) }
        assertSame(writeException, exception)
        assertArrayEquals(arrayOf<Throwable>(closeException), exception.suppressed)
        assertEquals(listOf("open", "delete", "write", "close"), backend.calls)
    }

    // U-C07
    @Test
    fun closeFailsAfterWrite_thrown() {
        val closeException = IOException("close")
        val backend = FakeBackend(closeFailure = closeException)
        assertSame(closeException, assertThrows(IOException::class.java) { backend.run(true) })
        assertEquals(listOf("open", "delete", "write", "close"), backend.calls)
    }

    // U-C08
    @Test
    fun check_failsBeforeDelete() {
        val exception = AccessDeniedException("s")
        val calls = mutableListOf<String>()
        assertSame(
            exception,
            assertThrows(IOException::class.java) {
                replaceAfterCheckingSource(
                    { calls += "check"; throw exception },
                    { calls += "delete" },
                    { calls += "write" }
                )
            }
        )
        assertEquals(listOf("check"), calls)
    }

    // U-C09
    @Test
    fun check_thenDeleteThenWrite() {
        val calls = mutableListOf<String>()
        val result = replaceAfterCheckingSource(
            { calls += "check" },
            { calls += "delete" },
            { calls += "write"; "result" }
        )
        assertEquals("result", result)
        assertEquals(listOf("check", "delete", "write"), calls)
    }
}

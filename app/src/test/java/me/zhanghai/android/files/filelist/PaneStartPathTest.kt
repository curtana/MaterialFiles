/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.FileSystemNotFoundException
import java8.nio.file.InvalidPathException
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderNotFoundException
import me.zhanghai.android.files.filejob.TestPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class PaneStartPathTest {
    private val restored = TestPaths.path("/storage/emulated/0/Download")
    private val intent = TestPaths.path("/storage/emulated/0/Music")
    private val default = TestPaths.path("/storage/emulated/0")

    private var defaultPathCalls = 0

    private fun defaultPath(): Path {
        ++defaultPathCalls
        return default
    }

    private var getPathCalls = 0

    private fun failingGetPath(): (URI) -> Path = {
        ++getPathCalls
        throw AssertionError("getPath must not be called")
    }

    // T-R01 (REQ-11-01): the restored path is used and the default directory isn't read.
    @Test
    fun resolvePaneStartPath_restoredOnly_returnsRestored() {
        assertSame(restored, resolvePaneStartPath(restored, null, ::defaultPath))
        assertEquals(0, defaultPathCalls)
    }

    // T-R02 (REQ-11-02): the restored path takes precedence over the intent path.
    @Test
    fun resolvePaneStartPath_restoredAndIntent_returnsRestored() {
        assertSame(restored, resolvePaneStartPath(restored, intent, ::defaultPath))
        assertEquals(0, defaultPathCalls)
    }

    // T-R03 (REQ-11-03): without a restored path, the intent path is used as before.
    @Test
    fun resolvePaneStartPath_intentOnly_returnsIntent() {
        assertSame(intent, resolvePaneStartPath(null, intent, ::defaultPath))
        assertEquals(0, defaultPathCalls)
    }

    // T-R04 (REQ-11-03, 06): without either, the default directory is read exactly once.
    @Test
    fun resolvePaneStartPath_neither_returnsDefaultOnce() {
        assertSame(default, resolvePaneStartPath(null, null, ::defaultPath))
        assertEquals(1, defaultPathCalls)
    }

    // T-D01: nothing saved.
    @Test
    fun decodePanePath_null_returnsNullWithoutGetPath() {
        assertNull(decodePanePath(null, failingGetPath()))
        assertEquals(0, getPathCalls)
    }

    // T-D02: a valid string is parsed into the URI passed to getPath.
    @Test
    fun decodePanePath_valid_returnsGetPathResult() {
        val uriString = "file:///storage/emulated/0/Download"
        var receivedUri: URI? = null
        val path = decodePanePath(uriString) {
            receivedUri = it
            restored
        }
        assertSame(restored, path)
        assertEquals(URI(uriString), receivedUri)
    }

    // T-D03: URI syntax errors return null without calling getPath.
    @Test
    fun decodePanePath_syntaxError_returnsNullWithoutGetPath() {
        assertNull(decodePanePath("%zz", failingGetPath()))
        assertNull(decodePanePath("a b", failingGetPath()))
        assertEquals(0, getPathCalls)
    }

    // T-D04: an empty string has no scheme.
    @Test
    fun decodePanePath_empty_returnsNull() {
        assertNull(decodePanePath(""))
    }

    // T-D05: a relative path has no scheme.
    @Test
    fun decodePanePath_relative_returnsNull() {
        assertNull(decodePanePath("Download/a"))
    }

    // T-D06: a scheme without an installed provider, e.g. after the provider is gone.
    @Test
    fun decodePanePath_unknownScheme_returnsNull() {
        assertNull(decodePanePath("mf-test-unknown:///a"))
    }

    // T-D07: pins what the backport Paths.get(URI) throws for a scheme without a provider.
    @Test
    fun pathsGet_unknownScheme_throwsFileSystemNotFoundException() {
        assertThrows(FileSystemNotFoundException::class.java) {
            Paths.get(URI("mf-test-unknown:///a"))
        }
    }

    // T-D08: each exception that a provider may throw for a bad URI returns null.
    @Test
    fun decodePanePath_caughtExceptions_returnNull() {
        val exceptions = listOf(
            IllegalArgumentException("Missing scheme"),
            InvalidPathException("a", "Invalid path"),
            FileSystemNotFoundException("Provider not installed"),
            ProviderNotFoundException("Provider not found")
        )
        for (exception in exceptions) {
            assertNull(
                exception.javaClass.name, decodePanePath("test:///a") { throw exception }
            )
        }
    }

    // T-D09: errors aren't caught.
    @Test
    fun decodePanePath_error_propagates() {
        assertThrows(AssertionError::class.java) {
            decodePanePath("test:///a") { throw AssertionError() }
        }
    }

    // T-D10: only the exceptions above are caught.
    @Test
    fun decodePanePath_otherException_propagates() {
        assertThrows(IllegalStateException::class.java) {
            decodePanePath("test:///a") { throw IllegalStateException() }
        }
    }

    // T-E01 (REQ-11-01): a path survives encoding and decoding, including spaces and non-ASCII.
    @Test
    fun encodePanePath_roundTrip_returnsSamePath() {
        val path = TestPaths.path("/storage/emulated/0/Download/a b/한글")
        val uriString = encodePanePath(path)
        assertEquals(path.toUri().toString(), uriString)
        assertTrue(uriString, uriString.startsWith("test:"))
        assertEquals(path, decodePanePath(uriString) { TestPaths.path(it.path) })
    }
}

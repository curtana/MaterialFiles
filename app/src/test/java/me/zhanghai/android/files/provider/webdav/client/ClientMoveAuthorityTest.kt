/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.webdav.client

import at.bitfire.dav4jvm.exception.DavException
import java8.nio.file.FileSystemException
import me.zhanghai.android.files.provider.webdav.toFileSystemException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// A move between two WebDAV servers must fail as a DavException so that WebDavCopyMove.move() falls
// back to copy (#95).
class ClientMoveAuthorityTest {
    private class FakePath(host: String) : Client.Path {
        override val authority: Authority = Authority(Protocol.DAVS, host, 443, "")
        override val url: HttpUrl = "https://$host/x".toHttpUrl()

        override fun resolve(other: String): Client.Path = throw UnsupportedOperationException()
    }

    private val source = FakePath("a.example")
    private val target = FakePath("b.example")

    // U-95-10
    @Test
    fun differentAuthority_throwsDavException() {
        val e = assertThrows(DavException::class.java) { Client.move(source, target, false) }
        assertTrue(e is DavIOException)
        assertEquals(MESSAGE, e.message)
    }

    // U-95-11
    @Test
    fun differentAuthority_toFileSystemException_keepsMessageAndCause() {
        val e = assertThrows(DavException::class.java) { Client.move(source, target, false) }
        val fileSystemException = e.toFileSystemException("a", "b")
        assertEquals(FileSystemException::class.java, fileSystemException.javaClass)
        assertEquals("a", fileSystemException.file)
        assertEquals("b", fileSystemException.otherFile)
        assertEquals(MESSAGE, fileSystemException.reason)
        val cause = fileSystemException.cause
        assertTrue(cause is IOException)
        assertEquals(MESSAGE, cause!!.message)
        assertSame(e.cause, cause)
    }

    companion object {
        private const val MESSAGE = "Paths aren't on the same authority"
    }
}

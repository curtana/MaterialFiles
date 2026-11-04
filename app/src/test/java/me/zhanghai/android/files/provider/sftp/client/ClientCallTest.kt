package me.zhanghai.android.files.provider.sftp.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ClientCallTest {
    private class FakeClient

    private val client = FakeClient()
    private val evicted = mutableListOf<FakeClient>()

    private inline fun <R> call(block: (FakeClient) -> R): R =
        client.callOrEvict({ evicted += it }, block)

    private inline fun expectClientException(block: () -> Unit): ClientException {
        try {
            block()
        } catch (e: ClientException) {
            return e
        }
        fail("ClientException expected")
        throw AssertionError()
    }

    // R-A2-01
    @Test
    fun runtimeException_evictsClient_andIsRethrownAsClientException() {
        val cause = ArrayIndexOutOfBoundsException("length=16384; index=-5")
        val e = expectClientException { call<Unit> { throw cause } }
        assertSame(cause, e.cause)
        assertEquals(listOf(client), evicted)
        // The providers turn it into an IOException.
        val ioException: IOException = e.toFileSystemException("/file")
        assertSame(e, ioException.cause)
    }

    // R-A2-02
    @Test
    fun ioException_doesNotEvictClient() {
        val cause = IOException("No such file")
        val e = expectClientException { call<Unit> { throw cause } }
        assertSame(cause, e.cause)
        assertEquals(emptyList<FakeClient>(), evicted)
    }

    // R-A2-03
    @Test
    fun success_returnsResult_andDoesNotEvictClient() {
        assertEquals("result", call { "result" })
        assertEquals(emptyList<FakeClient>(), evicted)
    }
}

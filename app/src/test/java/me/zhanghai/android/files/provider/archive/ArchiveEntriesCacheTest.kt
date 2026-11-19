/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.ClosedFileSystemException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ArchiveEntriesCacheTest {
    private val lock = Any()
    private val cache = ArchiveEntriesCache<String>(lock)
    private val readCount = AtomicInteger()

    private val read: () -> String = { "v${readCount.incrementAndGet()}" }

    private fun get(): String = synchronized(lock) { cache.getLocked(read) }

    private class BlockedRead {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val result = AtomicReference<String>()
    }

    // Starts a lookup on another thread holding the lock, whose read blocks until released.
    private fun startBlockedRead(blockedRead: BlockedRead): Thread =
        thread {
            val value = synchronized(lock) {
                cache.getLocked {
                    blockedRead.started.countDown()
                    blockedRead.release.await(5, TimeUnit.SECONDS)
                    read()
                }
            }
            blockedRead.result.set(value)
        }

    private fun startInvalidate(done: CountDownLatch): Thread =
        thread {
            cache.invalidate()
            done.countDown()
        }

    // T-83-01 (REQ-83-01): invalidation does not wait for a read in progress.
    @Test
    fun invalidate_whileReadBlocked_returnsWithoutWaiting() {
        val blockedRead = BlockedRead()
        val invalidated = CountDownLatch(1)
        var readThread: Thread? = null
        var invalidateThread: Thread? = null
        try {
            readThread = startBlockedRead(blockedRead)
            assertTrue(blockedRead.started.await(2, TimeUnit.SECONDS))
            invalidateThread = startInvalidate(invalidated)
            assertTrue(invalidated.await(1, TimeUnit.SECONDS))
            assertTrue(readThread.isAlive)
        } finally {
            blockedRead.release.countDown()
            readThread?.join(5000)
            invalidateThread?.join(5000)
        }
    }

    // T-83-02 (REQ-83-02, HLD R1): an invalidation during a read makes the next lookup re-read,
    // while the lookup that started the read returns its result.
    @Test
    fun invalidate_duringRead_nextGetReloads() {
        val blockedRead = BlockedRead()
        val invalidated = CountDownLatch(1)
        var readThread: Thread? = null
        var invalidateThread: Thread? = null
        try {
            readThread = startBlockedRead(blockedRead)
            assertTrue(blockedRead.started.await(2, TimeUnit.SECONDS))
            invalidateThread = startInvalidate(invalidated)
            assertTrue(invalidated.await(1, TimeUnit.SECONDS))
            blockedRead.release.countDown()
            readThread.join(5000)
            assertEquals("v1", blockedRead.result.get())
            assertEquals("v2", get())
            assertEquals(2, readCount.get())
        } finally {
            blockedRead.release.countDown()
            readThread?.join(5000)
            invalidateThread?.join(5000)
        }
    }

    // T-83-03 (REQ-83-02): without invalidation the entries are read once.
    @Test
    fun get_withoutInvalidate_readsOnce() {
        assertEquals("v1", get())
        assertEquals("v1", get())
        assertEquals(1, readCount.get())
    }

    // T-83-04 (REQ-83-02): an invalidation with no read in progress makes the next lookup re-read.
    @Test
    fun invalidate_afterRead_nextGetReloads() {
        assertEquals("v1", get())
        cache.invalidate()
        assertEquals("v2", get())
        assertEquals("v2", get())
        assertEquals(2, readCount.get())
    }

    // T-83-05 (HLD 3.1): invalidations before a read are covered by that read.
    @Test
    fun invalidate_beforeFirstGet_readsOnce() {
        cache.invalidate()
        cache.invalidate()
        assertEquals("v1", get())
        assertEquals("v1", get())
        assertEquals(1, readCount.get())
    }

    // T-83-06 (REQ-83-03): a closed cache throws ClosedFileSystemException.
    @Test
    fun closed_invalidateAndGetThrow() {
        assertEquals("v1", get())
        assertTrue(synchronized(lock) { cache.closeLocked() })
        assertFalse(synchronized(lock) { cache.closeLocked() })
        assertFalse(cache.isOpen)
        assertThrows(ClosedFileSystemException::class.java) { cache.invalidate() }
        assertThrows(ClosedFileSystemException::class.java) { get() }
        assertEquals(1, readCount.get())
    }

    // T-83-07 (REQ-83-04): a failing read propagates and the next lookup retries.
    @Test
    fun readThrows_propagatesAndNextGetRetries() {
        val exception = IOException("Read failed")
        val failNext = AtomicInteger(1)
        val failingRead: () -> String = {
            val count = readCount.incrementAndGet()
            if (failNext.getAndSet(0) == 1) {
                throw exception
            }
            "v$count"
        }
        fun failingGet(): String = synchronized(lock) { cache.getLocked(failingRead) }

        // (a) First read fails.
        assertSame(exception, assertThrows(IOException::class.java) { failingGet() })
        assertEquals("v2", failingGet())
        assertEquals(2, readCount.get())

        // (b) A read after an invalidation fails while a previous value exists.
        cache.invalidate()
        failNext.set(1)
        assertSame(exception, assertThrows(IOException::class.java) { failingGet() })
        assertEquals("v4", failingGet())
        assertEquals(4, readCount.get())
    }

    // T-83-08 (HLD H1): the locked functions require the caller to hold the lock.
    @Test
    fun lockedFunctions_withoutLock_throwIllegalState() {
        val getException = assertThrows(IllegalStateException::class.java) {
            cache.getLocked(read)
        }
        assertFalse(getException is ClosedFileSystemException)
        val closeException = assertThrows(IllegalStateException::class.java) {
            cache.closeLocked()
        }
        assertFalse(closeException is ClosedFileSystemException)
        assertTrue(cache.isOpen)
        assertEquals(0, readCount.get())
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.ClosedFileSystemException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ArchivePasswordListTest {
    private val lock = Any()
    private val cache = ArchiveEntriesCache<String>(lock)
    private val list = ArchivePasswordList(lock) { cache.isOpen }

    private fun get(): List<String> = synchronized(lock) { list.getLocked() }

    // T-86-01 (REQ-86-01): adding does not wait for another thread holding the lock.
    @Test
    fun add_whileLockHeld_returnsWithoutWaiting() {
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val added = CountDownLatch(1)
        var holdThread: Thread? = null
        var addThread: Thread? = null
        try {
            holdThread = thread {
                synchronized(lock) {
                    held.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            assertTrue(held.await(2, TimeUnit.SECONDS))
            addThread = thread {
                list.add("p1")
                added.countDown()
            }
            assertTrue(added.await(1, TimeUnit.SECONDS))
            assertTrue(holdThread.isAlive)
        } finally {
            release.countDown()
            holdThread?.join(5000)
            addThread?.join(5000)
        }
        assertEquals(listOf("p1"), get())
    }

    // T-86-02 (REQ-86-02, HLD R1): added passwords are read in order with duplicates, and a list
    // read earlier doesn't change.
    @Test
    fun add_thenGetLocked_returnsPasswordsInOrder() {
        val initial = get()
        assertEquals(listOf<String>(), initial)
        list.add("a")
        list.add("b")
        list.add("a")
        assertEquals(listOf("a", "b", "a"), get())
        assertEquals(listOf<String>(), initial)
    }

    // T-86-03 (REQ-86-03): adding to a closed file system throws and keeps the list.
    @Test
    fun add_whenClosed_throwsAndKeepsList() {
        list.add("a")
        synchronized(lock) { cache.closeLocked() }
        assertThrows(ClosedFileSystemException::class.java) { list.add("b") }
        assertEquals(listOf("a"), get())
    }

    // T-86-04 (REQ-86-04): concurrent additions lose nothing.
    @Test
    fun add_concurrently_losesNothing() {
        val threadCount = 8
        val countPerThread = 1000
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val threads = (0 until threadCount).map { i ->
            thread {
                if (start.await(5, TimeUnit.SECONDS)) {
                    for (j in 0 until countPerThread) {
                        list.add("t$i-$j")
                    }
                }
                done.countDown()
            }
        }
        try {
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
        } finally {
            start.countDown()
            threads.forEach { it.join(5000) }
        }
        val passwords = get()
        assertEquals(threadCount * countPerThread, passwords.size)
        val expected = (0 until threadCount).flatMap { i ->
            (0 until countPerThread).map { j -> "t$i-$j" }
        }
        assertEquals(expected.toSet(), passwords.toSet())
        for (i in 0 until threadCount) {
            assertEquals(
                (0 until countPerThread).map { j -> "t$i-$j" },
                passwords.filter { it.startsWith("t$i-") }
            )
        }
    }

    // T-86-05 (HLD D3): reading without the lock is a programming error, not a closed file system.
    @Test
    fun getLocked_withoutLock_throwsIllegalState() {
        val e = assertThrows(IllegalStateException::class.java) { list.getLocked() }
        assertFalse(e is ClosedFileSystemException)
    }
}

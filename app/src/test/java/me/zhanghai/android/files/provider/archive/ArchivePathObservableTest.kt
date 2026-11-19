/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import me.zhanghai.android.files.provider.common.PathObservable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ArchivePathObservableTest {
    private class FakeArchiveFileObservable : PathObservable {
        val observers = Collections.synchronizedList(mutableListOf<() -> Unit>())
        var removeCount = 0
        var isClosed = false

        override fun addObserver(observer: () -> Unit) {
            observers += observer
        }

        override fun removeObserver(observer: () -> Unit) {
            ++removeCount
            observers -= observer
        }

        override fun close() {
            isClosed = true
        }

        fun notifyObservers() {
            observers.toList().forEach { it() }
        }
    }

    private val immediateExecutor = Executor { it.run() }

    // T-H2-01 (REQ-69-01, 02): a change of the archive file invalidates before notifying.
    @Test
    fun notification_invalidatesBeforeObserver() {
        val source = FakeArchiveFileObservable()
        val events = mutableListOf<String>()
        val observable = ArchivePathObservable(source, { events += "invalidate" }, immediateExecutor)
        observable.addObserver { events += "observer" }
        source.notifyObservers()
        assertEquals(listOf("invalidate", "observer"), events)
    }

    // T-H2-02 (REQ-69-02): a failing invalidation (e.g. a closed file system) still notifies.
    @Test
    fun invalidateThrows_observerStillCalled() {
        val source = FakeArchiveFileObservable()
        val observerCount = AtomicInteger()
        val observable = ArchivePathObservable(
            source, { throw IllegalStateException("Closed") }, immediateExecutor
        )
        observable.addObserver { observerCount.incrementAndGet() }
        source.notifyObservers()
        assertEquals(1, observerCount.get())
    }

    // T-H2-03 (REQ-69-01): removing an observer removes its wrapper from the archive file.
    @Test
    fun removeObserver_removesWrapperFromSource() {
        val source = FakeArchiveFileObservable()
        val observable = ArchivePathObservable(source, {}, immediateExecutor)
        val observer = {}
        observable.addObserver(observer)
        assertEquals(1, source.observers.size)
        observable.removeObserver(observer)
        assertTrue(source.observers.isEmpty())
        assertEquals(1, source.removeCount)
        observable.removeObserver {}
        assertEquals(1, source.removeCount)
    }

    // T-H2-04 (REQ-69-01): closing closes the archive file observable.
    @Test
    fun close_closesSource() {
        val source = FakeArchiveFileObservable()
        val observable = ArchivePathObservable(source, {}, immediateExecutor)
        observable.addObserver {}
        observable.close()
        assertTrue(source.isClosed)
    }

    // T-H2-05: adding the same observer again replaces its wrapper.
    @Test
    fun addObserverTwice_replacesWrapper() {
        val source = FakeArchiveFileObservable()
        val observerCount = AtomicInteger()
        val observable = ArchivePathObservable(source, {}, immediateExecutor)
        val observer: () -> Unit = { observerCount.incrementAndGet() }
        observable.addObserver(observer)
        observable.addObserver(observer)
        assertEquals(1, source.observers.size)
        source.notifyObservers()
        assertEquals(1, observerCount.get())
    }

    // T-H2-06 (HLD R6, D6): an invalidation waiting for the archive lock doesn't block the caller,
    // i.e. the shared Notifier thread of AbstractPathObservable.
    @Test
    fun blockedInvalidate_wrapperReturnsImmediately() {
        val source = FakeArchiveFileObservable()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val observed = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val observable = ArchivePathObservable(
                source, {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }, executor
            )
            observable.addObserver { observed.countDown() }
            val startNanos = System.nanoTime()
            source.notifyObservers()
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            assertTrue("Wrapper took $elapsedMillis ms", elapsedMillis < 1000)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertEquals(1L, observed.count)
            release.countDown()
            assertTrue(observed.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}

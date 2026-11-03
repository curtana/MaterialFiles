package me.zhanghai.android.files.filelist

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FileListLoaderTest {
    private lateinit var backgroundExecutor: ExecutorService

    // The test thread plays the main thread and runs these.
    private val mainQueue = LinkedBlockingQueue<Runnable>()

    // Each load waits for its own gate; a load that is interrupted while waiting records it and
    // still returns, like a listing that fails with an interrupted exception.
    private val gates = mutableListOf<CountDownLatch>()
    private val loadCount = AtomicInteger()
    private val interruptedCount = AtomicInteger()

    private val started = mutableListOf<Boolean>()
    private val finished = mutableListOf<String>()

    private lateinit var loader: FileListLoader<String>

    @Before
    fun setUp() {
        backgroundExecutor = Executors.newCachedThreadPool()
        repeat(10) { gates += CountDownLatch(1) }
        loader = FileListLoader(
            backgroundExecutor, { mainQueue.put(it) }, ::load, { started += it },
            { finished += it }, { loader.requestReloadOnChange() }
        )
    }

    @After
    fun tearDown() {
        backgroundExecutor.shutdownNow()
    }

    private fun load(): String {
        val index = loadCount.getAndIncrement()
        return try {
            gates[index].await()
            "load$index"
        } catch (e: InterruptedException) {
            interruptedCount.incrementAndGet()
            "interrupted$index"
        }
    }

    private fun finishLoad(index: Int) {
        gates[index].countDown()
    }

    // Runs one posted main thread task, waiting for it to be posted.
    private fun runMainTask() {
        val task = mainQueue.poll(5, TimeUnit.SECONDS)
            ?: throw AssertionError("No main thread task was posted")
        task.run()
    }

    private fun waitForLoadCount(count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (loadCount.get() < count) {
            if (System.nanoTime() > deadline) {
                throw AssertionError("Load count is ${loadCount.get()} instead of $count")
            }
            Thread.sleep(1)
        }
    }

    private fun assertNoMainTask() {
        // Long enough for a wrongly interrupted load to post its result.
        Thread.sleep(100)
        assertTrue("Unexpected main thread task", mainQueue.isEmpty())
    }

    // R-B1-01
    @Test
    fun changeDuringLoad_doesNotCancelLoad_andReloadsOnceAfter() {
        loader.reload()
        waitForLoadCount(1)
        loader.requestReloadOnChange()
        assertNoMainTask()
        assertEquals(0, interruptedCount.get())
        assertEquals(1, loadCount.get())

        finishLoad(0)
        runMainTask()
        assertEquals(listOf("load0"), finished)
        // The pending change starts exactly one more load, as a load for a change.
        assertEquals(listOf(false, true), started)
        waitForLoadCount(2)
        finishLoad(1)
        runMainTask()
        assertEquals(listOf("load0", "load1"), finished)
        assertNoMainTask()
        assertEquals(2, loadCount.get())
        assertFalse(loader.isLoading)
    }

    // R-B1-02
    @Test
    fun manyChangesDuringLoad_coalesceIntoOneReload() {
        loader.reload()
        waitForLoadCount(1)
        repeat(5) { loader.requestReloadOnChange() }
        finishLoad(0)
        runMainTask()
        waitForLoadCount(2)
        finishLoad(1)
        runMainTask()
        assertNoMainTask()
        assertEquals(listOf("load0", "load1"), finished)
        assertEquals(2, loadCount.get())
        assertEquals(0, interruptedCount.get())
    }

    // R-B1-03
    @Test
    fun changeWhenIdle_loadsNow() {
        loader.requestReloadOnChange()
        assertEquals(listOf(true), started)
        waitForLoadCount(1)
        finishLoad(0)
        runMainTask()
        assertEquals(listOf("load0"), finished)
        assertNoMainTask()
    }

    // R-B2-01
    @Test
    fun explicitReloadDuringLoad_cancelsIt_andSupersededLoadPostsNothing() {
        loader.reload()
        waitForLoadCount(1)
        loader.reload()
        waitForLoadCount(2)
        finishLoad(1)
        // The first load was interrupted and returned, and the second one finished; both post,
        // but only the second one is delivered.
        runMainTask()
        runMainTask()
        assertEquals(1, interruptedCount.get())
        assertEquals(listOf("load1"), finished)
        assertNoMainTask()
    }

    // R-B2-02
    @Test
    fun close_cancelsLoad_andPostsNothing() {
        loader.reload()
        waitForLoadCount(1)
        loader.requestReloadOnChange()
        loader.close()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (interruptedCount.get() == 0) {
            if (System.nanoTime() > deadline) {
                throw AssertionError("The load wasn't cancelled")
            }
            Thread.sleep(1)
        }
        // Run whatever the cancelled load may have posted.
        Thread.sleep(100)
        while (mainQueue.isNotEmpty()) {
            mainQueue.poll()?.run()
        }
        assertEquals(emptyList<String>(), finished)
        loader.requestReloadOnChange()
        loader.reload()
        assertNoMainTask()
        assertEquals(1, loadCount.get())
    }
}

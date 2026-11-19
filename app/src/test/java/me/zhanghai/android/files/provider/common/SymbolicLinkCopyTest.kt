/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.AccessDeniedException
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileSystemException
import java8.nio.file.NoSuchFileException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SymbolicLinkCopyTest {
    private class FakeBackend(
        createFailures: List<IOException?>,
        deleteFailures: List<IOException?> = emptyList()
    ) {
        val calls = mutableListOf<String>()
        private val createFailures = ArrayDeque(createFailures)
        private val deleteFailures = ArrayDeque(deleteFailures)

        fun createLink() {
            calls += "create"
            createFailures.removeFirstOrNull()?.let { throw it }
        }

        fun deleteExisting() {
            calls += "delete"
            deleteFailures.removeFirstOrNull()?.let { throw it }
        }

        fun run(replaceExisting: Boolean) {
            createSymbolicLinkForCopy(replaceExisting, ::createLink, ::deleteExisting)
        }
    }

    // ⓪
    @Test
    fun noExisting_createdOnce() {
        val backend = FakeBackend(listOf(null))
        backend.run(true)
        assertEquals(listOf("create"), backend.calls)
    }

    // ①
    @Test
    fun existing_replace_deletesAndRecreates() {
        val backend = FakeBackend(listOf(FileAlreadyExistsException("t"), null), listOf(null))
        backend.run(true)
        assertEquals(listOf("create", "delete", "create"), backend.calls)
    }

    // ②
    @Test
    fun existing_replace_recreateFails_throwsWithoutRetry() {
        val first = FileAlreadyExistsException("t")
        val second = FileSystemException("x")
        val backend = FakeBackend(listOf(first, second), listOf(null))
        val thrown = assertThrows(IOException::class.java) { backend.run(true) }
        assertSame(second, thrown)
        assertArrayEquals(arrayOf<Throwable>(first), thrown.suppressed)
        assertEquals(listOf("create", "delete", "create"), backend.calls)
    }

    // ③
    @Test
    fun existing_replace_deleteNoSuchFile_ignored() {
        val backend = FakeBackend(
            listOf(FileAlreadyExistsException("t"), null), listOf(NoSuchFileException("t"))
        )
        backend.run(true)
        assertEquals(listOf("create", "delete", "create"), backend.calls)
    }

    // ④
    @Test
    fun existing_noReplace_throwsOriginal() {
        val first = FileAlreadyExistsException("t")
        val backend = FakeBackend(listOf(first))
        val thrown = assertThrows(IOException::class.java) { backend.run(false) }
        assertSame(first, thrown)
        assertEquals(listOf("create"), backend.calls)
    }

    // ⑤
    @Test
    fun existing_replace_deleteFails_throwsWithoutRecreate() {
        val first = FileAlreadyExistsException("t")
        val deleteFailure = AccessDeniedException("t")
        val backend = FakeBackend(listOf(first), listOf(deleteFailure))
        val thrown = assertThrows(IOException::class.java) { backend.run(true) }
        assertSame(deleteFailure, thrown)
        assertArrayEquals(arrayOf<Throwable>(first), thrown.suppressed)
        assertEquals(listOf("create", "delete"), backend.calls)
    }

    // ⑥
    @Test
    fun otherFailure_replace_notDeleted() {
        val first = FileSystemException("t")
        val backend = FakeBackend(listOf(first))
        val thrown = assertThrows(IOException::class.java) { backend.run(true) }
        assertSame(first, thrown)
        assertEquals(listOf("create"), backend.calls)
    }

    // ⑦
    @Test
    fun existing_replace_recreateAlreadyExists_throwsWithoutRetry() {
        val first = FileAlreadyExistsException("t")
        val second = FileAlreadyExistsException("t")
        val backend = FakeBackend(listOf(first, second), listOf(null))
        val thrown = assertThrows(IOException::class.java) { backend.run(true) }
        assertSame(second, thrown)
        assertArrayEquals(arrayOf<Throwable>(first), thrown.suppressed)
        assertEquals(listOf("create", "delete", "create"), backend.calls)
    }
}

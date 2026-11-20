/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.AccessDeniedException
import java8.nio.file.FileSystemException
import java8.nio.file.NoSuchFileException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RenameOrCopyMoveTest {
    private class FakeBackend(
        private val renameFailure: Exception? = null,
        private val copyFailure: IOException? = null,
        private val deleteSourceFailure: IOException? = null
    ) {
        val calls = mutableListOf<String>()
        var copyOptions: CopyOptions? = null
        val progress = mutableListOf<Long>()
        val progressListener: (Long) -> Unit = { progress += it }
        val cleanupFailureListener: () -> Unit = {}

        fun options(
            replaceExisting: Boolean = false,
            copyAttributes: Boolean = false,
            atomicMove: Boolean = false,
            noFollowLinks: Boolean = false
        ): CopyOptions =
            CopyOptions(
                replaceExisting, copyAttributes, atomicMove, noFollowLinks, 500,
                progressListener, cleanupFailureListener
            )

        fun run(copyOptions: CopyOptions, targetExists: Boolean = false) {
            moveByRenameOrCopy(
                copyOptions, targetExists, SOURCE_SIZE,
                rename = { overwrite ->
                    calls += "rename:$overwrite"
                    renameFailure?.let { throw it }
                },
                copy = {
                    calls += "copy"
                    this.copyOptions = it
                    copyFailure?.let { throw it }
                },
                deleteSource = {
                    calls += "deleteSource"
                    deleteSourceFailure?.let { throw it }
                }
            )
        }
    }

    // U-95-01
    @Test
    fun renameSucceeds_noTarget_reportsProgressOnly() {
        val backend = FakeBackend()
        backend.run(backend.options())
        assertEquals(listOf("rename:false"), backend.calls)
        assertEquals(listOf(SOURCE_SIZE), backend.progress)
    }

    // U-95-02
    @Test
    fun replace_targetExists_overwriteRenameWithoutDeletingFirst() {
        val backend = FakeBackend()
        backend.run(backend.options(replaceExisting = true), targetExists = true)
        assertEquals(listOf("rename:true"), backend.calls)
        assertEquals(listOf(SOURCE_SIZE), backend.progress)
    }

    // U-95-03
    @Test
    fun overwrite_onlyForReplaceWithExistingTarget() {
        for (targetExists in listOf(false, true)) {
            for (replaceExisting in listOf(false, true)) {
                val backend = FakeBackend()
                backend.run(backend.options(replaceExisting = replaceExisting), targetExists)
                assertEquals(
                    "targetExists=$targetExists replaceExisting=$replaceExisting",
                    listOf("rename:${targetExists && replaceExisting}"), backend.calls
                )
            }
        }
    }

    // U-95-04
    @Test
    fun atomic_renameFails_rethrowsWithoutFallback() {
        val exception = FileSystemException("source", "target", "refused")
        val backend = FakeBackend(renameFailure = exception)
        assertSame(
            exception,
            assertThrows(FileSystemException::class.java) {
                backend.run(backend.options(atomicMove = true, noFollowLinks = true))
            }
        )
        assertEquals(listOf("rename:false"), backend.calls)
        assertTrue(backend.progress.isEmpty())
    }

    // U-95-05
    @Test
    fun renameFails_fallsBackToCopyThenDeleteSource_withRebuiltOptions() {
        val backend = FakeBackend(renameFailure = FileSystemException("source"))
        backend.run(backend.options(replaceExisting = true), targetExists = true)
        assertEquals(listOf("rename:true", "copy", "deleteSource"), backend.calls)
        val copyOptions = backend.copyOptions!!
        assertTrue(copyOptions.replaceExisting)
        assertTrue(copyOptions.copyAttributes)
        assertFalse(copyOptions.atomicMove)
        assertTrue(copyOptions.noFollowLinks)
        assertFalse(copyOptions.skipAttributes)
        assertEquals(500, copyOptions.progressIntervalMillis)
        assertSame(backend.progressListener, copyOptions.progressListener)
        assertSame(backend.cleanupFailureListener, copyOptions.cleanupFailureListener)
        assertTrue(backend.progress.isEmpty())
    }

    // U-95-06
    @Test
    fun renameFails_optionsAlreadySuitable_passedUnchanged() {
        val backend = FakeBackend(renameFailure = FileSystemException("source"))
        val options = backend.options(copyAttributes = true, noFollowLinks = true)
        backend.run(options)
        assertSame(options, backend.copyOptions)
    }

    // U-95-07
    @Test
    fun deleteSourceFails_keepsCopyAndRethrows() {
        for (exception in listOf(AccessDeniedException("source"), NoSuchFileException("source"))) {
            val backend = FakeBackend(
                renameFailure = FileSystemException("source"), deleteSourceFailure = exception
            )
            assertSame(
                exception,
                assertThrows(IOException::class.java) {
                    backend.run(backend.options(replaceExisting = true), targetExists = true)
                }
            )
            assertEquals(0, exception.suppressed.size)
            // Nothing after deleting the source: the copy at the target is kept.
            assertEquals(listOf("rename:true", "copy", "deleteSource"), backend.calls)
        }
    }

    // U-95-08
    @Test
    fun copyFails_sourceNotDeleted() {
        val exception = IOException()
        val backend = FakeBackend(
            renameFailure = FileSystemException("source"), copyFailure = exception
        )
        assertSame(
            exception, assertThrows(IOException::class.java) { backend.run(backend.options()) }
        )
        assertEquals(listOf("rename:false", "copy"), backend.calls)
    }

    // U-95-09
    @Test
    fun renameThrowsOtherIOException_propagatesWithoutFallback() {
        val exception = IOException("not converted")
        val backend = FakeBackend(renameFailure = exception)
        assertSame(
            exception, assertThrows(IOException::class.java) { backend.run(backend.options()) }
        )
        assertEquals(listOf("rename:false"), backend.calls)
    }

    companion object {
        private const val SOURCE_SIZE = 42L
    }
}

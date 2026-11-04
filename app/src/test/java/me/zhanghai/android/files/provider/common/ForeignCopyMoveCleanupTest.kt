/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import java8.nio.file.attribute.BasicFileAttributeView
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.attribute.FileTime
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.URI

class ForeignCopyMoveCleanupTest {
    private val fileSystem = FakeFileSystem()
    private val provider = fileSystem.fakeProvider
    private val source = FakePath(fileSystem, "/source/file".toByteString())
    private val target = FakePath(fileSystem, "/target/file".toByteString())
    private var listenerCount = 0
    private val option = CleanupFailureCopyOption { ++listenerCount }

    @After
    fun clearInterrupted() {
        Thread.interrupted()
    }

    // U-M01
    @Test
    fun copy_writeInterrupted_deleteFails_reportsOnceAndKeepsException() {
        val writeException = InterruptedIOException()
        provider.writeException = writeException
        provider.deleteException = IOException()
        assertSame(
            writeException,
            assertThrows(InterruptedIOException::class.java) {
                ForeignCopyMove.copy(source, target, option)
            }
        )
        assertEquals(1, listenerCount)
    }

    // U-M02
    @Test
    fun copy_deleteUnsupported_reportsOnce() {
        provider.writeException = IOException()
        provider.deleteException = UnsupportedOperationException()
        assertThrows(IOException::class.java) { ForeignCopyMove.copy(source, target, option) }
        assertEquals(1, listenerCount)
    }

    // U-M03
    @Test
    fun copy_deleteSucceedsOrCopySucceeds_notReported() {
        provider.writeException = IOException()
        assertThrows(IOException::class.java) { ForeignCopyMove.copy(source, target, option) }
        assertEquals(0, listenerCount)
        assertEquals(listOf<Path>(target), provider.deletedPaths)

        provider.writeException = null
        provider.deletedPaths.clear()
        ForeignCopyMove.copy(source, target, option)
        assertEquals(0, listenerCount)
        assertTrue(provider.deletedPaths.isEmpty())
    }

    // U-M04
    @Test
    fun copy_listenerThrows_keepsException() {
        val writeException = IOException()
        provider.writeException = writeException
        provider.deleteException = IOException()
        val throwingOption = CleanupFailureCopyOption { throw RuntimeException() }
        assertSame(
            writeException,
            assertThrows(IOException::class.java) {
                ForeignCopyMove.copy(source, target, throwingOption)
            }
        )
    }

    // U-M05
    @Test
    fun move_optionsRebuilt_reportsOnce_sourceNotDeleted() {
        provider.writeException = IOException()
        provider.deleteException = IOException()
        // COPY_ATTRIBUTES and NOFOLLOW_LINKS are absent, so the options are rebuilt.
        assertThrows(IOException::class.java) { ForeignCopyMove.move(source, target, option) }
        assertEquals(1, listenerCount)
        assertFalse(source in provider.deletedPaths)
    }

    // U-M06
    @Test
    fun copy_interruptedStatusRestoredByBackend_clearedForCloseAndDelete() {
        provider.interruptOnWrite = true
        provider.writeException = InterruptedIOException()
        assertThrows(InterruptedIOException::class.java) {
            ForeignCopyMove.copy(source, target, option)
        }
        assertEquals(listOf(false), provider.interruptedOnClose)
        assertEquals(listOf(false), provider.interruptedOnDelete)
    }
}

private class FakeProvider(private val fileSystem: FileSystem) : FileSystemProvider() {
    var writeException: IOException? = null
    var interruptOnWrite = false
    var deleteException: Exception? = null
    val deletedPaths = mutableListOf<Path>()
    val interruptedOnClose = mutableListOf<Boolean>()
    val interruptedOnDelete = mutableListOf<Boolean>()

    override fun getScheme(): String = "fake"

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem =
        throw UnsupportedOperationException()

    override fun getFileSystem(uri: URI): FileSystem = fileSystem

    override fun getPath(uri: URI): Path = throw UnsupportedOperationException()

    override fun newInputStream(path: Path, vararg options: OpenOption): InputStream =
        ByteArrayInputStream(ByteArray(16))

    override fun newOutputStream(path: Path, vararg options: OpenOption): OutputStream =
        object : OutputStream() {
            override fun write(b: Int) {
                write(byteArrayOf(b.toByte()), 0, 1)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (interruptOnWrite) {
                    Thread.currentThread().interrupt()
                }
                writeException?.let { throw it }
            }

            override fun close() {
                interruptedOnClose += Thread.currentThread().isInterrupted
            }
        }

    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs: FileAttribute<*>
    ): SeekableByteChannel = throw UnsupportedOperationException()

    override fun newDirectoryStream(
        dir: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> = throw UnsupportedOperationException()

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) =
        throw UnsupportedOperationException()

    override fun delete(path: Path) {
        interruptedOnDelete += Thread.currentThread().isInterrupted
        deletedPaths.add(path)
        when (val exception = deleteException) {
            null -> {}
            is IOException -> throw exception
            is RuntimeException -> throw exception
            else -> throw AssertionError(exception)
        }
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption) =
        throw UnsupportedOperationException()

    override fun move(source: Path, target: Path, vararg options: CopyOption) =
        throw UnsupportedOperationException()

    override fun isSameFile(path: Path, path2: Path): Boolean = path == path2

    override fun isHidden(path: Path): Boolean = false

    override fun getFileStore(path: Path): FileStore = throw UnsupportedOperationException()

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        if (path.toString().startsWith("/target")) {
            throw NoSuchFileException(path.toString())
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V = FakeAttributeView as V

    @Suppress("UNCHECKED_CAST")
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        if (path.toString().startsWith("/target")) {
            throw NoSuchFileException(path.toString())
        }
        return FakeAttributes as A
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> = throw UnsupportedOperationException()

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) = throw UnsupportedOperationException()
}

private object FakeAttributeView : BasicFileAttributeView {
    override fun name(): String = "basic"

    override fun readAttributes(): BasicFileAttributes = FakeAttributes

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {}
}

private object FakeAttributes : BasicFileAttributes {
    private val time = FileTime.fromMillis(1)

    override fun lastModifiedTime(): FileTime = time

    override fun lastAccessTime(): FileTime = time

    override fun creationTime(): FileTime = time

    override fun isRegularFile(): Boolean = true

    override fun isDirectory(): Boolean = false

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun size(): Long = 16

    override fun fileKey(): Any? = null
}

private class FakeFileSystem : FileSystem() {
    val fakeProvider = FakeProvider(this)

    override fun provider(): FileSystemProvider = fakeProvider

    override fun close() = throw UnsupportedOperationException()

    override fun isOpen(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = "/"

    override fun getRootDirectories(): Iterable<Path> = throw UnsupportedOperationException()

    override fun getFileStores(): Iterable<FileStore> = throw UnsupportedOperationException()

    override fun supportedFileAttributeViews(): Set<String> = throw UnsupportedOperationException()

    override fun getPath(first: String, vararg more: String): Path =
        throw UnsupportedOperationException()

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher =
        throw UnsupportedOperationException()

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
        throw UnsupportedOperationException()

    override fun newWatchService(): WatchService = throw UnsupportedOperationException()
}

private class FakePath : ByteStringListPath<FakePath> {
    private val fileSystem: FakeFileSystem

    constructor(fileSystem: FakeFileSystem, path: ByteString) : super(SEPARATOR, path) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: FakeFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == SEPARATOR

    override fun createPath(path: ByteString): FakePath = FakePath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): FakePath =
        FakePath(fileSystem, absolute, segments)

    override val uriScheme: String
        get() = "fake"

    override val defaultDirectory: FakePath
        get() = FakePath(fileSystem, true, emptyList())

    override fun getFileSystem(): FileSystem = fileSystem

    override fun getRoot(): FakePath? =
        if (isAbsolute) FakePath(fileSystem, true, emptyList()) else null

    override fun toFile(): File = throw UnsupportedOperationException()

    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey = throw UnsupportedOperationException()

    override fun toRealPath(vararg options: LinkOption): FakePath =
        throw UnsupportedOperationException()

    companion object {
        private const val SEPARATOR = '/'.code.toByte()
    }
}

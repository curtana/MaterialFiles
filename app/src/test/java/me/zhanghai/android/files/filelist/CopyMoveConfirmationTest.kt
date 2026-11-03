/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java.io.File
import java.text.Collator
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.filejob.CopyMoveType
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CopyMoveConfirmationTest {
    private val fsA = TestFileSystem()
    private val fsB = TestFileSystem()

    private fun path(path: String, fileSystem: TestFileSystem = fsA): TestPath =
        TestPath(fileSystem, path.toByteString())

    private fun item(path: String, fileSystem: TestFileSystem = fsA): FileItem {
        val itemPath = path(path, fileSystem)
        return FileItem(
            itemPath, Collator.getInstance().getCollationKey(itemPath.name), TestAttributes, null,
            null, false, MimeType("text/plain")
        )
    }

    private fun items(vararg paths: String): FileItemSet =
        FileItemSet().apply { paths.forEach { add(item(it)) } }

    // U-01
    @Test
    fun getCopyMoveType_plainCopyIsCopy() {
        assertEquals(CopyMoveType.COPY, getCopyMoveType(true, items("/a/x", "/b/y")))
    }

    @Test
    fun getCopyMoveType_moveIsMove() {
        assertEquals(CopyMoveType.MOVE, getCopyMoveType(false, items("/a/x", "/b/y")))
    }

    // U-02
    @Test
    fun toCopyMoveJobPath_plainPathIsUnchanged() {
        assertEquals(path("/a/b"), path("/a/b").toCopyMoveJobPath())
    }

    @Test
    fun toCopyMoveJobPath_dotIsRemoved() {
        assertEquals(path("/a/b"), path("/a/./b").toCopyMoveJobPath())
    }

    @Test
    fun toCopyMoveJobPath_dotDotIsKept() {
        val dotDotPath = path("/a/b/../c")
        val jobPath = dotDotPath.toCopyMoveJobPath()
        assertSame(dotDotPath, jobPath)
        assertEquals("/a/b/../c", jobPath.toString())
        assertNotEquals(dotDotPath.normalize(), jobPath)
    }

    @Test
    fun toCopyMoveJobPath_rootIsUnchanged() {
        val jobPath = path("/").toCopyMoveJobPath()
        assertEquals(path("/"), jobPath)
        assertEquals("/", jobPath.toString())
    }

    // U-03
    @Test
    fun sameDirectory_all() {
        val confirmation = createCopyMoveConfirmation(items("/a/x", "/a/y"), path("/a"))
        assertEquals(2, confirmation.sameDirectoryCount)
        assertEquals(SameDirectoryNotice.ALL, confirmation.sameDirectoryNotice)
    }

    @Test
    fun sameDirectory_some() {
        val confirmation = createCopyMoveConfirmation(items("/a/x", "/b/y"), path("/a"))
        assertEquals(1, confirmation.sameDirectoryCount)
        assertEquals(SameDirectoryNotice.SOME, confirmation.sameDirectoryNotice)
    }

    @Test
    fun sameDirectory_none() {
        val confirmation = createCopyMoveConfirmation(items("/a/x", "/b/y"), path("/c"))
        assertEquals(0, confirmation.sameDirectoryCount)
        assertEquals(SameDirectoryNotice.NONE, confirmation.sameDirectoryNotice)
    }

    @Test
    fun sameDirectory_targetWithDotIsNormalized() {
        val confirmation = createCopyMoveConfirmation(items("/a/x"), path("/a/."))
        assertEquals(path("/a"), confirmation.targetDirectory)
        assertEquals(SameDirectoryNotice.ALL, confirmation.sameDirectoryNotice)
    }

    @Test
    fun sameDirectory_targetWithDotDotIsNotNormalized() {
        val target = path("/x/../a")
        val confirmation = createCopyMoveConfirmation(items("/a/y"), target)
        assertSame(target, confirmation.targetDirectory)
        assertEquals(SameDirectoryNotice.NONE, confirmation.sameDirectoryNotice)
    }

    @Test
    fun sameDirectory_sameStringOnOtherFileSystemIsNotSame() {
        val confirmation = createCopyMoveConfirmation(items("/a/x"), path("/a", fsB))
        assertEquals(0, confirmation.sameDirectoryCount)
        assertEquals(SameDirectoryNotice.NONE, confirmation.sameDirectoryNotice)
    }

    // U-04
    @Test
    fun locations_one() {
        val confirmation = createCopyMoveConfirmation(items("/a/x"), path("/c"))
        assertEquals(path("/a"), confirmation.firstLocation)
        assertEquals(0, confirmation.otherLocationCount)
        assertEquals(1, confirmation.itemCount)
        assertEquals("x", confirmation.singleItemName)
    }

    @Test
    fun locations_twoUsesFirstInsertedItem() {
        val confirmation = createCopyMoveConfirmation(items("/b/y", "/a/x"), path("/c"))
        assertEquals(path("/b"), confirmation.firstLocation)
        assertEquals(1, confirmation.otherLocationCount)
        assertEquals(2, confirmation.itemCount)
        assertNull(confirmation.singleItemName)
    }

    @Test
    fun locations_threeItemsInSameLocation() {
        val confirmation = createCopyMoveConfirmation(items("/a/x", "/a/y", "/a/z"), path("/c"))
        assertEquals(path("/a"), confirmation.firstLocation)
        assertEquals(0, confirmation.otherLocationCount)
        assertEquals(3, confirmation.itemCount)
    }

    // U-05
    @Test
    fun sources_sortedByUriAndNormalized() {
        val files = items("/c/z", "/a/./x", "/b/y")
        val confirmation = createCopyMoveConfirmation(files, path("/a"))
        assertEquals(listOf(path("/a/x"), path("/b/y"), path("/c/z")), confirmation.sources)
        assertEquals(
            files.map { it.path.toCopyMoveJobPath() }.sortedBy { it.toUri() },
            confirmation.sources
        )
        assertEquals(1, confirmation.sameDirectoryCount)
        assertEquals(SameDirectoryNotice.SOME, confirmation.sameDirectoryNotice)
    }
}

private class TestPath : ByteStringListPath<TestPath> {
    private val fileSystem: TestFileSystem

    constructor(fileSystem: TestFileSystem, path: ByteString) : super(SEPARATOR, path) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: TestFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == SEPARATOR

    override fun createPath(path: ByteString): TestPath = TestPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): TestPath =
        TestPath(fileSystem, absolute, segments)

    override val uriScheme: String
        get() = "test"

    override val defaultDirectory: TestPath
        get() = TestPath(fileSystem, true, emptyList())

    override fun getFileSystem(): FileSystem = fileSystem

    override fun getRoot(): TestPath? =
        if (isAbsolute) TestPath(fileSystem, true, emptyList()) else null

    override fun toFile(): File = throw UnsupportedOperationException()

    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey = throw UnsupportedOperationException()

    override fun toRealPath(vararg options: LinkOption): TestPath =
        throw UnsupportedOperationException()

    companion object {
        private const val SEPARATOR = '/'.code.toByte()
    }
}

private class TestFileSystem : FileSystem() {
    override fun provider(): FileSystemProvider = throw UnsupportedOperationException()

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

private object TestAttributes : BasicFileAttributes {
    private val time = FileTime.fromMillis(0)

    override fun lastModifiedTime(): FileTime = time

    override fun lastAccessTime(): FileTime = time

    override fun creationTime(): FileTime = time

    override fun isRegularFile(): Boolean = true

    override fun isDirectory(): Boolean = false

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun size(): Long = 0

    override fun fileKey(): Any? = null
}

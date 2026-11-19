/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.ftp.client

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.URI

class FtpSymbolicLinkTargetTest {
    private fun resolveTarget(link: String, target: String): String =
        TestFtpPath(LinkTestPath(link.toByteString())).resolveSymbolicLinkTarget(target)
            .remotePath

    // U-B1-01
    @Test
    fun relativeTarget_resolvesAgainstLinkDirectory() {
        assertEquals("/d/b", resolveTarget("/d/link", "b"))
    }

    // U-B1-02
    @Test
    fun absoluteTarget_unchanged() {
        assertEquals("/x/y", resolveTarget("/d/link", "/x/y"))
    }

    // U-B1-03
    @Test
    fun parentRelativeTarget_notNormalized() {
        assertEquals("/d/../e/f", resolveTarget("/d/link", "../e/f"))
    }

    // U-B1-04
    @Test
    fun linkAtRoot() {
        assertEquals("/b", resolveTarget("/link", "b"))
    }

    // U-B1-05
    @Test
    fun dotTarget() {
        assertEquals("/d/./b", resolveTarget("/d/link", "./b"))
    }
}

/**
 * A [Client.Path] backed by a [ByteStringListPath], like `FtpPath`, so that `resolve` and
 * `resolveSibling` run the same `ByteStringListPath`/`AbstractPath` code.
 */
private class TestFtpPath(private val path: LinkTestPath) : Client.Path {
    override val authority: Authority
        get() = throw UnsupportedOperationException()

    override val remotePath: String
        get() = path.toString()

    override fun resolve(other: String): Client.Path =
        TestFtpPath(path.resolve(other.toByteString()))

    override fun resolveSibling(other: String): Client.Path =
        TestFtpPath(path.resolveSibling(other.toByteString()))
}

// filejob.TestPaths cannot be used here: its file system has no provider, which
// ByteStringListPath.resolve() checks.
private class LinkTestPath : ByteStringListPath<LinkTestPath> {
    constructor(path: ByteString) : super(SEPARATOR, path)

    private constructor(absolute: Boolean, segments: List<ByteString>) : super(
        SEPARATOR, absolute, segments
    )

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == SEPARATOR

    override fun createPath(path: ByteString): LinkTestPath = LinkTestPath(path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): LinkTestPath =
        LinkTestPath(absolute, segments)

    override val uriScheme: String
        get() = "test"

    override val defaultDirectory: LinkTestPath
        get() = LinkTestPath(true, emptyList())

    override fun getFileSystem(): FileSystem = LinkTestFileSystem

    override fun getRoot(): LinkTestPath? =
        if (isAbsolute) LinkTestPath(true, emptyList()) else null

    override fun toFile(): File = throw UnsupportedOperationException()

    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey = throw UnsupportedOperationException()

    override fun toRealPath(vararg options: LinkOption): LinkTestPath =
        throw UnsupportedOperationException()

    companion object {
        private const val SEPARATOR = '/'.code.toByte()
    }
}

private object LinkTestFileSystem : FileSystem() {
    override fun provider(): FileSystemProvider = LinkTestFileSystemProvider

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

private object LinkTestFileSystemProvider : FileSystemProvider() {
    override fun getScheme(): String = "test"

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem =
        throw UnsupportedOperationException()

    override fun getFileSystem(uri: URI): FileSystem = LinkTestFileSystem

    override fun getPath(uri: URI): Path = throw UnsupportedOperationException()

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

    override fun delete(path: Path) = throw UnsupportedOperationException()

    override fun copy(source: Path, target: Path, vararg options: CopyOption) =
        throw UnsupportedOperationException()

    override fun move(source: Path, target: Path, vararg options: CopyOption) =
        throw UnsupportedOperationException()

    override fun isSameFile(path: Path, path2: Path): Boolean = path == path2

    override fun isHidden(path: Path): Boolean = false

    override fun getFileStore(path: Path): FileStore = throw UnsupportedOperationException()

    override fun checkAccess(path: Path, vararg modes: AccessMode) =
        throw UnsupportedOperationException()

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V = throw UnsupportedOperationException()

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A = throw UnsupportedOperationException()

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

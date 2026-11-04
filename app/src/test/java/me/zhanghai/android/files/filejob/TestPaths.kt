/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.toByteString
import java.io.File

internal object TestPaths {
    private val fileSystem = TestFileSystem()

    fun path(path: String): Path = TestPath(fileSystem, path.toByteString())
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

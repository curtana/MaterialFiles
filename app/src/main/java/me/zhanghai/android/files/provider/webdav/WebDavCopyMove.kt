/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.webdav

import at.bitfire.dav4jvm.exception.DavException
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.NoSuchFileException
import java8.nio.file.StandardCopyOption
import me.zhanghai.android.files.provider.common.CopyOptions
import me.zhanghai.android.files.provider.common.copyTo
import me.zhanghai.android.files.provider.common.moveByRenameOrCopy
import me.zhanghai.android.files.provider.common.replaceAfterCheckingSource
import me.zhanghai.android.files.provider.common.replaceAfterOpeningSource
import me.zhanghai.android.files.provider.webdav.client.Client
import me.zhanghai.android.files.provider.webdav.client.isDirectory
import me.zhanghai.android.files.provider.webdav.client.isSymbolicLink
import me.zhanghai.android.files.provider.webdav.client.lastModifiedTime
import me.zhanghai.android.files.provider.webdav.client.size
import java.io.IOException

internal object WebDavCopyMove {
    @Throws(IOException::class)
    fun copy(source: WebDavPath, target: WebDavPath, copyOptions: CopyOptions) {
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceResponse = try {
            Client.findProperties(source, copyOptions.noFollowLinks)
        } catch (e: DavException) {
            throw e.toFileSystemException(source.toString())
        }
        val targetFile = try {
            Client.findPropertiesOrNull(target, true)
        } catch (e: DavException) {
            throw e.toFileSystemException(target.toString())
        }
        val sourceSize = sourceResponse.size
        if (targetFile != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
        }
        val deleteExisting: (() -> Unit)? = if (targetFile != null) {
            {
                try {
                    Client.delete(target)
                } catch (e: DavException) {
                    throw e.toFileSystemException(target.toString())
                }
            }
        } else {
            null
        }
        when {
            sourceResponse.isDirectory ->
                replaceAfterCheckingSource(
                    checkSource = {
                        if (targetFile != null) {
                            try {
                                Client.findCollectionMembers(source)
                            } catch (e: DavException) {
                                throw e.toFileSystemException(source.toString())
                            }
                        }
                    },
                    deleteExisting = deleteExisting
                ) {
                    try {
                        Client.makeCollection(target)
                    } catch (e: DavException) {
                        throw e.toFileSystemException(target.toString())
                    }
                    copyOptions.progressListener?.invoke(sourceSize)
                }
            sourceResponse.isSymbolicLink ->
                throw UnsupportedOperationException("Cannot copy symbolic links")
            else ->
                replaceAfterOpeningSource(
                    openSource = {
                        try {
                            Client.get(source)
                        } catch (e: DavException) {
                            throw e.toFileSystemException(source.toString())
                        }
                    },
                    closeSource = {
                        try {
                            it.close()
                        } catch (e: DavException) {
                            throw e.toFileSystemException(source.toString())
                        }
                    },
                    deleteExisting = deleteExisting
                ) { sourceInputStream ->
                    val targetOutputStream = try {
                        Client.put(target)
                    } catch (e: DavException) {
                        throw e.toFileSystemException(target.toString())
                    }
                    var successful = false
                    try {
                        sourceInputStream.copyTo(
                            targetOutputStream, copyOptions.progressIntervalMillis,
                            copyOptions.progressListener
                        )
                        successful = true
                    } finally {
                        // The interrupted status may have been restored by a library, which would
                        // make the cleanup fail immediately. The job is stopped by isCanceled.
                        if (!successful) {
                            Thread.interrupted()
                        }
                        try {
                            targetOutputStream.close()
                        } catch (e: DavException) {
                            throw e.toFileSystemException(target.toString())
                        } finally {
                            if (!successful) {
                                try {
                                    Client.delete(target)
                                } catch (e: DavException) {
                                    e.printStackTrace()
                                    val exception = e.toFileSystemException(target.toString())
                                    if (exception !is NoSuchFileException) {
                                        copyOptions.cleanupFailureListener?.invoke()
                                    }
                                }
                            }
                        }
                    }
                }
        }
        if (copyOptions.skipAttributes) {
            return
        }
        // We don't take error when copying attribute fatal, so errors will only be logged from now
        // on.
        if (!sourceResponse.isSymbolicLink) {
            val lastModifiedTime = sourceResponse.lastModifiedTime
            if (lastModifiedTime != null) {
                try {
                    Client.setLastModifiedTime(target, lastModifiedTime)
                } catch (e: DavException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun move(source: WebDavPath, target: WebDavPath, copyOptions: CopyOptions) {
        val sourceResponse = try {
            Client.findProperties(source, copyOptions.noFollowLinks)
        } catch (e: DavException) {
            throw e.toFileSystemException(source.toString())
        }
        val targetResponse = try {
            Client.findPropertiesOrNull(target, true)
        } catch (e: DavException) {
            throw e.toFileSystemException(target.toString())
        }
        val sourceSize = sourceResponse.size
        if (targetResponse != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
        }
        moveByRenameOrCopy(
            copyOptions, targetResponse != null, sourceSize,
            rename = { overwrite ->
                try {
                    Client.move(source, target, overwrite)
                } catch (e: DavException) {
                    throw e.toFileSystemException(source.toString(), target.toString())
                }
            },
            copy = { copy(source, target, it) },
            deleteSource = {
                try {
                    Client.delete(source)
                } catch (e: DavException) {
                    throw e.toFileSystemException(source.toString())
                }
            }
        )
    }
}

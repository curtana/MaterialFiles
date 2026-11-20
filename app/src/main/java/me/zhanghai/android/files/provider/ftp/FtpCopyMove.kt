/*
 * Copyright (c) 2022 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.ftp

import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.StandardCopyOption
import me.zhanghai.android.files.provider.common.CopyOptions
import me.zhanghai.android.files.provider.common.copyTo
import me.zhanghai.android.files.provider.common.moveByRenameOrCopy
import me.zhanghai.android.files.provider.common.replaceAfterCheckingSource
import me.zhanghai.android.files.provider.common.replaceAfterOpeningSource
import me.zhanghai.android.files.provider.ftp.client.Client
import java.io.IOException

internal object FtpCopyMove {
    @Throws(IOException::class)
    fun copy(source: FtpPath, target: FtpPath, copyOptions: CopyOptions) {
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceFile = try {
            Client.listFile(source, copyOptions.noFollowLinks)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(source.toString())
        }
        val targetFile = try {
            Client.listFileOrNull(target, true)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(target.toString())
        }
        val sourceSize = sourceFile.size
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
                    Client.delete(target, targetFile.isDirectory)
                } catch (e: IOException) {
                    throw e.toFileSystemExceptionForFtp(target.toString())
                }
            }
        } else {
            null
        }
        when {
            sourceFile.isDirectory ->
                replaceAfterCheckingSource(
                    checkSource = {
                        if (targetFile != null) {
                            try {
                                Client.listDirectory(source)
                            } catch (e: IOException) {
                                throw e.toFileSystemExceptionForFtp(source.toString())
                            }
                        }
                    },
                    deleteExisting = deleteExisting
                ) {
                    try {
                        Client.createDirectory(target)
                    } catch (e: IOException) {
                        throw e.toFileSystemExceptionForFtp(target.toString())
                    }
                    copyOptions.progressListener?.invoke(sourceSize)
                }
            sourceFile.isSymbolicLink ->
                throw UnsupportedOperationException("Cannot copy symbolic links")
            else ->
                replaceAfterOpeningSource(
                    openSource = {
                        try {
                            Client.retrieveFile(source)
                        } catch (e: IOException) {
                            throw e.toFileSystemExceptionForFtp(source.toString())
                        }
                    },
                    closeSource = {
                        try {
                            it.close()
                        } catch (e: IOException) {
                            throw e.toFileSystemExceptionForFtp(source.toString())
                        }
                    },
                    deleteExisting = deleteExisting
                ) { sourceInputStream ->
                    val targetOutputStream = try {
                        Client.storeFile(target)
                    } catch (e: IOException) {
                        throw e.toFileSystemExceptionForFtp(target.toString())
                    }
                    var successful = false
                    try {
                        sourceInputStream.copyTo(
                            targetOutputStream, copyOptions.progressIntervalMillis,
                            copyOptions.progressListener
                        )
                        successful = true
                    } finally {
                        try {
                            targetOutputStream.close()
                        } catch (e: IOException) {
                            throw e.toFileSystemExceptionForFtp(target.toString())
                        } finally {
                            if (!successful) {
                                try {
                                    Client.delete(target, sourceFile.isDirectory)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                    // FTP reply 550 can't tell a missing file from a denied one, so
                                    // always report instead of possibly hiding a leftover.
                                    copyOptions.cleanupFailureListener?.invoke()
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
        if (!sourceFile.isSymbolicLink) {
            val timestamp = sourceFile.timestamp
            if (timestamp != null) {
                try {
                    Client.setLastModifiedTime(target, timestamp.toInstant())
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun move(source: FtpPath, target: FtpPath, copyOptions: CopyOptions) {
        val sourceFile = try {
            Client.listFile(source, copyOptions.noFollowLinks)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(source.toString())
        }
        val targetFile = try {
            Client.listFileOrNull(target, true)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(target.toString())
        }
        val sourceSize = sourceFile.size
        if (targetFile != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
        }
        // RNTO has no way to ask for an overwrite, so a replace move simply renames onto the
        // existing file and falls back to copying if the server refuses.
        moveByRenameOrCopy(
            copyOptions, targetFile != null, sourceSize,
            rename = {
                try {
                    Client.renameFile(source, target)
                } catch (e: IOException) {
                    throw e.toFileSystemExceptionForFtp(source.toString(), target.toString())
                }
            },
            copy = { copy(source, target, it) },
            deleteSource = {
                try {
                    Client.delete(source, sourceFile.isDirectory)
                } catch (e: IOException) {
                    throw e.toFileSystemExceptionForFtp(source.toString())
                }
            }
        )
    }
}

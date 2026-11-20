/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.smb

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.protocol.commons.EnumWithValue
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileSystemException
import java8.nio.file.NoSuchFileException
import java8.nio.file.StandardCopyOption
import me.zhanghai.android.files.provider.common.CopyOptions
import me.zhanghai.android.files.provider.common.createSymbolicLinkForCopy
import me.zhanghai.android.files.provider.common.moveByRenameOrCopy
import me.zhanghai.android.files.provider.common.replaceAfterCheckingSource
import me.zhanghai.android.files.provider.smb.client.Client
import me.zhanghai.android.files.provider.smb.client.ClientException
import me.zhanghai.android.files.provider.smb.client.FileInformation
import me.zhanghai.android.files.util.enumSetOf
import me.zhanghai.android.files.util.hasBits
import java.io.IOException
import java.io.InterruptedIOException

internal object SmbCopyMove {
    @Throws(IOException::class)
    fun copy(source: SmbPath, target: SmbPath, copyOptions: CopyOptions) {
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceInformation = try {
            Client.getPathInformation(source, copyOptions.noFollowLinks)
        } catch (e: ClientException) {
            throw e.toFileSystemException(source.toString())
        }
        sourceInformation as? FileInformation
            ?: throw FileSystemException(source.toString(), null, "Cannot copy shares")
        val targetInformation = try {
            Client.getPathInformation(target, true)
        } catch (e: ClientException) {
            val exception = e.toFileSystemException(target.toString())
            if (exception !is NoSuchFileException) {
                throw exception
            }
            // Ignored.
            null
        }
        if (targetInformation != null) {
            targetInformation as? FileInformation
                ?: throw FileSystemException(target.toString(), null, "Cannot copy shares")
            if (SmbFileKey(source, sourceInformation.fileId)
                == SmbFileKey(target, targetInformation.fileId)) {
                copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            // Symbolic links may not be supported so we cannot simply delete the target here.
        }
        val sourceIsReparsePoint = sourceInformation.fileAttributes
            .hasBits(FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.value)
        val sourceIsDirectory = !sourceIsReparsePoint &&
            sourceInformation.fileAttributes.hasBits(FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value)
        val sourceIsRegularFile = !sourceIsDirectory && !sourceIsReparsePoint
        val attributesToCopy = if (copyOptions.copyAttributes) {
            EnumWithValue.EnumUtils.toEnumSet(
                sourceInformation.fileAttributes, FileAttributes::class.java
            )
        } else {
            enumSetOf(FileAttributes.FILE_ATTRIBUTE_NORMAL)
        }
        val deleteExisting: (() -> Unit)? = if (targetInformation != null) {
            {
                try {
                    Client.delete(target)
                } catch (e: ClientException) {
                    throw e.toFileSystemException(target.toString())
                }
            }
        } else {
            null
        }
        if (sourceIsRegularFile) {
            try {
                Client.copyFile(
                    source, target, copyOptions.copyAttributes, copyOptions.noFollowLinks,
                    copyOptions.progressIntervalMillis, copyOptions.progressListener,
                    copyOptions.cleanupFailureListener, deleteExisting
                )
            } catch (e: ClientException) {
                (e.cause as? InterruptedIOException)?.let { throw it }
                e.maybeThrowInvalidFileNameException(target.toString())
                throw e.toFileSystemException(target.toString())
            }
        } else if (sourceIsDirectory) {
            replaceAfterCheckingSource(
                checkSource = {
                    if (targetInformation != null) {
                        try {
                            Client.openDirectoryIterator(source).close()
                        } catch (e: ClientException) {
                            throw e.toFileSystemException(source.toString())
                        }
                    }
                },
                deleteExisting = deleteExisting
            ) {
                try {
                    Client.createDirectory(target, attributesToCopy)
                } catch (e: ClientException) {
                    e.maybeThrowInvalidFileNameException(target.toString())
                    throw e.toFileSystemException(target.toString())
                }
                copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
            }
        } else if (sourceIsReparsePoint) {
            val sourceReparseData = try {
                Client.readSymbolicLink(source)
            } catch (e: ClientException) {
                throw e.toFileSystemException(source.toString())
            }
            createSymbolicLinkForCopy(
                copyOptions.replaceExisting,
                createLink = {
                    try {
                        Client.createSymbolicLink(target, sourceReparseData, attributesToCopy)
                    } catch (e: ClientException) {
                        e.maybeThrowInvalidFileNameException(target.toString())
                        throw e.toFileSystemException(target.toString())
                    }
                },
                deleteExisting = {
                    try {
                        Client.delete(target)
                    } catch (e: ClientException) {
                        throw e.toFileSystemException(target.toString())
                    }
                }
            )
            copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
        } else {
            throw AssertionError()
        }
        if (copyOptions.skipAttributes) {
            return
        }
        // We don't take error when copying attribute fatal, so errors will only be logged from now
        // on.
        // TODO: Copy SecurityDescriptor.
        // TODO: Change modified time last?
        try {
            val fileInformation = FileBasicInformation(
                if (copyOptions.copyAttributes) sourceInformation.creationTime else FileTime(0),
                if (copyOptions.copyAttributes) sourceInformation.lastAccessTime else FileTime(0),
                sourceInformation.lastWriteTime,
                if (copyOptions.copyAttributes) sourceInformation.changeTime else FileTime(0), 0
            )
            Client.setFileInformation(target, true, fileInformation)
        } catch (e: ClientException) {
            e.printStackTrace()
        }
        // TODO: Copy FileFullEaInformation.
    }

    @Throws(IOException::class)
    fun move(source: SmbPath, target: SmbPath, copyOptions: CopyOptions) {
        val sourceInformation = try {
            Client.getPathInformation(source, true)
        } catch (e: ClientException) {
            throw e.toFileSystemException(source.toString())
        }
        sourceInformation as? FileInformation
            ?: throw FileSystemException(source.toString(), null, "Cannot move shares")
        val targetInformation = try {
            Client.getPathInformation(target, true)
        } catch (e: ClientException) {
            val exception = e.toFileSystemException(target.toString())
            if (exception !is NoSuchFileException) {
                throw exception
            }
            // Ignored.
            null
        }
        if (targetInformation != null) {
            targetInformation as? FileInformation
                ?: throw FileSystemException(target.toString(), null, "Cannot move shares")
            if (SmbFileKey(source, sourceInformation.fileId)
                == SmbFileKey(target, targetInformation.fileId)) {
                copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
        }
        moveByRenameOrCopy(
            copyOptions, targetInformation != null, sourceInformation.endOfFile,
            rename = { overwrite ->
                try {
                    Client.rename(source, target, overwrite)
                } catch (e: ClientException) {
                    e.maybeThrowAtomicMoveNotSupportedException(source.toString(), target.toString())
                    e.maybeThrowInvalidFileNameException(target.toString())
                    throw e.toFileSystemException(source.toString(), target.toString())
                }
            },
            copy = { copy(source, target, it) },
            deleteSource = {
                try {
                    Client.delete(source)
                } catch (e: ClientException) {
                    throw e.toFileSystemException(source.toString())
                }
            }
        )
    }
}

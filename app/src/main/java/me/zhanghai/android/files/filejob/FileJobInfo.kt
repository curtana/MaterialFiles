/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java8.nio.file.Path
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.asByteStringListPath
import me.zhanghai.android.files.util.asFileName

enum class FileJobType {
    COPY,
    EXTRACT,
    MOVE,
    ARCHIVE,
    DELETE,
    CREATE,
    RENAME,
    OPEN,
    INSTALL_APK,
    SAVE,
    WRITE,
    SET_OWNER,
    SET_GROUP,
    SET_MODE,
    SET_SELINUX_CONTEXT,
    RESTORE_SELINUX_CONTEXT
}

/** What a file job is, for display. Made on the main thread without any I/O. */
data class FileJobInfo(
    val type: FileJobType,
    val subjectName: String,
    val subjectCount: Int,
    val destinationName: String?,
    val isRecursive: Boolean
) {
    /** Whether a toast should tell that the job has completed successfully. */
    val notifiesCompletion: Boolean
        get() =
            when (type) {
                FileJobType.COPY, FileJobType.EXTRACT, FileJobType.MOVE, FileJobType.ARCHIVE,
                FileJobType.DELETE -> true
                // These already have their own success toast.
                FileJobType.SAVE, FileJobType.WRITE -> false
                // The result of these is immediately visible.
                FileJobType.CREATE, FileJobType.RENAME, FileJobType.OPEN,
                FileJobType.INSTALL_APK -> false
                FileJobType.SET_OWNER, FileJobType.SET_GROUP, FileJobType.SET_MODE,
                FileJobType.SET_SELINUX_CONTEXT, FileJobType.RESTORE_SELINUX_CONTEXT -> isRecursive
            }

    companion object {
        fun of(
            type: FileJobType,
            subjects: List<Path>,
            destination: Path?,
            isRecursive: Boolean = false
        ): FileJobInfo =
            FileJobInfo(
                type, subjects.firstOrNull()?.fileJobName() ?: "", subjects.size,
                destination?.fileJobName(), isRecursive
            )
    }
}

internal fun copyTypeOf(isAllArchive: Boolean): FileJobType =
    if (isAllArchive) FileJobType.EXTRACT else FileJobType.COPY

// Same as getTargetFileName() in FileJobs.kt, but doesn't create the archive file system on the
// main thread.
internal fun Path.fileJobName(): String =
    if (isArchivePath && isAbsolute && nameCount == 0) {
        archiveFile.asByteStringListPath().fileNameByteString!!.asFileName().baseName.toString()
    } else {
        plainFileJobName()
    }

// Same as getFileName() in FileJobs.kt.
internal fun Path.plainFileJobName(): String =
    if (isAbsolute && nameCount == 0) {
        fileSystem.separator
    } else {
        fileName?.toString() ?: ""
    }

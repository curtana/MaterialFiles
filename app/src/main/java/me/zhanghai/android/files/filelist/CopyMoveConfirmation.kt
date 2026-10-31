/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.CopyMoveType
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath

fun getCopyMoveType(copy: Boolean, files: Collection<FileItem>): CopyMoveType =
    when {
        !copy -> CopyMoveType.MOVE
        files.all { it.path.isArchivePath } -> CopyMoveType.EXTRACT
        else -> CopyMoveType.COPY
    }

/**
 * The path value used for both the same directory check and the copy/move job.
 *
 * Paths containing a `..` name are kept as is, because [Path.normalize] would resolve `..`
 * lexically and could point to a different directory when a symbolic link is involved.
 */
internal fun Path.toCopyMoveJobPath(): Path =
    if ((0 until nameCount).any { getName(it).toString() == ".." }) this else normalize()

class CopyMoveConfirmation(
    val sources: List<Path>,
    val targetDirectory: Path,
    val itemCount: Int,
    val singleItemName: String?,
    val sameDirectoryCount: Int,
    val firstLocation: Path?,
    val otherLocationCount: Int
) {
    val sameDirectoryNotice: SameDirectoryNotice
        get() = when (sameDirectoryCount) {
            0 -> SameDirectoryNotice.NONE
            itemCount -> SameDirectoryNotice.ALL
            else -> SameDirectoryNotice.SOME
        }
}

fun createCopyMoveConfirmation(files: FileItemSet, targetDirectory: Path): CopyMoveConfirmation {
    val sources = files.map { it.path.toCopyMoveJobPath() }.sortedBy { it.toUri() }
    val jobTargetDirectory = targetDirectory.toCopyMoveJobPath()
    val itemCount = files.size
    val singleItemName = if (itemCount == 1) files.single().name else null
    val sameDirectoryCount = sources.count {
        val parent = it.parent
        parent != null && parent == jobTargetDirectory
    }
    val locations = files.mapNotNull { locationOf(it.path) }.distinct()
    return CopyMoveConfirmation(
        sources, jobTargetDirectory, itemCount, singleItemName, sameDirectoryCount,
        files.firstOrNull()?.let { locationOf(it.path) }, (locations.size - 1).coerceAtLeast(0)
    )
}

// For display only, not for the same directory check.
private fun locationOf(path: Path): Path? {
    val jobPath = path.toCopyMoveJobPath()
    return jobPath.parent ?: if (jobPath.isArchivePath) jobPath.archiveFile.parent else null
}

enum class SameDirectoryNotice {
    NONE,
    ALL,
    SOME
}

internal fun Path.toLocationDisplayString(): String =
    if (isArchivePath) {
        archiveFile.toUserFriendlyString() + (if (nameCount == 0) "" else toString())
    } else {
        toUserFriendlyString()
    }

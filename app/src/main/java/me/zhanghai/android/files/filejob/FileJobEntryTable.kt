/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

data class FileJobEntry(
    val id: Int,
    val info: FileJobInfo,
    val progress: FileJobProgress?,
    val isPending: Boolean
)

/** The file jobs that haven't finished, in the order they started. Not thread safe. */
internal class FileJobEntryTable {
    private val entriesById = linkedMapOf<Int, FileJobEntry>()

    val entries: List<FileJobEntry>
        get() = entriesById.values.toList()

    // Colliding job IDs are counted as one job.
    fun start(id: Int, info: FileJobInfo, isPending: Boolean): FileJobEntry? {
        if (id in entriesById) {
            return null
        }
        return FileJobEntry(id, info, null, isPending).also { entriesById[id] = it }
    }

    fun setRunning(id: Int): Boolean {
        val entry = entriesById[id] ?: return false
        if (!entry.isPending) {
            return false
        }
        entriesById[id] = entry.copy(isPending = false)
        return true
    }

    fun setProgress(id: Int, progress: FileJobProgress): Boolean {
        val entry = entriesById[id] ?: return false
        entriesById[id] = entry.copy(progress = progress, isPending = false)
        return true
    }

    fun finish(id: Int): Boolean = entriesById.remove(id) != null
}

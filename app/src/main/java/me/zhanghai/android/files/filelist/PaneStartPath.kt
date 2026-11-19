/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import java8.nio.file.FileSystemNotFoundException
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderNotFoundException
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.storage.createOrLog
import java.net.URI

// These are file level functions so that they can be tested without Android.

/**
 * The path a pane starts at: the path saved before the system recreated the pane, then the path
 * from the intent, then the default directory. The default directory is only read when neither of
 * the former is available.
 */
internal fun <T : Any> resolvePaneStartPath(
    restoredPath: T?,
    intentPath: T?,
    defaultPath: () -> T
): T = restoredPath ?: intentPath ?: defaultPath()

/**
 * Decodes a path saved by [encodePanePath], or returns `null` if it can't be read, e.g. when it was
 * saved by an older version of the app or its file system provider is gone. This doesn't access
 * the file system.
 */
internal fun decodePanePath(
    uriString: String?,
    getPath: (URI) -> Path = { Paths.get(it) }
): Path? {
    if (uriString == null) {
        return null
    }
    val uri = URI::class.createOrLog(uriString) ?: return null
    return try {
        getPath(uri)
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        null
    } catch (e: FileSystemNotFoundException) {
        e.printStackTrace()
        null
    } catch (e: ProviderNotFoundException) {
        e.printStackTrace()
        null
    }
}

/** Encodes a path for saving, in the same form as the path extra of an intent. */
internal fun encodePanePath(path: Path): String = path.toUri().toString()

// TEMPORARY (fail-before, #80 LLD §3): mimics the current behaviour; replaced in the next commit.

internal const val ACTION_VIEW_DOWNLOADS = "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"

internal const val NEW_TASK_VIEW_INTENT_FLAGS =
    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK

internal fun isPickAction(action: String?): Boolean =
    when (action) {
        Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT,
        Intent.ACTION_OPEN_DOCUMENT_TREE -> true
        else -> false
    }

internal fun resolveIntentPath(
    action: String?,
    path: Path?,
    mimeType: String?,
    downloadsPath: () -> Path,
    archiveRootPath: (Path) -> Path = { it.createArchiveRootPath() }
): Path? {
    if (action == ACTION_VIEW_DOWNLOADS) {
        return downloadsPath()
    }
    var resolvedPath = path
    if (resolvedPath != null) {
        val type = mimeType?.asMimeTypeOrNull()
        if (type != null && resolvedPath.isArchiveFile(type)) {
            resolvedPath = archiveRootPath(resolvedPath)
        }
    }
    return resolvedPath
}

internal enum class NewIntentAction {
    IGNORE,
    OPEN_IN_ACTIVE_PANE,
    OPEN_IN_NEW_TASK
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveNewIntentAction(
    isPickScreen: Boolean,
    action: String?,
    flags: Int,
    path: Path?
): NewIntentAction = NewIntentAction.IGNORE

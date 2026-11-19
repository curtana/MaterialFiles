/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.FileSystemNotFoundException
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderNotFoundException
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

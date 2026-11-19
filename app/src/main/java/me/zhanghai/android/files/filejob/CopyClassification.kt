/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java8.nio.file.Path
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.document.resolver.DocumentResolver
import me.zhanghai.android.files.provider.linux.isLinuxPath

// @see DocumentResolver.MTP_DOCUMENTS_PROVIDER_AUTHORITY
private const val MTP_DOCUMENTS_PROVIDER_AUTHORITY = "com.android.mtp.documents"

/** Which copy rules apply to a copy (#55): `cp` defaults or network defaults. */
internal enum class CopyKind {
    PHONE_TO_PHONE,
    NETWORK
}

/** Where a path lives, as far as [classifyCopy] cares. Pure, so the rule is testable on the JVM. */
internal sealed interface CopyLocation {
    data object Linux : CopyLocation

    data class Archive(val archiveFile: CopyLocation) : CopyLocation

    data class Document(val isLocal: Boolean, val authority: String?) : CopyLocation

    data object Other : CopyLocation
}

internal val CopyLocation.isPhone: Boolean
    get() =
        when (this) {
            CopyLocation.Linux -> true
            is CopyLocation.Archive -> archiveFile.isPhone
            is CopyLocation.Document -> isLocal && authority != MTP_DOCUMENTS_PROVIDER_AUTHORITY
            CopyLocation.Other -> false
        }

internal fun classifyCopy(source: CopyLocation, targetDirectory: CopyLocation): CopyKind =
    if (source.isPhone && targetDirectory.isPhone) CopyKind.PHONE_TO_PHONE else CopyKind.NETWORK

/**
 * The only place that decides whether a copy is phone to phone or network (#55). Called once per
 * start item of a copy; items below it inherit the result.
 */
internal fun classifyCopy(source: Path, targetDirectory: Path): CopyKind =
    classifyCopy(source.copyLocation, targetDirectory.copyLocation)

private val Path.copyLocation: CopyLocation
    get() =
        when {
            // Check archive paths first, so that the archive file decides where they live.
            isArchivePath -> CopyLocation.Archive(archiveFile.copyLocation)
            isLinuxPath -> CopyLocation.Linux
            isDocumentPath -> {
                this as DocumentResolver.Path
                CopyLocation.Document(DocumentResolver.isLocal(this), treeUri.authority)
            }
            else -> CopyLocation.Other
        }

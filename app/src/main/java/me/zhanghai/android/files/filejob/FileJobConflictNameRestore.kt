/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

/** The views of the conflict dialog to restore when it is created (#67). */
internal data class FileJobConflictNameRestore(
    val nameText: String,
    val isNameLayoutVisible: Boolean,
    val isRename: Boolean,
    val isAllChecked: Boolean,
    val isAllEnabled: Boolean
)

/**
 * Pass `null, false, false` for the saved values when the dialog is first opened.
 *
 * @see FileJobConflictDialogFragment.hasNewName
 */
internal fun conflictNameRestoreOf(
    targetFileName: String,
    savedNewName: String?,
    savedIsNameLayoutVisible: Boolean,
    savedIsAllChecked: Boolean
): FileJobConflictNameRestore =
    if (!savedNewName.isNullOrEmpty() && savedNewName != targetFileName) {
        FileJobConflictNameRestore(savedNewName, savedIsNameLayoutVisible, true, false, false)
    } else {
        // Without a new name the dialog stays as it was, even if the name was shown (#67 E3).
        FileJobConflictNameRestore(targetFileName, false, false, savedIsAllChecked, true)
    }

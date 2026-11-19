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
    // TODO(#67): Temporary body with the current behavior, to see the tests fail first.
    FileJobConflictNameRestore(targetFileName, false, false, savedIsAllChecked, true)

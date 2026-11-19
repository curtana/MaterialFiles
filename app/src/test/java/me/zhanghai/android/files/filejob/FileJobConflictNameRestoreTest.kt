/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Test

class FileJobConflictNameRestoreTest {
    private val targetFileName = "a.txt"

    private fun restoreOf(
        newName: String?,
        isNameLayoutVisible: Boolean,
        isAllChecked: Boolean
    ): FileJobConflictNameRestore =
        conflictNameRestoreOf(targetFileName, newName, isNameLayoutVisible, isAllChecked)

    // U-R01
    @Test
    fun firstOpen_originalNameCollapsed() {
        assertEquals(
            FileJobConflictNameRestore("a.txt", false, false, false, true),
            restoreOf(null, false, false)
        )
    }

    // U-R02
    @Test
    fun noNewName_keepsAllChecked() {
        assertEquals(
            FileJobConflictNameRestore("a.txt", false, false, true, true),
            restoreOf(null, false, true)
        )
    }

    // U-R03
    @Test
    fun noNewName_ignoresExpansion() {
        assertEquals(
            FileJobConflictNameRestore("a.txt", false, false, false, true),
            restoreOf(null, true, false)
        )
    }

    // U-R04
    @Test
    fun newName_expanded_rename() {
        assertEquals(
            FileJobConflictNameRestore("b.txt", true, true, false, false),
            restoreOf("b.txt", true, false)
        )
    }

    // U-R05
    @Test
    fun newName_collapsed_rename() {
        assertEquals(
            FileJobConflictNameRestore("b.txt", false, true, false, false),
            restoreOf("b.txt", false, false)
        )
    }

    // U-R06
    @Test
    fun newName_savedAllChecked_uncheckedAndDisabled() {
        assertEquals(
            FileJobConflictNameRestore("b.txt", true, true, false, false),
            restoreOf("b.txt", true, true)
        )
    }

    // U-R07
    @Test
    fun emptyName_treatedAsNoNewName() {
        assertEquals(
            FileJobConflictNameRestore("a.txt", false, false, true, true),
            restoreOf("", true, true)
        )
    }

    // U-R08
    @Test
    fun originalName_treatedAsNoNewName() {
        assertEquals(
            FileJobConflictNameRestore("a.txt", false, false, false, true),
            restoreOf("a.txt", true, false)
        )
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileJobInfoTest {
    // U-I01
    @Test
    fun of_onePath() {
        val info = FileJobInfo.of(FileJobType.DELETE, listOf(TestPaths.path("/a/b.txt")), null)
        assertEquals("b.txt", info.subjectName)
        assertEquals(1, info.subjectCount)
        assertNull(info.destinationName)
    }

    // U-I02
    @Test
    fun of_multiplePathsWithDestination() {
        val info = FileJobInfo.of(
            FileJobType.COPY, listOf(TestPaths.path("/a/b.txt"), TestPaths.path("/a/c.txt")),
            TestPaths.path("/d/e")
        )
        assertEquals("b.txt", info.subjectName)
        assertEquals(2, info.subjectCount)
        assertEquals("e", info.destinationName)
    }

    // U-I03
    @Test
    fun of_empty() {
        val info = FileJobInfo.of(FileJobType.DELETE, emptyList(), null)
        assertEquals("", info.subjectName)
        assertEquals(0, info.subjectCount)
    }

    // U-I04
    @Test
    fun plainFileJobName_root_isSeparator() {
        assertEquals("/", TestPaths.path("/").plainFileJobName())
    }

    // U-I05
    @Test
    fun copyTypeOf() {
        assertEquals(FileJobType.EXTRACT, copyTypeOf(true))
        assertEquals(FileJobType.COPY, copyTypeOf(false))
    }

    // U-I06
    @Test
    fun types_count() {
        assertEquals(16, FileJobType.entries.size)
    }

    // U-I07
    @Test
    fun notifiesCompletion_table() {
        val always = setOf(
            FileJobType.COPY, FileJobType.EXTRACT, FileJobType.MOVE, FileJobType.ARCHIVE,
            FileJobType.DELETE
        )
        val recursiveOnly = setOf(
            FileJobType.SET_OWNER, FileJobType.SET_GROUP, FileJobType.SET_MODE,
            FileJobType.SET_SELINUX_CONTEXT, FileJobType.RESTORE_SELINUX_CONTEXT
        )
        for (type in FileJobType.entries) {
            for (isRecursive in listOf(false, true)) {
                val expected = type in always || (type in recursiveOnly && isRecursive)
                assertEquals(
                    "$type $isRecursive", expected,
                    FileJobInfo(type, "a", 1, null, isRecursive).notifiesCompletion
                )
            }
        }
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Checks the resource contract of the replace warning (#54) and the Korean replace message
// (#58). Unit tests run with app/ as the working directory.
class FileJobReplaceWarningResourcesTest {
    private fun readRes(path: String): String = File("src/main/res/$path").readText()

    private fun stringValue(xml: String, name: String): String? =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)

    private fun countOf(text: String, part: String): Int = text.windowed(part.length).count {
        it == part
    }

    // U-W01
    @Test
    fun warning_definedInEnglishAndKorean() {
        for (path in listOf("values/strings.xml", "values-ko/strings.xml")) {
            val value = stringValue(readRes(path), "file_job_replace_warning")
            assertNotNull(path, value)
            assertTrue(path, value!!.isNotBlank())
            assertFalse(path, value.contains('%'))
        }
    }

    // U-W05
    @Test
    fun koreanReplaceMessage_namesFolder() {
        val korean = stringValue(
            readRes("values-ko/strings.xml"), "file_job_replace_message_format"
        )
        assertEquals(
            "“%1\$s” 폴더에 같은 이름의 파일이 이미 있습니다.\\n파일을 교체하면 대상 파일의 내용을 덮어씁니다.",
            korean
        )
        assertEquals(1, countOf(korean!!, "%1\$s"))
        val english = stringValue(readRes("values/strings.xml"), "file_job_replace_message_format")
        assertNotNull(english)
        assertEquals(1, countOf(english!!, "%1\$s"))
    }

    // U-W06
    @Test
    fun layout_warningIsFirstAndHidden() {
        val layout = readRes("layout/file_job_conflict_dialog_view.xml")
        val warningIndex = layout.indexOf("@+id/replaceWarningText")
        val targetIconIndex = layout.indexOf("@+id/targetIconImage")
        assertTrue(warningIndex >= 0)
        assertTrue(targetIconIndex >= 0)
        assertTrue(warningIndex < targetIconIndex)
        val element = Regex("""<TextView\b[^>]*?@\+id/replaceWarningText[^>]*?/?>""")
            .find(layout)?.value
        assertNotNull(element)
        assertTrue(element!!.contains("android:visibility=\"gone\""))
        assertTrue(element.contains("style=\"?materialAlertDialogBodyTextStyle\""))
        assertTrue(element.contains("@string/file_job_replace_warning"))
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Pins the color state list items behind Material 3 secondary text (#27). This only checks the
 * static resource XML; the resolved on-screen colors are verified by measurement.
 */
class SecondaryTextColorResourceTest {
    // U-27-01
    @Test
    fun staticSecondaryText_defaultIsNeutralVariant40() {
        val items = items("color/m3_default_color_secondary_text.xml")
        assertEquals(2, items.size)
        assertEquals(mapOf("color" to "@color/m3_ref_palette_neutral_variant40"), items[1])
    }

    // U-27-02
    @Test
    fun staticSecondaryText_disabledUnchanged() {
        val items = items("color/m3_default_color_secondary_text.xml")
        assertEquals(
            mapOf(
                "state_enabled" to "false",
                "alpha" to "@dimen/material_emphasis_disabled",
                "color" to "@color/m3_sys_color_light_on_surface"
            ),
            items[0]
        )
    }

    // U-27-03
    @Test
    fun dynamicSecondaryText_defaultIsDynamicNeutralVariant40() {
        val items = items("color-v31/m3_dynamic_default_color_secondary_text.xml")
        assertEquals(2, items.size)
        assertEquals(
            mapOf(
                "state_enabled" to "false",
                "alpha" to "@dimen/material_emphasis_disabled",
                "color" to "@color/m3_sys_color_dynamic_light_on_surface"
            ),
            items[0]
        )
        assertEquals(mapOf("color" to "@color/m3_ref_palette_dynamic_neutral_variant40"), items[1])
    }

    // U-27-04
    @Test
    fun drawerSubtitle_defaultFollowsSecondaryText() {
        val items = items("color/navigation_item_subtitle_text_color_material3.xml")
        assertEquals(
            listOf(
                mapOf(
                    "alpha" to "@dimen/material_emphasis_disabled",
                    "color" to "?colorOutline",
                    "state_enabled" to "false"
                ),
                mapOf("color" to "?colorOnSecondaryContainer", "state_checked" to "true"),
                mapOf("color" to "?android:textColorSecondary")
            ),
            items
        )
    }

    // U-27-05
    @Test
    fun darkSecondaryText_unchanged() {
        assertEquals(
            "@color/m3_sys_color_dark_outline",
            items("color/m3_dark_default_color_secondary_text.xml")[1]["color"]
        )
        assertEquals(
            "@color/m3_sys_color_dynamic_dark_outline",
            items("color-v31/m3_dynamic_dark_default_color_secondary_text.xml")[1]["color"]
        )
    }

    private fun items(path: String): List<Map<String, String>> {
        val file = File("src/main/res/$path")
        assertTrue("Missing resource file: ${file.absolutePath}", file.exists())
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        val selector = document.documentElement
        assertEquals("selector", selector.tagName)
        val items = mutableListOf<Map<String, String>>()
        val children = selector.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != "item") {
                continue
            }
            val attributes = child.attributes
            val map = mutableMapOf<String, String>()
            for (j in 0 until attributes.length) {
                val attribute = attributes.item(j)
                if (attribute.namespaceURI == ANDROID_NAMESPACE) {
                    map[attribute.localName] = attribute.nodeValue
                }
            }
            items += map
        }
        return items
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

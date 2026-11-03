package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPaneLayoutTest {
    // U-01
    @Test
    fun isDualPaneWidth_exactly600Dp_isDualPane() {
        assertTrue(isDualPaneWidth(1575, 2.625f))
    }

    // U-02
    @Test
    fun isDualPaneWidth_below600Dp_isSinglePane() {
        assertFalse(isDualPaneWidth(1574, 2.625f))
        assertFalse(isDualPaneWidth(599, 1f))
    }

    // U-03
    @Test
    fun computeAvailableWidthPx_modalDrawer_usesOwnWidth() {
        assertEquals(1000, computeAvailableWidthPx(1000, 1200, null, false))
    }

    // U-04
    @Test
    fun computeAvailableWidthPx_persistentDrawer_usesTargetState() {
        // The own width varies while the drawer animates, but doesn't affect the result.
        assertEquals(1360, computeAvailableWidthPx(1360, 2000, 640, true))
        assertEquals(1360, computeAvailableWidthPx(1700, 2000, 640, true))
        assertEquals(2000, computeAvailableWidthPx(2000, 2000, 640, false))
        assertEquals(2000, computeAvailableWidthPx(1500, 2000, 640, false))
    }

    // U-05
    @Test
    fun computeAvailableWidthPx_drawerWiderThanParent_isZero() {
        assertEquals(0, computeAvailableWidthPx(0, 500, 640, true))
    }
}

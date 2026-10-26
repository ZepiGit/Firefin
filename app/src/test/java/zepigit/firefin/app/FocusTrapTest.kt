package zepigit.firefin.app

import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.ui.FocusTrap

class FocusTrapTest {
    @Test fun `sideways moves that leave the list or its line keep the focused card`() {
        assertTrue(FocusTrap.keepsFocus(View.FOCUS_RIGHT, nextOnSameLine = false))
        assertTrue(FocusTrap.keepsFocus(View.FOCUS_LEFT, nextOnSameLine = false))
    }

    @Test fun `sideways moves along the line and all vertical moves pass through`() {
        assertFalse(FocusTrap.keepsFocus(View.FOCUS_RIGHT, nextOnSameLine = true))
        assertFalse(FocusTrap.keepsFocus(View.FOCUS_UP, nextOnSameLine = false))
        assertFalse(FocusTrap.keepsFocus(View.FOCUS_DOWN, nextOnSameLine = false))
    }
}

package zepigit.firefin.app

import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.ui.FocusTrap

class FocusTrapTest {
    @Test fun `sideways moves that leave the list keep the focused card`() {
        assertTrue(FocusTrap.keepsFocus(View.FOCUS_RIGHT, nextInsideList = false))
        assertTrue(FocusTrap.keepsFocus(View.FOCUS_LEFT, nextInsideList = false))
    }

    @Test fun `sideways moves inside the list and all vertical moves pass through`() {
        assertFalse(FocusTrap.keepsFocus(View.FOCUS_RIGHT, nextInsideList = true))
        assertFalse(FocusTrap.keepsFocus(View.FOCUS_UP, nextInsideList = false))
        assertFalse(FocusTrap.keepsFocus(View.FOCUS_DOWN, nextInsideList = false))
    }
}

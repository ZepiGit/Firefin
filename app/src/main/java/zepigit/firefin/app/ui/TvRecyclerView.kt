package zepigit.firefin.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView whose D-pad focus never leaves the list sideways. At the end
 * of a row or grid line the framework's FocusFinder would otherwise jump to
 * whatever view lies further in that direction (another row, a toolbar
 * button); here LEFT/RIGHT stay on the focused card. Vertical moves are
 * unchanged, so rows and grids still hand focus to the header above them.
 */
class TvRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {
    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        return if (FocusTrap.keepsFocus(direction, next != null && isDescendant(next))) focused else next
    }

    private fun isDescendant(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }
}

object FocusTrap {
    /** True when a sideways move found nothing inside the list and must therefore stay put. */
    fun keepsFocus(direction: Int, nextInsideList: Boolean): Boolean =
        (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && !nextInsideList
}

/** Runs [block] once after the next layout pass; API-21 replacement for doOnLayout. */
fun View.afterNextLayout(block: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            block()
        }
    })
}

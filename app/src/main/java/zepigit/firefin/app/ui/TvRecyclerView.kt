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
        if (next == null || focused == null) return next
        // Same list and same line: a short last grid row must not jump diagonally into the row above.
        val from = findContainingItemView(focused)
        val to = findContainingItemView(next)
        val sameLine = from != null && to != null && Math.abs(to.top - from.top) < from.height / 2
        return if (FocusTrap.keepsFocus(direction, sameLine)) focused else next
    }
}

object FocusTrap {
    /** True when a sideways move found no card on the same line of this list and must therefore stay put. */
    fun keepsFocus(direction: Int, nextOnSameLine: Boolean): Boolean =
        (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && !nextOnSameLine
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

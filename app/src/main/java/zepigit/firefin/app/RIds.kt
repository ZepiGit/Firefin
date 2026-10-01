package zepigit.firefin.app

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** Marker kept for parity with legacy card adapters; no behavior attached. */
@Suppress("unused")
abstract class BindableHolder(view: View) : RecyclerView.ViewHolder(view)

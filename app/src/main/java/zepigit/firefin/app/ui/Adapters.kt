package zepigit.firefin.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.UserView
import zepigit.firefin.app.images.ArtworkPolicy
import zepigit.firefin.app.util.Urls

/** Horizontal media card with stable item ids and poster-size artwork. */
class MediaCardAdapter(
    private val posterStyle: Boolean = true,
    private val onClick: (MediaItem) -> Unit,
) : RecyclerView.Adapter<MediaCardAdapter.Holder>() {

    private val items = mutableListOf<MediaItem>()

    fun submit(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun appendItems(newItems: List<MediaItem>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    override fun getItemCount(): Int = items.size
    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
    override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(if (posterStyle) R.layout.item_card else R.layout.item_card_landscape, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = if (item.isEpisode && item.seriesName.isNotEmpty()) {
            "${item.seriesName} · ${item.name}"
        } else {
            item.name
        }
        val width = if (posterStyle) ArtworkPolicy.POSTER_WIDTH else ArtworkPolicy.LANDSCAPE_WIDTH
        val imageType = if (posterStyle && item.posterTag != null) "Primary" else "Thumb"
        val tag = if (posterStyle) item.posterTag else (item.thumbTag ?: item.posterTag)
        val url = Urls.imageUrl(
            ServiceLocator.client.baseUrl,
            item.id,
            imageType,
            width,
            tag,
            ServiceLocator.session.accessToken,
        )
        ServiceLocator.images.load(url, width, holder.image)
        holder.card.setOnClickListener { onClick(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: FrameLayout = view.findViewById(R.id.card)
        val image: ImageView = view.findViewById(R.id.image)
        val name: TextView = view.findViewById(R.id.name)
    }
}

data class HomeRow(
    val title: String,
    val items: List<MediaItem>,
    val parentViewId: String? = null,
)

/** Vertical list of home rows; row titles open the underlying library. */
class HomeRowsAdapter(
    private val onItem: (MediaItem) -> Unit,
    private val onRowTitle: (UserView) -> Unit,
) : RecyclerView.Adapter<HomeRowsAdapter.RowHolder>() {

    private val rows = mutableListOf<HomeRow>()
    private val viewIds = mutableMapOf<Int, String>()

    fun submitRows(newRows: List<HomeRow>) {
        rows.clear()
        rows.addAll(newRows.filter { it.items.isNotEmpty() })
        viewIds.clear()
        rows.forEachIndexed { index, row -> row.parentViewId?.let { viewIds[index] = it } }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size
    override fun getItemId(position: Int): Long = rows[position].title.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_home_row, parent, false)
        return RowHolder(view)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        holder.title.setOnClickListener {
            viewIds[position]?.let { id ->
                onRowTitle(UserView(id, row.title, ""))
            }
        }
        if (holder.items.adapter == null) {
            holder.items.layoutManager = LinearLayoutManager(holder.items.context, LinearLayoutManager.HORIZONTAL, false)
            holder.items.adapter = MediaCardAdapter(onClick = onItem)
        }
        (holder.items.adapter as MediaCardAdapter).submit(row.items)
    }

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val items: RecyclerView = view.findViewById(R.id.rowItems)
    }
}

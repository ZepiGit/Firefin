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

class MediaCardAdapter(
    private val posterStyle: Boolean = true,
    /** Episodes inside a season list show only their episode number, not the series name. */
    private val inSeason: Boolean = false,
    private val onFocus: (MediaItem) -> Unit = {},
    private val artwork: ((MediaItem, ImageView) -> Unit)? = null,
    private val onClick: (MediaItem) -> Unit,
) : RecyclerView.Adapter<MediaCardAdapter.Holder>() {
    val posterStyleForBinding: Boolean get() = posterStyle
    init { setHasStableIds(true) }
    private val items = mutableListOf<MediaItem>()
    fun submit(newItems: List<MediaItem>) {
        if (items == newItems) return
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }
    fun appendItems(newItems: List<MediaItem>) {
        val unique = newItems.filter { next -> items.none { it.id == next.id } }
        val start = items.size; items.addAll(unique); notifyItemRangeInserted(start, unique.size)
    }
    fun positionOf(item: MediaItem) = items.indexOfFirst { it.id == item.id }
    override fun getItemCount() = items.size
    override fun getItemId(position: Int) = items[position].id.hashCode().toLong()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(if (posterStyle) R.layout.item_card else R.layout.item_card_landscape, parent, false),
    )
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = when {
            item.isEpisode && inSeason -> listOfNotNull(item.indexNumber.takeIf { it > 0 }?.let { String.format(java.util.Locale.ROOT, "E%02d", it) }, item.name).joinToString(" · ")
            item.isEpisode -> listOfNotNull(item.seriesName.ifBlank { null }, item.episodeCode, item.name).joinToString(" · ")
            item.type == "Season" && item.seriesName.isNotEmpty() -> "${item.seriesName} · ${item.name}"
            else -> item.name
        }
        holder.subtitle.text = buildString {
            if (item.year > 0) append(item.year)
            if (item.played) append("  ✓ ").append(holder.itemView.context.getString(R.string.mark_watched))
            else if (item.resumeTicks > 0) append("  ▶ ").append(holder.itemView.context.getString(R.string.resume))
        }
        if (artwork != null) artwork.invoke(item, holder.image) else {
            val type = when {
                posterStyle && item.posterTag != null -> "Primary"
                item.thumbTag != null -> "Thumb"
                item.backdropTag != null -> "Backdrop"
                else -> "Primary"
            }
            val width = if (type == "Primary") ArtworkPolicy.POSTER_WIDTH else ArtworkPolicy.LANDSCAPE_WIDTH
            val tag = when (type) { "Primary" -> item.posterTag; "Thumb" -> item.thumbTag; else -> item.backdropTag }
            val url = Urls.imageUrl(ServiceLocator.client.baseUrl, item.id, type, width, tag,
                maxHeight = if (type == "Primary") ArtworkPolicy.POSTER_MAX_HEIGHT else null)
            ServiceLocator.images.load(url, ArtworkPolicy.decodeBucket(width), holder.image)
        }
        holder.card.setOnClickListener { onClick(item) }
        // Card focus expansion is locked off on this target (EffectiveDevicePreferences);
        // focus only starts the title marquee and notifies the screen.
        holder.card.setOnFocusChangeListener { _, focused ->
            holder.name.isSelected = focused
            if (focused) onFocus(item)
        }
        holder.name.isSelected = holder.card.hasFocus()
    }
    override fun onViewRecycled(holder: Holder) {
        ServiceLocator.images.cancel(holder.image)
        holder.card.setOnFocusChangeListener(null)
        super.onViewRecycled(holder)
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: FrameLayout = view.findViewById(R.id.card)
        val image: ImageView = view.findViewById(R.id.image)
        val name: TextView = view.findViewById(R.id.name)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
    }
}

data class HomeRow(val title: String, val items: List<MediaItem>, val libraryId: String? = null, val libraryName: String? = null, val libraryType: String = "", val landscape: Boolean = false)

class HomeRowsAdapter(
    private val onItem: (MediaItem) -> Unit,
    private val onFocus: (MediaItem) -> Unit = {},
    private val onRowTitle: (UserView) -> Unit,
) : RecyclerView.Adapter<HomeRowsAdapter.RowHolder>() {
    init { setHasStableIds(true) }
    private val rows = mutableListOf<HomeRow>()
    fun submitRows(newRows: List<HomeRow>) {
        val visible = newRows.filter { it.items.isNotEmpty() }
        if (rows == visible) return
        rows.clear(); rows.addAll(visible); notifyDataSetChanged()
    }
    override fun getItemCount() = rows.size
    override fun getItemId(position: Int) = rows[position].title.hashCode().toLong()
    override fun getItemViewType(position: Int) = if (rows[position].landscape) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder = RowHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_home_row, parent, false))
    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        val library = row.libraryId != null
        holder.title.isFocusable = library
        holder.title.isClickable = library
        holder.title.setOnClickListener(if (library) View.OnClickListener { onRowTitle(UserView(row.libraryId!!, row.libraryName ?: row.title, row.libraryType)) } else null)
        if (holder.items.adapter == null || holder.items.adapter is MediaCardAdapter && (holder.items.adapter as MediaCardAdapter).posterStyleForBinding != !row.landscape) {
            holder.items.layoutManager = LinearLayoutManager(holder.items.context, LinearLayoutManager.HORIZONTAL, false)
            holder.items.adapter = MediaCardAdapter(posterStyle = !row.landscape, onFocus = onFocus, onClick = onItem)
        }
        (holder.items.adapter as MediaCardAdapter).submit(row.items)
    }
    class RowHolder(view: View) : RecyclerView.ViewHolder(view) { val title: TextView = view.findViewById(R.id.rowTitle); val items: RecyclerView = view.findViewById(R.id.rowItems) }
}

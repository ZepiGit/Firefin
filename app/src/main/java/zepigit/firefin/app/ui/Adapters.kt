package zepigit.firefin.app.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    /** MENU key on a card; only Jellyfin cards get one, Seerr cards carry synthetic ids. */
    private val onMenu: ((MediaItem) -> Unit)? = null,
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
    /** Rebinds one card after its user data changed on the server. */
    fun replace(item: MediaItem) {
        val index = positionOf(item)
        if (index >= 0) { items[index] = item; notifyItemChanged(index) }
    }
    fun remove(itemId: String) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index >= 0) { items.removeAt(index); notifyItemRemoved(index) }
    }
    override fun getItemCount() = items.size
    override fun getItemId(position: Int) = stableId(items[position].id)
    /** Poster and landscape holders must never be swapped through a shared pool. */
    override fun getItemViewType(position: Int) = if (posterStyle) TYPE_POSTER else TYPE_LANDSCAPE
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
        // Resume position as a static bar under the artwork; width is set from the card's own pixel width.
        val fraction = if (item.resumeTicks > 0 && item.runTimeTicks > 0) (item.resumeTicks.toDouble() / item.runTimeTicks).coerceIn(0.02, 1.0) else 0.0
        holder.progress.visibility = if (fraction > 0) View.VISIBLE else View.GONE
        if (fraction > 0) holder.progress.layoutParams = holder.progress.layoutParams.apply {
            width = ((holder.card.layoutParams.width - 2 * holder.card.paddingLeft) * fraction).toInt()
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
            // Decode for the card's own pixel width, not the server request class (a 440 px landscape card needs 480, not 640).
            ServiceLocator.images.load(url, ArtworkPolicy.decodeBucket(holder.card.layoutParams.width), holder.image)
        }
        holder.card.setOnClickListener { onClick(item) }
        holder.card.setOnKeyListener(onMenu?.let { menu ->
            View.OnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { menu(item); true } else false
            }
        })
        // Card focus expansion is locked off on this target (EffectiveDevicePreferences);
        // the static background selector is the whole focus effect.
        holder.card.setOnFocusChangeListener { _, focused -> if (focused) onFocus(item) }
    }
    override fun onViewRecycled(holder: Holder) {
        ServiceLocator.images.cancel(holder.image)
        holder.card.setOnFocusChangeListener(null)
        holder.card.setOnKeyListener(null)
        super.onViewRecycled(holder)
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val image: ImageView = view.findViewById(R.id.image)
        val progress: View = view.findViewById(R.id.progress)
        val name: TextView = view.findViewById(R.id.name)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
    }

    companion object {
        const val TYPE_POSTER = 1
        const val TYPE_LANDSCAPE = 2
    }
}

/** 64-bit FNV-1a of [key]; String.hashCode is 32-bit and collides in large libraries, which confuses focus restore. */
fun stableId(key: String): Long {
    var hash = -0x340d631b7bdddcdbL
    for (byte in key.toByteArray(Charsets.UTF_8)) hash = (hash xor (byte.toLong() and 0xff)) * 0x100000001b3L
    return hash
}

data class HomeRow(val title: String, val items: List<MediaItem>, val libraryId: String? = null, val libraryName: String? = null, val libraryType: String = "", val landscape: Boolean = false)

class HomeRowsAdapter(
    private val onItem: (MediaItem) -> Unit,
    private val onFocus: (MediaItem) -> Unit = {},
    private val onRowTitle: (UserView) -> Unit,
    private val onMenu: ((MediaItem) -> Unit)? = null,
) : RecyclerView.Adapter<HomeRowsAdapter.RowHolder>() {
    init { setHasStableIds(true) }
    private val rows = mutableListOf<HomeRow>()
    /** Card views are shared across rows, so scrolling down reuses holders instead of inflating a fresh set per row. */
    private val cardPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(MediaCardAdapter.TYPE_POSTER, 16)
        setMaxRecycledViews(MediaCardAdapter.TYPE_LANDSCAPE, 12)
    }
    fun submitRows(newRows: List<HomeRow>) {
        val visible = newRows.filter { it.items.isNotEmpty() }
        if (rows == visible) return
        rows.clear(); rows.addAll(visible); notifyDataSetChanged()
    }
    override fun getItemCount() = rows.size
    override fun getItemId(position: Int) = stableId(rows[position].title)
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
            holder.items.layoutManager = LinearLayoutManager(holder.items.context, LinearLayoutManager.HORIZONTAL, false).apply { initialPrefetchItemCount = 6 }
            holder.items.setRecycledViewPool(cardPool)
            holder.items.adapter = MediaCardAdapter(posterStyle = !row.landscape, onFocus = onFocus, onMenu = onMenu, onClick = onItem)
        }
        (holder.items.adapter as MediaCardAdapter).submit(row.items)
    }
    class RowHolder(view: View) : RecyclerView.ViewHolder(view) { val title: TextView = view.findViewById(R.id.rowTitle); val items: RecyclerView = view.findViewById(R.id.rowItems) }
}

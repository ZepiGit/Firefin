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

/** Horizontal media card row with stable ids and policy-driven artwork classes. */
class MediaCardAdapter(
    private val posterStyle: Boolean = true,
    private val onClick: (MediaItem) -> Unit,
) : RecyclerView.Adapter<MediaCardAdapter.Holder>() {

    init {
        setHasStableIds(true)
    }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(if (posterStyle) R.layout.item_card else R.layout.item_card_landscape, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = when {
            item.isEpisode && item.seriesName.isNotEmpty() ->
                "S%d:E%d · %s".format(item.parentIndexNumber, item.indexNumber, item.name)
            else -> item.name
        }
        val (imageType, width, tag, maxHeight) = if (posterStyle && item.posterTag != null) {
            ArtworkParams("Primary", ArtworkPolicy.POSTER_WIDTH, item.posterTag, ArtworkPolicy.POSTER_MAX_HEIGHT)
        } else {
            ArtworkParams("Thumb", ArtworkPolicy.LANDSCAPE_WIDTH, item.thumbTag ?: item.backdropTag, null)
        }
        val url = Urls.imageUrl(
            ServiceLocator.client.baseUrl,
            item.id,
            imageType,
            width,
            tag,
            ServiceLocator.session.accessToken,
            maxHeight,
        )
        ServiceLocator.images.load(url, ArtworkPolicy.decodeBucket(width), holder.image)
        holder.card.setOnClickListener { onClick(item) }
    }

    private data class ArtworkParams(
        val imageType: String,
        val width: Int,
        val tag: String?,
        val maxHeight: Int?,
    )

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: FrameLayout = view.findViewById(R.id.card)
        val image: ImageView = view.findViewById(R.id.image)
        val name: TextView = view.findViewById(R.id.name)
    }
}

data class HomeRow(
    val title: String,
    val items: List<MediaItem>,
    val libraryId: String? = null,
    val libraryName: String? = null,
    val landscape: Boolean = false,
)

/** Vertical list of home rows; row titles open the underlying library. */
class HomeRowsAdapter(
    private val onItem: (MediaItem) -> Unit,
    private val onRowTitle: (UserView) -> Unit,
) : RecyclerView.Adapter<HomeRowsAdapter.RowHolder>() {

    init {
        setHasStableIds(true)
    }

    private val rows = mutableListOf<HomeRow>()

    fun submitRows(newRows: List<HomeRow>) {
        rows.clear()
        rows.addAll(newRows.filter { it.items.isNotEmpty() })
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
        if (row.libraryId != null) {
            holder.title.setOnClickListener {
                onRowTitle(UserView(row.libraryId, row.libraryName ?: row.title, ""))
            }
        } else {
            // Resume/Next-up rows have no single library behind them.
            holder.title.setOnClickListener(null)
            holder.title.isClickable = false
            holder.title.isFocusable = false
        }
        if (holder.items.adapter == null) {
            holder.items.layoutManager = LinearLayoutManager(holder.items.context, LinearLayoutManager.HORIZONTAL, false)
            holder.items.adapter = MediaCardAdapter(posterStyle = !row.landscape, onClick = onItem)
        }
        (holder.items.adapter as MediaCardAdapter).submit(row.items)
    }

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val items: RecyclerView = view.findViewById(R.id.rowItems)
    }
}

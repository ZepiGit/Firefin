package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.PageCursor
import zepigit.firefin.app.images.ArtworkPolicy
import zepigit.firefin.app.util.Ticks
import zepigit.firefin.app.util.Urls

/** Item details with backdrop, play/resume, favorite/watched and paged children. */
class DetailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var item: MediaItem? = null
    private var itemId = ""
    private var firstResume = true
    private var loadJob: Job? = null
    private var startingPlayback = false
    private var childCursor = PageCursor()
    private var childAdapter: MediaCardAdapter? = null

    private lateinit var backdrop: ImageView
    private lateinit var name: TextView
    private lateinit var meta: TextView
    private lateinit var overview: TextView
    private lateinit var play: Button
    private lateinit var restart: Button
    private lateinit var favorite: Button
    private lateinit var watched: Button
    private lateinit var retry: Button
    private lateinit var progress: ProgressBar
    private lateinit var children: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        itemId = intent.getStringExtra(EXTRA_ID) ?: return finish()
        backdrop = findViewById(R.id.backdrop)
        name = findViewById(R.id.name)
        meta = findViewById(R.id.meta)
        overview = findViewById(R.id.overview)
        play = findViewById(R.id.playButton)
        restart = findViewById(R.id.restartButton)
        favorite = findViewById(R.id.favoriteButton)
        watched = findViewById(R.id.watchedButton)
        retry = findViewById(R.id.retryButton)
        progress = findViewById(R.id.progress)
        children = findViewById(R.id.children)
        retry.setOnClickListener { loadItem() }
        loadItem()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Re-queries user data on return from the player so resume/watch state stays current. */
    override fun onResume() {
        super.onResume()
        if (firstResume) firstResume = false else refreshUserData()
    }

    private fun loadItem() {
        loadItemInternal(reloadChildren = true)
    }

    private fun refreshUserData() {
        loadItemInternal(reloadChildren = false)
    }

    private fun loadItemInternal(reloadChildren: Boolean) {
        progress.visibility = View.VISIBLE
        retry.visibility = View.GONE
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val loaded = ServiceLocator.client.item(itemId)
                item = loaded
                name.text = displayName(loaded)
                meta.text = buildString {
                    if (loaded.year > 0) append(loaded.year)
                    if (loaded.runTimeTicks > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(Ticks.format(loaded.runTimeTicks))
                    }
                    if (loaded.resumeTicks > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(getString(R.string.detail_remaining, Ticks.format(loaded.runTimeTicks - loaded.resumeTicks)))
                    }
                }
                overview.text = loaded.overview
                // Leaf items play directly; a series offers its next episode; other
                // containers (Season, BoxSet, Folder) have no play button.
                when {
                    loaded.isPlayable -> {
                        play.visibility = View.VISIBLE
                        play.text = if (loaded.resumeTicks > 0) getString(R.string.resume) else getString(R.string.play)
                        play.setOnClickListener { startPlayback(loaded, loaded.resumeTicks) }
                    }
                    loaded.isSeries -> showNextEpisode(loaded)
                    else -> play.visibility = View.GONE
                }
                restart.visibility = if (loaded.isPlayable && loaded.resumeTicks > 0) View.VISIBLE else View.GONE
                restart.setOnClickListener { startPlayback(loaded, 0L) }
                favorite.text = if (loaded.favorite) "★ " + getString(R.string.favorite) else getString(R.string.favorite)
                watched.text = if (loaded.played) "✓ " + getString(R.string.mark_watched) else getString(R.string.mark_watched)
                favorite.setOnClickListener {
                    scope.launch {
                        try { ServiceLocator.client.setFavorite(loaded.id, !loaded.favorite); refreshUserData() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Toast.makeText(this@DetailActivity, errorMessage(e), Toast.LENGTH_LONG).show() }
                    }
                }
                watched.setOnClickListener {
                    scope.launch {
                        try { ServiceLocator.client.setPlayed(loaded.id, !loaded.played); refreshUserData() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Toast.makeText(this@DetailActivity, errorMessage(e), Toast.LENGTH_LONG).show() }
                    }
                }
                val backdropUrl = Urls.imageUrl(
                    ServiceLocator.client.baseUrl,
                    loaded.id,
                    if (loaded.backdropTag != null) "Backdrop" else "Primary",
                    ArtworkPolicy.BACKDROP_WIDTH,
                    loaded.backdropTag,
                )
                if (ServiceLocator.preferences.effective().stored.backdropEnabled) ServiceLocator.images.load(backdropUrl, ArtworkPolicy.decodeBucket(ArtworkPolicy.BACKDROP_WIDTH), backdrop)
                else ServiceLocator.images.cancel(backdrop)
                if (!loaded.isPlayable && reloadChildren) {
                    showChildren(loaded)
                } else if (reloadChildren) {
                    play.requestFocus()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showLoadError(e)
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun showLoadError(error: Exception) {
        overview.text = errorMessage(error, R.string.load_error)
        retry.visibility = View.VISIBLE
        retry.requestFocus()
    }

    /** Offers the series' next-up episode; the button stays hidden when the server has none. */
    private suspend fun showNextEpisode(series: MediaItem) {
        val next = try {
            ServiceLocator.client.nextUp(limit = 1, seriesId = series.id).firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (next == null) {
            play.visibility = View.GONE
            return
        }
        play.visibility = View.VISIBLE
        play.text = getString(R.string.continue_series, next.episodeCode ?: next.name)
        play.setOnClickListener { startPlayback(next, next.resumeTicks) }
    }

    /**
     * Children load page by page. The next page is requested when the D-pad focus
     * gets close to the loaded end; a failed page keeps the loaded ones and is
     * requested again on the next focus move.
     */
    private fun showChildren(container: MediaItem) {
        val cursor = PageCursor().also { childCursor = it }
        val adapter = MediaCardAdapter(
            posterStyle = container.type != "Season",
            inSeason = container.type == "Season",
            onFocus = { child -> childAdapter?.let { if (cursor.nearEnd(it.positionOf(child))) loadChildPage(container, cursor) } },
            onClick = { child -> start(this, child.id) },
        )
        childAdapter = adapter
        children.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        children.adapter = adapter
        findViewById<TextView>(R.id.childrenTitle).setText(
            if (container.isSeries) R.string.children_seasons else if (container.type == "Season") R.string.children_episodes else R.string.children_items,
        )
        loadChildPage(container, cursor, focusFirst = true)
    }

    private fun loadChildPage(container: MediaItem, cursor: PageCursor, focusFirst: Boolean = false) {
        if (cursor !== childCursor) return
        val start = cursor.next() ?: return
        scope.launch {
            try {
                val (page, total) = ServiceLocator.client.children(container, start)
                if (cursor !== childCursor) return@launch
                cursor.received(page.size, total)
                childAdapter?.appendItems(page)
                if (focusFirst && page.isNotEmpty()) children.post { children.getChildAt(0)?.requestFocus() }
            } catch (e: CancellationException) {
                cursor.failed()
                throw e
            } catch (e: Exception) {
                cursor.failed()
                if (cursor !== childCursor) return@launch
                if (start == 0) showLoadError(e)
                else Toast.makeText(this@DetailActivity, R.string.children_load_more_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayName(item: MediaItem): String =
        if (item.isEpisode && item.seriesName.isNotEmpty()) listOfNotNull(item.seriesName, item.episodeCode, item.name).joinToString(" · ")
        else item.name

    private fun startPlayback(loaded: MediaItem, startTicks: Long) {
        if (startingPlayback) return
        startingPlayback = true
        scope.launch {
            try {
                val source = ServiceLocator.client.playbackInfo(loaded.id)
                PlayerActivity.start(
                    this@DetailActivity,
                    itemId = loaded.id,
                    url = source.url,
                    playSessionId = source.playSessionId,
                    mediaSourceId = source.mediaSourceId,
                    startMs = Ticks.toMs(startTicks),
                    name = displayName(loaded),
                    isTranscode = source.isTranscode,
                    isHls = source.isHls,
                    liveStreamId = source.liveStreamId,
                    subtitleUrl = source.subtitleUrl,
                    subtitleMime = source.subtitleMime,
                    playMethod = source.playMethod,
                    tracks = source.tracks,
                    audioIndex = source.audioIndex,
                    subtitleIndex = source.subtitleIndex,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, errorMessage(e), Toast.LENGTH_LONG).show()
            } finally {
                startingPlayback = false
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "item.id"

        fun start(context: Context, itemId: String) {
            context.startActivity(Intent(context, DetailActivity::class.java).putExtra(EXTRA_ID, itemId))
        }
    }
}

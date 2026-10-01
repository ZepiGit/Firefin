package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import zepigit.firefin.app.images.ArtworkPolicy
import zepigit.firefin.app.util.Ticks
import zepigit.firefin.app.util.Urls

/** Item details with backdrop, play/resume, favorite/watched and episode children. */
class DetailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var item: MediaItem? = null
    private var itemId = ""
    private var firstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        itemId = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val backdrop = findViewById<ImageView>(R.id.backdrop)
        val name = findViewById<TextView>(R.id.name)
        val meta = findViewById<TextView>(R.id.meta)
        val overview = findViewById<TextView>(R.id.overview)
        val play = findViewById<Button>(R.id.playButton)
        val favorite = findViewById<Button>(R.id.favoriteButton)
        val watched = findViewById<Button>(R.id.watchedButton)
        val children = findViewById<RecyclerView>(R.id.children)

        loadItem()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Re-queries user data on return from the player so resume/watch state stays current. */
    override fun onResume() {
        super.onResume()
        if (!firstLoad) refreshUserData()
    }

    private fun loadItem() {
        firstLoad = false
        loadItemInternal(reloadChildren = true)
    }

    private fun refreshUserData() {
        loadItemInternal(reloadChildren = false)
    }

    private fun loadItemInternal(reloadChildren: Boolean) {
        val backdrop = findViewById<ImageView>(R.id.backdrop)
        val name = findViewById<TextView>(R.id.name)
        val meta = findViewById<TextView>(R.id.meta)
        val overview = findViewById<TextView>(R.id.overview)
        val play = findViewById<Button>(R.id.playButton)
        val favorite = findViewById<Button>(R.id.favoriteButton)
        val watched = findViewById<Button>(R.id.watchedButton)
        val children = findViewById<RecyclerView>(R.id.children)
        scope.launch {
            try {
                val loaded = ServiceLocator.client.item(itemId)
                item = loaded
                name.text = if (loaded.isEpisode && loaded.seriesName.isNotEmpty()) {
                    "${loaded.seriesName} · ${loaded.name}"
                } else {
                    loaded.name
                }
                meta.text = buildString {
                    if (loaded.year > 0) append(loaded.year)
                    if (loaded.runTimeTicks > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(Ticks.format(loaded.runTimeTicks))
                    }
                    if (loaded.resumeTicks > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("Rest: ").append(Ticks.format(loaded.runTimeTicks - loaded.resumeTicks))
                    }
                }
                overview.text = loaded.overview
                // Only leaf items can play; series get the episode strip, other
                // containers (Season, BoxSet, MusicAlbum, Folder) get no play button.
                if (loaded.isPlayable) {
                    play.visibility = View.VISIBLE
                } else {
                    play.visibility = View.GONE
                }
                play.text = if (loaded.resumeTicks > 0) getString(R.string.resume) else getString(R.string.play)
                favorite.text = if (loaded.favorite) "★ " + getString(R.string.favorite) else getString(R.string.favorite)
                watched.text = if (loaded.played) "✓ " + getString(R.string.mark_watched) else getString(R.string.mark_watched)
                play.setOnClickListener { startPlayback(loaded, loaded.resumeTicks) }
                favorite.setOnClickListener {
                    scope.launch {
                        try { ServiceLocator.client.setFavorite(loaded.id, !loaded.favorite); refreshUserData() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Toast.makeText(this@DetailActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show() }
                    }
                }
                watched.setOnClickListener {
                    scope.launch {
                        try { ServiceLocator.client.setPlayed(loaded.id, !loaded.played); refreshUserData() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Toast.makeText(this@DetailActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show() }
                    }
                }
                val backdropUrl = Urls.imageUrl(
                    ServiceLocator.client.baseUrl,
                    loaded.id,
                    if (loaded.backdropTag != null) "Backdrop" else "Primary",
                    ArtworkPolicy.BACKDROP_WIDTH,
                    loaded.backdropTag,
                    ServiceLocator.session.accessToken,
                )
                ServiceLocator.images.load(backdropUrl, ArtworkPolicy.decodeBucket(ArtworkPolicy.BACKDROP_WIDTH), backdrop)
                if (loaded.isSeries && reloadChildren) {
                    children.layoutManager = LinearLayoutManager(
                        this@DetailActivity,
                        LinearLayoutManager.HORIZONTAL,
                        false,
                    )
                    children.adapter = MediaCardAdapter(onClick = { child ->
                        if (child.isPlayable) startPlayback(child, child.resumeTicks)
                        else DetailActivity.start(this@DetailActivity, child.id)
                    })
                    val episodes = ServiceLocator.client.children(loaded.id)
                    (children.adapter as MediaCardAdapter).submit(episodes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPlayback(loaded: MediaItem, startTicks: Long) {
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
                    name = loaded.name,
                    isTranscode = source.isTranscode,
                    isHls = source.isHls,
                )
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, e.message ?: "Playback-Fehler", Toast.LENGTH_LONG).show()
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

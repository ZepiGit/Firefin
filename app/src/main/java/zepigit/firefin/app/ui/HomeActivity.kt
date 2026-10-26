package zepigit.firefin.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.SessionExpiredException
import zepigit.firefin.app.data.UserView

/**
 * Home: resume, next up and per-library latest rows in one vertical
 * RecyclerView with stable ids; the scroll position is restored after the
 * rows are (re)loaded. Resume/Next-up rows use landscape artwork; per-library
 * latest rows use posters.
 */
class HomeActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var rowsView: RecyclerView
    private lateinit var rowsAdapter: HomeRowsAdapter
    private lateinit var progress: ProgressBar
    private lateinit var stateView: View
    private lateinit var emptyView: TextView
    private lateinit var retryButton: View
    private lateinit var previewName: TextView
    private lateinit var previewMeta: TextView
    private lateinit var previewOverview: TextView
    private lateinit var previewImage: android.widget.ImageView
    private var pendingScroll: Int? = null
    private var homeLoaded = false
    private var loadingHome = false
    private var libraries = emptyList<UserView>()
    private var previewJob: Job? = null
    private var latestRows = emptyList<HomeRow>()
    /** Read once per resume instead of hashing the account key on every focus change. */
    private var backdropsEnabled = true
    private var lastKeyUptime = 0L
    private var loadedOrder = emptyList<String>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastKeyUptime = event.eventTime
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ServiceLocator.session.isLoggedIn) {
            LoginActivity.startFresh(this)
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        val toolbarActionLabel = findViewById<TextView>(R.id.toolbarActionLabel)
        val toolbarButtons = listOf(
            R.id.homeButton, R.id.searchButton, R.id.libraryButton,
            R.id.mediaRequestsButton, R.id.randomButton, R.id.favoritesButton,
            R.id.settingsButton,
        ).map { findViewById<View>(it) }
        for (button in toolbarButtons) {
            button.setOnFocusChangeListener { view, hasFocus ->
                toolbarActionLabel.text = if (hasFocus) view.contentDescription else ""
                toolbarActionLabel.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            }
        }
        toolbarButtons.firstOrNull { it.hasFocus() }?.let {
            toolbarActionLabel.text = it.contentDescription
            toolbarActionLabel.visibility = View.VISIBLE
        }
        // Self-targeted XML focus ids alone still allow Android's fallback focus search.
        toolbarButtons.first().setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_DPAD_LEFT }
        toolbarButtons.last().setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_DPAD_RIGHT }

        rowsView = findViewById(R.id.rows)
        progress = findViewById(R.id.progress)
        stateView = findViewById(R.id.stateView)
        emptyView = findViewById(R.id.stateText)
        retryButton = findViewById(R.id.stateRetry)
        retryButton.setOnClickListener { loadHome() }
        previewName = findViewById(R.id.previewName)
        previewMeta = findViewById(R.id.previewMeta)
        previewOverview = findViewById(R.id.previewOverview)
        previewImage = findViewById(R.id.previewImage)
        rowsAdapter = HomeRowsAdapter(
            onItem = { item -> DetailActivity.start(this, item.id) },
            onFocus = ::preview,
            onRowTitle = { row ->
                LibraryActivity.start(this, row.libraryId.orEmpty(), row.libraryName ?: row.title, row.libraryType, favorites = row.kind == "favorites")
            },
            onMenu = { item -> CardMenu.show(this, scope, item, ::onCardChanged) },
        )
        rowsView.layoutManager = LinearLayoutManager(this)
        rowsView.adapter = rowsAdapter

        findViewById<View>(R.id.searchButton).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<android.view.View>(R.id.homeButton).setOnClickListener {
            rowsView.scrollToPosition(0)
            rowsView.post { rowsView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
            loadHome(refreshOnly = homeLoaded)
        }
        findViewById<View>(R.id.randomButton).setOnClickListener {
            scope.launch {
                try { ServiceLocator.client.randomItem()?.let { DetailActivity.start(this@HomeActivity, it.id) } ?: Toast.makeText(this@HomeActivity, R.string.empty_library, Toast.LENGTH_SHORT).show() }
                catch (e: CancellationException) { throw e }
                catch (e: SessionExpiredException) {
                    ServiceLocator.session.clear()
                    LoginActivity.startFresh(this@HomeActivity)
                }
                catch (e: Exception) { Toast.makeText(this@HomeActivity, errorMessage(e), Toast.LENGTH_LONG).show() }
            }
        }
        findViewById<View>(R.id.mediaRequestsButton).setOnClickListener { startActivity(Intent(this, SeerrActivity::class.java)) }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.libraryButton).setOnClickListener {
            // Music, Live TV, books, photos and playlists have no native player yet; hide them instead of dead ends.
            val browsable = libraries.filter { it.collectionType in BROWSABLE_LIBRARY_TYPES }
            if (browsable.isEmpty()) Toast.makeText(this, R.string.empty_library, Toast.LENGTH_SHORT).show()
            else androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.libraries).setItems(browsable.map { it.name }.toTypedArray()) { _, index ->
                    val library = browsable[index]
                    LibraryActivity.start(this, library.id, library.name, library.collectionType)
                }.show()
        }
        findViewById<View>(R.id.favoritesButton).setOnClickListener {
            LibraryActivity.start(this, "", getString(R.string.favorites), favorites = true)
        }
        loadHome()
    }

    private fun preview(item: MediaItem) {
        previewJob?.cancel()
        previewJob = scope.launch {
            kotlinx.coroutines.delay(150)
            previewName.text = item.seriesName.takeIf { item.isEpisode && it.isNotBlank() } ?: item.name
            previewMeta.text = listOfNotNull(
                item.episodeCode?.let { code -> listOf(code, item.name).joinToString(" · ") },
                item.year.takeIf { it > 0 }?.toString(),
                if (item.resumeTicks > 0) getString(R.string.resume) else null,
            ).joinToString(" · ")
            previewOverview.text = item.overview
            val type = if (item.backdropTag != null) "Backdrop" else if (item.thumbTag != null) "Thumb" else "Primary"
            val tag = when (type) { "Backdrop" -> item.backdropTag; "Thumb" -> item.thumbTag; else -> item.posterTag }
            if (backdropsEnabled) {
                val width = if (type == "Primary") 320 else 960
                ServiceLocator.images.load(zepigit.firefin.app.util.Urls.imageUrl(ServiceLocator.client.baseUrl, item.id, type, width, tag), width, previewImage)
            } else ServiceLocator.images.cancel(previewImage)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val lm = rowsView.layoutManager as? LinearLayoutManager ?: return
        outState.putInt(KEY_SCROLL, lm.findFirstVisibleItemPosition())
    }

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)
        pendingScroll = state.getInt(KEY_SCROLL, -1).takeIf { it >= 0 }
    }

    override fun onResume() {
        super.onResume()
        backdropsEnabled = ServiceLocator.preferences.effective().stored.backdropEnabled
        if (::rowsAdapter.isInitialized && !backdropsEnabled) ServiceLocator.images.cancel(previewImage)
        // Refresh resume/next-up after returning from Detail or Player. The adapter
        // preserves stable row/card ids and the saved vertical position.
        if (::rowsAdapter.isInitialized && homeLoaded && !loadingHome) loadHome(refreshOnly = loadedOrder == ServiceLocator.preferences.stored().homeSectionOrder)
        else if (::rowsAdapter.isInitialized && !homeLoaded && !loadingHome) loadHome()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Rebinds the rows and keeps a usable focus: when the focused card's row
     * vanished (a finished film leaves "Continue watching"), the framework
     * drops focus entirely, so the first card takes it after the layout pass.
     */
    private fun showRows(rows: List<HomeRow>) {
        val hadRowFocus = rowsView.hasFocus()
        val submittedAt = android.os.SystemClock.uptimeMillis()
        rowsAdapter.submitRows(rows)
        val empty = rowsAdapter.itemCount == 0
        emptyView.text = if (empty) getString(R.string.empty_home) else ""
        stateView.visibility = if (empty) View.VISIBLE else View.GONE
        retryButton.visibility = View.GONE
        if (hadRowFocus) rowsView.afterNextLayout {
            // A key press since the submit means the user moved on purpose; otherwise the framework
            // may have parked focus nowhere, on the list itself or on the toolbar.
            if (lastKeyUptime > submittedAt) return@afterNextLayout
            val kept = rowsView.findFocus()
            if (kept == null || kept === rowsView) rowsView.getChildAt(0)?.findViewById<RecyclerView>(R.id.rowItems)?.getChildAt(0)?.requestFocus()
        }
    }

    /** A card changed on the server: patch the cached latest rows and reload resume and next up. */
    private fun onCardChanged(item: MediaItem) {
        latestRows = latestRows.map { row -> row.copy(items = row.items.map { if (it.id == item.id) item else it }) }
        loadHome(refreshOnly = true)
    }

    private fun loadHome(refreshOnly: Boolean = false) {
        if (loadingHome) return
        loadingHome = true
        progress.visibility = View.VISIBLE
        scope.launch {
            try {
                val order = ServiceLocator.preferences.stored().homeSectionOrder
                val rows = coroutineScope {
                    val resume = async { if ("resume" in order) ServiceLocator.client.resume() else emptyList() }
                    val nextUp = async { if ("nextUp" in order) ServiceLocator.client.nextUp() else emptyList() }
                    val favorites = async {
                        if ("favorites" in order) ServiceLocator.client.items(filters = "IsFavorite", includeTypes = "Movie,Series", sortBy = "DateCreated", sortOrder = "Descending", limit = 12).first
                        else emptyList()
                    }
                    if (!refreshOnly) {
                        val views = async { ServiceLocator.client.views() }
                        val excluded = async {
                            try { ServiceLocator.client.latestExcludes() }
                            catch (e: CancellationException) { throw e }
                            catch (e: SessionExpiredException) { throw e }
                            catch (_: Exception) { emptySet<String>() }
                        }
                        libraries = views.await()
                        val excludes = excluded.await()
                        latestRows = if ("latest" in order) libraries.filter {
                            it.collectionType in setOf("movies", "tvshows", "mixed", "") && it.id !in excludes
                        }.map { view ->
                            async {
                                HomeRow("latest", getString(R.string.latest_media) + " · " + view.name,
                                    ServiceLocator.client.latest(view.id), libraryId = view.id,
                                    libraryName = view.name, libraryType = view.collectionType)
                            }
                        }.awaitAll() else emptyList()
                    }
                    listOf(
                        HomeRow("resume", getString(R.string.continue_watching), resume.await(), landscape = true),
                        HomeRow("nextUp", getString(R.string.next_up), nextUp.await(), landscape = true),
                        HomeRow("favorites", getString(R.string.favorites), favorites.await()),
                    ) + latestRows
                }
                loadedOrder = order
                val ordered = HomeSections.order(order, rows)
                showRows(ordered)
                if (!homeLoaded) ordered.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()?.let(::preview)
                homeLoaded = true
                pendingScroll?.let {
                    (rowsView.layoutManager as LinearLayoutManager).scrollToPosition(it)
                    pendingScroll = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                ServiceLocator.session.clear()
                LoginActivity.startFresh(this@HomeActivity)
            } catch (e: Exception) {
                emptyView.text = errorMessage(e)
                stateView.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
                if (!rowsView.hasFocus()) retryButton.requestFocus()
            } finally {
                loadingHome = false
                progress.visibility = View.GONE
            }
        }
    }

    private companion object {
        const val KEY_SCROLL = "home.scroll"
        val BROWSABLE_LIBRARY_TYPES = setOf("movies", "tvshows", "homevideos", "boxsets", "mixed", "")
    }
}

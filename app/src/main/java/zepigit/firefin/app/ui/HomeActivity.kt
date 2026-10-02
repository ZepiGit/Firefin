package zepigit.firefin.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
import kotlinx.coroutines.cancel
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
    private lateinit var emptyView: TextView
    private var pendingScroll: Int? = null
    private var homeLoaded = false
    private var loadingHome = false
    private var libraries = emptyList<UserView>()
    private var previewJob: Job? = null
    private var latestRows = emptyList<HomeRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ServiceLocator.session.isLoggedIn) {
            LoginActivity.startFresh(this)
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        rowsView = findViewById(R.id.rows)
        progress = findViewById(R.id.progress)
        emptyView = findViewById(R.id.empty)
        rowsAdapter = HomeRowsAdapter(
            onItem = { item -> DetailActivity.start(this, item.id) },
            onFocus = ::preview,
            onRowTitle = { view -> LibraryActivity.start(this, view.id, view.name, view.collectionType) },
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
            if (libraries.isNotEmpty()) androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.libraries).setItems(libraries.map { it.name }.toTypedArray()) { _, index ->
                    val library = libraries[index]
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
            findViewById<TextView>(R.id.previewName).text = item.seriesName.takeIf { item.isEpisode && it.isNotBlank() } ?: item.name
            findViewById<TextView>(R.id.previewMeta).text = listOfNotNull(item.year.takeIf { it > 0 }?.toString(), if (item.resumeTicks > 0) getString(R.string.resume) else null).joinToString(" · ")
            findViewById<TextView>(R.id.previewOverview).text = item.overview
            val type = if (item.backdropTag != null) "Backdrop" else if (item.thumbTag != null) "Thumb" else "Primary"
            val tag = when (type) { "Backdrop" -> item.backdropTag; "Thumb" -> item.thumbTag; else -> item.posterTag }
            val previewImage = findViewById<android.widget.ImageView>(R.id.previewImage)
            if (ServiceLocator.preferences.effective().stored.backdropEnabled) {
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
        if (::rowsAdapter.isInitialized && !ServiceLocator.preferences.effective().stored.backdropEnabled) {
            ServiceLocator.images.cancel(findViewById(R.id.previewImage))
        }
        // Refresh resume/next-up after returning from Detail or Player. The adapter
        // preserves stable row/card ids and the saved vertical position.
        if (::rowsAdapter.isInitialized && homeLoaded && !loadingHome) loadHome(refreshOnly = true)
        else if (::rowsAdapter.isInitialized && !homeLoaded && !loadingHome) loadHome()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadHome(refreshOnly: Boolean = false) {
        if (loadingHome) return
        loadingHome = true
        progress.visibility = View.VISIBLE
        scope.launch {
            try {
                val rows = mutableListOf<HomeRow>()
                rows.add(HomeRow(getString(R.string.continue_watching), ServiceLocator.client.resume(), landscape = true))
                rows.add(HomeRow(getString(R.string.next_up), ServiceLocator.client.nextUp(), landscape = true))
                if (refreshOnly) {
                    rows.addAll(latestRows)
                    rowsAdapter.submitRows(rows)
                    homeLoaded = true
                    emptyView.text = if (rowsAdapter.itemCount == 0) getString(R.string.empty_home) else ""
                    emptyView.visibility = if (rowsAdapter.itemCount == 0) View.VISIBLE else View.GONE
                    return@launch
                }
                libraries = ServiceLocator.client.views()
                val mediaViews = libraries.filter {
                    it.collectionType in setOf("movies", "tvshows", "mixed", "")
                }
                for (view in mediaViews) {
                    rows.add(
                        HomeRow(
                            getString(R.string.latest_media) + " · " + view.name,
                            ServiceLocator.client.latest(view.id),
                            libraryId = view.id,
                            libraryName = view.name,
                            libraryType = view.collectionType,
                        ),
                    )
                }
                latestRows = rows.drop(2)
                rowsAdapter.submitRows(rows)
                if (!homeLoaded) rows.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()?.let(::preview)
                homeLoaded = true
                emptyView.text = if (rowsAdapter.itemCount == 0) getString(R.string.empty_home) else ""
                emptyView.visibility = if (rowsAdapter.itemCount == 0) View.VISIBLE else View.GONE
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
                emptyView.visibility = View.VISIBLE
            } finally {
                loadingHome = false
                progress.visibility = View.GONE
            }
        }
    }

    private companion object {
        const val KEY_SCROLL = "home.scroll"
    }
}

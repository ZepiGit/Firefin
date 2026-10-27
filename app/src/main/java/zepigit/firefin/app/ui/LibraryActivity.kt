package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator

/** Library grid with paging and sort, filter and genre pickers; no full refresh per scroll tick. */
class LibraryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adapter: MediaCardAdapter
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var stateView: View
    private lateinit var stateText: TextView
    private lateinit var stateRetry: Button

    private var libraryId: String = ""
    private var total = -1
    private var loading = false
    private var sortByIndex = 0
    private var filterIndex = 0
    private var genre: zepigit.firefin.app.data.UserView? = null
    private var genres: List<zepigit.firefin.app.data.UserView>? = null
    private var genresLoading = false
    private val filters = listOf(null, "IsUnplayed", "IsPlayed", "IsFavorite")
    private val favoritesGrid get() = intent.getBooleanExtra("library.favorites", false)
    private val includeTypes get() = when (intent.getStringExtra("library.type")) {
        "movies" -> "Movie,BoxSet"
        "tvshows" -> "Series"
        else -> if (favoritesGrid) "Movie,Series,Episode" else null
    }
    private var generation = 0
    private var requestJob: kotlinx.coroutines.Job? = null

    private data class SortOption(val by: String, val order: String, val label: Int)

    private val sortOptions = listOf(
        SortOption("SortName", "Ascending", R.string.sort_name),
        SortOption("DateCreated", "Descending", R.string.sort_date_added),
        SortOption("ProductionYear", "Descending", R.string.sort_year),
        SortOption("CommunityRating", "Descending", R.string.sort_rating),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryId = intent.getStringExtra(EXTRA_LIBRARY).orEmpty()
        setContentView(R.layout.activity_library)
        titleView = findViewById(R.id.libraryTitle)
        progress = findViewById(R.id.progress)
        titleView.text = intent.getStringExtra(EXTRA_TITLE) ?: ""

        stateView = findViewById(R.id.stateView)
        stateText = findViewById(R.id.stateText)
        stateRetry = findViewById(R.id.stateRetry)
        stateRetry.setOnClickListener { loadMore() }
        adapter = MediaCardAdapter(
            onMenu = { item ->
                CardMenu.show(this, scope, item) { fresh ->
                    val matches = when (if (favoritesGrid) "IsFavorite" else filters[filterIndex]) {
                        "IsFavorite" -> fresh.favorite
                        "IsUnplayed" -> !fresh.played
                        "IsPlayed" -> fresh.played
                        else -> true
                    }
                    if (matches) adapter.replace(fresh) else {
                        adapter.remove(fresh.id)
                        if (total > 0) total--
                        if (adapter.itemCount == 0 && total > 0) loadMore()
                        else if (adapter.itemCount == 0) {
                            showState(getString(R.string.empty_library), retry = false)
                            findViewById<Button>(R.id.sortButton).requestFocus()
                        }
                    }
                    updateTitle()
                }
            },
            onClick = { DetailActivity.start(this, it.id) },
        )
        val grid = findViewById<RecyclerView>(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, GRID_SPAN)
        grid.adapter = adapter
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as GridLayoutManager
                if (!loading && (total < 0 || adapter.itemCount < total)) {
                    if (lm.findLastVisibleItemPosition() >= adapter.itemCount - 12) loadMore()
                }
            }
        })
        findViewById<Button>(R.id.sortButton).setOnClickListener {
            choose(R.string.sort_button, sortOptions.map { getString(it.label) }) { sortByIndex = it; reload() }
        }
        findViewById<Button>(R.id.filterButton).apply {
            visibility = if (favoritesGrid) View.GONE else View.VISIBLE
            setOnClickListener { choose(R.string.filter_button, resources.getStringArray(R.array.library_filter_options).toList()) { filterIndex = it; reload() } }
        }
        findViewById<Button>(R.id.genreButton).apply {
            visibility = if (favoritesGrid) View.GONE else View.VISIBLE
            setOnClickListener { showGenres() }
        }
        loadMore()
    }

    private fun reload() {
        generation++
        requestJob?.cancel()
        loading = false
        adapter.submit(emptyList())
        total = -1
        updateTitle()
        loadMore()
    }

    /** The header keeps name and count; each picker button carries its own current value. */
    private fun updateTitle() {
        titleView.text = getString(R.string.library_title, intent.getStringExtra(EXTRA_TITLE).orEmpty(), total.coerceAtLeast(0))
        findViewById<Button>(R.id.sortButton).text = getString(R.string.setting_value, getString(R.string.sort_button), getString(sortOptions[sortByIndex].label))
        findViewById<Button>(R.id.filterButton).text = getString(R.string.setting_value, getString(R.string.filter_button), resources.getStringArray(R.array.library_filter_options)[filterIndex])
        findViewById<Button>(R.id.genreButton).text = genre?.let { getString(R.string.setting_value, getString(R.string.genre_button), it.name) } ?: getString(R.string.genre_button)
    }

    /** One list dialog per picker: a visible focused row beats cycling values with repeated presses. */
    private fun choose(title: Int, labels: List<String>, onChosen: (Int) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, index -> onChosen(index) }.show()
    }

    private fun showGenres() {
        if (genresLoading) return
        genresLoading = true
        scope.launch {
            try {
                val choices = genres ?: ServiceLocator.client.genres(libraryId, includeTypes).also { genres = it }
                if (isFinishing || isDestroyed) return@launch
                val labels = listOf(getString(R.string.genre_all)) + choices.map { it.name }
                choose(R.string.genre_button, labels) { genre = choices.getOrNull(it - 1); reload() }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@LibraryActivity, errorMessage(e), Toast.LENGTH_LONG).show() }
            finally { genresLoading = false }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadMore() {
        if (loading) return
        loading = true
        progress.visibility = View.VISIBLE
        stateView.visibility = View.GONE
        val gen = generation
        val start = adapter.itemCount
        val sort = sortOptions[sortByIndex]
        requestJob = scope.launch {
            try {
                val (items, totalCount) = ServiceLocator.client.items(
                    parentId = libraryId,
                    startIndex = if (total < 0) 0 else start,
                    sortBy = sort.by,
                    sortOrder = sort.order,
                    includeTypes = includeTypes,
                    recursive = intent.getStringExtra("library.type") in setOf("movies", "tvshows") || favoritesGrid,
                    filters = if (favoritesGrid) "IsFavorite" else filters[filterIndex],
                    genreIds = genre?.id,
                )
                if (gen != generation) return@launch
                total = totalCount
                updateTitle()
                adapter.appendItems(items)
                if (adapter.itemCount == 0) showState(getString(R.string.empty_library), retry = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed first page gets a focusable Reload; a failed later page keeps the grid and tells the user.
                if (adapter.itemCount == 0) showState(errorMessage(e), retry = true)
                else Toast.makeText(this@LibraryActivity, errorMessage(e), Toast.LENGTH_LONG).show()
            } finally {
                if (gen == generation) {
                    loading = false
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun showState(message: String, retry: Boolean) {
        stateText.text = message
        stateRetry.visibility = if (retry) View.VISIBLE else View.GONE
        stateView.visibility = View.VISIBLE
        if (retry) stateRetry.requestFocus()
    }

    companion object {
        private const val EXTRA_LIBRARY = "library.id"
        private const val EXTRA_TITLE = "library.title"
        private const val GRID_SPAN = 5

        fun start(context: Context, libraryId: String, title: String, collectionType: String = "", favorites: Boolean = false) {
            context.startActivity(
                Intent(context, LibraryActivity::class.java)
                    .putExtra(EXTRA_LIBRARY, libraryId)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra("library.type", collectionType)
                    .putExtra("library.favorites", favorites),
            )
        }
    }
}

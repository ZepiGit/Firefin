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

/** Library grid with paging and a cycling sort control; no full refresh per scroll tick. */
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
        val favoritesGrid = intent.getBooleanExtra("library.favorites", false)
        adapter = MediaCardAdapter(
            onMenu = { item ->
                CardMenu.show(this, scope, item) { fresh ->
                    // An un-favorited card has no place in the favorites grid any more.
                    if (favoritesGrid && !fresh.favorite) { adapter.remove(fresh.id); if (total > 0) total-- } else adapter.replace(fresh)
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
            sortByIndex = (sortByIndex + 1) % sortOptions.size
            generation++
            requestJob?.cancel()
            loading = false
            adapter.submit(emptyList())
            total = -1
            loadMore()
        }
        loadMore()
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
                    includeTypes = when (intent.getStringExtra("library.type")) {
                        "movies" -> "Movie,BoxSet"
                        "tvshows" -> "Series"
                        else -> if (intent.getBooleanExtra("library.favorites", false)) "Movie,Series,Episode" else null
                    },
                    recursive = intent.getStringExtra("library.type") in setOf("movies", "tvshows") || intent.getBooleanExtra("library.favorites", false),
                    favorites = intent.getBooleanExtra("library.favorites", false),
                )
                if (gen != generation) return@launch
                total = totalCount
                titleView.text = getString(R.string.library_title, intent.getStringExtra(EXTRA_TITLE) ?: "", total, getString(sort.label))
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
        private const val GRID_SPAN = 6

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

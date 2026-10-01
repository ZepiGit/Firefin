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
import zepigit.firefin.app.data.MediaItem

/** Library grid with paging and a cycling sort control; no full refresh per scroll tick. */
class LibraryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adapter: MediaCardAdapter
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView

    private var libraryId: String = ""
    private var total = -1
    private var loading = false
    private var sortByIndex = 0
    private var generation = 0

    private val sortOptions = listOf(
        "SortName" to "Ascending",
        "DateCreated" to "Descending",
        "ProductionYear" to "Descending",
        "CommunityRating" to "Descending",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryId = intent.getStringExtra(EXTRA_LIBRARY) ?: return
        setContentView(R.layout.activity_library)
        titleView = findViewById(R.id.libraryTitle)
        progress = findViewById(R.id.progress)
        titleView.text = intent.getStringExtra(EXTRA_TITLE) ?: ""

        adapter = MediaCardAdapter(onClick = { DetailActivity.start(this, it.id) })
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
        val gen = generation
        val start = adapter.itemCount
        val (by, order) = sortOptions[sortByIndex]
        scope.launch {
            try {
                val (items, totalCount) = ServiceLocator.client.items(
                    parentId = libraryId,
                    startIndex = if (total < 0) 0 else start,
                    sortBy = by,
                    sortOrder = order,
                )
                if (gen != generation) return@launch
                total = totalCount
                titleView.text = (intent.getStringExtra(EXTRA_TITLE) ?: "") +
                    " (${total} · ${by.removePrefix("Sort").removePrefix("Date")})"
                adapter.appendItems(items)
            } catch (e: Exception) {
                Toast.makeText(this@LibraryActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show()
            } finally {
                if (gen == generation) {
                    loading = false
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun MediaCardAdapter.append(items: List<MediaItem>) = appendItems(items)

    companion object {        private const val EXTRA_LIBRARY = "library.id"
        private const val EXTRA_TITLE = "library.title"
        private const val GRID_SPAN = 6

        fun start(context: Context, libraryId: String, title: String) {
            context.startActivity(
                Intent(context, LibraryActivity::class.java)
                    .putExtra(EXTRA_LIBRARY, libraryId)
                    .putExtra(EXTRA_TITLE, title),
            )
        }
    }
}

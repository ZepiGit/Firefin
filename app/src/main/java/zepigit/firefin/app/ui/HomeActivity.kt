package zepigit.firefin.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.UserView

/**
 * Home: resume, next up and per-library latest rows in one vertical
 * RecyclerView with stable ids; the scroll position is restored after the
 * rows are (re)loaded.
 */
class HomeActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var rowsView: RecyclerView
    private lateinit var rowsAdapter: HomeRowsAdapter
    private lateinit var progress: ProgressBar
    private var pendingScroll: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ServiceLocator.session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        rowsView = findViewById(R.id.rows)
        progress = findViewById(R.id.progress)
        rowsAdapter = HomeRowsAdapter(
            onItem = { item -> DetailActivity.start(this, item.id) },
            onRowTitle = { view -> LibraryActivity.start(this, view.id, view.name) },
        )
        rowsView.layoutManager = LinearLayoutManager(this)
        rowsView.adapter = rowsAdapter

        findViewById<Button>(R.id.searchButton).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.remoteButton).setOnClickListener {
            startActivity(Intent(this, RemoteActivity::class.java))
        }

        loadHome()
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

    private fun loadHome() {
        progress.visibility = View.VISIBLE
        scope.launch {
            try {
                val rows = mutableListOf<HomeRow>()
                rows.add(HomeRow(getString(R.string.continue_watching), ServiceLocator.client.resume()))
                rows.add(HomeRow(getString(R.string.next_up), ServiceLocator.client.nextUp()))
                val views = ServiceLocator.client.views()
                val mediaViews = views.filter {
                    it.collectionType in setOf("movies", "tvshows", "mixed", "")
                }.take(4)
                for (view in mediaViews) {
                    rows.add(
                        HomeRow(
                            getString(R.string.latest_media) + " · " + view.name,
                            ServiceLocator.client.latest(view.id),
                            parentViewId = view.id,
                        ),
                    )
                }
                rowsAdapter.submitRows(rows)
                pendingScroll?.let {
                    (rowsView.layoutManager as LinearLayoutManager).scrollToPosition(it)
                    pendingScroll = null
                }
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private companion object {
        const val KEY_SCROLL = "home.scroll"
    }
}

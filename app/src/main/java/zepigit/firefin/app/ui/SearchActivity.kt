package zepigit.firefin.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator

/** Search with 400 ms debounce and cancellation of stale requests. */
class SearchActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentQuery = ""
    private var queryJob: Job? = null

    override fun onDestroy() { queryJob?.cancel(); scope.cancel(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        val box = findViewById<EditText>(R.id.searchBox)
        val results = findViewById<RecyclerView>(R.id.results)
        val empty = findViewById<TextView>(R.id.empty)
        val progress = findViewById<android.widget.ProgressBar>(R.id.progress)
        val adapter = MediaCardAdapter(onClick = { DetailActivity.start(this, it.id) })
        results.layoutManager = GridLayoutManager(this, GRID_SPAN)
        results.adapter = adapter

        box.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                queryJob?.cancel()
                val q = s?.toString()?.trim().orEmpty()
                if (q.length < 2) {
                    adapter.submit(emptyList())
                    empty.visibility = View.GONE
                    return
                }
                queryJob = scope.launch {
                    delay(400)
                    currentQuery = q
                    progress.visibility = View.VISIBLE
                    try {
                        val (items, _) = withContext(Dispatchers.IO) {
                            ServiceLocator.client.items(
                                searchTerm = q,
                                includeTypes = "Movie,Series,Episode,MusicAlbum",
                            )
                        }
                        if (currentQuery != q) return@launch
                        adapter.submit(items)
                        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(this@SearchActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show()
                    } finally {
                        if (currentQuery == q) progress.visibility = View.GONE
                    }
                }
            }
        })
        box.requestFocus()
    }

    private companion object {
        const val GRID_SPAN = 6
    }
}

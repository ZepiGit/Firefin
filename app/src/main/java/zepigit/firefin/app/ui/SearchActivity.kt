package zepigit.firefin.app.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.SearchQueryGate
import zepigit.firefin.app.data.SeerrClient
import zepigit.firefin.app.data.SeerrMedia
import zepigit.firefin.app.data.SessionExpiredException
import zepigit.firefin.app.images.TmdbArtwork

class SearchActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var queryJob: Job? = null
    private val queryGate = SearchQueryGate()
    @Volatile private var seerrProbeUnavailable = false

    override fun onDestroy() { queryJob?.cancel(); scope.cancel(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        val box = findViewById<EditText>(R.id.searchBox)
        val results = findViewById<RecyclerView>(R.id.results)
        val empty = findViewById<TextView>(R.id.empty)
        val progress = findViewById<android.widget.ProgressBar>(R.id.progress)
        val seerrClient = SeerrClient(ServiceLocator.client.transportSnapshot())
        val rows = SearchRowsAdapter(
            onJellyfin = { DetailActivity.start(this, it.id) },
            onSeerr = { SeerrDetailActivity.start(this, it.type, it.id) },
        )
        results.layoutManager = LinearLayoutManager(this)
        results.adapter = rows
        box.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                queryJob?.cancel()
                val generation = queryGate.next()
                val q = s?.toString()?.trim().orEmpty()
                if (q.length < 2) {
                    rows.submit(emptyList(), emptyList())
                    empty.visibility = View.GONE
                    progress.visibility = View.GONE
                    return
                }
                queryJob = scope.launch {
                    delay(400); progress.visibility = View.VISIBLE
                    try {
                        val pair = kotlinx.coroutines.supervisorScope {
                            val jf = async(Dispatchers.IO) {
                                try {
                                    ServiceLocator.client.items(searchTerm = q, includeTypes = "Movie,Series,Episode,MusicAlbum").first
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: SessionExpiredException) {
                                    throw e
                                } catch (e: Exception) {
                                    throw e
                                }
                            }
                            val seerr = async(Dispatchers.IO) {
                                if (seerrProbeUnavailable) return@async emptyList()
                                try {
                                    if (seerrClient.connect()) {
                                        seerrClient.page("", 1, q).items
                                    } else {
                                        seerrProbeUnavailable = true
                                        emptyList()
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: SessionExpiredException) {
                                    throw e
                                } catch (_: Exception) {
                                    seerrProbeUnavailable = true
                                    emptyList()
                                }
                            }
                            jf.await() to seerr.await()
                        }
                        if (!queryGate.isCurrent(generation)) return@launch
                        val local = pair.first; val remote = pair.second
                        rows.submit(local, remote)
                        empty.text = getString(R.string.empty_search)
                        empty.visibility = if (local.isEmpty() && remote.isEmpty()) View.VISIBLE else View.GONE
                    } catch (e: CancellationException) { throw e }
                    catch (e: SessionExpiredException) {
                        if (queryGate.isCurrent(generation)) {
                            ServiceLocator.session.clear()
                            LoginActivity.startFresh(this@SearchActivity)
                        }
                    }
                    catch (e: Exception) {
                        if (!queryGate.isCurrent(generation)) return@launch
                        rows.submit(emptyList(), emptyList())
                        empty.text = e.message ?: getString(R.string.error_generic)
                        empty.visibility = View.VISIBLE
                    }
                    finally {
                        if (queryGate.isCurrent(generation)) progress.visibility = View.GONE
                    }
                }
            }
        })
        box.setOnEditorActionListener { _, _, _ -> (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(box.windowToken, 0); results.requestFocus(); true }
        box.requestFocus()
    }

    private class SearchRowsAdapter(private val onJellyfin: (zepigit.firefin.app.data.MediaItem) -> Unit, private val onSeerr: (SeerrMedia) -> Unit) : RecyclerView.Adapter<SearchRowsAdapter.Holder>() {
        private var local = emptyList<zepigit.firefin.app.data.MediaItem>()
        private var remote = emptyList<SeerrMedia>()
        fun submit(j: List<zepigit.firefin.app.data.MediaItem>, s: List<SeerrMedia>) {
            local = j
            remote = s
            notifyDataSetChanged()
        }
        override fun getItemCount() = listOf(local.isNotEmpty(), remote.isNotEmpty()).count { it }
        override fun getItemViewType(position: Int): Int = if (local.isNotEmpty() && (position == 0 || remote.isEmpty())) 0 else 1
        override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int) = Holder(android.view.LayoutInflater.from(p.context).inflate(R.layout.view_search_row, p, false))
        override fun onBindViewHolder(h: Holder, pos: Int) {
            val isRemote = getItemViewType(pos) == 1
            h.title.text = h.itemView.context.getString(if (isRemote) R.string.search_in_seerr else R.string.search_in_library)
            if (h.items.layoutManager == null) h.items.layoutManager = LinearLayoutManager(h.itemView.context, LinearLayoutManager.HORIZONTAL, false)
            val rowType = if (isRemote) 1 else 0
            val adapter = if (h.boundType == rowType) {
                h.items.adapter as MediaCardAdapter
            } else {
                MediaCardAdapter(
                    artwork = if (isRemote) ({ item: zepigit.firefin.app.data.MediaItem, view: android.widget.ImageView ->
                        TmdbArtwork.url(item.posterTag)?.let { ServiceLocator.discoveryImages.load(it, 320, view) }
                            ?: ServiceLocator.discoveryImages.cancel(view)
                    }) else null,
                    onClick = if (isRemote) ({ found: zepigit.firefin.app.data.MediaItem -> remote.firstOrNull { it.key == found.id }?.let { onSeerr(it) } ?: Unit }) else onJellyfin,
                ).also {
                    h.items.adapter = it
                    h.boundType = rowType
                }
            }
            adapter.submit(if (isRemote) remote.map { it.card() } else local)
        }
        class Holder(v: View): RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.searchRowTitle)
            val items: RecyclerView = v.findViewById(R.id.searchRowItems)
            var boundType: Int = -1
        }
    }
}

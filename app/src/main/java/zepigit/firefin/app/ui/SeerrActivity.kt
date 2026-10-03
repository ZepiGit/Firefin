package zepigit.firefin.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.SeerrClient
import zepigit.firefin.app.data.SeerrMedia
import zepigit.firefin.app.data.SessionExpiredException
import zepigit.firefin.app.images.TmdbArtwork

class SeerrActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var client: SeerrClient
    private lateinit var adapter: MediaCardAdapter
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private val titles = mutableMapOf<String, SeerrMedia>()
    private var requestJob: Job? = null
    private var connected = false
    private var category = "trending"
    private var page = 1
    private var pages = 1
    private var query = ""
    private var showingRequests = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ServiceLocator.session.isLoggedIn) { LoginActivity.startFresh(this); return }
        setContentView(R.layout.activity_seerr)
        client = SeerrClient(ServiceLocator.client.transportSnapshot())
        status = findViewById(R.id.seerrStatus)
        progress = findViewById(R.id.seerrProgress)
        adapter = MediaCardAdapter(artwork = { item, view ->
            val url = TmdbArtwork.url(item.posterTag)
            if (url != null) ServiceLocator.discoveryImages.load(url, 320, view)
            else ServiceLocator.discoveryImages.cancel(view)
        }, onClick = { item -> titles[item.id]?.let { SeerrDetailActivity.start(this, it.type, it.id) } })
        findViewById<RecyclerView>(R.id.seerrGrid).apply {
            layoutManager = GridLayoutManager(this@SeerrActivity, 6)
            adapter = this@SeerrActivity.adapter
        }
        findViewById<Button>(R.id.seerrConnect).setOnClickListener { if (connected) load() else connect() }
        findViewById<Button>(R.id.seerrCategory).setOnClickListener {
            val labels = listOf(R.string.seerr_trending, R.string.seerr_movies, R.string.seerr_series)
            AlertDialog.Builder(this).setTitle(R.string.seerr_discover).setItems(labels.map { getString(it) }.toTypedArray()) { _, index ->
                category = listOf("trending", "movie", "tv")[index]
                findViewById<Button>(R.id.seerrCategory).setText(labels[index])
                showingRequests = false; page = 1; load()
            }.show()
        }
        findViewById<Button>(R.id.seerrRequests).setOnClickListener { showingRequests = true; page = 1; load() }
        findViewById<Button>(R.id.seerrPrevious).setOnClickListener { if (page > 1) { page--; load() } }
        findViewById<Button>(R.id.seerrNext).setOnClickListener { if (page < pages) { page++; load() } }
        val search = findViewById<EditText>(R.id.seerrSearch)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s.toString().trim(); page = 1; showingRequests = false
                load(debounce = true)
            }
        })
        search.setOnEditorActionListener { _, _, _ ->
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
            findViewById<RecyclerView>(R.id.seerrGrid).requestFocus()
            true
        }
        connect()
    }

    private fun connect() {
        requestJob?.cancel()
        requestJob = scope.launch {
            progress.visibility = View.VISIBLE
            try {
                connected = client.connect()
                if (!connected) loginDialog() else load()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
            finally { progress.visibility = View.GONE }
        }
    }

    private fun loginDialog() {
        status.setText(R.string.seerr_login_needed)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 16, 28, 16) }
        val username = EditText(this).apply { setHint(R.string.username_hint); setText(ServiceLocator.session.userName); isSingleLine = true }
        val password = EditText(this).apply {
            setHint(R.string.seerr_password_hint)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        }
        box.addView(username); box.addView(password)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.seerr_connect_title).setView(box)
            .setPositiveButton(R.string.login_button, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            fun login(quick: Boolean) {
                val name = username.text.toString()
                val secret = password.text.toString()
                password.text.clear()
                dialog.dismiss()
                requestJob = scope.launch {
                    progress.visibility = View.VISIBLE
                    try {
                        client.login(name, secret, quickConnect = quick)
                        connected = true; load()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { showError(e) }
                    finally { progress.visibility = View.GONE }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { login(false) }
        }
        dialog.show()
    }

    private fun load(debounce: Boolean = false) {
        if (!connected) return
        requestJob?.cancel()
        val selectedPage = page
        val selectedQuery = query
        val requests = showingRequests
        requestJob = scope.launch {
            if (debounce) delay(400)
            progress.visibility = View.VISIBLE
            try {
                val result = if (requests) {
                    val (list, total) = client.requests((selectedPage - 1) * 20)
                    pages = ((total + 19) / 20).coerceAtLeast(1)
                    // Details are fetched concurrently but bounded, so a page of requests
                    // does not open one Moonbase round trip per entry at once.
                    val limiter = Semaphore(DETAIL_CONCURRENCY)
                    coroutineScope {
                        list.distinctBy { "${it.type}:${it.mediaId}" }
                            .filter { it.mediaId > 0 && it.type in setOf("movie", "tv") }
                            .map { entry ->
                                async {
                                    limiter.withPermit {
                                        runCatching { client.detail(entry.type, entry.mediaId) }
                                            .getOrElse {
                                                if (it is CancellationException || it is SessionExpiredException) throw it
                                                SeerrMedia(entry.mediaId, entry.type, getString(R.string.seerr_request_number, entry.id), getString(entry.statusLabelRes), null, null, 0, entry.status)
                                            }
                                            .let { it.copy(overview = "${getString(entry.statusLabelRes)}\n${it.overview}") }
                                    }
                                }
                            }.awaitAll()
                    }
                } else {
                    val result = client.page(category, selectedPage, selectedQuery)
                    pages = result.totalPages.coerceAtLeast(1)
                    result.items
                }
                titles.clear(); result.forEach { titles[it.key] = it }
                adapter.submit(result.map { it.card() })
                val heading = when {
                    requests -> getString(R.string.seerr_my_requests)
                    selectedQuery.isNotBlank() -> getString(R.string.seerr_search_status, selectedQuery)
                    else -> getString(R.string.seerr_discover)
                }
                status.text = getString(
                    if (result.isEmpty()) R.string.seerr_page_status_empty else R.string.seerr_page_status,
                    heading, selectedPage, pages,
                )
                findViewById<Button>(R.id.seerrConnect).setText(R.string.refresh)
                findViewById<Button>(R.id.seerrPrevious).isEnabled = selectedPage > 1
                findViewById<Button>(R.id.seerrNext).isEnabled = selectedPage < pages
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
            finally { progress.visibility = View.GONE }
        }
    }

    private fun showError(error: Exception) {
        if (error is SessionExpiredException) { ServiceLocator.session.clear(); LoginActivity.startFresh(this); return }
        if (error is zepigit.firefin.app.data.SeerrException && error.status == 401) {
            client.invalidate()
            connected = false
        }
        status.text = errorMessage(error, R.string.seerr_load_failed)
        findViewById<Button>(R.id.seerrConnect).setText(if (connected) R.string.reload else R.string.connect)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private companion object {
        const val DETAIL_CONCURRENCY = 4
    }
}

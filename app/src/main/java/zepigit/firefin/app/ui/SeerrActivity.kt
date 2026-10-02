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
            AlertDialog.Builder(this).setTitle("Entdecken").setItems(arrayOf("Trending", "Filme", "Serien")) { _, index ->
                category = listOf("trending", "movie", "tv")[index]
                findViewById<Button>(R.id.seerrCategory).text = listOf("Trending", "Filme", "Serien")[index]
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
        status.text = "Moonbase erkannt. Seerr benötigt eine Anmeldung."
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 16, 28, 16) }
        val username = EditText(this).apply { hint = "Benutzername"; setText(ServiceLocator.session.userName); isSingleLine = true }
        val password = EditText(this).apply {
            hint = "Passwort (wird nicht gespeichert)"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        }
        box.addView(username); box.addView(password)
        val dialog = AlertDialog.Builder(this).setTitle("Seerr über Moonbase verbinden").setView(box)
            .setPositiveButton("Anmelden", null).setNegativeButton("Abbrechen", null).create()
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
                    list.distinctBy { "${it.type}:${it.mediaId}" }.mapNotNull { entry ->
                        if (entry.mediaId <= 0 || entry.type !in setOf("movie", "tv")) null
                        else runCatching { client.detail(entry.type, entry.mediaId) }
                            .getOrElse {
                                if (it is CancellationException || it is SessionExpiredException) throw it
                                SeerrMedia(entry.mediaId, entry.type, "Anfrage #${entry.id}", entry.statusLabel, null, null, 0, entry.status)
                            }
                            .let { it.copy(overview = "${entry.statusLabel}\n${it.overview}") }
                    }
                } else {
                    val result = client.page(category, selectedPage, selectedQuery)
                    pages = result.totalPages.coerceAtLeast(1)
                    result.items
                }
                titles.clear(); result.forEach { titles[it.key] = it }
                adapter.submit(result.map { it.card() })
                status.text = "${if (requests) "Meine Anfragen" else if (selectedQuery.isNotBlank()) "Suche: $selectedQuery" else "Entdecken"} · Seite $selectedPage / $pages" + if (result.isEmpty()) " · Keine Ergebnisse" else ""
                findViewById<Button>(R.id.seerrConnect).text = "Aktualisieren"
                findViewById<Button>(R.id.seerrPrevious).isEnabled = selectedPage > 1
                findViewById<Button>(R.id.seerrNext).isEnabled = selectedPage < pages
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
            finally { progress.visibility = View.GONE }
        }
    }

    private fun showError(error: Exception) {
        if (error is SessionExpiredException) { ServiceLocator.session.clear(); LoginActivity.startFresh(this); return }
        if (error is zepigit.firefin.app.data.SeerrException && error.status == 401) connected = false
        status.text = error.message ?: "Seerr konnte nicht geladen werden."
        findViewById<Button>(R.id.seerrConnect).text = if (connected) "Erneut laden" else "Verbinden"
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

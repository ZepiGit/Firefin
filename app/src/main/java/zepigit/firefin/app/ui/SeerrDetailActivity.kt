package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.SeerrClient
import zepigit.firefin.app.data.SeerrMedia
import zepigit.firefin.app.data.SessionExpiredException
import zepigit.firefin.app.images.TmdbArtwork

class SeerrDetailActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var client: SeerrClient
    private var media: SeerrMedia? = null
    private var busy = false
    private val type: String get() = intent.getStringExtra("seerr.type").orEmpty()
    private val mediaId: Int get() = intent.getIntExtra("seerr.id", 0)
    private val preferences by lazy { getSharedPreferences("seerr_unresolved", MODE_PRIVATE) }
    private val key: String get() = "${ServiceLocator.session.serverUrl}:${ServiceLocator.session.userId}:$type:$mediaId"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ServiceLocator.session.isLoggedIn) { LoginActivity.startFresh(this); return }
        setContentView(R.layout.activity_detail)
        findViewById<Button>(R.id.favoriteButton).visibility = View.GONE
        findViewById<Button>(R.id.watchedButton).visibility = View.GONE
        findViewById<Button>(R.id.restartButton).visibility = View.GONE
        findViewById<View>(R.id.children).visibility = View.GONE
        findViewById<TextView>(R.id.childrenTitle).visibility = View.GONE
        findViewById<Button>(R.id.retryButton).setOnClickListener { load() }
        client = SeerrClient(ServiceLocator.client.transportSnapshot())
        load()
    }

    private fun load() {
        if (busy) return
        busy = true
        findViewById<ProgressBar>(R.id.progress).visibility = View.VISIBLE
        scope.launch {
            try {
                if (!client.connect()) {
                    findViewById<Button>(R.id.playButton).visibility = View.GONE
                    findViewById<TextView>(R.id.overview).text = "Bitte zuerst im Seerr-Bereich verbinden."
                    return@launch
                }
                render(client.detail(type, mediaId))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e) }
            finally { busy = false; findViewById<ProgressBar>(R.id.progress).visibility = View.GONE }
        }
    }

    private fun render(loaded: SeerrMedia) {
        media = loaded
        findViewById<TextView>(R.id.name).text = loaded.title
        findViewById<TextView>(R.id.meta).text = "${if (loaded.year > 0) "${loaded.year} · " else ""}${if (type == "tv") "Serie" else "Film"} · ${loaded.statusLabel}"
        findViewById<TextView>(R.id.overview).text = loaded.overview
        TmdbArtwork.url(loaded.backdrop ?: loaded.poster, backdrop = loaded.backdrop != null)?.let {
            ServiceLocator.discoveryImages.load(it, 960, findViewById(R.id.backdrop))
        }
        val unresolved = preferences.contains(key)
        val button = findViewById<Button>(R.id.playButton)
        button.visibility = View.VISIBLE
        button.text = when {
            unresolved -> "Anfrage wird geprüft"
            !client.user.canRequest(type) -> "Keine Anfrageberechtigung"
            !loaded.requestable -> loaded.statusLabel
            else -> "Titel anfragen (HD)"
        }
        button.isEnabled = client.user.canRequest(type) && loaded.requestable && !unresolved
        button.setOnClickListener { chooseRequest(loaded) }
        findViewById<Button>(R.id.retryButton).apply { visibility = View.VISIBLE; text = "Status aktualisieren"; setOnClickListener { load() } }
        if (unresolved) {
            findViewById<TextView>(R.id.overview).text = "Das Ergebnis der letzten Anfrage ist noch unklar. Keine erneute Anfrage senden. Prüfe den Status oder hebe die Sperre bewusst auf.\n\n${loaded.overview}"
            findViewById<Button>(R.id.retryButton).text = "Status prüfen"
            findViewById<Button>(R.id.retryButton).setOnClickListener { load() }
            findViewById<Button>(R.id.restartButton).apply {
                visibility = View.VISIBLE
                text = "Sperre aufheben"
                setOnClickListener {
                    AlertDialog.Builder(this@SeerrDetailActivity).setTitle("Unklare Anfrage entsperren?")
                        .setMessage("Die Anfrage könnte bereits auf dem Server angelegt sein. Prüfe zuerst Meine Anfragen. Entsperren sendet nichts; eine neue Anfrage benötigt erneut deine Bestätigung.")
                        .setPositiveButton("Bewusst entsperren") { _, _ -> preferences.edit().remove(key).apply(); render(loaded) }
                        .setNegativeButton("Abbrechen", null).show()
                }
            }
        } else {
            findViewById<Button>(R.id.restartButton).visibility = View.GONE
        }
        if (button.isEnabled) button.requestFocus() else findViewById<Button>(R.id.retryButton).requestFocus()
    }

    private fun chooseRequest(loaded: SeerrMedia) {
        if (busy || !loaded.requestable || preferences.contains(key)) return
        if (type == "movie") confirm(loaded, emptyList()) else {
            val seasons = loaded.eligibleSeasons
            val selected = BooleanArray(seasons.size)
            AlertDialog.Builder(this).setTitle("Staffeln auswählen")
                .setMultiChoiceItems(seasons.map { "${it.name.ifBlank { "Staffel ${it.number}" }} · ${it.episodes} Folgen" }.toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
                .setPositiveButton("Weiter") { _, _ ->
                    val numbers = seasons.filterIndexed { index, _ -> selected[index] }.map { it.number }
                    if (numbers.isNotEmpty()) confirm(loaded, numbers)
                }.setNegativeButton("Abbrechen", null).show()
        }
    }

    private fun confirm(loaded: SeerrMedia, seasons: List<Int>) {
        AlertDialog.Builder(this).setTitle("Anfrage bestätigen")
            .setMessage("${loaded.title}\nHD${if (seasons.isEmpty()) "" else " · Staffeln ${seasons.joinToString()}"}\n\nDiese Anfrage wird an deinen Seerr-Server gesendet.")
            .setPositiveButton("Jetzt anfragen") { _, _ -> submit(loaded, seasons) }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun submit(loaded: SeerrMedia, seasons: List<Int>) {
        if (busy || preferences.contains(key)) return
        busy = true
        preferences.edit().putString(key, seasons.joinToString(",").ifBlank { "movie" }).commit()
        findViewById<Button>(R.id.playButton).isEnabled = false
        scope.launch {
            try {
                val result = try { client.request(loaded, seasons) } catch (e: zepigit.firefin.app.data.SeerrException) {
                    if (e.status in 400..499) preferences.edit().remove(key).apply()
                    throw e
                }
                if (result != SeerrClient.RequestResult.UNKNOWN) preferences.edit().remove(key).apply()
                val message = when (result) {
                    SeerrClient.RequestResult.CREATED -> "Anfrage angelegt."
                    SeerrClient.RequestResult.NOTHING_NEW -> "Keine neuen Staffeln anforderbar."
                    SeerrClient.RequestResult.ALREADY_REQUESTED -> "Bereits angefragt."
                    SeerrClient.RequestResult.UNKNOWN -> "Ergebnis unklar. Keine erneute Anfrage senden; Status prüfen."
                }
                render(client.detail(type, mediaId))
                AlertDialog.Builder(this@SeerrDetailActivity).setMessage(message).setPositiveButton("OK", null).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error(e)
            } finally { busy = false }
        }
    }

    private fun error(error: Exception) {
        if (error is zepigit.firefin.app.data.SeerrException && error.status == 401) client.invalidate()
        if (error is SessionExpiredException) { ServiceLocator.session.clear(); LoginActivity.startFresh(this); return }
        findViewById<TextView>(R.id.overview).text = error.message ?: "Seerr konnte nicht geladen werden."
        findViewById<Button>(R.id.retryButton).apply { visibility = View.VISIBLE; requestFocus() }
    }
    override fun onDestroy() {
        findViewById<ImageView?>(R.id.backdrop)?.let { ServiceLocator.discoveryImages.cancel(it) }
        scope.cancel(); super.onDestroy()
    }
    companion object {
        fun start(context: Context, type: String, id: Int) {
            context.startActivity(Intent(context, SeerrDetailActivity::class.java).putExtra("seerr.type", type).putExtra("seerr.id", id))
        }
    }
}

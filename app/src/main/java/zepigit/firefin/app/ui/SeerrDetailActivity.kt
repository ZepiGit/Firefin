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
                    findViewById<TextView>(R.id.overview).setText(R.string.seerr_connect_first)
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
        val typeLabel = getString(if (type == "tv") R.string.seerr_type_series else R.string.seerr_type_movie)
        findViewById<TextView>(R.id.meta).text = "${if (loaded.year > 0) "${loaded.year} · " else ""}$typeLabel · ${getString(loaded.statusLabelRes)}"
        findViewById<TextView>(R.id.overview).text = loaded.overview
        TmdbArtwork.url(loaded.backdrop ?: loaded.poster, backdrop = loaded.backdrop != null)?.let {
            ServiceLocator.discoveryImages.load(it, 960, findViewById(R.id.backdrop))
        }
        val unresolved = preferences.contains(key)
        val button = findViewById<Button>(R.id.playButton)
        button.visibility = View.VISIBLE
        button.setText(when {
            unresolved -> R.string.seerr_request_checking
            !client.user.canRequest(type) -> R.string.seerr_no_permission
            !loaded.requestable -> loaded.statusLabelRes
            else -> R.string.seerr_request_title_hd
        })
        button.isEnabled = client.user.canRequest(type) && loaded.requestable && !unresolved
        button.setOnClickListener { chooseRequest(loaded) }
        findViewById<Button>(R.id.retryButton).apply { visibility = View.VISIBLE; setText(R.string.seerr_update_status); setOnClickListener { load() } }
        if (unresolved) {
            findViewById<TextView>(R.id.overview).text = getString(R.string.seerr_unclear_notice, loaded.overview)
            findViewById<Button>(R.id.retryButton).setText(R.string.seerr_check_status)
            findViewById<Button>(R.id.retryButton).setOnClickListener { load() }
            findViewById<Button>(R.id.restartButton).apply {
                visibility = View.VISIBLE
                setText(R.string.seerr_remove_lock)
                setOnClickListener {
                    AlertDialog.Builder(this@SeerrDetailActivity).setTitle(R.string.seerr_unlock_title)
                        .setMessage(R.string.seerr_unlock_message)
                        .setPositiveButton(R.string.seerr_unlock_confirm) { _, _ -> preferences.edit().remove(key).apply(); render(loaded) }
                        .setNegativeButton(R.string.cancel, null).show()
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
            val labels = seasons.map {
                val name = it.name.ifBlank { getString(R.string.seerr_season_default, it.number) }
                resources.getQuantityString(R.plurals.seerr_season_episodes, it.episodes, name, it.episodes)
            }
            AlertDialog.Builder(this).setTitle(R.string.seerr_choose_seasons)
                .setMultiChoiceItems(labels.toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
                .setPositiveButton(R.string.continue_button) { _, _ ->
                    val numbers = seasons.filterIndexed { index, _ -> selected[index] }.map { it.number }
                    if (numbers.isNotEmpty()) confirm(loaded, numbers)
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun confirm(loaded: SeerrMedia, seasons: List<Int>) {
        val message = if (seasons.isEmpty()) getString(R.string.seerr_confirm_message_movie, loaded.title)
        else getString(R.string.seerr_confirm_message_series, loaded.title, seasons.joinToString())
        AlertDialog.Builder(this).setTitle(R.string.seerr_confirm_title)
            .setMessage(message)
            .setPositiveButton(R.string.seerr_request_now) { _, _ -> submit(loaded, seasons) }
            .setNegativeButton(R.string.cancel, null).show()
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
                    SeerrClient.RequestResult.CREATED -> R.string.seerr_result_created
                    SeerrClient.RequestResult.NOTHING_NEW -> R.string.seerr_result_nothing_new
                    SeerrClient.RequestResult.ALREADY_REQUESTED -> R.string.seerr_result_already
                    SeerrClient.RequestResult.UNKNOWN -> R.string.seerr_result_unknown
                }
                render(client.detail(type, mediaId))
                AlertDialog.Builder(this@SeerrDetailActivity).setMessage(message).setPositiveButton(R.string.ok, null).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error(e)
            } finally { busy = false }
        }
    }

    private fun error(error: Exception) {
        if (error is zepigit.firefin.app.data.SeerrException && error.status == 401) client.invalidate()
        if (error is SessionExpiredException) { ServiceLocator.session.clear(); LoginActivity.startFresh(this); return }
        findViewById<TextView>(R.id.overview).text = errorMessage(error, R.string.seerr_load_failed)
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

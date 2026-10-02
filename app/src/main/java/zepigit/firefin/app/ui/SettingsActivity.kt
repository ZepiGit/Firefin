package zepigit.firefin.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.JellyfinClient

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val session = ServiceLocator.session
        val accountInfo = findViewById<TextView>(R.id.accountInfo)
        val aboutText = findViewById<TextView>(R.id.aboutText)
        accountInfo.text = "${session.userName} @ ${session.serverUrl}"
        aboutText.text = getString(R.string.about_text, JellyfinClient.VERSION)

        val container = accountInfo.parent as android.widget.LinearLayout
        fun setting(label: String, action: () -> Unit) {
            val button = Button(this).apply { text = label; setOnClickListener { action() } }
            container.addView(button, 1, android.widget.LinearLayout.LayoutParams((320 * resources.displayMetrics.density).toInt(), (44 * resources.displayMetrics.density).toInt()).apply { topMargin = (12 * resources.displayMetrics.density).toInt() })
        }
        setting(getString(R.string.setting_quality)) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.quality_dialog_title)
                .setItems(R.array.quality_options) { _, index ->
                    val stored = ServiceLocator.preferences.stored()
                    val heights = listOf(1080, 720, 720, 480)
                    val rates = listOf(4_000_000L, 4_000_000L, 2_000_000L, 1_000_000L)
                    ServiceLocator.preferences.save(stored.copy(preferredBitrate = rates[index], preferredHeight = heights[index]))
                }.show()
        }
        setting(getString(R.string.setting_backdrops)) {
            val stored = ServiceLocator.preferences.stored()
            ServiceLocator.preferences.save(stored.copy(backdropEnabled = !stored.backdropEnabled))
            Toast.makeText(this, if (stored.backdropEnabled) R.string.backdrops_off else R.string.backdrops_on, Toast.LENGTH_SHORT).show()
        }
        setting(getString(R.string.setting_audio_language)) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.audio_language_title).setItems(R.array.audio_language_options) { _, index ->
                ServiceLocator.preferences.save(ServiceLocator.preferences.stored().copy(audioLanguage = listOf("", "de", "en")[index]))
            }.show()
        }
        setting(getString(R.string.setting_subtitle_language)) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.subtitle_language_title).setItems(R.array.subtitle_language_options) { _, index ->
                ServiceLocator.preferences.save(ServiceLocator.preferences.stored().copy(subtitleLanguage = listOf("", "de", "en")[index]))
            }.show()
        }
        findViewById<Button>(R.id.remoteButton).setOnClickListener {
            startActivity(Intent(this, RemoteActivity::class.java))
        }
        findViewById<Button>(R.id.clearCacheButton).setOnClickListener {
            val before = ServiceLocator.images.diskCacheBytes() + ServiceLocator.discoveryImages.diskCacheBytes()
            ServiceLocator.images.clearCache()
            ServiceLocator.discoveryImages.clearCache()
            Toast.makeText(this, getString(R.string.cache_cleared, before / 1024), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.aboutButton).setOnClickListener {
            aboutText.visibility =
                if (aboutText.visibility == android.view.View.VISIBLE) android.view.View.GONE
                else android.view.View.VISIBLE
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            session.clear()
            LoginActivity.startFresh(this)
        }
    }
}

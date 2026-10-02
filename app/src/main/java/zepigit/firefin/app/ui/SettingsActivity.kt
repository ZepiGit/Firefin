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
        aboutText.text = "Firefin ${JellyfinClient.VERSION}\n" +
            "Independent native Kotlin Jellyfin client for legacy Fire TV devices, " +
            "originally derived from Moonfin Core. Not affiliated with or supported by Moonfin."

        val container = accountInfo.parent as android.widget.LinearLayout
        fun setting(label: String, action: () -> Unit) {
            val button = Button(this).apply { text = label; setOnClickListener { action() } }
            container.addView(button, 1, android.widget.LinearLayout.LayoutParams((320 * resources.displayMetrics.density).toInt(), (44 * resources.displayMetrics.density).toInt()).apply { topMargin = (12 * resources.displayMetrics.density).toInt() })
        }
        setting("Wiedergabequalität") {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Qualität (Gerät: höchstens 1080p / 4 Mbit/s)")
                .setItems(arrayOf("1080p · 4 Mbit/s", "720p · 4 Mbit/s", "720p · 2 Mbit/s", "480p · 1 Mbit/s")) { _, index ->
                    val stored = ServiceLocator.preferences.stored()
                    val heights = listOf(1080, 720, 720, 480)
                    val rates = listOf(4_000_000L, 4_000_000L, 2_000_000L, 1_000_000L)
                    ServiceLocator.preferences.save(stored.copy(preferredBitrate = rates[index], preferredHeight = heights[index]))
                }.show()
        }
        setting("Hintergrundbilder ein/aus") {
            val stored = ServiceLocator.preferences.stored()
            ServiceLocator.preferences.save(stored.copy(backdropEnabled = !stored.backdropEnabled))
            Toast.makeText(this, if (stored.backdropEnabled) "Hintergrundbilder aus" else "Hintergrundbilder an", Toast.LENGTH_SHORT).show()
        }
        setting("Bevorzugte Tonsprache") {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Tonsprache").setItems(arrayOf("Automatisch", "Deutsch", "Englisch")) { _, index ->
                ServiceLocator.preferences.save(ServiceLocator.preferences.stored().copy(audioLanguage = listOf("", "de", "en")[index]))
            }.show()
        }
        setting("Bevorzugte Untertitelsprache") {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Untertitel").setItems(arrayOf("Aus", "Deutsch", "Englisch")) { _, index ->
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
            Toast.makeText(this, "Bildcache geleert (${before / 1024} KiB)", Toast.LENGTH_SHORT).show()
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

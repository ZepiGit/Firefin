package zepigit.firefin.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.JellyfinClient
import zepigit.firefin.app.preferences.StoredPreferences

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val session = ServiceLocator.session
        val accountInfo = findViewById<TextView>(R.id.accountInfo)
        val aboutText = findViewById<TextView>(R.id.aboutText)
        accountInfo.text = getString(R.string.account_info, session.userName, session.serverUrl)
        aboutText.text = getString(R.string.about_text, JellyfinClient.VERSION)

        // Each button shows its current value, so a change is visible without reopening the dialog.
        fun setting(id: Int, label: Int, value: (StoredPreferences) -> String, action: (refresh: () -> Unit) -> Unit) {
            val button = findViewById<Button>(id)
            val refresh = { button.text = getString(R.string.setting_value, getString(label), value(ServiceLocator.preferences.stored())) }
            refresh()
            button.setOnClickListener { action(refresh) }
        }
        fun choose(title: Int, options: Int, refresh: () -> Unit, apply: (StoredPreferences, Int) -> StoredPreferences) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setItems(options) { _, index ->
                ServiceLocator.preferences.save(apply(ServiceLocator.preferences.stored(), index))
                refresh()
            }.show()
        }

        val qualityLabels = resources.getStringArray(R.array.quality_options)
        val audioLabels = resources.getStringArray(R.array.audio_language_options)
        val subtitleLabels = resources.getStringArray(R.array.subtitle_language_options)
        setting(R.id.qualityButton, R.string.setting_quality, { stored ->
            QUALITIES.indexOf(stored.preferredHeight to stored.preferredBitrate).let { if (it >= 0) qualityLabels[it] else "${stored.preferredHeight}p" }
        }) { refresh ->
            choose(R.string.quality_dialog_title, R.array.quality_options, refresh) { stored, index ->
                stored.copy(preferredHeight = QUALITIES[index].first, preferredBitrate = QUALITIES[index].second)
            }
        }
        setting(R.id.backdropsButton, R.string.setting_backdrops, { stored -> getString(if (stored.backdropEnabled) R.string.state_on else R.string.state_off) }) { refresh ->
            val stored = ServiceLocator.preferences.stored()
            ServiceLocator.preferences.save(stored.copy(backdropEnabled = !stored.backdropEnabled))
            refresh()
        }
        setting(R.id.audioLanguageButton, R.string.setting_audio_language, { stored -> audioLabels[zepigit.firefin.app.preferences.Languages.CODES.indexOf(stored.audioLanguage).coerceAtLeast(0)] }) { refresh ->
            choose(R.string.audio_language_title, R.array.audio_language_options, refresh) { stored, index -> stored.copy(audioLanguage = zepigit.firefin.app.preferences.Languages.CODES[index]) }
        }
        setting(R.id.subtitleLanguageButton, R.string.setting_subtitle_language, { stored -> subtitleLabels[zepigit.firefin.app.preferences.Languages.CODES.indexOf(stored.subtitleLanguage).coerceAtLeast(0)] }) { refresh ->
            choose(R.string.subtitle_language_title, R.array.subtitle_language_options, refresh) { stored, index -> stored.copy(subtitleLanguage = zepigit.firefin.app.preferences.Languages.CODES[index]) }
        }
        val sizeLabels = resources.getStringArray(R.array.subtitle_size_options)
        setting(R.id.subtitleSizeButton, R.string.setting_subtitle_size, { sizeLabels[it.subtitleSize.coerceIn(0, 2)] }) { refresh ->
            choose(R.string.setting_subtitle_size, R.array.subtitle_size_options, refresh) { stored, index -> stored.copy(subtitleSize = index) }
        }
        val homeLabels = resources.getStringArray(R.array.home_order_options)
        setting(R.id.homeOrderButton, R.string.setting_home_order, { stored ->
            homeLabels[HomeSections.presets.indexOf(stored.homeSectionOrder).coerceAtLeast(0)]
        }) { refresh ->
            choose(R.string.setting_home_order, R.array.home_order_options, refresh) { stored, index -> stored.copy(homeSectionOrder = HomeSections.presets[index]) }
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
        findViewById<Button>(R.id.developerGithubButton).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_GITHUB_URL)))
            } catch (_: ActivityNotFoundException) {
                showGithubLinkWithoutBrowser()
            } catch (_: SecurityException) {
                showGithubLinkWithoutBrowser()
            }
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            ServiceLocator.client.logout()
            session.clear()
            LoginActivity.startFresh(this)
        }
    }

    private fun showGithubLinkWithoutBrowser() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.github_no_browser_title)
            .setMessage(getString(R.string.github_no_browser_message, DEVELOPER_GITHUB_URL))
            .setPositiveButton(R.string.copy_link) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.developer_github), DEVELOPER_GITHUB_URL))
                Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.ok, null)
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            textSize = 18f
            setTextIsSelectable(true)
        }
    }

    private companion object {
        const val DEVELOPER_GITHUB_URL = "https://github.com/ZepiGit"
        /** Height and total bitrate per entry of R.array.quality_options. */
        val QUALITIES = listOf(1080 to 4_000_000L, 720 to 4_000_000L, 720 to 2_000_000L, 480 to 1_000_000L)
    }
}

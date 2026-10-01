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

        findViewById<Button>(R.id.remoteButton).setOnClickListener {
            startActivity(Intent(this, RemoteActivity::class.java))
        }
        findViewById<Button>(R.id.clearCacheButton).setOnClickListener {
            val before = ServiceLocator.images.diskCacheBytes()
            ServiceLocator.images.clearCache()
            Toast.makeText(this, "Bildcache geleert (${before / 1024} KiB)", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.aboutButton).setOnClickListener {
            aboutText.visibility =
                if (aboutText.visibility == android.view.View.VISIBLE) android.view.View.GONE
                else android.view.View.VISIBLE
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            session.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

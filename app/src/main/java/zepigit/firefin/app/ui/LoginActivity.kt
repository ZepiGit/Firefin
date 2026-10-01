package zepigit.firefin.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator

class LoginActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ServiceLocator.session.isLoggedIn) {
            goHome()
            return
        }
        setContentView(R.layout.activity_login)
        val server = findViewById<EditText>(R.id.serverUrl)
        val user = findViewById<EditText>(R.id.username)
        val password = findViewById<EditText>(R.id.password)
        val button = findViewById<Button>(R.id.loginButton)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val error = findViewById<TextView>(R.id.error)

        button.setOnClickListener {
            button.isEnabled = false
            progress.visibility = View.VISIBLE
            error.visibility = View.GONE
            scope.launch {
                try {
                    ServiceLocator.client.login(
                        server.text.toString(),
                        user.text.toString(),
                        password.text.toString(),
                    )
                    goHome()
                } catch (e: Exception) {
                    error.text = when {
                        e.message?.contains("401") == true -> "Anmeldung fehlgeschlagen: Benutzer oder Passwort falsch."
                        e.message?.contains("HTTP") == true -> "Server nicht erreichbar oder Fehler: ${e.message}"
                        else -> "Verbindung fehlgeschlagen: ${e.message}"
                    }
                    error.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                    button.isEnabled = true
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}

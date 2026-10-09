package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.util.Urls

class LoginActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

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

        // Sign-out keeps the address and user name, so returning users only type the password.
        server.setText(ServiceLocator.session.serverUrl)
        user.setText(ServiceLocator.session.userName)
        if (server.text.isNotBlank() && user.text.isNotBlank()) password.requestFocus()

        fun showError(message: String) {
            error.text = message
            error.visibility = View.VISIBLE
            progress.visibility = View.GONE
            button.isEnabled = true
        }

        fun signIn(address: String, explicitScheme: Boolean) {
            button.isEnabled = false
            progress.visibility = View.VISIBLE
            error.visibility = View.GONE
            val name = user.text.toString()
            val secret = password.text.toString()
            scope.launch {
                try {
                    // The same login also opens the optional Seerr session; its failures never surface here.
                    ServiceLocator.client.login(address, name, secret)
                    password.text.clear()
                    goHome()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showError(loginErrorMessage(e, explicitScheme))
                }
            }
        }

        button.setOnClickListener {
            val input = server.text.toString()
            val explicitScheme = input.trim().contains("://")
            val address = try {
                Urls.normalizeServer(input)
            } catch (e: IllegalArgumentException) {
                showError(loginErrorMessage(e, explicitScheme))
                return@setOnClickListener
            }
            if (address.startsWith("http://")) {
                // Cleartext is the user's explicit choice for a trusted local network; confirm it every sign-in.
                AlertDialog.Builder(this)
                    .setTitle(R.string.cleartext_title)
                    .setMessage(R.string.cleartext_message)
                    .setPositiveButton(R.string.cleartext_continue) { _, _ -> signIn(address, explicitScheme) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                signIn(address, explicitScheme)
            }
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            // A sign-in in flight disables the button, so Done cannot submit twice.
            if (button.isEnabled) button.performClick()
            true
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    companion object {
        /** Starts login clearing the whole task (used after logout — no stale Home underneath). */
        fun startFresh(context: Context) {
            context.startActivity(
                Intent(context, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
    }
}

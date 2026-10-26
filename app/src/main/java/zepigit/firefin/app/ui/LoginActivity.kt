package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
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
import zepigit.firefin.app.util.Urls

class LoginActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var attemptJob: Job? = null
    private var attempt = 0
    private var dialog: AlertDialog? = null
    private lateinit var server: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var button: Button
    private lateinit var quickButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ServiceLocator.session.isLoggedIn) { goHome(); return }
        setContentView(R.layout.activity_login)
        server = findViewById(R.id.serverUrl)
        user = findViewById(R.id.username)
        password = findViewById(R.id.password)
        button = findViewById(R.id.loginButton)
        quickButton = findViewById(R.id.quickConnectButton)
        progress = findViewById(R.id.progress)
        error = findViewById(R.id.error)
        server.setText(ServiceLocator.session.serverUrl)
        user.setText(ServiceLocator.session.userName)
        if (server.text.isNotBlank() && user.text.isNotBlank()) password.requestFocus()
        server.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { cancelAttempt() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        button.setOnClickListener { confirmSignIn(quick = false) }
        quickButton.setOnClickListener { confirmSignIn(quick = true) }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            if (button.isEnabled) button.performClick()
            true
        }
    }

    private fun busy(value: Boolean) {
        button.isEnabled = !value
        quickButton.isEnabled = !value
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun closeDialog() {
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun cancelAttempt() {
        attempt++
        attemptJob?.cancel()
        attemptJob = null
        closeDialog()
        if (::button.isInitialized) busy(false)
    }

    private fun confirmSignIn(quick: Boolean) {
        if (attemptJob != null) return
        val input = server.text.toString()
        val explicitScheme = input.trim().contains("://")
        val address = try { Urls.normalizeServer(input) }
        catch (e: IllegalArgumentException) { showError(loginErrorMessage(e, explicitScheme)); return }
        busy(true)
        error.visibility = View.GONE
        if (address.startsWith("http://")) {
            dialog = AlertDialog.Builder(this).setTitle(R.string.cleartext_title).setMessage(R.string.cleartext_message)
                .setPositiveButton(R.string.cleartext_continue) { _, _ -> signIn(address, explicitScheme, quick) }
                .setNegativeButton(R.string.cancel) { _, _ -> cancelAttempt() }
                .setOnCancelListener { cancelAttempt() }.show()
        } else signIn(address, explicitScheme, quick)
    }

    private fun signIn(address: String, explicitScheme: Boolean, quick: Boolean) {
        closeDialog()
        val ownAttempt = ++attempt
        val name = user.text.toString()
        val secret = if (quick) "" else password.text.toString()
        attemptJob = scope.launch {
            try {
                if (quick) ServiceLocator.client.quickConnect(address) { code ->
                    if (ownAttempt == attempt) showCode(code)
                } else ServiceLocator.client.login(address, name, secret)
                if (ownAttempt != attempt) return@launch
                password.text.clear()
                closeDialog()
                goHome()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (ownAttempt == attempt) { closeDialog(); showError(loginErrorMessage(e, explicitScheme)) }
            } finally {
                if (ownAttempt == attempt) { attemptJob = null; busy(false) }
            }
        }
    }

    private fun showCode(code: String) {
        closeDialog()
        val panel = layoutInflater.inflate(R.layout.view_quick_connect, null)
        panel.findViewById<TextView>(R.id.quickConnectCode).text = code
        dialog = AlertDialog.Builder(this).setTitle(R.string.quick_connect_button).setView(panel)
            .setNegativeButton(R.string.cancel) { _, _ -> cancelAttempt(); quickButton.requestFocus() }
            .setOnCancelListener { cancelAttempt(); quickButton.requestFocus() }.show().also {
                it.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
            }
    }

    private fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
        busy(false)
    }

    @Deprecated("Uses the API-21-compatible back callback")
    override fun onBackPressed() {
        if (attemptJob != null) { cancelAttempt(); quickButton.requestFocus() } else super.onBackPressed()
    }

    override fun onDestroy() {
        cancelAttempt()
        scope.cancel()
        super.onDestroy()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    companion object {
        fun startFresh(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
}

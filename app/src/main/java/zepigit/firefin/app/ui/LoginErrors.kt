package zepigit.firefin.app.ui

import android.content.Context
import androidx.annotation.StringRes
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import zepigit.firefin.app.R
import zepigit.firefin.app.data.LocalizedIOException
import zepigit.firefin.app.data.ServerResponseException
import zepigit.firefin.app.data.SessionExpiredException

/**
 * Sign-in failure classified by exception type, never by message text.
 * [explicitScheme] is false when the user typed no scheme and the address was
 * completed to https://; a TLS failure then usually means a plain-HTTP server.
 */
@StringRes
internal fun loginErrorRes(error: Throwable, explicitScheme: Boolean): Int = when (error) {
    is SessionExpiredException -> R.string.login_error_credentials
    is ServerResponseException -> R.string.login_error_server
    is SSLException -> if (explicitScheme) R.string.login_error_certificate else R.string.login_error_https_hint
    is UnknownHostException -> R.string.login_error_unknown_host
    is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> R.string.login_error_unreachable
    is IllegalArgumentException -> R.string.login_error_address
    else -> R.string.login_error_connection
}

fun Context.loginErrorMessage(error: Throwable, explicitScheme: Boolean): String {
    val res = loginErrorRes(error, explicitScheme)
    return when {
        error is ServerResponseException -> getString(res, error.status)
        res != R.string.login_error_connection -> getString(res)
        error is LocalizedIOException -> errorMessage(error)
        else -> getString(res, error.message ?: error.javaClass.simpleName)
    }
}

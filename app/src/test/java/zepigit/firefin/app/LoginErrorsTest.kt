package zepigit.firefin.app

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Test
import zepigit.firefin.app.data.ServerResponseException
import zepigit.firefin.app.data.SessionExpiredException
import zepigit.firefin.app.ui.loginErrorRes

class LoginErrorsTest {

    @Test fun `rejected credentials are reported as wrong credentials, not as an expired session`() {
        // ServerTransport maps every HTTP 401 to SessionExpiredException.
        assertEquals(R.string.login_error_credentials, loginErrorRes(SessionExpiredException(), explicitScheme = true))
        assertEquals(R.string.login_error_credentials, loginErrorRes(SessionExpiredException(), explicitScheme = false))
    }

    @Test fun `tls failure suggests http only when the user typed no scheme`() {
        assertEquals(R.string.login_error_https_hint, loginErrorRes(SSLHandshakeException("plaintext port"), explicitScheme = false))
        assertEquals(R.string.login_error_certificate, loginErrorRes(SSLHandshakeException("untrusted"), explicitScheme = true))
        assertEquals(R.string.login_error_certificate, loginErrorRes(SSLPeerUnverifiedException("hostname"), explicitScheme = true))
    }

    @Test fun `transport errors carry localizable messages`() {
        assertEquals(R.string.error_session_expired, SessionExpiredException().messageRes)
        val status = ServerResponseException(503)
        assertEquals(R.string.error_server_status, status.messageRes)
        assertEquals(listOf<Any>(503), status.formatArgs)
    }

    @Test fun `network, server and address failures have their own messages`() {
        assertEquals(R.string.login_error_unknown_host, loginErrorRes(UnknownHostException("jellyfin.invalid"), true))
        assertEquals(R.string.login_error_unreachable, loginErrorRes(ConnectException("refused"), true))
        assertEquals(R.string.login_error_unreachable, loginErrorRes(SocketTimeoutException("timeout"), true))
        assertEquals(R.string.login_error_server, loginErrorRes(ServerResponseException(500), true))
        assertEquals(R.string.login_error_address, loginErrorRes(IllegalArgumentException("bad url"), false))
        assertEquals(R.string.login_error_connection, loginErrorRes(IOException("unexpected"), true))
    }
}

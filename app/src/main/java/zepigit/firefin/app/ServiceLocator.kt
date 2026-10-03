package zepigit.firefin.app

import android.content.Context
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import zepigit.firefin.app.data.JellyfinClient
import zepigit.firefin.app.data.SessionStore
import zepigit.firefin.app.images.ImageLoader

/** Simple constructor-injection composition root; no reflection framework. */
object ServiceLocator {
    lateinit var session: SessionStore
        private set
    lateinit var client: JellyfinClient
        private set
    lateinit var images: ImageLoader
        private set

    lateinit var discoveryImages: ImageLoader
        private set
    lateinit var preferences: zepigit.firefin.app.preferences.PreferenceStore
        private set

    fun isReady(): Boolean = ::session.isInitialized && ::client.isInitialized

    fun init(context: Context) {
        session = SessionStore(context.applicationContext)
        preferences = zepigit.firefin.app.preferences.PreferenceStore(context.applicationContext, session)
        // Android 5.1 predates ISRG Root X1 (Let's Encrypt), which platforms ship
        // from 7.1.1 on. It is added next to the platform anchors, never instead of
        // them; chain and hostname checks are unchanged for Jellyfin and TMDB.
        val rootPem = context.resources.openRawResource(R.raw.isrgrootx1).bufferedReader().use { it.readText() }
        val trust = HandshakeCertificates.Builder()
            .addTrustedCertificate(rootPem.decodeCertificatePem())
            .addPlatformTrustedCertificates().build()
        client = JellyfinClient(session, trust) { preferences.effective() }
        images = ImageLoader(context.applicationContext, client.okHttp, accountKey = {
            session.serverUrl + "\u0000" + session.userId
        })
        val publicHttp = okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val url = chain.request().url
                require(url.scheme == "https" && url.host == "image.tmdb.org" && url.port == 443 &&
                    url.username.isEmpty() && url.password.isEmpty() && url.encodedPath.startsWith("/t/p/"))
                chain.proceed(chain.request().newBuilder().removeHeader("Authorization").removeHeader("Cookie").build())
            }.build()
        discoveryImages = ImageLoader(context.applicationContext, publicHttp, "discovery", 8 * 1024 * 1024)
    }
}

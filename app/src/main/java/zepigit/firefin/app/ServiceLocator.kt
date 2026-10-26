package zepigit.firefin.app

import android.content.Context
import java.security.Security
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import org.conscrypt.Conscrypt
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

    fun init(context: Context) {
        session = SessionStore(context.applicationContext)
        preferences = zepigit.firefin.app.preferences.PreferenceStore(context.applicationContext, session)
        // Android 5.1 predates ISRG Root X1 (Let's Encrypt), which platforms ship
        // from 7.1.1 on. It is added next to the platform anchors, never instead of
        // them; chain and hostname checks are unchanged for Jellyfin and TMDB.
        // Built lazily on the first request (an IO thread): the Conscrypt JNI load and
        // the platform store parse are too slow for the main thread at app start.
        val resources = context.applicationContext.resources
        val trust: Lazy<HandshakeCertificates?> = lazy {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
            val rootPem = resources.openRawResource(R.raw.isrgrootx1).bufferedReader().use { it.readText() }
            HandshakeCertificates.Builder()
                .addTrustedCertificate(rootPem.decodeCertificatePem())
                .addPlatformTrustedCertificates().build()
        }
        client = JellyfinClient(session, trust) { preferences.effective() }
        // Warm the lazy trust store and clients off the main thread: a cold restore straight into
        // the player would otherwise parse the certificate store on it. Lazy is synchronized, so a
        // concurrent first request simply waits for this thread instead of doing the work twice.
        Thread({ runCatching { client.okHttp } }, "firefin-tls-warmup").start()
        images = ImageLoader(context.applicationContext, { client.okHttp }, accountKey = {
            session.serverUrl + "\u0000" + session.userId
        })
        val publicHttp = lazy {
            val anchors = trust.value!!
            okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(anchors.sslSocketFactory(), anchors.trustManager)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val url = chain.request().url
                    require(url.scheme == "https" && url.host == "image.tmdb.org" && url.port == 443 &&
                        url.username.isEmpty() && url.password.isEmpty() && url.encodedPath.startsWith("/t/p/"))
                    chain.proceed(chain.request().newBuilder().removeHeader("Authorization").removeHeader("Cookie").build())
                }.build()
        }
        discoveryImages = ImageLoader(context.applicationContext, { publicHttp.value }, "discovery", 8 * 1024 * 1024)
    }
}

package zepigit.firefin.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SessionExpiredException : IOException("Your session has expired. Please sign in again.")
class ServerResponseException(val status: Int) : IOException("Server request failed (HTTP $status).")

class ServerCredentials(val serverUrl: String, val userId: String, val token: String)

/** A single origin-bound, cancellable HTTP stack shared by REST, images and Media3. */
class ServerTransport(initial: ServerCredentials, private val deviceId: String, private val deviceName: String = "Fire TV", private val version: String = "0.1.0-firefin") {
    @Volatile private var current = initial
    private val lock = Any()

    private fun baseBuilder() = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)

    val http: OkHttpClient = baseBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", authorization())
                .header("Accept", "application/json")
                .removeHeader("Cookie")
                .build()
            requireServerUrl(request.url)
            chain.proceed(request)
        }).build()

    /** Media3 keeps long-running VOD bodies open while sharing origin/auth policy. */
    val mediaHttp: OkHttpClient = baseBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .addInterceptor(Interceptor { chain ->
            var request = chain.request()
            var redirects = 0
            while (true) {
                requireServerUrl(request.url)
                request = request.newBuilder()
                    .header("Authorization", authorization())
                    .header("Accept", if (request.header("Accept")?.contains("json") == true) "application/json" else "*/*")
                    .removeHeader("Cookie")
                    .build()
                val response = chain.proceed(request)
                if (response.code !in REDIRECTS) return@Interceptor response
                val target = response.header("Location")?.let(request.url::resolve)
                response.close()
                if (request.method != "GET" || target == null || ++redirects > 3) {
                    throw IOException("Server redirected the request. Check the configured server address.")
                }
                requireServerUrl(target)
                request = request.newBuilder().url(target).build()
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        })
        .build()

    fun update(credentials: ServerCredentials) {
        synchronized(lock) { current = credentials }
    }

    fun credentials(): ServerCredentials = current

    fun requireServerUrl(url: HttpUrl) {
        val configured = baseOrNull() ?: throw IOException("No server is configured.")
        val inOrigin = url.scheme == configured.scheme && url.host == configured.host && url.port == configured.port
        val prefix = configured.encodedPath.trimEnd('/')
        val inPath = prefix.isEmpty() || url.encodedPath == prefix || url.encodedPath.startsWith("$prefix/")
        if (!inOrigin || !inPath || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw IOException("Refusing a request outside the configured server.")
        }
    }

    fun url(path: String): HttpUrl {
        val configured = baseOrNull() ?: throw IOException("No server is configured.")
        val result = configured.resolve(path.removePrefix("/")) ?: throw IOException("Invalid server path.")
        requireServerUrl(result)
        return result
    }

    suspend fun json(path: String, method: String = "GET", body: String? = null): String {
        val builder = Request.Builder().url(url(path))
        val content = (body ?: "{}").toRequestBody(JSON)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(content)
            "DELETE" -> builder.delete()
            "PUT" -> builder.put(content)
            else -> throw IllegalArgumentException("Unsupported request method")
        }
        return execute(builder.build())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (it.code == 401) throw SessionExpiredException()
                            if (!it.isSuccessful) throw ServerResponseException(it.code)
                            val body = it.body ?: return@use ""
                            if (body.contentLength() > MAX_JSON_BYTES) throw IOException("Server response is too large.")
                            body.string().also { value -> if (value.toByteArray().size > MAX_JSON_BYTES) throw IOException("Server response is too large.") }
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
    }

    private fun baseOrNull(): HttpUrl? = current.serverUrl.trimEnd('/').takeIf { it.isNotBlank() }?.toHttpUrl()

    private fun authorization(): String = buildString {
        append("MediaBrowser Client=\"Firefin\", Device=\"").append(headerValue(deviceName))
        append("\", DeviceId=\"").append(headerValue(deviceId))
        append("\", Version=\"").append(headerValue(version)).append('"')
        current.token.takeIf { it.isNotBlank() }?.let { append(", Token=\"").append(headerValue(it)).append('"') }
    }

    private fun headerValue(value: String): String = value.filter { it in ' '..'~' && it != '"' && it != '\\' }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        const val MAX_JSON_BYTES = 8L * 1024 * 1024
    }
}

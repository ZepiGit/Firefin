package zepigit.firefin.app.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import zepigit.firefin.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Bounded artwork cache/loader for the 1 GiB Fire TV target: memory LruCache,
 * disk cache capped at 64 MiB (trimmed on startup and on write), at most two
 * concurrent fetches, decode sampled to the ArtworkPolicy bucket, and hard
 * caps on both network and disk input bytes so a hostile or broken artwork
 * response can neither OOM the app nor hold a fetch permit forever.
 */
class ImageLoader(context: Context, http: okhttp3.OkHttpClient) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val fetchPermits = Semaphore(2)
    private val imageHttp = http.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val memCache = object : android.util.LruCache<String, Bitmap>(minOf(32 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt())) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val diskDir = File(context.cacheDir, "images").apply { mkdirs() }
    private val diskCacheCapBytes = 64L * 1024 * 1024

    init {
        scope.launch(Dispatchers.IO) { trimDiskCache() }
    }

    fun load(url: String, targetWidth: Int, view: ImageView, placeholder: Int = android.R.color.transparent) {
        if (view.getTag(R.id.image_loader_url) == url) return
        view.setTag(R.id.image_loader_url, url)
        view.setImageResource(placeholder)
        memCache.get(url)?.let { view.setImageBitmap(it); return }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadFromDisk(url, targetWidth) ?: fetchAndDecode(url, targetWidth) }
            if (bitmap == null) {
                if (view.getTag(R.id.image_loader_url) == url) view.setTag(R.id.image_loader_url, null)
            } else if (view.getTag(R.id.image_loader_url) == url) view.setImageBitmap(bitmap)
        }
    }

    fun clearCache() { memCache.evictAll(); scope.launch(Dispatchers.IO) { diskDir.listFiles()?.forEach { it.delete() } } }
    fun diskCacheBytes(): Long = diskDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun diskFile(url: String) = File(diskDir, MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })

    private fun loadFromDisk(url: String, targetWidth: Int): Bitmap? {
        val file = diskFile(url)
        if (!file.exists()) return null
        if (file.length() > MAX_IMAGE_BYTES) { file.delete(); return null }
        val bytes = readBounded(file.inputStream(), MAX_IMAGE_BYTES) ?: run { file.delete(); return null }
        return decode(bytes, targetWidth)?.also { memCache.put(url, it) } ?: run { file.delete(); null }
    }

    private fun fetchAndDecode(url: String, targetWidth: Int): Bitmap? = try {
        fetchPermits.acquire()
        try {
            imageHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                val bytes = readBounded(body.byteStream(), MAX_IMAGE_BYTES) ?: return@use null
                decode(bytes, targetWidth)?.also {
                    memCache.put(url, it)
                    runCatching { diskFile(url).writeBytes(bytes); trimDiskCache() }
                }
            }
        } finally { fetchPermits.release() }
    } catch (_: Exception) { null }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray? = try {
        input.use { stream ->
            val buffer = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
            val chunk = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    } catch (_: Exception) { null }

    private fun decode(bytes: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    private fun trimDiskCache() {
        var files = diskDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        while (total > diskCacheCapBytes && files.isNotEmpty()) { val old = files.first(); total -= old.length(); old.delete(); files = files.drop(1) }
    }

    private companion object { const val MAX_IMAGE_BYTES = 12 * 1024 * 1024 }
}

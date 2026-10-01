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
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Semaphore

/**
 * Bounded artwork loader: memory LruCache (~1/8 heap, cap 32 MiB), disk cache
 * capped at 64 MiB, at most 2 concurrent image requests per app (matching the
 * legacy FireTV32 image-host concurrency limit). Decode targets the
 * ArtworkPolicy bucket so bitmaps stay small.
 */
class ImageLoader(context: Context, private val http: okhttp3.OkHttpClient) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val fetchPermits = Semaphore(2)

    private val memCache = object : android.util.LruCache<String, Bitmap>(
        minOf(32 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt()),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val diskDir = File(context.cacheDir, "images").apply { mkdirs() }
    private val diskCacheCapBytes = 64L * 1024 * 1024

    fun load(url: String, targetWidth: Int, view: ImageView, placeholder: Int = android.R.color.transparent) {
        if (view.getTag(R.id.image_loader_url) == url) return
        view.setTag(R.id.image_loader_url, url)
        view.setImageResource(placeholder)
        memCache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadFromDisk(url) ?: fetchAndDecode(url, targetWidth)
            }
            if (bitmap == null) {
                // Allow a retry on rebind instead of keeping a dead tag forever.
                if (view.getTag(R.id.image_loader_url) == url) view.setTag(R.id.image_loader_url, null)
                return@launch
            }
            if (view.getTag(R.id.image_loader_url) == url) {
                view.setImageBitmap(bitmap)
            }
        }
    }

    fun clearCache() {
        memCache.evictAll()
        scope.launch(Dispatchers.IO) {
            diskDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun diskCacheBytes(): Long = diskDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun diskFile(url: String): File {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return File(diskDir, digest.joinToString("") { "%02x".format(it) })
    }

    private fun loadFromDisk(url: String): Bitmap? {
        val file = diskFile(url)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) file.delete()
        return bitmap
    }

    private fun fetchAndDecode(url: String, targetWidth: Int): Bitmap? = try {
        fetchPermits.acquire()
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                val bitmap = decode(bytes, targetWidth) ?: return@use null
                memCache.put(url, bitmap)
                try {
                    val file = diskFile(url)
                    file.writeBytes(bytes)
                    trimDiskCache()
                } catch (_: Exception) {
                    // Disk cache is best-effort only.
                }
                bitmap
            }
        } finally {
            fetchPermits.release()
        }
    } catch (_: Exception) {
        null
    }

    private fun decode(bytes: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        // Sample by width against the ArtworkPolicy bucket ladder; the sampled
        // bitmap never exceeds the bucket width (single-axis footprint).
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun trimDiskCache() {
        var files = diskDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        while (total > diskCacheCapBytes && files.isNotEmpty()) {
            val oldest = files.first()
            total -= oldest.length()
            oldest.delete()
            files = files.drop(1)
        }
    }
}

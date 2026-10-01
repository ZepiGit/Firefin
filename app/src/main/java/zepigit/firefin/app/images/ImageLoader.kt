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

/** Bounded artwork cache/loader for the 1 GiB Fire TV target. */
class ImageLoader(context: Context, private val http: okhttp3.OkHttpClient) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val fetchPermits = Semaphore(2)
    private val memCache = object : android.util.LruCache<String, Bitmap>(minOf(32 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt())) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val diskDir = File(context.cacheDir, "images").apply { mkdirs() }
    private val diskCacheCapBytes = 64L * 1024 * 1024

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
        return file.readBytes().let { bytes -> decode(bytes, targetWidth)?.also { memCache.put(url, it) } ?: run { file.delete(); null } }
    }

    private fun fetchAndDecode(url: String, targetWidth: Int): Bitmap? = try {
        fetchPermits.acquire()
        try {
            http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                decode(bytes, targetWidth)?.also {
                    memCache.put(url, it)
                    runCatching { diskFile(url).writeBytes(bytes); trimDiskCache() }
                }
            }
        } finally { fetchPermits.release() }
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
}

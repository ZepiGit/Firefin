package zepigit.firefin.app.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import zepigit.firefin.app.R

/** Two shared decode/fetch slots, bounded weak-view queue, and sampled RGB565 caches. */
class ImageLoader(context: Context, http: okhttp3.OkHttpClient, cacheName: String = "images", memoryLimit: Int = 16 * 1024 * 1024, private val accountKey: () -> String = { "public" }) {
    private class Load(val url: String, val width: Int, val account: String, val key: String, view: ImageView) {
        val view = WeakReference(view)
        @Volatile var cancelled = false
        @Volatile var call: Call? = null
        fun cancel() { cancelled = true; call?.cancel() }
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val pending = Channel<Load>(32, BufferOverflow.DROP_OLDEST, onUndeliveredElement = { it.cancel() })
    private val imageHttp = http.newBuilder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val memCache = object : android.util.LruCache<String, Bitmap>(minOf(memoryLimit, (Runtime.getRuntime().maxMemory() / 8).toInt())) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failures = android.util.LruCache<String, Long>(128)
    private val diskDir = File(context.cacheDir, cacheName).apply { mkdirs() }
    private val diskCacheCapBytes = if (cacheName == "images") 48L * 1024 * 1024 else 16L * 1024 * 1024
    private val diskLock = Any()
    @Volatile private var generation = 0

    init {
        scope.launch(Dispatchers.IO) { trimDiskCache() }
        repeat(2) {
            scope.launch {
                for (load in pending) {
                    val view = load.view.get()
                    if (load.cancelled || view == null || view.getTag(R.id.image_loader_url) !== load) continue
                    val epoch = generation
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeSlots.withPermit {
                            if (load.cancelled || epoch != generation || load.account != accountKey()) null
                            else loadFromDisk(load, epoch) ?: fetchAndDecode(load, epoch)
                        }
                    }
                    if (view.getTag(R.id.image_loader_url) === load && !load.cancelled) {
                        if (bitmap != null && epoch == generation && load.account == accountKey()) view.setImageBitmap(bitmap)
                        else view.setTag(R.id.image_loader_url, null)
                    }
                }
            }
        }
    }

    fun load(url: String, targetWidth: Int, view: ImageView, placeholder: Int = android.R.color.transparent) {
        val previous = view.getTag(R.id.image_loader_url) as? Load
        if (previous?.url == url && previous.width == targetWidth && previous.account == accountKey() && !previous.cancelled) return
        previous?.cancel()
        view.setImageResource(placeholder)
        val cacheKey = cacheKey(url, targetWidth)
        memCache.get(cacheKey)?.let { view.setImageBitmap(it); view.setTag(R.id.image_loader_url, null); return }
        if (System.currentTimeMillis() - (failures.get(url) ?: 0) < 30_000) {
            view.setTag(R.id.image_loader_url, null)
            return
        }
        val load = Load(url, targetWidth, accountKey(), cacheKey, view)
        view.setTag(R.id.image_loader_url, load)
        pending.trySend(load)
    }

    fun cancel(view: ImageView) {
        (view.getTag(R.id.image_loader_url) as? Load)?.cancel()
        view.setTag(R.id.image_loader_url, null)
        view.setImageDrawable(null)
    }

    fun clearCache() {
        synchronized(diskLock) {
            generation++
            memCache.evictAll(); failures.evictAll()
        }
        scope.launch(Dispatchers.IO) { synchronized(diskLock) { diskDir.listFiles()?.forEach { it.delete() } } }
    }
    fun diskCacheBytes(): Long = synchronized(diskLock) { diskDir.listFiles()?.sumOf { it.length() } ?: 0L }
    private fun cacheKey(url: String, width: Int) = "${accountKey()}\u0000$url@$width"
    private fun diskFile(key: String) = File(diskDir, MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) })

    private fun loadFromDisk(load: Load, epoch: Int): Bitmap? {
        val bytes = synchronized(diskLock) {
            val file = diskFile(load.key)
            if (!file.exists()) return null
            if (file.length() > MAX_IMAGE_BYTES) { file.delete(); return null }
            // Touch on read so the size trim removes the least recently used files first.
            file.setLastModified(System.currentTimeMillis())
            runCatching { readBounded(file.inputStream()) }.getOrNull()
        } ?: return null
        val bitmap = runCatching { decode(bytes, load.width) }.getOrNull()
        synchronized(diskLock) {
            if (epoch != generation || load.account != accountKey() || load.cancelled) return null
            if (bitmap == null) diskFile(load.key).delete() else memCache.put(load.key, bitmap)
        }
        return bitmap
    }

    private fun fetchAndDecode(load: Load, epoch: Int): Bitmap? = try {
        val call = imageHttp.newCall(okhttp3.Request.Builder().url(load.url).build())
        load.call = call
        if (load.cancelled) { call.cancel(); null } else call.execute().use { response ->
            if (!response.isSuccessful || load.cancelled) { failures.put(load.url, System.currentTimeMillis()); return@use null }
            val body = response.body ?: return@use null
            if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
            val bytes = readBounded(body.byteStream()) ?: return@use null
            if (load.cancelled || epoch != generation || load.account != accountKey()) return@use null
            decode(bytes, load.width)?.also { bitmap ->
                synchronized(diskLock) {
                    if (epoch == generation && !load.cancelled && load.account == accountKey()) {
                        memCache.put(load.key, bitmap)
                        runCatching {
                            val temp = File.createTempFile("art", ".tmp", diskDir)
                            try {
                                temp.writeBytes(bytes)
                                if (!temp.renameTo(diskFile(load.key))) temp.delete()
                                trimDiskCache()
                            } finally { temp.delete() }
                        }
                    }
                }
            }
        }
    } catch (failure: Exception) {
        if (zepigit.firefin.app.BuildConfig.DEBUG && !load.cancelled) android.util.Log.w("FirefinArtwork", failure.javaClass.simpleName)
        if (!load.cancelled) failures.put(load.url, System.currentTimeMillis())
        null
    } catch (failure: OutOfMemoryError) {
        // A single oversized artwork must not end the shared decode workers on a 1 GB device.
        memCache.evictAll()
        if (!load.cancelled) failures.put(load.url, System.currentTimeMillis())
        null
    } finally { load.call = null }

    private fun readBounded(input: java.io.InputStream): ByteArray? = input.use { stream ->
        val buffer = ByteArrayOutputStream(128 * 1024)
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_IMAGE_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray()
    }

    private fun decode(bytes: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val pixels = ((bounds.outWidth.toLong() + sample - 1) / sample) * ((bounds.outHeight.toLong() + sample - 1) / sample)
        if (pixels > MAX_DECODE_PIXELS) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    private fun trimDiskCache() = synchronized(diskLock) {
        val files = diskDir.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= diskCacheCapBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private companion object {
        val decodeSlots = Semaphore(2)
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        const val MAX_DECODE_PIXELS = 4_000_000L
    }
}

package app.nodx.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Progress/state of one model download. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Double,
    ) : DownloadState {
        val progress: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
        val etaSeconds: Long
            get() = if (bytesPerSecond > 1) ((totalBytes - downloadedBytes) / bytesPerSecond).toLong() else -1
    }

    data class Paused(val downloadedBytes: Long) : DownloadState
    data class Done(val path: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Downloads GGUF models with progress, pause/resume (HTTP Range on the
 * .part file), and cancel. Files land in filesDir/models/<filename>.
 */
class ModelDownloader(
    private val http: OkHttpClient,
    private val context: Context,
) {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    @Volatile private var paused = mutableSetOf<String>()

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun pathFor(filename: String): File = File(modelsDir, filename)

    fun isDownloaded(filename: String): Boolean = pathFor(filename).exists()

    /** Starts (or resumes) a download. Safe to re-call after pause/failure. */
    suspend fun download(model: CatalogModel) = withContext(Dispatchers.IO) {
        val dest = pathFor(model.filename)
        if (dest.exists()) {
            _states.value = _states.value + (model.id to DownloadState.Done(dest.absolutePath))
            return@withContext
        }
        paused.remove(model.id)
        val part = File(modelsDir, model.filename + ".part")
        var downloaded = if (part.exists()) part.length() else 0L

        val request = Request.Builder()
            .url(model.url)
            .apply { if (downloaded > 0) header("Range", "bytes=$downloaded-") }
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    fail(model.id, "HTTP ${resp.code}")
                    return@withContext
                }
                // 206 Partial → append; 200 → server ignored Range, restart
                val appending = resp.code == 206
                if (!appending) {
                    downloaded = 0
                    part.delete()
                }
                val total = if (appending) downloaded + (resp.body?.contentLength() ?: 0)
                else resp.body?.contentLength() ?: model.sizeBytes

                val startMs = System.currentTimeMillis()
                val startBytes = downloaded
                var lastTick = startMs

                resp.body?.byteStream()?.let { input ->
                    java.io.FileOutputStream(part, appending).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (model.id in paused) {
                                _states.value = _states.value +
                                    (model.id to DownloadState.Paused(part.length()))
                                return@withContext
                            }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 250) {
                                lastTick = now
                                val bps = (downloaded - startBytes) /
                                    ((now - startMs) / 1000.0).coerceAtLeast(0.25)
                                _states.value = _states.value + (
                                    model.id to DownloadState.Downloading(downloaded, total, bps)
                                    )
                            }
                        }
                    }
                }

                if (part.length() < 1024 * 1024) { // < 1 MB is never a real model
                    part.delete()
                    fail(model.id, "Downloaded file too small — broken link?")
                    return@withContext
                }
                if (!part.renameTo(dest)) {
                    fail(model.id, "Could not finalize ${model.filename}")
                    return@withContext
                }
                _states.value = _states.value + (model.id to DownloadState.Done(dest.absolutePath))
            }
        } catch (e: IOException) {
            if (model.id in paused) {
                _states.value = _states.value + (model.id to DownloadState.Paused(part.length()))
            } else {
                fail(model.id, e.message ?: e.toString())
            }
        }
    }

    fun pause(id: String) {
        paused.add(id)
    }

    fun delete(filename: String) {
        pathFor(filename).delete()
        File(modelsDir, filename + ".part").delete()
    }

    private fun fail(id: String, message: String) {
        _states.value = _states.value + (id to DownloadState.Failed(message))
    }
}

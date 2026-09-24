package com.example.data.music

import android.content.Context
import android.util.Log
import com.example.data.api.JioSaavnApiClient
import com.example.data.api.JioSaavnDecoder
import com.example.data.model.DownloadedSongItem
import com.example.data.model.SaavnSongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MusicDownloadManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadsDir: File = File(context.filesDir, "music_downloads").apply { mkdirs() }
    private val metaFile: File = File(context.filesDir, "music_downloads.json")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadedSongs = MutableStateFlow<List<DownloadedSongItem>>(emptyList())
    val downloadedSongs: StateFlow<List<DownloadedSongItem>> = _downloadedSongs.asStateFlow()

    // Map of songId to progress percentage (0..100)
    private val _activeDownloads = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Int>> = _activeDownloads.asStateFlow()

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        if (!metaFile.exists()) return
        try {
            val content = metaFile.readText()
            if (content.isBlank()) return
            val array = JSONArray(content)
            val list = mutableListOf<DownloadedSongItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val path = obj.optString("localFilePath")
                val file = File(path)
                // Keep only valid existing files
                if (file.exists() && file.length() > 30 * 1024) {
                    list.add(
                        DownloadedSongItem(
                            id = id,
                            title = obj.optString("title"),
                            artist = obj.optString("artist"),
                            album = obj.optString("album"),
                            coverUrl = obj.optString("coverUrl"),
                            durationSec = obj.optInt("durationSec"),
                            bitrateKbps = obj.optString("bitrateKbps", "320"),
                            localFilePath = path,
                            fileSizeBytes = obj.optLong("fileSizeBytes", file.length()),
                            downloadedAt = obj.optLong("downloadedAt")
                        )
                    )
                }
            }
            _downloadedSongs.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading downloaded songs metadata", e)
        }
    }

    private fun saveMetadata() {
        try {
            val array = JSONArray()
            _downloadedSongs.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("coverUrl", item.coverUrl)
                    put("durationSec", item.durationSec)
                    put("bitrateKbps", item.bitrateKbps)
                    put("localFilePath", item.localFilePath)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("downloadedAt", item.downloadedAt)
                }
                array.put(obj)
            }
            metaFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving downloaded songs metadata", e)
        }
    }

    fun isSongDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.id == songId }
    }

    fun getDownloadedSong(songId: String): DownloadedSongItem? {
        return _downloadedSongs.value.firstOrNull { it.id == songId }
    }

    fun getDownloadedFile(songId: String): File? {
        val song = getDownloadedSong(songId) ?: return null
        val f = File(song.localFilePath)
        return if (f.exists()) f else null
    }

    fun downloadSong(song: SaavnSongItem, preferredBitrate: String = "320", onComplete: ((Boolean) -> Unit)? = null) {
        if (isSongDownloaded(song.id)) {
            onComplete?.invoke(true)
            return
        }
        if (_activeDownloads.value.containsKey(song.id)) return

        scope.launch {
            _activeDownloads.value = _activeDownloads.value + (song.id to 0)
            val success = performDownload(song, preferredBitrate)
            _activeDownloads.value = _activeDownloads.value - song.id
            withContext(Dispatchers.Main) {
                onComplete?.invoke(success)
            }
        }
    }

    private suspend fun performDownload(song: SaavnSongItem, preferredBitrate: String): Boolean = withContext(Dispatchers.IO) {
        // Fallback sequence: 320 -> 160 -> 96
        val bitratesToTry = when (preferredBitrate) {
            "320" -> listOf("320", "160", "96")
            "160" -> listOf("160", "96", "320")
            else -> listOf("96", "160", "320")
        }

        var directUrl: String? = null
        var chosenBitrate = preferredBitrate

        for (br in bitratesToTry) {
            val decrypted = JioSaavnDecoder.decryptMediaUrl(song.encryptedMediaUrl, br)
            if (!decrypted.isNullOrBlank()) {
                // Verify HEAD request
                try {
                    val headReq = Request.Builder().url(decrypted).head().build()
                    val headResp = httpClient.newCall(headReq).execute()
                    if (headResp.isSuccessful) {
                        directUrl = decrypted
                        chosenBitrate = br
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        if (directUrl == null) {
            // Fallback: try raw decrypted without head check
            directUrl = JioSaavnDecoder.decryptMediaUrl(song.encryptedMediaUrl, preferredBitrate)
        }

        if (directUrl.isNullOrBlank()) {
            Log.e(TAG, "Cannot decrypt download URL for song ${song.id}")
            return@withContext false
        }

        val targetFile = File(downloadsDir, "saavn_${song.id}_${chosenBitrate}k.m4a")
        val tempFile = File(downloadsDir, "temp_song_${song.id}.tmp")

        try {
            val req = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                tempFile.delete()
                return@withContext false
            }

            val body = resp.body ?: run {
                tempFile.delete()
                return@withContext false
            }

            val totalLength = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalLength > 0) {
                            val progress = ((downloadedBytes * 100) / totalLength).toInt()
                            _activeDownloads.value = _activeDownloads.value + (song.id to progress)
                        }
                    }
                    output.flush()
                }
            }

            // Validate minimum size (at least 30 KB)
            if (tempFile.length() < 30 * 1024) {
                tempFile.delete()
                return@withContext false
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val item = DownloadedSongItem(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                coverUrl = song.image,
                durationSec = song.durationSec,
                bitrateKbps = chosenBitrate,
                localFilePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length(),
                downloadedAt = System.currentTimeMillis()
            )

            _downloadedSongs.value = _downloadedSongs.value.filter { it.id != song.id } + item
            saveMetadata()
            Log.d(TAG, "Song ${song.title} downloaded successfully (${targetFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for song ${song.id}", e)
            tempFile.delete()
            false
        }
    }

    fun deleteDownloadedSong(songId: String) {
        val song = getDownloadedSong(songId)
        if (song != null) {
            try {
                val f = File(song.localFilePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete file for $songId: ${e.message}")
            }
        }
        _downloadedSongs.value = _downloadedSongs.value.filter { it.id != songId }
        saveMetadata()
    }

    companion object {
        private const val TAG = "MusicDownloadManager"

        @Volatile
        private var INSTANCE: MusicDownloadManager? = null

        fun getInstance(context: Context): MusicDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.2f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.0f KB".format(kb)
                else -> "$bytes B"
            }
        }
    }
}

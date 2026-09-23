package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.api.MovieBoxApiClient
import com.example.data.api.StoryTvApiClient
import com.example.data.api.VskitShortsApiClient
import com.example.data.model.MovieItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.StringReader
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "MovieDownloadManager"
private const val DOWNLOAD_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
private const val REFERER_MOVIEBOX = "https://movieboxph.org/"
private const val ORIGIN_MOVIEBOX = "https://movieboxph.org"
private const val REFERER_VSKIT = "https://vskit.online/"
private const val ORIGIN_VSKIT = "https://vskit.online"

class MovieDownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.downloadDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedItemIds = ConcurrentHashMap.newKeySet<String>()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        // Observe downloads from database
        scope.launch {
            dao.getAllDownloads().collect { list ->
                _downloads.value = list
            }
        }
        // Scan completed downloads into phone Gallery on startup
        scope.launch {
            delay(2000)
            try {
                val completed = dao.getCompletedDownloads()
                for (item in completed) {
                    val targetFile = File(item.localFilePath)
                    if (targetFile.exists() && targetFile.length() > 0) {
                        exportVideoToGallery(targetFile, item)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed scanning existing downloads to gallery: ${e.message}")
            }
        }
    }

    /**
     * Start or queue a download for a movie, series episode, or short video
     */
    fun startDownload(
        movie: MovieItem,
        quality: String,
        downloadUrl: String,
        dubLabel: String = "",
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        isSeries: Boolean = false
    ) {
        val safeMovieId = movie.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "media_${System.currentTimeMillis()}" }
        val safeQuality = quality.filter { it.isLetterOrDigit() }.ifBlank { "720p" }
        val downloadId = if (isSeries && (seasonNumber > 0 || episodeNumber > 0)) {
            "${safeMovieId}_s${seasonNumber}e${episodeNumber}_${safeQuality}"
        } else {
            "${safeMovieId}_${safeQuality}"
        }

        pausedItemIds.remove(downloadId)

        scope.launch {
            val existing = dao.getDownloadById(downloadId)
            if (existing != null && existing.status == DownloadStatus.COMPLETED) {
                val targetFile = File(existing.localFilePath)
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@launch
                }
            }

            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "movies")
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }

            val safeFileName = if (isSeries && (seasonNumber > 0 || episodeNumber > 0)) {
                "${safeMovieId}_s${seasonNumber}e${episodeNumber}_${safeQuality}.mp4"
            } else {
                "${safeMovieId}_${safeQuality}.mp4"
            }
            val targetFile = File(moviesDir, safeFileName)
            val tempFile = File(moviesDir, "$safeFileName.download")

            val item = DownloadItem(
                id = downloadId,
                movieId = movie.id,
                title = movie.title,
                coverUrl = movie.coverUrl,
                backdropUrl = movie.backdropUrl,
                quality = quality,
                downloadUrl = downloadUrl.trim(),
                localFilePath = targetFile.absolutePath,
                totalBytes = 0L,
                downloadedBytes = 0L,
                progress = 0f,
                speedFormatted = "Starting...",
                status = DownloadStatus.DOWNLOADING,
                errorMessage = null,
                dubLabel = dubLabel,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                isSeries = isSeries,
                createdAt = System.currentTimeMillis()
            )

            dao.insertOrUpdate(item)
            launchDownloadJob(item, tempFile, targetFile)
        }
    }

    /**
     * Start or queue an "All-in-One" download for shorts, merging all episodes serial-wise
     * into a single continuous video file.
     */
    fun startMergeDownload(
        movie: MovieItem,
        quality: String,
        dubLabel: String = "Original",
        episodes: List<Pair<Int, Int>>,
        firstStreamUrl: String = ""
    ) {
        val safeMovieId = movie.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "shorts_${System.currentTimeMillis()}" }
        val safeQuality = quality.filter { it.isLetterOrDigit() }.ifBlank { "720p" }
        val sortedEps = episodes.sortedWith(compareBy({ it.first }, { it.second })).ifEmpty {
            listOf(1 to 1)
        }
        val totalEpisodes = sortedEps.size
        val epRangeLabel = if (totalEpisodes > 1) {
            "Ep ${sortedEps.first().second}-${sortedEps.last().second}"
        } else {
            "Ep ${sortedEps.first().second}"
        }
        val downloadId = "${safeMovieId}_all_in_one_${safeQuality}"

        pausedItemIds.remove(downloadId)

        scope.launch {
            val existing = dao.getDownloadById(downloadId)
            if (existing != null && existing.status == DownloadStatus.COMPLETED) {
                val targetFile = File(existing.localFilePath)
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@launch
                }
            }

            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "movies")
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }

            val safeFileName = "${safeMovieId}_All_In_One_${safeQuality}.mp4"
            val targetFile = File(moviesDir, safeFileName)
            val tempFile = File(moviesDir, "$safeFileName.download")

            val item = DownloadItem(
                id = downloadId,
                movieId = movie.id,
                title = "${movie.title} [All-in-One $epRangeLabel]",
                coverUrl = movie.coverUrl,
                backdropUrl = movie.backdropUrl,
                quality = quality,
                downloadUrl = firstStreamUrl.trim(),
                localFilePath = targetFile.absolutePath,
                totalBytes = 0L,
                downloadedBytes = 0L,
                progress = 0f,
                speedFormatted = "Preparing All-in-One download...",
                status = DownloadStatus.DOWNLOADING,
                errorMessage = null,
                dubLabel = dubLabel,
                seasonNumber = sortedEps.first().first,
                episodeNumber = sortedEps.first().second,
                isSeries = true,
                createdAt = System.currentTimeMillis()
            )

            dao.insertOrUpdate(item)
            launchMergeDownloadJob(item, sortedEps, tempFile, targetFile, firstStreamUrl)
        }
    }

    /**
     * Pause an ongoing download without showing failed or error.
     */
    fun pauseDownload(id: String) {
        pausedItemIds.add(id)
        val job = activeJobs.remove(id)
        job?.cancel()

        scope.launch {
            val item = dao.getDownloadById(id) ?: return@launch
            if (item.status == DownloadStatus.COMPLETED) return@launch

            val tempFile = File("${item.localFilePath}.download")
            val currentBytes = if (tempFile.exists()) tempFile.length() else item.downloadedBytes
            val progress = if (item.totalBytes > 0) {
                (currentBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f)
            } else {
                item.progress
            }

            val updated = item.copy(
                status = DownloadStatus.PAUSED,
                speedFormatted = "",
                errorMessage = null,
                downloadedBytes = currentBytes,
                progress = progress
            )
            dao.update(updated)
        }
    }

    /**
     * Resume a paused or failed download
     */
    fun resumeDownload(id: String) {
        pausedItemIds.remove(id)
        scope.launch {
            val item = dao.getDownloadById(id) ?: return@launch
            if (item.status == DownloadStatus.DOWNLOADING && activeJobs.containsKey(id)) return@launch

            val targetFile = File(item.localFilePath)
            val tempFile = File("${item.localFilePath}.download")
            targetFile.parentFile?.mkdirs()

            val downloadedSoFar = if (tempFile.exists()) tempFile.length() else item.downloadedBytes
            val progress = if (item.totalBytes > 0) {
                (downloadedSoFar.toFloat() / item.totalBytes).coerceIn(0f, 1f)
            } else {
                item.progress
            }

            val updated = item.copy(
                status = DownloadStatus.DOWNLOADING,
                downloadedBytes = downloadedSoFar,
                progress = progress,
                speedFormatted = "Resuming...",
                errorMessage = null
            )
            dao.update(updated)

            if (item.id.contains("_all_in_one_")) {
                val epMatch = Regex("Ep (\\d+)-(\\d+)").find(item.title)
                val startEp = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val endEp = epMatch?.groupValues?.get(2)?.toIntOrNull() ?: item.episodeNumber.coerceAtLeast(1)
                val epList = (startEp..endEp).map { item.seasonNumber to it }
                launchMergeDownloadJob(updated, epList, tempFile, targetFile, item.downloadUrl)
            } else {
                launchDownloadJob(updated, tempFile, targetFile)
            }
        }
    }

    /**
     * Cancel and delete a download
     */
    fun deleteDownload(id: String) {
        pausedItemIds.remove(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        scope.launch {
            val item = dao.getDownloadById(id)
            if (item != null) {
                try {
                    val file = File(item.localFilePath)
                    if (file.exists()) file.delete()
                    val temp = File("${item.localFilePath}.download")
                    if (temp.exists()) temp.delete()

                    val displayName = if (file.name.endsWith(".mp4", ignoreCase = true)) file.name else "${file.nameWithoutExtension}.mp4"
                    try {
                        context.contentResolver.delete(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                            arrayOf(displayName)
                        )
                    } catch (_: Exception) {}

                    try {
                        val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MovieBox/$displayName")
                        if (publicFile.exists()) {
                            publicFile.delete()
                            MediaScannerConnection.scanFile(context, arrayOf(publicFile.absolutePath), arrayOf("video/mp4"), null)
                        }
                    } catch (_: Exception) {}
                } catch (_: Exception) {}
            }
            dao.deleteById(id)
        }
    }

    /**
     * Guarantee that paused state is saved into Room DB with NO error message.
     */
    private suspend fun ensurePausedState(id: String, tempFile: File? = null) {
        val dbItem = dao.getDownloadById(id) ?: return
        if (dbItem.status == DownloadStatus.COMPLETED) return
        val downloaded = if (tempFile != null && tempFile.exists()) tempFile.length() else dbItem.downloadedBytes
        val progress = if (dbItem.totalBytes > 0) {
            (downloaded.toFloat() / dbItem.totalBytes).coerceIn(0f, 1f)
        } else {
            dbItem.progress
        }
        dao.update(
            dbItem.copy(
                status = DownloadStatus.PAUSED,
                downloadedBytes = downloaded,
                progress = progress,
                speedFormatted = "",
                errorMessage = null
            )
        )
    }

    /**
     * Self-healing: resolves a fresh signed stream URL from API for movies, episodes, or shorts.
     */
    private suspend fun resolveFreshStreamUrl(item: DownloadItem): String? {
        val resolved = resolveEpisodeStreamUrl(
            movieId = item.movieId,
            title = item.title,
            seasonNumber = item.seasonNumber,
            episodeNumber = item.episodeNumber,
            quality = item.quality
        )
        if (!resolved.isNullOrBlank()) return resolved
        if (item.downloadUrl.isNotBlank()) return item.downloadUrl
        return null
    }

    private suspend fun resolveEpisodeStreamUrl(
        movieId: String,
        title: String,
        seasonNumber: Int,
        episodeNumber: Int,
        quality: String
    ): String? {
        try {
            // 1. Vskit shorts
            if (movieId.startsWith("vskit_")) {
                val cleanId = movieId.removePrefix("vskit_")
                val eps = VskitShortsApiClient.fetchShortsEpisodes(cleanId)
                val targetEp = eps.find { it.ep == episodeNumber } ?: eps.firstOrNull()
                if (targetEp != null) {
                    val targetDigits = quality.filter { it.isDigit() }
                    val matching = targetEp.streams.find { it.resolution.contains(targetDigits) }
                        ?: targetEp.streams.firstOrNull()
                    val res = matching?.url?.ifBlank { null } ?: targetEp.videoUrl.ifBlank { null }
                    if (!res.isNullOrBlank()) return res
                }
            }

            // 2. Story TV drama/short
            val isStoryTv = movieId.startsWith("storytv_") || movieId.startsWith("story_")
            if (isStoryTv) {
                val cleanId = movieId.removePrefix("storytv_").removePrefix("story_")
                val streamUrl = StoryTvApiClient.fetchEpisodeStream(cleanId, episodeNumber)
                if (!streamUrl.isNullOrBlank()) {
                    if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                        val qualities = StoryTvApiClient.fetchHlsStreamQualities(streamUrl)
                        val targetDigits = quality.filter { it.isDigit() }
                        val matched = qualities.find { extractHeightDigits(it.resolution) == targetDigits }
                            ?: qualities.firstOrNull()
                        return matched?.url?.ifBlank { null } ?: streamUrl
                    }
                    return streamUrl
                }
            }

            // 3. MovieBox movie, series episode, or short
            val isShortItem = (!isStoryTv && !movieId.startsWith("vskit_")) && (seasonNumber > 0 || title.contains("Short", ignoreCase = true))
            val result = MovieBoxApiClient.fetchPlayStreams(
                context = context,
                subjectId = movieId,
                detailPath = "",
                isShort = isShortItem,
                season = seasonNumber,
                episode = episodeNumber
            )
            val validStreams = result.streams.filter { it.url.isNotBlank() }
            if (validStreams.isNotEmpty()) {
                val targetDigits = quality.filter { it.isDigit() }
                val match = validStreams.firstOrNull { extractHeightDigits(it.resolution) == targetDigits }
                    ?: validStreams.firstOrNull { it.format.equals("MP4", ignoreCase = true) }
                    ?: validStreams.first()
                return match.url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving episode stream for $movieId ep $episodeNumber: ${e.message}")
        }
        return null
    }

    private fun extractHeightDigits(res: String): String {
        return res.split(",").firstOrNull()?.filter { it.isDigit() } ?: ""
    }

    /**
     * Downloads multiple episodes sequentially and merges them into a single local continuous MP4 video.
     */
    private fun launchMergeDownloadJob(
        initialItem: DownloadItem,
        sortedEps: List<Pair<Int, Int>>,
        tempFile: File,
        targetFile: File,
        firstStreamUrl: String
    ) {
        pausedItemIds.remove(initialItem.id)
        activeJobs[initialItem.id]?.cancel()

        val job = scope.launch {
            var currentItem = initialItem
            val totalEps = sortedEps.size

            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            var lastSpeedSampleTime = System.currentTimeMillis()
            var bytesAtLastSample = downloadedBytes
            var lastDbUpdateTime = System.currentTimeMillis()
            var currentSpeedStr = "0 KB/s"

            try {
                FileOutputStream(tempFile, downloadedBytes > 0).use { output ->
                    for ((index, epPair) in sortedEps.withIndex()) {
                        val (sNum, epNum) = epPair

                        if (pausedItemIds.contains(currentItem.id) || !isActive) {
                            output.flush()
                            ensurePausedState(currentItem.id, tempFile)
                            return@launch
                        }

                        val baseProgress = index.toFloat() / totalEps
                        dao.update(
                            currentItem.copy(
                                progress = baseProgress,
                                speedFormatted = "Ep $epNum of $totalEps ($currentSpeedStr)"
                            )
                        )

                        // 1. Resolve episode stream URL
                        val epUrl = if (index == 0 && firstStreamUrl.isNotBlank()) {
                            firstStreamUrl
                        } else {
                            resolveEpisodeStreamUrl(
                                movieId = currentItem.movieId,
                                title = currentItem.title,
                                seasonNumber = sNum,
                                episodeNumber = epNum,
                                quality = currentItem.quality
                            )
                        }

                        if (epUrl.isNullOrBlank()) {
                            Log.w(TAG, "All-in-One: stream URL not found for episode $epNum")
                            continue
                        }

                        // 2. Download segments (if HLS) or direct stream
                        if (isHlsUrl(epUrl)) {
                            val segmentUrls = extractHlsSegmentUrls(epUrl, currentItem.quality)
                            val totalSegs = segmentUrls.size
                            for ((segIdx, segUrl) in segmentUrls.withIndex()) {
                                if (pausedItemIds.contains(currentItem.id) || !isActive) {
                                    output.flush()
                                    ensurePausedState(currentItem.id, tempFile)
                                    return@launch
                                }

                                val segResp = executeWithHeaderFallback(segUrl, 0L)
                                if (segResp.isSuccessful && segResp.body != null) {
                                    val bytes = segResp.body!!.bytes()
                                    output.write(bytes)
                                    downloadedBytes += bytes.size
                                    segResp.close()
                                } else {
                                    segResp.close()
                                    delay(300)
                                    if (pausedItemIds.contains(currentItem.id) || !isActive) {
                                        output.flush()
                                        ensurePausedState(currentItem.id, tempFile)
                                        return@launch
                                    }
                                    val retryResp = executeWithHeaderFallback(segUrl, 0L)
                                    if (retryResp.isSuccessful && retryResp.body != null) {
                                        val bytes = retryResp.body!!.bytes()
                                        output.write(bytes)
                                        downloadedBytes += bytes.size
                                    }
                                    retryResp.close()
                                }

                                val now = System.currentTimeMillis()
                                if (now - lastSpeedSampleTime >= 800) {
                                    val bytesDelta = downloadedBytes - bytesAtLastSample
                                    val timeSec = (now - lastSpeedSampleTime) / 1000.0
                                    if (timeSec > 0) {
                                        val speed = (bytesDelta / timeSec).toLong()
                                        currentSpeedStr = formatSpeed(speed)
                                    }
                                    lastSpeedSampleTime = now
                                    bytesAtLastSample = downloadedBytes
                                }

                                if (now - lastDbUpdateTime >= 1000 || segIdx == totalSegs - 1) {
                                    val segProgressInEp = if (totalSegs > 0) (segIdx.toFloat() / totalSegs) else 0f
                                    val overallProgress = (index + segProgressInEp) / totalEps
                                    dao.update(
                                        currentItem.copy(
                                            downloadedBytes = downloadedBytes,
                                            progress = overallProgress.coerceIn(0f, 0.99f),
                                            speedFormatted = "Ep $epNum of $totalEps ($currentSpeedStr)"
                                        )
                                    )
                                    lastDbUpdateTime = now
                                }
                            }
                        } else {
                            // Direct stream (MP4)
                            val directResp = executeWithHeaderFallback(epUrl, 0L)
                            if (directResp.isSuccessful && directResp.body != null) {
                                val input = directResp.body!!.byteStream()
                                val buffer = ByteArray(64 * 1024)
                                var read = input.read(buffer)
                                while (read != -1) {
                                    if (pausedItemIds.contains(currentItem.id) || !isActive) {
                                        output.flush()
                                        input.close()
                                        directResp.close()
                                        ensurePausedState(currentItem.id, tempFile)
                                        return@launch
                                    }
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastSpeedSampleTime >= 800) {
                                        val bytesDelta = downloadedBytes - bytesAtLastSample
                                        val timeSec = (now - lastSpeedSampleTime) / 1000.0
                                        if (timeSec > 0) {
                                            val speed = (bytesDelta / timeSec).toLong()
                                            currentSpeedStr = formatSpeed(speed)
                                        }
                                        lastSpeedSampleTime = now
                                        bytesAtLastSample = downloadedBytes
                                    }

                                    if (now - lastDbUpdateTime >= 1000) {
                                        val overallProgress = (index + 0.5f) / totalEps
                                        dao.update(
                                            currentItem.copy(
                                                downloadedBytes = downloadedBytes,
                                                progress = overallProgress.coerceIn(0f, 0.99f),
                                                speedFormatted = "Ep $epNum of $totalEps ($currentSpeedStr)"
                                            )
                                        )
                                        lastDbUpdateTime = now
                                    }

                                    read = input.read(buffer)
                                }
                                input.close()
                                directResp.close()
                            } else {
                                directResp.close()
                            }
                        }
                    }
                    output.flush()
                }
            } catch (e: Throwable) {
                if (e is CancellationException || pausedItemIds.contains(currentItem.id) || !isActive) {
                    ensurePausedState(currentItem.id, tempFile)
                    return@launch
                }
                Log.e(TAG, "All-in-One download error: ${e.message}", e)
                dao.update(
                    currentItem.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Merge download error",
                        speedFormatted = ""
                    )
                )
                return@launch
            } finally {
                activeJobs.remove(initialItem.id)
            }

            if (pausedItemIds.contains(currentItem.id) || !isActive) {
                ensurePausedState(currentItem.id, tempFile)
                return@launch
            }

            // Finish download: rename tempFile to targetFile
            if (targetFile.exists()) targetFile.delete()
            val success = tempFile.renameTo(targetFile)
            val finalPath = if (success) targetFile.absolutePath else tempFile.absolutePath

            val completedItem = currentItem.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                progress = 1.0f,
                speedFormatted = "",
                localFilePath = finalPath,
                status = DownloadStatus.COMPLETED,
                errorMessage = null
            )
            dao.update(completedItem)

            // Export to phone Gallery
            exportVideoToGallery(File(finalPath), completedItem)
        }

        activeJobs[initialItem.id] = job
    }

    private suspend fun extractHlsSegmentUrls(hlsUrl: String, targetQuality: String): List<String> {
        val resp = executeWithHeaderFallback(hlsUrl, 0L)
        if (!resp.isSuccessful || resp.body == null) {
            resp.close()
            return emptyList()
        }
        val playlistText = resp.body!!.string()
        resp.close()

        var mediaPlaylistUrl = hlsUrl
        var mediaPlaylistContent = playlistText

        if (playlistText.contains("#EXT-X-STREAM-INF")) {
            val targetDigits = targetQuality.filter { it.isDigit() }
            val lines = playlistText.lines()
            var selectedUri: String? = null
            var highestBandwidthUri: String? = null
            var maxBandwidth = 0L

            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.value ?: ""

                    var nextLine = ""
                    var j = i + 1
                    while (j < lines.size) {
                        val candidate = lines[j].trim()
                        if (candidate.isNotBlank() && !candidate.startsWith("#")) {
                            nextLine = candidate
                            break
                        }
                        j++
                    }

                    if (nextLine.isNotBlank()) {
                        if (bw > maxBandwidth) {
                            maxBandwidth = bw
                            highestBandwidthUri = nextLine
                        }
                        if (targetDigits.isNotBlank() && (line.contains(targetDigits) || resMatch.contains(targetDigits))) {
                            selectedUri = nextLine
                            break
                        }
                    }
                }
                i++
            }

            val chosenVariant = selectedUri ?: highestBandwidthUri
            if (!chosenVariant.isNullOrBlank()) {
                mediaPlaylistUrl = resolveUrl(hlsUrl, chosenVariant)
                val variantResp = executeWithHeaderFallback(mediaPlaylistUrl, 0L)
                if (variantResp.isSuccessful && variantResp.body != null) {
                    mediaPlaylistContent = variantResp.body!!.string()
                    variantResp.close()
                } else {
                    variantResp.close()
                }
            }
        }

        val segmentUrls = mutableListOf<String>()
        mediaPlaylistContent.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotBlank() && !line.startsWith("#")) {
                segmentUrls.add(resolveUrl(mediaPlaylistUrl, line))
            }
        }
        return segmentUrls
    }

    private fun launchDownloadJob(
        initialItem: DownloadItem,
        tempFile: File,
        targetFile: File
    ) {
        pausedItemIds.remove(initialItem.id)
        activeJobs[initialItem.id]?.cancel()

        val job = scope.launch {
            var currentItem = initialItem
            var currentUrl = currentItem.downloadUrl.trim()

            // 1. Resolve stream URL if blank
            if (currentUrl.isBlank()) {
                val freshUrl = resolveFreshStreamUrl(currentItem)
                if (freshUrl.isNullOrBlank()) {
                    if (pausedItemIds.contains(currentItem.id) || !isActive) {
                        ensurePausedState(currentItem.id, tempFile)
                        return@launch
                    }
                    dao.update(
                        currentItem.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = "Stream URL unavailable",
                            speedFormatted = ""
                        )
                    )
                    return@launch
                }
                currentUrl = freshUrl
                currentItem = currentItem.copy(downloadUrl = currentUrl)
                dao.update(currentItem)
            }

            if (pausedItemIds.contains(currentItem.id) || !isActive) {
                ensurePausedState(currentItem.id, tempFile)
                return@launch
            }

            // 2. Check if URL is HLS (.m3u8)
            if (isHlsUrl(currentUrl)) {
                try {
                    downloadHlsStream(currentItem, currentUrl, tempFile, targetFile)
                } catch (e: Throwable) {
                    if (e is CancellationException || pausedItemIds.contains(currentItem.id) || !isActive) {
                        Log.d(TAG, "HLS download paused cleanly for ${currentItem.id}")
                        ensurePausedState(currentItem.id, tempFile)
                        return@launch
                    }
                    val currentDb = dao.getDownloadById(currentItem.id)
                    if (currentDb?.status == DownloadStatus.PAUSED || pausedItemIds.contains(currentItem.id)) {
                        ensurePausedState(currentItem.id, tempFile)
                        return@launch
                    }
                    Log.e(TAG, "HLS download error: ${e.message}", e)
                    // If error may be due to expired URL, try refreshing once
                    val freshUrl = resolveFreshStreamUrl(currentItem)
                    if (!freshUrl.isNullOrBlank() && freshUrl != currentUrl) {
                        try {
                            if (pausedItemIds.contains(currentItem.id) || !isActive) {
                                ensurePausedState(currentItem.id, tempFile)
                                return@launch
                            }
                            currentItem = currentItem.copy(downloadUrl = freshUrl)
                            dao.update(currentItem)
                            downloadHlsStream(currentItem, freshUrl, tempFile, targetFile)
                            return@launch
                        } catch (e2: Throwable) {
                            if (e2 is CancellationException || pausedItemIds.contains(currentItem.id) || !isActive) {
                                ensurePausedState(currentItem.id, tempFile)
                                return@launch
                            }
                            Log.e(TAG, "HLS retry error: ${e2.message}", e2)
                        }
                    }
                    val latest = dao.getDownloadById(currentItem.id)
                    if (latest != null && latest.status == DownloadStatus.DOWNLOADING && !pausedItemIds.contains(currentItem.id) && isActive) {
                        dao.update(
                            latest.copy(
                                status = DownloadStatus.FAILED,
                                errorMessage = e.localizedMessage ?: "HLS download error",
                                speedFormatted = ""
                            )
                        )
                    }
                } finally {
                    activeJobs.remove(initialItem.id)
                }
                return@launch
            }

            // 3. Direct download (MP4 / WebM / etc.)
            try {
                downloadDirectStream(currentItem, currentUrl, tempFile, targetFile)
            } catch (e: Throwable) {
                if (e is CancellationException || pausedItemIds.contains(currentItem.id) || !isActive) {
                    Log.d(TAG, "Direct download paused cleanly for ${currentItem.id}")
                    ensurePausedState(currentItem.id, tempFile)
                    return@launch
                }
                val currentDb = dao.getDownloadById(currentItem.id)
                if (currentDb?.status == DownloadStatus.PAUSED || pausedItemIds.contains(currentItem.id)) {
                    ensurePausedState(currentItem.id, tempFile)
                    return@launch
                }
                Log.e(TAG, "Direct download error: ${e.message}", e)
                // If failed, try resolving a fresh URL once (handles expired signatures)
                val freshUrl = resolveFreshStreamUrl(currentItem)
                if (!freshUrl.isNullOrBlank() && freshUrl != currentUrl) {
                    try {
                        if (pausedItemIds.contains(currentItem.id) || !isActive) {
                            ensurePausedState(currentItem.id, tempFile)
                            return@launch
                        }
                        currentItem = currentItem.copy(downloadUrl = freshUrl)
                        dao.update(currentItem)
                        downloadDirectStream(currentItem, freshUrl, tempFile, targetFile)
                        return@launch
                    } catch (e2: Throwable) {
                        if (e2 is CancellationException || pausedItemIds.contains(currentItem.id) || !isActive) {
                            ensurePausedState(currentItem.id, tempFile)
                            return@launch
                        }
                        Log.e(TAG, "Direct retry error: ${e2.message}", e2)
                    }
                }
                val latest = dao.getDownloadById(currentItem.id)
                if (latest != null && latest.status == DownloadStatus.DOWNLOADING && !pausedItemIds.contains(currentItem.id) && isActive) {
                    dao.update(
                        latest.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = e.localizedMessage ?: "Download error",
                            speedFormatted = ""
                        )
                    )
                }
            } finally {
                activeJobs.remove(initialItem.id)
            }
        }

        activeJobs[initialItem.id] = job
    }

    private fun isHlsUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("format=hls")
    }

    /**
     * Executes HTTP GET with multi-tiered headers:
     * Tier 1: movieboxph.org
     * Tier 2: vskit.online
     * Tier 3: No referer/origin
     */
    private fun executeWithHeaderFallback(
        url: String,
        existingBytes: Long = 0L
    ): Response {
        val isVskitOnly = url.contains("vskit.online") || url.contains("msacdn")
        val isMovieBox = url.contains("moviebox") || url.contains("box") || url.contains("freshext")
        val isStoryTvOrCdn = !isMovieBox && !isVskitOnly

        fun buildRequest(referer: String?, origin: String?, includeRange: Boolean): Request {
            val b = Request.Builder()
                .url(url)
                .addHeader("User-Agent", DOWNLOAD_USER_AGENT)
                .addHeader("Accept", "*/*")
            if (referer != null) b.addHeader("Referer", referer)
            if (origin != null) b.addHeader("Origin", origin)
            if (includeRange && existingBytes > 0) {
                b.addHeader("Range", "bytes=$existingBytes-")
            }
            return b.build()
        }

        // For Story TV or general CDN URLs, attempt direct request without referer/origin first
        if (isStoryTvOrCdn) {
            val respDirect = okHttpClient.newCall(buildRequest(null, null, existingBytes > 0)).execute()
            if (respDirect.isSuccessful) return respDirect
            if (existingBytes > 0 && (respDirect.code == 416 || respDirect.code == 400)) {
                respDirect.close()
                val respNoRange = okHttpClient.newCall(buildRequest(null, null, false)).execute()
                if (respNoRange.isSuccessful) return respNoRange
                respNoRange.close()
            } else {
                respDirect.close()
            }

            // Also try with ktor-client User-Agent if Story TV server
            val ktorReq = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ktor-client")
                .addHeader("Accept", "*/*")
                .apply {
                    if (existingBytes > 0) addHeader("Range", "bytes=$existingBytes-")
                }
                .build()
            val ktorResp = okHttpClient.newCall(ktorReq).execute()
            if (ktorResp.isSuccessful) return ktorResp
            ktorResp.close()
        }

        // Decide Tier 1 referer for MovieBox or Vskit
        val primaryReferer = if (isVskitOnly) REFERER_VSKIT else REFERER_MOVIEBOX
        val primaryOrigin = if (isVskitOnly) ORIGIN_VSKIT else ORIGIN_MOVIEBOX

        // 1. Try Primary
        val resp1 = okHttpClient.newCall(buildRequest(primaryReferer, primaryOrigin, true)).execute()
        if (resp1.isSuccessful) return resp1

        // If Range was rejected (416/400)
        if (existingBytes > 0 && (resp1.code == 416 || resp1.code == 400)) {
            resp1.close()
            val respRangeReset = okHttpClient.newCall(buildRequest(primaryReferer, primaryOrigin, false)).execute()
            if (respRangeReset.isSuccessful) return respRangeReset
            respRangeReset.close()
        } else {
            resp1.close()
        }

        // 2. Try Secondary (Opposite domain)
        val secondaryReferer = if (primaryReferer == REFERER_MOVIEBOX) REFERER_VSKIT else REFERER_MOVIEBOX
        val secondaryOrigin = if (primaryOrigin == ORIGIN_MOVIEBOX) ORIGIN_VSKIT else ORIGIN_MOVIEBOX
        val resp2 = okHttpClient.newCall(buildRequest(secondaryReferer, secondaryOrigin, existingBytes > 0)).execute()
        if (resp2.isSuccessful) return resp2
        resp2.close()

        // 3. Try with NO referer/origin (direct CDN pull)
        val resp3 = okHttpClient.newCall(buildRequest(null, null, existingBytes > 0)).execute()
        if (resp3.isSuccessful) return resp3

        // 4. Try with NO referer/origin and NO range
        if (existingBytes > 0) {
            resp3.close()
            return okHttpClient.newCall(buildRequest(null, null, false)).execute()
        }

        return resp3
    }

    private suspend fun downloadDirectStream(
        item: DownloadItem,
        url: String,
        tempFile: File,
        targetFile: File
    ) {
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val response = executeWithHeaderFallback(url, existingBytes)
        if (!response.isSuccessful || response.body == null) {
            val code = response.code
            response.close()
            if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                ensurePausedState(item.id, tempFile)
                return
            }
            val latest = dao.getDownloadById(item.id)
            if (latest != null && latest.status != DownloadStatus.PAUSED && !pausedItemIds.contains(item.id)) {
                dao.update(
                    item.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "Server returned HTTP $code",
                        speedFormatted = ""
                    )
                )
            }
            return
        }

        val contentType = response.header("Content-Type")?.lowercase() ?: ""
        if (contentType.contains("mpegurl") || contentType.contains("application/x-mpegurl")) {
            val bodyString = response.body?.string() ?: ""
            response.close()
            downloadHlsStream(item, url, tempFile, targetFile, initialContent = bodyString)
            return
        }

        val isPartial = (response.code == 206)
        val resumeOffset = if (isPartial) existingBytes else 0L
        if (!isPartial && existingBytes > 0) {
            tempFile.delete()
        }

        val body = response.body!!
        val totalLength = if (isPartial) {
            resumeOffset + body.contentLength()
        } else {
            body.contentLength().takeIf { it > 0 } ?: (item.totalBytes.takeIf { it > 0 } ?: 0L)
        }

        processBodyStream(item, body, tempFile, targetFile, resumeOffset, totalLength)
    }

    private suspend fun processBodyStream(
        item: DownloadItem,
        body: okhttp3.ResponseBody,
        tempFile: File,
        targetFile: File,
        initialDownloadedBytes: Long,
        totalBytesEstimated: Long
    ) {
        val append = (initialDownloadedBytes > 0)
        var downloaded = initialDownloadedBytes
        var total = totalBytesEstimated

        var lastSpeedSampleTime = System.currentTimeMillis()
        var bytesAtLastSample = downloaded
        var lastDbUpdateTime = System.currentTimeMillis()
        var currentSpeedStr = "0 KB/s"

        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                            output.flush()
                            ensurePausedState(item.id, tempFile)
                            return
                        }

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (total <= 0 && body.contentLength() > 0) {
                            total = body.contentLength()
                        }

                        val now = System.currentTimeMillis()
                        // Update speed every 700ms
                        if (now - lastSpeedSampleTime >= 700) {
                            val bytesDelta = downloaded - bytesAtLastSample
                            val timeSeconds = (now - lastSpeedSampleTime) / 1000.0
                            if (timeSeconds > 0) {
                                val speedBytesPerSec = (bytesDelta / timeSeconds).toLong()
                                currentSpeedStr = formatSpeed(speedBytesPerSec)
                            }
                            lastSpeedSampleTime = now
                            bytesAtLastSample = downloaded
                        }

                        // Update DB every 900ms
                        if (now - lastDbUpdateTime >= 900) {
                            if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                                output.flush()
                                ensurePausedState(item.id, tempFile)
                                return
                            }
                            val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                            dao.update(
                                item.copy(
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    progress = progress,
                                    speedFormatted = currentSpeedStr,
                                    status = DownloadStatus.DOWNLOADING,
                                    errorMessage = null
                                )
                            )
                            lastDbUpdateTime = now
                        }
                    }
                    output.flush()
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException || pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                ensurePausedState(item.id, tempFile)
                return
            }
            throw e
        }

        if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
            ensurePausedState(item.id, tempFile)
            return
        }

        // Rename temp to target
        if (targetFile.exists()) targetFile.delete()
        val success = tempFile.renameTo(targetFile)
        val finalPath = if (success) targetFile.absolutePath else tempFile.absolutePath

        val completedItem = item.copy(
            downloadedBytes = downloaded,
            totalBytes = if (total > 0) total else downloaded,
            progress = 1.0f,
            speedFormatted = "",
            localFilePath = finalPath,
            status = DownloadStatus.COMPLETED,
            errorMessage = null
        )
        dao.update(completedItem)

        // Export to phone Gallery so it shows up in user's Gallery app immediately!
        exportVideoToGallery(File(finalPath), completedItem)
    }

    /**
     * Complete HLS download handler:
     * - Fetches playlist
     * - Resolves master/variant playlist
     * - Extracts all segment URIs
     * - Downloads and appends media segments into tempFile
     * - Marks as COMPLETED
     */
    private suspend fun downloadHlsStream(
        item: DownloadItem,
        hlsUrl: String,
        tempFile: File,
        targetFile: File,
        initialContent: String? = null
    ) {
        val playlistText = if (!initialContent.isNullOrBlank()) {
            initialContent
        } else {
            val resp = executeWithHeaderFallback(hlsUrl, 0L)
            if (!resp.isSuccessful || resp.body == null) {
                val code = resp.code
                resp.close()
                throw IllegalStateException("Failed to load HLS playlist: HTTP $code")
            }
            val text = resp.body!!.string()
            resp.close()
            text
        }

        var mediaPlaylistUrl = hlsUrl
        var mediaPlaylistContent = playlistText

        // Check if Master Playlist
        if (playlistText.contains("#EXT-X-STREAM-INF")) {
            val targetDigits = item.quality.filter { it.isDigit() }
            val lines = playlistText.lines()
            var selectedUri: String? = null
            var highestBandwidthUri: String? = null
            var maxBandwidth = 0L

            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    val bw = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)?.value ?: ""

                    var nextLine = ""
                    var j = i + 1
                    while (j < lines.size) {
                        val candidate = lines[j].trim()
                        if (candidate.isNotBlank() && !candidate.startsWith("#")) {
                            nextLine = candidate
                            break
                        }
                        j++
                    }

                    if (nextLine.isNotBlank()) {
                        if (bw > maxBandwidth) {
                            maxBandwidth = bw
                            highestBandwidthUri = nextLine
                        }
                        if (targetDigits.isNotBlank() && (line.contains(targetDigits) || resMatch.contains(targetDigits))) {
                            selectedUri = nextLine
                            break
                        }
                    }
                }
                i++
            }

            val chosenVariant = selectedUri ?: highestBandwidthUri
            if (!chosenVariant.isNullOrBlank()) {
                mediaPlaylistUrl = resolveUrl(hlsUrl, chosenVariant)
                val resp = executeWithHeaderFallback(mediaPlaylistUrl, 0L)
                if (resp.isSuccessful && resp.body != null) {
                    mediaPlaylistContent = resp.body!!.string()
                    resp.close()
                } else {
                    resp.close()
                }
            }
        }

        // Parse media segments
        val segmentUrls = mutableListOf<String>()
        mediaPlaylistContent.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotBlank() && !line.startsWith("#")) {
                segmentUrls.add(resolveUrl(mediaPlaylistUrl, line))
            }
        }

        if (segmentUrls.isEmpty()) {
            if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                ensurePausedState(item.id, tempFile)
                return
            }
            throw IllegalStateException("No media segments found in HLS playlist")
        }

        val totalSegments = segmentUrls.size
        // Start downloading segments
        var downloadedBytes = 0L
        if (tempFile.exists()) {
            downloadedBytes = tempFile.length()
        }

        var lastSpeedSampleTime = System.currentTimeMillis()
        var bytesAtLastSample = downloadedBytes
        var lastDbUpdateTime = System.currentTimeMillis()
        var currentSpeedStr = "0 KB/s"

        try {
            FileOutputStream(tempFile, downloadedBytes > 0).use { output ->
                for ((index, segUrl) in segmentUrls.withIndex()) {
                    if (pausedItemIds.contains(item.id) || !scope.isActive || !currentCoroutineContext().isActive) {
                        output.flush()
                        ensurePausedState(item.id, tempFile)
                        return
                    }

                    val segResp = executeWithHeaderFallback(segUrl, 0L)
                    if (segResp.isSuccessful && segResp.body != null) {
                        val segBytes = segResp.body!!.bytes()
                        output.write(segBytes)
                        downloadedBytes += segBytes.size
                        segResp.close()
                    } else {
                        segResp.close()
                        if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                            output.flush()
                            ensurePausedState(item.id, tempFile)
                            return
                        }
                        // Attempt one retry for this segment
                        delay(300)
                        if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                            output.flush()
                            ensurePausedState(item.id, tempFile)
                            return
                        }
                        val retryResp = executeWithHeaderFallback(segUrl, 0L)
                        if (retryResp.isSuccessful && retryResp.body != null) {
                            val segBytes = retryResp.body!!.bytes()
                            output.write(segBytes)
                            downloadedBytes += segBytes.size
                        }
                        retryResp.close()
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastSpeedSampleTime >= 800) {
                        val bytesDelta = downloadedBytes - bytesAtLastSample
                        val timeSeconds = (now - lastSpeedSampleTime) / 1000.0
                        if (timeSeconds > 0) {
                            val speedBytesPerSec = (bytesDelta / timeSeconds).toLong()
                            currentSpeedStr = formatSpeed(speedBytesPerSec)
                        }
                        lastSpeedSampleTime = now
                        bytesAtLastSample = downloadedBytes
                    }

                    if (now - lastDbUpdateTime >= 1000 || index == totalSegments - 1) {
                        if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                            output.flush()
                            ensurePausedState(item.id, tempFile)
                            return
                        }
                        val progress = ((index + 1).toFloat() / totalSegments).coerceIn(0f, 1f)
                        val estimatedTotal = if (progress > 0) (downloadedBytes / progress).toLong() else 0L
                        dao.update(
                            item.copy(
                                downloadedBytes = downloadedBytes,
                                totalBytes = estimatedTotal,
                                progress = progress,
                                speedFormatted = currentSpeedStr,
                                status = DownloadStatus.DOWNLOADING,
                                errorMessage = null
                            )
                        )
                        lastDbUpdateTime = now
                    }
                }
                output.flush()
            }
        } catch (e: Throwable) {
            if (e is CancellationException || pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
                ensurePausedState(item.id, tempFile)
                return
            }
            throw e
        }

        if (pausedItemIds.contains(item.id) || !currentCoroutineContext().isActive) {
            ensurePausedState(item.id, tempFile)
            return
        }

        // Rename temp to target
        if (targetFile.exists()) targetFile.delete()
        val success = tempFile.renameTo(targetFile)
        val finalPath = if (success) targetFile.absolutePath else tempFile.absolutePath

        val completedItem = item.copy(
            downloadedBytes = downloadedBytes,
            totalBytes = downloadedBytes,
            progress = 1.0f,
            speedFormatted = "",
            localFilePath = finalPath,
            status = DownloadStatus.COMPLETED,
            errorMessage = null
        )
        dao.update(completedItem)

        // Export to phone Gallery so it shows up in user's Gallery app immediately!
        exportVideoToGallery(File(finalPath), completedItem)
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        val trimmed = relativeOrAbsolute.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            val lastSlash = baseUrl.lastIndexOf('/')
            if (lastSlash != -1) {
                baseUrl.substring(0, lastSlash + 1) + trimmed
            } else {
                trimmed
            }
        }
    }

    /**
     * Exports a downloaded video into the Android MediaStore and public Movies directory,
     * ensuring it shows up directly in the user's phone Gallery and Photos app.
     */
    private fun exportVideoToGallery(videoFile: File, item: DownloadItem) {
        if (!videoFile.exists() || videoFile.length() <= 0L) return

        scope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val cleanTitle = item.title.ifBlank { videoFile.nameWithoutExtension }
                val displayName = if (videoFile.name.endsWith(".mp4", ignoreCase = true)) {
                    videoFile.name
                } else {
                    "${videoFile.nameWithoutExtension}.mp4"
                }

                // 1. Insert into MediaStore (Android 10+ and standard MediaStore)
                val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                var alreadyInMediaStore = false
                try {
                    val projection = arrayOf(MediaStore.Video.Media._ID)
                    val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                    val args = arrayOf(displayName)
                    resolver.query(videoCollection, projection, selection, args, null)?.use { cursor ->
                        if (cursor.count > 0) {
                            alreadyInMediaStore = true
                        }
                    }
                } catch (_: Exception) {}

                if (!alreadyInMediaStore) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.TITLE, cleanTitle)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MovieBox")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        } else {
                            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MovieBox")
                            if (!publicDir.exists()) publicDir.mkdirs()
                            val publicFile = File(publicDir, displayName)
                            put(MediaStore.Video.Media.DATA, publicFile.absolutePath)
                        }
                    }

                    val insertedUri = resolver.insert(videoCollection, contentValues)
                    if (insertedUri != null) {
                        try {
                            resolver.openOutputStream(insertedUri)?.use { out ->
                                videoFile.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                                out.flush()
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                                resolver.update(insertedUri, contentValues, null, null)
                            }
                            Log.d(TAG, "Video successfully saved to MediaStore gallery: $insertedUri")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed writing video stream to MediaStore URI: ${e.message}")
                        }
                    }
                }

                // 2. Also copy to public Movies/MovieBox folder if direct filesystem write is permitted
                try {
                    val publicMoviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MovieBox")
                    if (!publicMoviesDir.exists()) {
                        publicMoviesDir.mkdirs()
                    }
                    val publicFile = File(publicMoviesDir, displayName)
                    if (!publicFile.exists() || publicFile.length() != videoFile.length()) {
                        videoFile.copyTo(publicFile, overwrite = true)
                    }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(publicFile.absolutePath, videoFile.absolutePath),
                        arrayOf("video/mp4", "video/mp4")
                    ) { path, uri ->
                        Log.d(TAG, "MediaScanner scanned $path -> $uri")
                    }
                } catch (_: Exception) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(videoFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                }

                // 3. Broadcast Media Scanner intent
                try {
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        data = Uri.fromFile(videoFile)
                    }
                    context.sendBroadcast(mediaScanIntent)
                } catch (_: Exception) {}

            } catch (e: Exception) {
                Log.e(TAG, "exportVideoToGallery error: ${e.message}", e)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MovieDownloadManager? = null

        fun getInstance(context: Context): MovieDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MovieDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
                bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%d KB/s", bytesPerSec / 1024)
                bytesPerSec > 0 -> "$bytesPerSec B/s"
                else -> "0 KB/s"
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
                bytes > 0 -> "$bytes B"
                else -> "0 MB"
            }
        }
    }
}

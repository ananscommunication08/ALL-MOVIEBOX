package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SaavnAlbumItem
import com.example.data.model.SaavnHomeData
import com.example.data.model.SaavnPlaylistItem
import com.example.data.model.SaavnSongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnDecoder {
    private const val DES_KEY = "38346591"

    fun decryptMediaUrl(encryptedUrl: String, bitrate: String = "320"): String? {
        if (encryptedUrl.isBlank()) return null
        return try {
            val key = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)

            val decodedBytes = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            var decryptedUrl = String(decryptedBytes, Charsets.UTF_8).trim()

            // Bitrate replacement (_96.mp4 / _160.mp4 / _320.mp4)
            decryptedUrl = decryptedUrl.replace(Regex("_\\d+\\.mp4"), "_$bitrate.mp4")
            decryptedUrl = decryptedUrl.replace(Regex("_\\d+\\.m4a"), "_$bitrate.m4a")

            // Ensure HTTPS
            if (decryptedUrl.startsWith("http://")) {
                decryptedUrl = decryptedUrl.replaceFirst("http://", "https://")
            }
            decryptedUrl
        } catch (e: Exception) {
            Log.e("JioSaavnDecoder", "Decryption failed for URL: $encryptedUrl", e)
            null
        }
    }
}

object JioSaavnApiClient {
    private const val TAG = "JioSaavnApiClient"

    // Multi-server mirrors for high resilience:
    // If one server or API domain is rate limited, expired, or down,
    // the client automatically falls back to secondary and tertiary mirrors!
    private val API_BASE_URLS = listOf(
        "https://www.jiosaavn.com/api.php",
        "https://saavn.com/api.php",
        "https://www.saavn.com/api.php"
    )

    private val API_CONTEXTS = listOf(
        "ctx=android&api_version=4",
        "ctx=web6dot0&api_version=4",
        "ctx=wap6dot0&api_version=4"
    )

    @Volatile
    private var currentServerIndex = 0

    @Volatile
    private var currentContextIndex = 0

    @Volatile
    private var cachedHomeData: SaavnHomeData? = null

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Sanitizes and cleans text so raw JSON or bracketed data like "{}" never appears in the UI.
     */
    fun cleanText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var str = input.trim()

        // Clean out literal raw empty JSON brackets or object indicators
        if (str == "{}" || str == "[]" || str == "null" || str == "{ }") {
            return ""
        }

        // If string contains or begins with JSON structure, parse out readable contents
        if (str.startsWith("{") || str.startsWith("[") || str.contains("{\"") || str.contains("\":")) {
            str = extractReadableTextFromJson(str)
        }

        // Remove any residual braces or raw JSON artifacts
        str = str.replace(Regex("""\{.*?\}"""), "").trim()
        str = str.replace("{", "").replace("}", "").trim()

        // HTML entity decoding and tag stripping
        return str
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<.*?>"), "") // Strip HTML tags
            .replace(Regex("\\s+"), " ") // Normalize multiple spaces
            .trim()
    }

    /**
     * Extracts human-readable text from raw JSON string (e.g. artist maps, subtitles).
     */
    private fun extractReadableTextFromJson(jsonStr: String): String {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                // 1. Check primary_artists list
                val primaryArtists = obj.optJSONArray("primary_artists")
                if (primaryArtists != null && primaryArtists.length() > 0) {
                    val names = mutableListOf<String>()
                    for (i in 0 until primaryArtists.length()) {
                        val artistObj = primaryArtists.optJSONObject(i)
                        val name = artistObj?.optString("name", "") ?: ""
                        if (name.isNotBlank()) names.add(name)
                    }
                    if (names.isNotEmpty()) return names.joinToString(", ")
                }
                // 2. Check artists list
                val artists = obj.optJSONArray("artists")
                if (artists != null && artists.length() > 0) {
                    val names = mutableListOf<String>()
                    for (i in 0 until artists.length()) {
                        val artistObj = artists.optJSONObject(i)
                        val name = artistObj?.optString("name", "") ?: ""
                        if (name.isNotBlank()) names.add(name)
                    }
                    if (names.isNotEmpty()) return names.joinToString(", ")
                }
                // 3. Check simple name or title or firstname
                val name = obj.optString("name", obj.optString("title", obj.optString("firstname", "")))
                if (name.isNotBlank() && !name.startsWith("{")) return name
                ""
            } else if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                val names = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    if (item is JSONObject) {
                        val n = item.optString("name", item.optString("title", ""))
                        if (n.isNotBlank()) names.add(n)
                    } else if (item is String && item.isNotBlank() && !item.startsWith("{")) {
                        names.add(item)
                    }
                }
                names.joinToString(", ")
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun getHighQualityImage(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url
            .replace("50x50.jpg", "500x500.jpg")
            .replace("150x150.jpg", "500x500.jpg")
            .replace("50x50.png", "500x500.png")
            .replace("150x150.png", "500x500.png")
            .replace("http://", "https://")
    }

    private fun getActiveBaseUrl(): String {
        return API_BASE_URLS[currentServerIndex % API_BASE_URLS.size]
    }

    private fun getActiveContext(): String {
        return API_CONTEXTS[currentContextIndex % API_CONTEXTS.size]
    }

    /**
     * Automatically rotate to the next backup server mirror if the current one expires or fails.
     */
    private fun rotateToBackupServer() {
        val nextServer = (currentServerIndex + 1) % API_BASE_URLS.size
        val nextContext = (currentContextIndex + 1) % API_CONTEXTS.size
        currentServerIndex = nextServer
        currentContextIndex = nextContext
        Log.w(TAG, "Rotated to backup JioSaavn server: ${getActiveBaseUrl()} context: ${getActiveContext()}")
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
            .build()
    }

    /**
     * Safely executes an HTTP call with automatic fallback and failover between mirror servers.
     */
    private fun executeWithFailover(callQuery: String): String? {
        for (attempt in 0 until (API_BASE_URLS.size * 2)) {
            val base = getActiveBaseUrl()
            val ctx = getActiveContext()
            val sep = if (callQuery.contains("?")) "&" else "?"
            val fullUrl = if (callQuery.startsWith("http")) callQuery else "$base$sep$callQuery&$ctx&_format=json&_marker=0"

            try {
                val response = httpClient.newCall(buildRequest(fullUrl)).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank() && !body.contains("\"error\":\"API_EXPIRED\"")) {
                    return body
                } else {
                    Log.w(TAG, "Server $base returned code ${response.code}, rotating...")
                    rotateToBackupServer()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network failure calling $base: ${e.message}, rotating to next mirror...")
                rotateToBackupServer()
            }
        }
        return null
    }

    private fun parseSongObject(obj: JSONObject): SaavnSongItem {
        val id = obj.optString("id", obj.optString("songid", ""))
        val title = cleanText(obj.optString("title", obj.optString("song", "")))
        val rawSubtitle = obj.optString("subtitle", "")
        val subtitle = cleanText(rawSubtitle)
        val moreInfo = obj.optJSONObject("more_info")

        // Safely extract artist names without raw JSON objects
        var artistName = ""
        val artistMapObj = moreInfo?.optJSONObject("artistMap")
        if (artistMapObj != null) {
            val primaryArray = artistMapObj.optJSONArray("primary_artists")
            if (primaryArray != null && primaryArray.length() > 0) {
                val names = mutableListOf<String>()
                for (k in 0 until primaryArray.length()) {
                    val aObj = primaryArray.optJSONObject(k)
                    val aName = aObj?.optString("name", "") ?: ""
                    if (aName.isNotBlank()) names.add(aName)
                }
                if (names.isNotEmpty()) artistName = names.joinToString(", ")
            }
        }

        if (artistName.isBlank()) {
            artistName = cleanText(
                moreInfo?.optString("singers") ?:
                moreInfo?.optString("music") ?:
                obj.optString("primary_artists", obj.optString("singers", subtitle))
            )
        }

        val album = cleanText(
            moreInfo?.optString("album") ?:
            obj.optString("album", "")
        )
        val albumId = moreInfo?.optString("album_id") ?: obj.optString("albumid", "")
        val durationStr = moreInfo?.optString("duration") ?: obj.optString("duration", "0")
        val durationSec = durationStr.toIntOrNull() ?: 0

        val rawImage = obj.optString("image", moreInfo?.optString("image", ""))
        val image = getHighQualityImage(rawImage)

        val encryptedMediaUrl = moreInfo?.optString("encrypted_media_url") ?:
            obj.optString("encrypted_media_url", "")

        val hasLyrics = (moreInfo?.optString("has_lyrics") ?: obj.optString("has_lyrics", "false")) == "true"
        val lyricsId = moreInfo?.optString("lyrics_id") ?: obj.optString("lyrics_id", "")
        val copyright = cleanText(moreInfo?.optString("copyright_text") ?: obj.optString("copyright_text", ""))
        val releaseDate = moreInfo?.optString("release_date") ?: obj.optString("release_date", "")
        val year = obj.optString("year", "")
        val language = cleanText(obj.optString("language", "hindi"))
        val playCount = (moreInfo?.optString("play_count") ?: "0").toLongOrNull() ?: 0L

        return SaavnSongItem(
            id = id,
            title = title,
            subtitle = subtitle,
            artist = artistName,
            album = album,
            albumId = albumId,
            durationSec = durationSec,
            image = image,
            encryptedMediaUrl = encryptedMediaUrl,
            hasLyrics = hasLyrics,
            lyricsId = lyricsId,
            copyright = copyright,
            releaseDate = releaseDate,
            year = year,
            language = language,
            playCount = playCount
        )
    }

    private fun parseAlbumObject(obj: JSONObject): SaavnAlbumItem {
        val id = obj.optString("id", obj.optString("albumid", ""))
        val title = cleanText(obj.optString("title", obj.optString("name", "")))
        val subtitle = cleanText(obj.optString("subtitle", ""))
        val rawImage = obj.optString("image", "")
        val image = getHighQualityImage(rawImage)
        val artist = cleanText(obj.optString("artist", obj.optString("primary_artists", subtitle)))
        val year = obj.optString("year", "")
        val language = cleanText(obj.optString("language", ""))
        val songCount = obj.optInt("song_count", 0)

        return SaavnAlbumItem(
            id = id,
            title = title,
            subtitle = subtitle,
            image = image,
            artist = artist,
            year = year,
            language = language,
            songCount = songCount
        )
    }

    private fun parsePlaylistObject(obj: JSONObject): SaavnPlaylistItem {
        val id = obj.optString("id", obj.optString("listid", ""))
        val title = cleanText(obj.optString("title", obj.optString("listname", "")))
        val subtitle = cleanText(obj.optString("subtitle", ""))
        val rawImage = obj.optString("image", "")
        val image = getHighQualityImage(rawImage)
        val songCount = obj.optInt("song_count", obj.optInt("count", 0))
        val followerCount = obj.optLong("follower_count", 0L)

        return SaavnPlaylistItem(
            id = id,
            title = title,
            subtitle = subtitle,
            image = image,
            songCount = songCount,
            followerCount = followerCount
        )
    }

    /**
     * Parse MediaItem from JioSaavn JSON object (Mixed content: Song, Album, Playlist, Artist)
     */
     fun parseMediaItem(obj: JSONObject): MediaItem {
         val rawType = obj.optString("type", "song").lowercase()
         val type = when {
             rawType == "album" -> MediaType.ALBUM
             rawType == "playlist" -> MediaType.PLAYLIST
             rawType == "artist" -> MediaType.ARTIST
             else -> MediaType.SONG
         }

         val id = obj.optString("id").ifBlank { obj.optString("perma_url") }
         val title = cleanText(obj.optString("title").ifBlank { obj.optString("name") })
         val moreInfo = obj.optJSONObject("more_info")

         val subtitle = cleanText(
             obj.optString("subtitle").ifBlank {
                 moreInfo?.optString("subtitle")?.ifBlank {
                     moreInfo?.optString("music")?.ifBlank {
                         moreInfo?.optString("singers")?.ifBlank {
                             moreInfo?.optString("artistMap")?.ifBlank {
                                 moreInfo?.optString("firstname")?.ifBlank {
                                     obj.optString("header_desc")
                                 } ?: ""
                             } ?: ""
                         } ?: ""
                     } ?: ""
                 } ?: obj.optString("header_desc")
             }
         )

         var image = obj.optString("image")
         if (image.isBlank() && moreInfo != null) {
             image = moreInfo.optString("image")
         }

         val language = cleanText(obj.optString("language", "hindi"))
         val followerCount = (moreInfo?.optString("follower_count") ?: "0").toLongOrNull() ?: 0L
         val songCount = (moreInfo?.optString("song_count") ?: "0").toIntOrNull() ?: 0
         val encryptedMediaUrl = moreInfo?.optString("encrypted_media_url")
             ?: obj.optString("encrypted_media_url", "")

         return MediaItem(
             id = id,
             title = title,
             subtitle = subtitle,
             imageUrl = image,
             type = type,
             language = language,
             followerCount = followerCount,
             songCount = songCount,
             encryptedMediaUrl = encryptedMediaUrl
         )
     }

    /**
     * Fetch JioSaavn Home / Editorial Feed with automatic mirror rotation and fallback caching.
     */
    suspend fun fetchHomeFeed(): SaavnHomeData = withContext(Dispatchers.IO) {
        try {
            val editorialDeferred = async {
                fetchJsonObject("__call=webapi.get&type=editorial&p=1&n=30")
            }
            val trendingDeferred = async {
                fetchJsonArray("__call=content.getTrending")
            }
            val chartsDeferred = async {
                fetchJsonArray("__call=content.getCharts")
            }
            val chartsObjDeferred = async {
                fetchJsonObject("__call=content.getCharts")
            }
            val extraTopChartsDeferred = async {
                searchPlaylists("Top 50", page = 1, limit = 25)
            }
            val extraTrendingChartsDeferred = async {
                searchPlaylists("Trending", page = 1, limit = 20)
            }
            val extraChartsDeferred = async {
                searchPlaylists("Charts", page = 1, limit = 20)
            }
            val playlistsDeferred = async {
                fetchJsonArray("__call=content.getFeaturedPlaylists&p=1&n=30")
            }
            val albumsDeferred = async {
                fetchJsonArray("__call=content.getAlbums&p=1&n=30")
            }

            val editorialObj = editorialDeferred.await()
            val trendingArray = trendingDeferred.await()
            val chartsArray = chartsDeferred.await()
            val chartsObj = chartsObjDeferred.await()
            val extraTopCharts = extraTopChartsDeferred.await()
            val extraTrendingCharts = extraTrendingChartsDeferred.await()
            val extraCharts = extraChartsDeferred.await()
            val playlistsArray = playlistsDeferred.await()
            val albumsArray = albumsDeferred.await()

            val trendingItems = mutableListOf<MediaItem>()
            val chartToppers = mutableListOf<SaavnSongItem>()
            val topCharts = mutableListOf<MediaItem>()
            val featuredPlaylists = mutableListOf<MediaItem>()
            val newReleases = mutableListOf<MediaItem>()

            fun addMediaItems(arr: JSONArray?, targetList: MutableList<MediaItem>, defaultType: MediaType? = null) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val rawItem = parseMediaItem(obj)
                    val item = if (defaultType != null && rawItem.type == MediaType.SONG) rawItem.copy(type = defaultType) else rawItem
                    if (item.title.isNotBlank() && targetList.none { it.id == item.id }) {
                        targetList.add(item)
                    }
                    if (chartToppers.size < 25 && (item.type == MediaType.SONG || obj.has("more_info"))) {
                        val songItem = parseSongObject(obj)
                        if (songItem.title.isNotBlank() && chartToppers.none { it.id == songItem.id }) {
                            chartToppers.add(songItem)
                        }
                    }
                }
            }

            // 1. Process Editorial Feed first (Guaranteed comprehensive and rich starting cards)
            if (editorialObj != null) {
                addMediaItems(extractArrayFromField(editorialObj, "new_trending"), trendingItems)
                addMediaItems(extractArrayFromField(editorialObj, "new_albums"), newReleases, defaultType = MediaType.ALBUM)
                addMediaItems(extractArrayFromField(editorialObj, "top_playlists"), featuredPlaylists, defaultType = MediaType.PLAYLIST)
                addMediaItems(extractArrayFromField(editorialObj, "charts"), topCharts, defaultType = MediaType.PLAYLIST)
                addMediaItems(extractArrayFromField(editorialObj, "top_charts"), topCharts, defaultType = MediaType.PLAYLIST)
            }

            // 2. Supplement with specific content endpoints without duplicates
            addMediaItems(trendingArray, trendingItems)
            addMediaItems(albumsArray, newReleases, defaultType = MediaType.ALBUM)
            addMediaItems(playlistsArray, featuredPlaylists, defaultType = MediaType.PLAYLIST)
            addMediaItems(chartsArray, topCharts, defaultType = MediaType.PLAYLIST)
            if (chartsObj != null) {
                addMediaItems(extractArrayFromField(chartsObj, "charts"), topCharts, defaultType = MediaType.PLAYLIST)
                addMediaItems(extractArrayFromField(chartsObj, "data"), topCharts, defaultType = MediaType.PLAYLIST)
            }

            // 3. Ensure Top Charts row has ALL cards by merging top chart playlists
            for (p in extraTopCharts + extraTrendingCharts + extraCharts) {
                if (topCharts.none { it.id == p.id || it.title.equals(p.title, ignoreCase = true) }) {
                    topCharts.add(
                        MediaItem(
                            id = p.id,
                            title = p.title,
                            subtitle = p.subtitle.ifBlank { "Top Chart" },
                            imageUrl = p.image,
                            type = MediaType.PLAYLIST,
                            songCount = p.songCount,
                            followerCount = p.followerCount
                        )
                    )
                }
            }

            val result = SaavnHomeData(
                trendingItems = trendingItems,
                topCharts = topCharts,
                featuredPlaylists = featuredPlaylists,
                newReleases = newReleases,
                chartToppers = chartToppers
            )

            if (trendingItems.isNotEmpty() || newReleases.isNotEmpty() || featuredPlaylists.isNotEmpty()) {
                cachedHomeData = result
            }

            result.ifEmptyFallback(cachedHomeData)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching home feed, falling back to cache", e)
            cachedHomeData ?: SaavnHomeData()
        }
    }

    private fun SaavnHomeData.ifEmptyFallback(fallback: SaavnHomeData?): SaavnHomeData {
        if (trendingItems.isEmpty() && newReleases.isEmpty() && featuredPlaylists.isEmpty() && fallback != null) {
            return fallback
        }
        return this
    }

    private fun extractArrayFromField(root: JSONObject, field: String): JSONArray? {
        val direct = root.optJSONArray(field)
        if (direct != null && direct.length() > 0) return direct
        val modules = root.optJSONObject("modules")
        val inModules = modules?.optJSONArray(field)
        if (inModules != null && inModules.length() > 0) return inModules
        val moduleObj = modules?.optJSONObject(field)
        if (moduleObj != null) {
            val modArr = moduleObj.optJSONArray("data")
                ?: moduleObj.optJSONArray("list")
                ?: moduleObj.optJSONArray("results")
                ?: moduleObj.optJSONArray("items")
            if (modArr != null && modArr.length() > 0) return modArr
        }
        val obj = root.optJSONObject(field)
        if (obj != null) {
            return obj.optJSONArray("data")
                ?: obj.optJSONArray("list")
                ?: obj.optJSONArray("results")
                ?: obj.optJSONArray("items")
                ?: obj.optJSONArray("charts")
        }
        return null
    }

    private fun fetchJsonObject(callQuery: String): JSONObject? {
        val body = executeWithFailover(callQuery) ?: return null
        return try {
            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing json object from $callQuery", e)
            null
        }
    }

    private fun fetchJsonArray(callQuery: String): JSONArray? {
        val body = executeWithFailover(callQuery) ?: return null
        return try {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                root.optJSONArray("data")
                    ?: root.optJSONArray("results")
                    ?: root.optJSONArray("list")
                    ?: root.optJSONArray("new_albums")
                    ?: root.optJSONArray("albums")
                    ?: root.optJSONArray("new_trending")
                    ?: root.optJSONArray("top_playlists")
                    ?: root.optJSONArray("charts")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing json array from $callQuery", e)
            null
        }
    }

    /**
     * Search songs with query using failover mirrors
     */
    suspend fun searchSongs(query: String, page: Int = 1, limit: Int = 30): List<SaavnSongItem> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val callQuery = "__call=search.getResults&q=$encodedQuery&p=$page&n=$limit"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val songs = mutableListOf<SaavnSongItem>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val song = parseSongObject(obj)
                if (song.title.isNotBlank()) {
                    songs.add(song)
                }
            }
            songs
        } catch (e: Exception) {
            Log.e(TAG, "Error searching songs for query $query", e)
            emptyList()
        }
    }

    /**
     * Search albums with query using failover mirrors
     */
    suspend fun searchAlbums(query: String, page: Int = 1, limit: Int = 20): List<SaavnAlbumItem> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val callQuery = "__call=search.getAlbumResults&q=$encodedQuery&p=$page&n=$limit"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val albums = mutableListOf<SaavnAlbumItem>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val album = parseAlbumObject(obj)
                if (album.title.isNotBlank()) {
                    albums.add(album)
                }
            }
            albums
        } catch (e: Exception) {
            Log.e(TAG, "Error searching albums for query $query", e)
            emptyList()
        }
    }

    /**
     * Search playlists with query using failover mirrors
     */
    suspend fun searchPlaylists(query: String, page: Int = 1, limit: Int = 20): List<SaavnPlaylistItem> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val callQuery = "__call=search.getPlaylistResults&q=$encodedQuery&p=$page&n=$limit"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val playlists = mutableListOf<SaavnPlaylistItem>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val playlist = parsePlaylistObject(obj)
                if (playlist.title.isNotBlank()) {
                    playlists.add(playlist)
                }
            }
            playlists
        } catch (e: Exception) {
            Log.e(TAG, "Error searching playlists for query $query", e)
            emptyList()
        }
    }

    /**
     * Get Album Details & Tracklist
     */
    suspend fun getAlbumDetails(albumId: String): List<SaavnSongItem> = withContext(Dispatchers.IO) {
        val callQuery = "__call=content.getAlbumDetails&albumid=$albumId"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val songsArray = root.optJSONArray("songs") ?: root.optJSONArray("list") ?: return@withContext emptyList()
            val songs = mutableListOf<SaavnSongItem>()
            for (i in 0 until songsArray.length()) {
                val obj = songsArray.optJSONObject(i) ?: continue
                val song = parseSongObject(obj)
                if (song.title.isNotBlank()) {
                    songs.add(song)
                }
            }
            songs
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching album details $albumId", e)
            emptyList()
        }
    }

    /**
     * Get Playlist Details & Tracklist
     */
    suspend fun getPlaylistDetails(listId: String): List<SaavnSongItem> = withContext(Dispatchers.IO) {
        val callQuery = "__call=playlist.getDetails&listid=$listId"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val songsArray = root.optJSONArray("songs") ?: root.optJSONArray("list") ?: return@withContext emptyList()
            val songs = mutableListOf<SaavnSongItem>()
            for (i in 0 until songsArray.length()) {
                val obj = songsArray.optJSONObject(i) ?: continue
                val song = parseSongObject(obj)
                if (song.title.isNotBlank()) {
                    songs.add(song)
                }
            }
            songs
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist details $listId", e)
            emptyList()
        }
    }

    /**
     * Get Song Details by ID (if needed)
     */
    suspend fun getSongDetails(songId: String): SaavnSongItem? = withContext(Dispatchers.IO) {
        val callQuery = "__call=song.getDetails&pids=$songId&cc=in"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext null
            val root = JSONObject(body)
            val songObj = root.optJSONObject(songId) ?: return@withContext null
            parseSongObject(songObj)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching song details $songId", e)
            null
        }
    }

    /**
     * Formats lyrics with proper linebreaks and cleans HTML/JSON artifacts.
     */
    fun formatLyrics(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val unescaped = raw
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("</p>".toRegex(RegexOption.IGNORE_CASE), "\n\n")
            .replace("<p>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<.*?>"), "") // Strip HTML tags
            .replace(Regex("""\{.*?\}"""), "") // Strip JSON artifacts
            .replace("\r", "")

        return unescaped.lines()
            .map { it.trim() }
            .joinToString("\n")
            .trim()
    }

    private fun cleanTitleForLyrics(title: String): String {
        return title
            .replace(Regex("""\((?:from|feat|ft|with|audio|video|official|remix|version|original|soundtrack|hindi|tamil|telugu|punjabi).*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:from|feat|ft|with|audio|video|official|remix|version|original|soundtrack|hindi|tamil|telugu|punjabi).*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\{.*?\}"""), "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanArtistForLyrics(artist: String): String {
        val first = artist.split(",", ";", "&", "feat.", "ft.", "/").firstOrNull() ?: artist
        return cleanText(first).trim()
    }

    /**
     * Get Lyrics for a song using multi-source fallbacks:
     * 1. Official JioSaavn web6dot0 lyrics endpoint
     * 2. JioSaavn public mirrors (saavn.dev / saavn.me)
     * 3. LRCLIB global free lyrics database
     * 4. Lyrics.ovh open fallback
     */
    suspend fun getLyrics(
        songId: String,
        lyricsId: String = "",
        title: String = "",
        artist: String = ""
    ): String? = withContext(Dispatchers.IO) {
        // Source 1: Direct JioSaavn official API call with explicit lyrics_id or songId
        val candidates = listOfNotNull(
            lyricsId.takeIf { it.isNotBlank() },
            songId.takeIf { it.isNotBlank() }
        ).distinct()

        for (candId in candidates) {
            try {
                val officialUrl = "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&lyrics_id=$candId&ctx=web6dot0&_format=json&_marker=0&api_version=4"
                val response = httpClient.newCall(buildRequest(officialUrl)).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val rawLyrics = root.optString("lyrics", "")
                    if (rawLyrics.isNotBlank() && !rawLyrics.equals("null", ignoreCase = true)) {
                        val formatted = formatLyrics(rawLyrics)
                        if (formatted.isNotBlank()) return@withContext formatted
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Official lyrics endpoint failed for $candId: ${e.message}")
            }
        }

        // Source 1b: Fetch song details from JioSaavn to get accurate lyrics_id or lyrics snippet
        if (songId.isNotBlank()) {
            try {
                val detailsUrl = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$songId&ctx=web6dot0&_format=json"
                val response = httpClient.newCall(buildRequest(detailsUrl)).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val songObj = root.optJSONObject(songId)
                    val moreInfo = songObj?.optJSONObject("more_info")
                    val fetchedLyricsId = moreInfo?.optString("lyrics_id", "") ?: ""
                    val snippet = moreInfo?.optString("lyrics_snippet", "") ?: ""
                    if (fetchedLyricsId.isNotBlank() && fetchedLyricsId != songId) {
                        val lyrUrl = "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&lyrics_id=$fetchedLyricsId&ctx=web6dot0&_format=json&_marker=0&api_version=4"
                        val lyrResp = httpClient.newCall(buildRequest(lyrUrl)).execute()
                        val lyrBody = lyrResp.body?.string()
                        if (lyrResp.isSuccessful && !lyrBody.isNullOrBlank()) {
                            val lyrRoot = JSONObject(lyrBody)
                            val rawLyrics = lyrRoot.optString("lyrics", "")
                            if (rawLyrics.isNotBlank() && !rawLyrics.equals("null", ignoreCase = true)) {
                                val formatted = formatLyrics(rawLyrics)
                                if (formatted.isNotBlank()) return@withContext formatted
                            }
                        }
                    }
                    if (snippet.isNotBlank()) {
                        val formatted = formatLyrics(snippet)
                        if (formatted.isNotBlank()) return@withContext formatted
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "song.getDetails failed for $songId: ${e.message}")
            }
        }

        // Source 2: JioSaavn Mirror endpoints
        val mirrorEndpoints = listOf(
            "https://saavn.dev/api/songs/$songId/lyrics",
            "https://saavn.dev/api/lyrics?id=$songId",
            "https://saavn.me/lyrics?id=$songId"
        )
        for (mirror in mirrorEndpoints) {
            try {
                val response = httpClient.newCall(buildRequest(mirror)).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val rawLyrics = root.optJSONObject("data")?.optString("lyrics")
                        ?: root.optString("lyrics", "")
                    if (!rawLyrics.isNullOrBlank() && !rawLyrics.equals("null", ignoreCase = true)) {
                        val formatted = formatLyrics(rawLyrics)
                        if (formatted.isNotBlank()) return@withContext formatted
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror $mirror failed for $songId: ${e.message}")
            }
        }

        // Clean query terms for external global databases
        val cleanTitle = cleanTitleForLyrics(title)
        val cleanArt = cleanArtistForLyrics(artist)

        if (cleanTitle.isNotBlank()) {
            // Source 3: LRCLIB (Free, open global lyrics database)
            try {
                val encodedTrack = URLEncoder.encode(cleanTitle, "UTF-8")
                val encodedArtist = URLEncoder.encode(cleanArt, "UTF-8")
                val lrclibUrl = if (cleanArt.isNotBlank()) {
                    "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
                } else {
                    "https://lrclib.net/api/search?q=$encodedTrack"
                }
                val req = Request.Builder()
                    .url(lrclibUrl)
                    .header("User-Agent", "MovieBoxSaavnMusicApp/1.0")
                    .build()
                val response = httpClient.newCall(req).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    if (body.trim().startsWith("[")) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            val plain = first.optString("plainLyrics", "")
                            if (plain.isNotBlank()) {
                                return@withContext formatLyrics(plain)
                            }
                            val synced = first.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) {
                                val stripTimestamps = synced.replace(Regex("""\[\d+:\d+(?:\.\d+)?\]\s*"""), "")
                                return@withContext formatLyrics(stripTimestamps)
                            }
                        }
                    } else if (body.trim().startsWith("{")) {
                        val obj = JSONObject(body)
                        val plain = obj.optString("plainLyrics", "")
                        if (plain.isNotBlank()) {
                            return@withContext formatLyrics(plain)
                        }
                        val synced = obj.optString("syncedLyrics", "")
                        if (synced.isNotBlank()) {
                            val stripTimestamps = synced.replace(Regex("""\[\d+:\d+(?:\.\d+)?\]\s*"""), "")
                            return@withContext formatLyrics(stripTimestamps)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "LRCLIB failed for $cleanTitle: ${e.message}")
            }

            // Source 4: Lyrics.ovh fallback
            if (cleanArt.isNotBlank()) {
                try {
                    val encArt = URLEncoder.encode(cleanArt, "UTF-8")
                    val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                    val ovhUrl = "https://api.lyrics.ovh/v1/$encArt/$encTitle"
                    val req = Request.Builder().url(ovhUrl).build()
                    val response = httpClient.newCall(req).execute()
                    val body = response.body?.string()
                    if (response.isSuccessful && !body.isNullOrBlank() && body.trim().startsWith("{")) {
                        val obj = JSONObject(body)
                        val lyrics = obj.optString("lyrics", "")
                        if (lyrics.isNotBlank()) {
                            return@withContext formatLyrics(lyrics)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Lyrics.ovh failed for $cleanTitle: ${e.message}")
                }
            }
        }

        null
    }

    /**
     * Search artists with query
     */
    suspend fun searchArtists(query: String, page: Int = 1, limit: Int = 20): List<com.example.data.model.SaavnArtistItem> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val callQuery = "__call=search.getArtistResults&q=$encodedQuery&p=$page&n=$limit"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val artists = mutableListOf<com.example.data.model.SaavnArtistItem>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val id = obj.optString("id", obj.optString("artistid", ""))
                val name = cleanText(obj.optString("name", obj.optString("title", "")))
                val rawImage = obj.optString("image", "")
                val image = getHighQualityImage(rawImage)
                val role = cleanText(obj.optString("role", ""))
                if (id.isNotBlank() && name.isNotBlank() && !name.startsWith("{")) {
                    artists.add(com.example.data.model.SaavnArtistItem(id = id, name = name, image = image, role = role))
                }
            }
            artists
        } catch (e: Exception) {
            Log.e(TAG, "Error searching artists for query $query", e)
            emptyList()
        }
    }
}

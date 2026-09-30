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
        "ctx=web6dot0&api_version=4",
        "ctx=android&api_version=4",
        "ctx=wap6dot0&api_version=4"
    )

    @Volatile
    private var currentServerIndex = 0

    @Volatile
    private var currentContextIndex = 0

    @Volatile
    private var cachedHomeData: SaavnHomeData? = null

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

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
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
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
            .header("Cookie", "L=hindi%2Cenglish; gdpr_acceptance=true; DL=english")
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
            val ctxParam = if (callQuery.contains("ctx=")) "" else "&$ctx"
            val formatParam = if (callQuery.contains("_format=")) "" else "&_format=json&_marker=0"
            val fullUrl = if (callQuery.startsWith("http")) callQuery else "$base$sep$callQuery$ctxParam$formatParam"

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
         val rawType = obj.optString("type", "").lowercase().trim()
         val moreInfo = obj.optJSONObject("more_info")
         val moreType = moreInfo?.optString("type", "")?.lowercase()?.trim() ?: ""
         val subType = obj.optString("subtype", "").lowercase().trim()

         val encryptedMediaUrl = moreInfo?.optString("encrypted_media_url")
             ?: obj.optString("encrypted_media_url", "")
         val hasEncryptedMedia = encryptedMediaUrl.isNotBlank()

         // Accurate item type detection:
         // 1. If encrypted_media_url is present, or type is "song"/"track", it is definitely a single song/music track!
         // 2. If type is "album", it is an Album!
         // 3. If type is "playlist"/"chart"/"channel", it is a Playlist!
         // 4. If type is "artist", it is an Artist!
         val type = when {
             hasEncryptedMedia || rawType == "song" || rawType == "track" || moreType == "song" || moreType == "track" || subType == "song" -> MediaType.SONG
             rawType == "album" || moreType == "album" || subType == "album" -> MediaType.ALBUM
             rawType == "playlist" || moreType == "playlist" || subType == "playlist" ||
                 rawType == "chart" || rawType == "channel" || rawType == "radio" -> MediaType.PLAYLIST
             rawType == "artist" || moreType == "artist" -> MediaType.ARTIST
             moreInfo?.has("song_count") == true -> MediaType.ALBUM
             else -> MediaType.SONG
         }

         val id = obj.optString("id", obj.optString("listid", obj.optString("songid", obj.optString("albumid", "")))).ifBlank { obj.optString("perma_url") }
         val title = cleanText(obj.optString("title").ifBlank { obj.optString("name") })

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
         val hdImage = getHighQualityImage(image)

         val language = cleanText(obj.optString("language", "hindi"))
         val followerCount = (moreInfo?.optString("follower_count") ?: "0").toLongOrNull() ?: 0L
         val songCount = (moreInfo?.optString("song_count") ?: "0").toIntOrNull() ?: 0

         return MediaItem(
             id = id,
             title = title,
             subtitle = subtitle,
             imageUrl = hdImage,
             type = type,
             language = language,
             followerCount = followerCount,
             songCount = songCount,
             encryptedMediaUrl = encryptedMediaUrl
         )
     }

    /**
     * Helper to format artist follower counts like 38245100 -> "38.2M Fans"
     */
    fun formatFollowers(countStr: String?): String {
        if (countStr.isNullOrBlank()) return "Artist"
        val clean = countStr.replace(",", "").trim()
        val count = clean.toLongOrNull() ?: return if (clean.contains("Fans", ignoreCase = true)) clean else "$clean Fans"
        return when {
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM Fans", count / 1_000_000.0)
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK Fans", count / 1_000.0)
            else -> "$count Fans"
        }
    }

    /**
     * Fetch JioSaavn Home / Editorial Feed with automatic mirror rotation and fallback caching.
     */
    suspend fun fetchHomeFeed(): SaavnHomeData = withContext(Dispatchers.IO) {
        try {
            // Master Launch Data API - official JioSaavn single master endpoint
            val launchDataDeferred = async {
                fetchJsonObject("__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=web6dot0")
            }
            // API 2: Pop Hits Curated Playlist & Pop Hindi Albums (Dedicated Clean Data)
            val popHitsPlaylistDeferred = async {
                fetchJsonObject("__call=playlist.getDetails&_format=json&cc=in&_marker=0&listid=1302009985")
            }
            val popHindiAlbumsDeferred = async {
                searchAlbums("Pop Hindi", page = 1, limit = 15)
            }
            // PART 3: Top Artists Carousel API
            val topArtistsDeferred = async {
                getTopArtists(page = 1, limit = 50)
            }
            // Additional fallback endpoints
            val chartsDeferred = async {
                fetchJsonArray("__call=content.getCharts&_format=json&_marker=0&ctx=web6dot0&api_version=4")
            }

            val launchDataObj = launchDataDeferred.await()
            val popHitsPlaylist = popHitsPlaylistDeferred.await()
            val popHindiAlbums = popHindiAlbumsDeferred.await()
            val topArtists = topArtistsDeferred.await()
            val chartsArray = chartsDeferred.await()

            val trendingNow = mutableListOf<MediaItem>()
            val editorialPicks = mutableListOf<MediaItem>()
            val newTrendingAlbums = mutableListOf<MediaItem>()
            val topCharts = mutableListOf<MediaItem>()
            val genresAndMoods = mutableListOf<MediaItem>()
            val freeHits = mutableListOf<MediaItem>()
            val devotional = mutableListOf<MediaItem>()
            val bestOf90s = mutableListOf<MediaItem>()
            val popHindiItems = mutableListOf<MediaItem>()

            fun addMediaItems(
                arr: JSONArray?,
                targetList: MutableList<MediaItem>,
                defaultType: MediaType? = null,
                badge: String = ""
            ) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val rawItem = parseMediaItem(obj)
                    val hasExplicitTypeOrMedia = obj.has("type") ||
                        obj.optJSONObject("more_info")?.has("type") == true ||
                        !rawItem.encryptedMediaUrl.isNullOrBlank()

                    var item = if (!hasExplicitTypeOrMedia && defaultType != null) {
                        rawItem.copy(type = defaultType)
                    } else {
                        rawItem
                    }
                    if (badge.isNotBlank()) {
                        item = item.copy(badge = badge)
                    }
                    if (item.title.isNotBlank() && targetList.none { it.id == item.id || it.title.equals(item.title, ignoreCase = true) }) {
                        targetList.add(item)
                    }
                }
            }

            // 1. Process Master Launch Data (Section mapping per guide)
            if (launchDataObj != null) {
                // Section 1: Trending Now - json["new_trending"]
                addMediaItems(extractArrayFromField(launchDataObj, "new_trending"), trendingNow)
                // Section 2: Editorial Picks - json["top_playlists"]
                addMediaItems(extractArrayFromField(launchDataObj, "top_playlists"), editorialPicks, defaultType = MediaType.PLAYLIST)
                // Section 3: New Trending Albums - json["new_albums"]
                addMediaItems(extractArrayFromField(launchDataObj, "new_albums"), newTrendingAlbums, defaultType = MediaType.ALBUM, badge = "ALBUM")
                // Section 4: Top Charts - json["charts"]
                addMediaItems(extractArrayFromField(launchDataObj, "charts"), topCharts, defaultType = MediaType.PLAYLIST)
                // Section 5: Top Genres & Moods - json["browse_discover"]
                addMediaItems(extractArrayFromField(launchDataObj, "browse_discover"), genresAndMoods, defaultType = MediaType.PLAYLIST, badge = "GENRE")
                // Section 6: Free Hits - json["promo:vx:data:68"]
                addMediaItems(extractArrayFromField(launchDataObj, "promo:vx:data:68"), freeHits, defaultType = MediaType.PLAYLIST, badge = "FREE HITS")
                // Section 7: Jai Ganesh & Devotional - json["promo:vx:data:112"] or 107
                addMediaItems(extractArrayFromField(launchDataObj, "promo:vx:data:112") ?: extractArrayFromField(launchDataObj, "promo:vx:data:107"), devotional, defaultType = MediaType.PLAYLIST, badge = "BHAKTI")
                // Section 8: Best of 90s - json["promo:vx:data:185"] or 211
                addMediaItems(extractArrayFromField(launchDataObj, "promo:vx:data:185") ?: extractArrayFromField(launchDataObj, "promo:vx:data:211"), bestOf90s, defaultType = MediaType.PLAYLIST, badge = "90s RETRO")
            }

            // Fallback for promotional sections if promo keys rotated
            if (freeHits.isEmpty()) {
                val searchRes = searchPlaylists("Free Hits", limit = 10)
                for (p in searchRes) {
                    freeHits.add(MediaItem(id = p.id, title = p.title, subtitle = p.subtitle.ifBlank { "Free Hits" }, imageUrl = p.image, type = MediaType.PLAYLIST, badge = "FREE HITS"))
                }
            }
            if (devotional.isEmpty()) {
                val searchRes = searchPlaylists("Bhakti", limit = 10)
                for (p in searchRes) {
                    devotional.add(MediaItem(id = p.id, title = p.title, subtitle = p.subtitle.ifBlank { "Devotional" }, imageUrl = p.image, type = MediaType.PLAYLIST, badge = "BHAKTI"))
                }
            }
            if (bestOf90s.isEmpty()) {
                val searchRes = searchPlaylists("90s Bollywood", limit = 10)
                for (p in searchRes) {
                    bestOf90s.add(MediaItem(id = p.id, title = p.title, subtitle = p.subtitle.ifBlank { "90s Retro" }, imageUrl = p.image, type = MediaType.PLAYLIST, badge = "90s RETRO"))
                }
            }

            // Ensure Top Charts has rich list
            addMediaItems(chartsArray, topCharts, defaultType = MediaType.PLAYLIST)

            // API 2: Pop Hindi Curated Playlist + Albums
            if (popHitsPlaylist != null) {
                val pItem = parseMediaItem(popHitsPlaylist).copy(type = MediaType.PLAYLIST, badge = "POP HIT")
                if (pItem.title.isNotBlank()) {
                    popHindiItems.add(pItem)
                }
                val playlistSongs = popHitsPlaylist.optJSONArray("songs") ?: popHitsPlaylist.optJSONArray("list")
                if (playlistSongs != null) {
                    for (i in 0 until playlistSongs.length()) {
                        val sObj = playlistSongs.optJSONObject(i) ?: continue
                        val sItem = parseMediaItem(sObj).copy(type = MediaType.SONG, badge = "POP HIT")
                        if (sItem.title.isNotBlank() && popHindiItems.none { it.id == sItem.id }) {
                            popHindiItems.add(sItem)
                        }
                    }
                }
            }
            for (popAlbum in popHindiAlbums) {
                if (popHindiItems.none { it.id == popAlbum.id || it.title.equals(popAlbum.title, ignoreCase = true) }) {
                    popHindiItems.add(
                        MediaItem(
                            id = popAlbum.id,
                            title = popAlbum.title,
                            subtitle = popAlbum.artist.ifBlank { "Pop Hindi" },
                            imageUrl = popAlbum.image,
                            type = MediaType.ALBUM,
                            songCount = popAlbum.songCount,
                            badge = "POP ALBUM"
                        )
                    )
                }
            }

            val result = SaavnHomeData(
                trendingNow = trendingNow,
                editorialPicks = editorialPicks,
                popHindiItems = popHindiItems,
                topArtists = topArtists,
                newTrendingAlbums = newTrendingAlbums,
                freeHits = freeHits,
                topCharts = topCharts,
                devotional = devotional,
                bestOf90s = bestOf90s,
                genresAndMoods = genresAndMoods,

                // Backward compatibility
                trendingItems = trendingNow,
                topPlaylists = emptyList(),
                featuredPlaylists = editorialPicks,
                newReleases = newTrendingAlbums
            )

            if (trendingNow.isNotEmpty() || topCharts.isNotEmpty() || newTrendingAlbums.isNotEmpty()) {
                cachedHomeData = result
            }

            result.ifEmptyFallback(cachedHomeData)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching home feed, falling back to cache", e)
            cachedHomeData ?: SaavnHomeData()
        }
    }

    private fun SaavnHomeData.ifEmptyFallback(fallback: SaavnHomeData?): SaavnHomeData {
        if (trendingNow.isEmpty() && trendingItems.isEmpty() && newTrendingAlbums.isEmpty() && fallback != null) {
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
     * Search songs with query using failover mirrors (returns songs and total available count)
     */
    suspend fun searchSongsWithTotal(query: String, page: Int = 1, limit: Int = 50): Pair<List<SaavnSongItem>, Int> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val callQuery = "__call=search.getResults&q=$encodedQuery&p=$page&n=$limit"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext Pair(emptyList(), 0)
            val root = JSONObject(body)
            val total = root.optInt("total", 0)
            val results = root.optJSONArray("results") ?: return@withContext Pair(emptyList(), total)
            val songs = mutableListOf<SaavnSongItem>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val song = parseSongObject(obj)
                if (song.title.isNotBlank()) {
                    songs.add(song)
                }
            }
            Pair(songs, total)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching songs for query $query", e)
            Pair(emptyList(), 0)
        }
    }

    /**
     * Search songs with query using failover mirrors
     */
    suspend fun searchSongs(query: String, page: Int = 1, limit: Int = 50): List<SaavnSongItem> {
        return searchSongsWithTotal(query, page, limit).first
    }

    /**
     * Search albums with query using failover mirrors
     */
    suspend fun searchAlbums(query: String, page: Int = 1, limit: Int = 40): List<SaavnAlbumItem> = withContext(Dispatchers.IO) {
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
    suspend fun searchPlaylists(query: String, page: Int = 1, limit: Int = 40): List<SaavnPlaylistItem> = withContext(Dispatchers.IO) {
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
    suspend fun searchArtists(query: String, page: Int = 1, limit: Int = 40): List<com.example.data.model.SaavnArtistItem> = withContext(Dispatchers.IO) {
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

    /**
     * Get Top Artists list for Home Screen Carousel
     */
    suspend fun getTopArtists(page: Int = 1, limit: Int = 50): List<com.example.data.model.SaavnArtistItem> = withContext(Dispatchers.IO) {
        val callQuery = "__call=social.getTopArtists&_format=json&_marker=0&api_version=4&ctx=web6dot0&p=$page&n=$limit"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val topArtistsArray = root.optJSONArray("top_artists") ?: root.optJSONArray("artists") ?: return@withContext emptyList()
            val artists = mutableListOf<com.example.data.model.SaavnArtistItem>()
            for (i in 0 until topArtistsArray.length()) {
                val obj = topArtistsArray.optJSONObject(i) ?: continue
                val id = obj.optString("artistid", obj.optString("id", ""))
                val name = cleanText(obj.optString("name", obj.optString("title", "")))
                val rawImage = obj.optString("image", "")
                val image = rawImage
                    .replace("150x150", "500x500")
                    .replace("50x50", "500x500")
                    .replace("http://", "https://")
                val rawFollowers = obj.optString("follower_count", obj.optString("fan_count", ""))
                val followerCountFormatted = formatFollowers(rawFollowers)
                val role = cleanText(obj.optString("role", "Artist"))
                if (id.isNotBlank() && name.isNotBlank()) {
                    artists.add(
                        com.example.data.model.SaavnArtistItem(
                            id = id,
                            name = name,
                            image = image,
                            role = role,
                            followerCount = followerCountFormatted,
                            isVerified = true
                        )
                    )
                }
            }
            artists
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top artists", e)
            emptyList()
        }
    }

    /**
     * Get Complete Artist Page Details: Discography, Top Albums, Top Songs (PART 4 Critical Rules)
     */
    suspend fun getArtistPageDetails(artistId: String): com.example.data.model.ArtistDiscography? = withContext(Dispatchers.IO) {
        val callQuery = "__call=artist.getArtistPageDetails&_format=json&_marker=0&artistId=$artistId&n_song=50&n_album=50"
        try {
            val body = executeWithFailover(callQuery) ?: return@withContext null
            val root = JSONObject(body)
            val name = cleanText(root.optString("name", root.optString("artistName", "")))
            val rawImage = root.optString("image", "")
            val image = rawImage
                .replace("150x150", "500x500")
                .replace("50x50", "500x500")
                .replace("http://", "https://")
            val subtitle = cleanText(root.optString("subtitle", root.optString("role", "Playback Singer, Music Director")))
            val rawFollowers = root.optString("follower_count", root.optString("fan_count", ""))
            val followerCountFormatted = formatFollowers(rawFollowers)
            val isVerified = root.optBoolean("isVerified", true)

            // Top Songs
            val songs = mutableListOf<SaavnSongItem>()
            val topSongsObj = root.optJSONObject("topSongs")
            val songsArray = topSongsObj?.optJSONArray("songs") ?: root.optJSONArray("topSongs") ?: root.optJSONArray("songs")
            if (songsArray != null) {
                for (i in 0 until songsArray.length()) {
                    val sObj = songsArray.optJSONObject(i) ?: continue
                    val song = parseSongObject(sObj)
                    if (song.title.isNotBlank()) songs.add(song)
                }
            }

            // Top Albums (Discography) with 4 Critical Parsing Rules
            val albums = mutableListOf<SaavnAlbumItem>()
            val topAlbumsObj = root.optJSONObject("topAlbums")
            val albumsArray = topAlbumsObj?.optJSONArray("albums") ?: root.optJSONArray("topAlbums") ?: root.optJSONArray("albums")
            if (albumsArray != null) {
                for (i in 0 until albumsArray.length()) {
                    val aObj = albumsArray.optJSONObject(i) ?: continue
                    val moreInfo = aObj.optJSONObject("more_info")

                    // 1. Album Title Parsing
                    val rawTitle = aObj.optString("album").ifEmpty {
                        aObj.optString("title").ifEmpty {
                            aObj.optString("name").ifEmpty {
                                moreInfo?.optString("album") ?: "Unknown Album"
                            }
                        }
                    }
                    val albumTitle = cleanText(rawTitle)

                    // 2. Album Image URL Parsing (500x500 HD)
                    val rawImg = aObj.optString("imageUrl").ifEmpty {
                        aObj.optString("image").ifEmpty {
                            moreInfo?.optString("image") ?: ""
                        }
                    }
                    val hdImageUrl = rawImg
                        .replace("150x150", "500x500")
                        .replace("50x50", "500x500")
                        .replace("http://", "https://")

                    // 3. Album Subtitle & Artists
                    val rawSubtitle = aObj.optString("primaryArtists").ifEmpty {
                        aObj.optString("singers").ifEmpty {
                            aObj.optString("subtitle").ifEmpty {
                                moreInfo?.optString("singers") ?: "Album"
                            }
                        }
                    }
                    val albumSubtitle = cleanText(rawSubtitle)

                    // 4. Album ID & Song Count
                    val albumId = aObj.optString("albumid").ifEmpty {
                        aObj.optString("id").ifEmpty {
                            aObj.optString("album_id") ?: ""
                        }
                    }
                    val songCount = aObj.optInt("numSongs", 0).takeIf { it > 0 }
                        ?: aObj.optInt("song_pids", 0)
                    val year = aObj.optString("year", "")
                    val language = cleanText(aObj.optString("language", ""))

                    if (albumId.isNotBlank() && albumTitle.isNotBlank()) {
                        albums.add(
                            SaavnAlbumItem(
                                id = albumId,
                                title = albumTitle,
                                subtitle = albumSubtitle,
                                image = hdImageUrl,
                                artist = albumSubtitle,
                                year = year,
                                language = language,
                                songCount = songCount
                            )
                        )
                    }
                }
            }

            com.example.data.model.ArtistDiscography(
                artistId = artistId,
                artistName = name,
                subtitle = subtitle,
                artistImage = image,
                followerCount = followerCountFormatted,
                isVerified = isVerified,
                topSongs = songs,
                topAlbums = albums
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist page details for $artistId", e)
            null
        }
    }
}

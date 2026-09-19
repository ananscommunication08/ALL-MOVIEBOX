package com.example.data.api

import android.util.Log
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class StoryTvLanguage(
    val langId: Int,
    val title: String,
    val thumbnailUrl: String = "",
    val isPreSelected: Boolean = false
)

data class StoryTvEpisodeItem(
    val index: Int,
    val contentId: String = "",
    val title: String = "",
    val url: String = "",
    val thumb: String = "",
    val subTxt: String = ""
)

object StoryTvApiClient {
    private const val TAG = "StoryTvApiClient"
    private const val BASE_URL = "https://elitemods-tv.onrender.com"
    private const val AUTH_TOKEN =
        "Bearer eyJhbGciOiJIUzI1NiJ9.eyJjcmVhdGVkRGF0ZSI6IkZyaSBKdW4gMTIgMDc6MDQ6MTAgVVRDIDIwMjYiLCJzZXNzaW9uSWQiOiIxNTI0MDExNDMiLCJkZXZpY2VJZCI6IjI4MDhkNDliN2UwOGI3MTYiLCJzdWIiOiIxMTYwMDQ2NDciLCJleHAiOjE3ODE1MDcwNTB9._GKqF_5WtYkgAIJVhkt3L27t9fvgLkFtfgaOLSPsrOA"
    private const val APP_VERSION = "62"
    private const val OS_NAME = "Android 13 (API 33)"
    private const val DEVICE_ID = "a6229bad5c179d51"
    private const val PLATFORM = "0"
    private const val IAMSINU = "iamsinu"
    private const val NETWORK_TYPE = "WIFI"
    private const val USER_AGENT = "ktor-client"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRequest(url: String, method: String = "GET", body: okhttp3.RequestBody? = null): Request {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val builder = Request.Builder()
            .url(url)
            .addHeader("Authorization", AUTH_TOKEN)
            .addHeader("appVersion", APP_VERSION)
            .addHeader("os", OS_NAME)
            .addHeader("deviceId", DEVICE_ID)
            .addHeader("platform", PLATFORM)
            .addHeader("iamsinu", IAMSINU)
            .addHeader("network_type", NETWORK_TYPE)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("ts", ts)

        if (method == "POST") {
            builder.post(body ?: "".toRequestBody("application/json".toMediaType()))
        } else {
            builder.get()
        }
        return builder.build()
    }

    /**
     * Fetch Explore Dramas with pagination
     * /feedservice/v2/explore/shows?page={page}&size={size}&intCount=0
     */
    suspend fun fetchExploreShows(page: Int = 0, size: Int = 50): Pair<List<MovieItem>, Boolean> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/feedservice/v2/explore/shows?page=$page&size=$size&intCount=0"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext Pair(emptyList(), false)
            if (!response.isSuccessful) {
                Log.e(TAG, "fetchExploreShows error HTTP ${response.code}")
                return@withContext Pair(emptyList(), false)
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), false)
            val contentArray = dataObj.optJSONArray("content") ?: JSONArray()

            val items = mutableListOf<MovieItem>()
            for (i in 0 until contentArray.length()) {
                val itemWrapper = contentArray.optJSONObject(i) ?: continue
                val data = itemWrapper.optJSONObject("data") ?: continue
                val showInfo = data.optJSONObject("showInfo") ?: continue
                val contentInfo = data.optJSONObject("contentInfo")

                val showId = showInfo.optString("id", "")
                val title = showInfo.optString("title", "")
                if (showId.isBlank() && title.isBlank()) continue

                val imageUrl = showInfo.optString("imageUrl", "")
                val numOfEpisodes = showInfo.optInt("numOfEpisodes", 0)
                val metaObj = showInfo.optJSONObject("metadata")
                val genre = metaObj?.optString("genre", "") ?: ""
                val tagsList = mutableListOf<String>()
                val tagsArr = metaObj?.optJSONArray("tags")
                if (tagsArr != null) {
                    for (k in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(k, "").trim()
                        if (t.isNotBlank()) tagsList.add(t)
                    }
                }

                val epsUrl = contentInfo?.optString("epsUrl", "") ?: ""
                val epsTitle = contentInfo?.optString("epsTitle", "") ?: ""

                val realTag = genre.ifBlank { tagsList.firstOrNull() ?: "Drama" }

                val movie = MovieItem(
                    id = showId.ifBlank { "storytv_$i" },
                    title = title,
                    description = tagsList.joinToString(" • "),
                    coverUrl = imageUrl,
                    backdropUrl = imageUrl,
                    rating = "",
                    genre = realTag,
                    country = "Story TV",
                    corner = realTag,
                    duration = "",
                    isShort = true,
                    detailPath = showId,
                    directUrl = epsUrl,
                    source = "storytv",
                    uploadBy = "StoryTV",
                    totalEpisodes = numOfEpisodes,
                    isSeries = false,
                    isVskitServer = false,
                    isStoryTvServer = true,
                    subjectType = 7
                )
                items.add(movie)
            }

            val hasMore = items.size >= size
            Pair(items, hasMore)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching explore shows page=$page", e)
            Pair(emptyList(), false)
        }
    }

    /**
     * Fetch available languages
     * /userservice/v1/languages
     */
    suspend fun fetchLanguages(): List<StoryTvLanguage> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/userservice/v1/languages"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val langArray = dataObj.optJSONArray("languages") ?: return@withContext emptyList()

            val list = mutableListOf<StoryTvLanguage>()
            for (i in 0 until langArray.length()) {
                val obj = langArray.optJSONObject(i) ?: continue
                val langId = obj.optInt("langId", 0)
                val title = obj.optString("title", "")
                val thumb = obj.optString("thumbnailUrl", "")
                val preSelected = obj.optBoolean("isPreSelected", false)
                if (langId > 0 && title.isNotBlank()) {
                    list.add(StoryTvLanguage(langId, title, thumb, preSelected))
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching languages", e)
            emptyList()
        }
    }

    /**
     * Select active language
     * /userservice/v1/language/select?langId={langId}
     */
    suspend fun selectLanguage(langId: Int): Boolean = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/userservice/v1/language/select?langId=$langId"
        try {
            val jsonBody = JSONObject().apply { put("langId", langId) }.toString()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = httpClient.newCall(buildRequest(url, method = "POST", body = requestBody)).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting language $langId", e)
            false
        }
    }

    /**
     * Drama Episodes List
     * /feedservice/v1/episode/list/{showId}
     */
    suspend fun fetchEpisodeList(showId: String): Pair<Int, List<StoryTvEpisodeItem>> = withContext(Dispatchers.IO) {
        val cleanId = showId.filter { it.isDigit() }.ifBlank { showId }
        val url = "$BASE_URL/feedservice/v1/episode/list/$cleanId"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext Pair(0, emptyList())
            if (!response.isSuccessful) return@withContext Pair(0, emptyList())

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(0, emptyList())
            val epCount = dataObj.optInt("epCount", 0)
            val epDataArray = dataObj.optJSONArray("data") ?: JSONArray()

            val eps = mutableListOf<StoryTvEpisodeItem>()
            for (i in 0 until epDataArray.length()) {
                val epObj = epDataArray.optJSONObject(i) ?: continue
                val idx = epObj.optInt("index", i + 1)
                val epTitle = epObj.optString("epTitle", "Episode $idx")
                eps.add(StoryTvEpisodeItem(index = idx, title = epTitle))
            }
            Pair(epCount, eps)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching episode list for $showId", e)
            Pair(0, emptyList())
        }
    }

    /**
     * Episode Streaming Metadata & Video URLs
     * /feedservice/v1/episode/metadata/{showId}?cursor={cursor}&dir=NEXT
     */
    suspend fun fetchEpisodeMetadata(showId: String, cursor: Int = 0): List<StoryTvEpisodeItem> = withContext(Dispatchers.IO) {
        val cleanId = showId.filter { it.isDigit() }.ifBlank { showId }
        val url = "$BASE_URL/feedservice/v1/episode/metadata/$cleanId?cursor=$cursor&dir=NEXT"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val contentArray = dataObj.optJSONArray("content")
                ?: dataObj.optJSONArray("metadata")
                ?: dataObj.optJSONArray("data")
                ?: JSONArray()

            val list = mutableListOf<StoryTvEpisodeItem>()
            for (i in 0 until contentArray.length()) {
                val obj = contentArray.optJSONObject(i) ?: continue
                val idx = obj.optInt("index", i + 1)
                val contentId = obj.optString("contentId", "")
                val title = obj.optString("epsTitle", "").ifBlank { obj.optString("title", "Episode $idx") }
                val streamUrl = obj.optString("epsUrl", "").ifBlank { obj.optString("url", "") }
                val thumb = obj.optString("thumb", "").ifBlank { obj.optString("imageUrl", "") }
                val subTxt = obj.optString("subTxt", "")
                list.add(StoryTvEpisodeItem(
                    index = idx,
                    contentId = contentId,
                    title = title,
                    url = streamUrl,
                    thumb = thumb,
                    subTxt = subTxt
                ))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching episode metadata for $showId cursor=$cursor", e)
            emptyList()
        }
    }

    /**
     * Convenience method to fetch stream URL for a specific episode index
     */
    suspend fun fetchEpisodeStream(showId: String, episodeIndex: Int): String? = withContext(Dispatchers.IO) {
        val cleanId = showId.filter { it.isDigit() }.ifBlank { showId }
        val cursor = (episodeIndex - 1).coerceAtLeast(0)
        val metadata = fetchEpisodeMetadata(cleanId, cursor = cursor)
        val found = metadata.find { it.index == episodeIndex } ?: metadata.firstOrNull()
        found?.url?.ifBlank { null }
    }

    /**
     * Fetch & parse HLS qualities from master.m3u8
     * Returns list of MovieStream with resolutions formatted as "0000 × 0000" (e.g. 1080 × 1920, 720 × 1280)
     * Sorted from highest resolution to lowest resolution.
     */
    suspend fun fetchHlsStreamQualities(masterUrl: String): List<MovieStream> = withContext(Dispatchers.IO) {
        if (!masterUrl.contains(".m3u8", ignoreCase = true)) return@withContext emptyList()
        try {
            val req = Request.Builder().url(masterUrl).get().build()
            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful || !body.contains("#EXT-X-STREAM-INF")) {
                return@withContext emptyList()
            }

            // Base URL directory before master.m3u8
            val baseUrlDir = masterUrl.substringBefore("master.m3u8")

            val streams = mutableListOf<MovieStream>()
            val lines = body.lines()
            var currentRes = ""
            var currentBandwidth = 0L

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                    val resMatch = Regex("""RESOLUTION=(\d+)[xX](\d+)""").find(trimmed)
                    if (resMatch != null) {
                        val w = resMatch.groupValues[1]
                        val h = resMatch.groupValues[2]
                        currentRes = "$w × $h"
                    } else {
                        currentRes = ""
                    }
                    val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(trimmed)
                    currentBandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    if (currentRes.isNotBlank()) {
                        val fullUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            trimmed
                        } else {
                            baseUrlDir + trimmed
                        }
                        streams.add(
                            MovieStream(
                                id = "storytv_hls_${currentRes.replace(" ", "")}",
                                resolution = currentRes,
                                format = "HLS",
                                url = fullUrl,
                                size = currentBandwidth
                            )
                        )
                    }
                    currentRes = ""
                    currentBandwidth = 0L
                }
            }

            // Sort highest quality first
            streams.sortedByDescending { stream ->
                val parts = stream.resolution.split("×").mapNotNull { it.trim().toIntOrNull() }
                parts.maxOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse HLS qualities for $masterUrl", e)
            emptyList()
        }
    }

    /**
     * Search API
     * /searchservice/v1/search/{query}
     */
    suspend fun searchShows(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val cleanQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE_URL/searchservice/v1/search/$cleanQuery"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val contentArray = dataObj.optJSONArray("content") ?: JSONArray()

            val list = mutableListOf<MovieItem>()
            for (i in 0 until contentArray.length()) {
                val obj = contentArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val title = obj.optString("title", "")
                if (id.isBlank() && title.isBlank()) continue

                val imageUrl = obj.optString("imageUrl", "")
                val numOfEpisodes = obj.optInt("numOfEpisodes", 0)
                val metaObj = obj.optJSONObject("metadata")
                val genre = metaObj?.optString("genre", "") ?: ""
                val tagsList = mutableListOf<String>()
                val tagsArr = metaObj?.optJSONArray("tags")
                if (tagsArr != null) {
                    for (k in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(k, "").trim()
                        if (t.isNotBlank()) tagsList.add(t)
                    }
                }

                val realTag = genre.ifBlank { tagsList.firstOrNull() ?: "Drama" }

                list.add(
                    MovieItem(
                        id = id,
                        title = title,
                        description = tagsList.joinToString(" • "),
                        coverUrl = imageUrl,
                        backdropUrl = imageUrl,
                        rating = "",
                        genre = realTag,
                        country = "Story TV",
                        corner = realTag,
                        isShort = true,
                        detailPath = id,
                        directUrl = "",
                        source = "storytv",
                        uploadBy = "StoryTV",
                        totalEpisodes = numOfEpisodes,
                        isSeries = false,
                        isVskitServer = false,
                        isStoryTvServer = true,
                        subjectType = 7
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error searching shows query=$query", e)
            emptyList()
        }
    }
}

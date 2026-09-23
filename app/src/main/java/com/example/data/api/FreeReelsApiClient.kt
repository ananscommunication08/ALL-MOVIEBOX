package com.example.data.api

import android.util.Log
import com.example.data.model.FreeReelsEpisodeItem
import com.example.data.model.MovieItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.GzipSource
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * FreeReels API Client (Server 5)
 * Handles authentication, gzip responses, home tabs, infinite feed, drama info, and search.
 */
object FreeReelsApiClient {

    private const val TAG = "FreeReelsApiClient"
    private const val BASE_URL = "https://apiv2.free-reels.com"

    private const val AUTH_HEADER = "oauth_signature=bf505aa6bb04c96350296bed13d0a920,oauth_token=fyqFvwztTYc5x8v8egEYrfa88WfQCL5D,ts=1789800192295"
    private const val DEVICE_ID = "BC7C2318F4322CFFE00CBDE5043E5644"
    private const val ANDROID_ID = "bc29b75f90824a5c"
    private const val APPSFLYER_ID = "1789721594808-3992160409787450647"
    private const val GAID = "dfa2b45c-0134-49be-87b0-47203e73a707"
    private const val FIREBASE_ID = "a2ec25128fa6336d28b449c28a2943a9"
    private const val SESSION_ID = "58cfafdd-6bb0-409c-8391-df9687441d82"

    private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

    /**
     * Interceptor to decompress GZIP responses if the server returns Content-Encoding: gzip
     */
    private class GzipDecompressingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val encoding = response.header("Content-Encoding")
            return if (encoding != null && encoding.equals("gzip", ignoreCase = true)) {
                val body = response.body ?: return response
                val gzipSource = GzipSource(body.source())
                val strippedHeaders = response.headers.newBuilder()
                    .removeAll("Content-Encoding")
                    .removeAll("Content-Length")
                    .build()
                response.newBuilder()
                    .headers(strippedHeaders)
                    .body(gzipSource.buffer().asResponseBody(body.contentType(), -1))
                    .build()
            } else {
                response
            }
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(GzipDecompressingInterceptor())
            .build()
    }

    /**
     * Build standard FreeReels request with all mandatory headers
     */
    private fun buildRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("authorization", AUTH_HEADER)
            .header("content-type", "application/json; charset=UTF-8")
            .header("accept", "application/json")
            .header("country", "IN")
            .header("timezone", "+5")
            .header("language", "en-US")
            .header("device-country", "US")
            .header("app-display-lang", "en-US")
            .header("device-language", "en-US")
            .header("device-id", DEVICE_ID)
            .header("android-id", ANDROID_ID)
            .header("appsflyer-id", APPSFLYER_ID)
            .header("x-appsflyer_id", APPSFLYER_ID)
            .header("gaid", GAID)
            .header("firebase-id", FIREBASE_ID)
            .header("session-id", SESSION_ID)
            .header("app-name", "com.freereels.app")
            .header("app-version", "2.4.80")
            .header("device", "android")
            .header("x-device-brand", "Infinix")
            .header("x-device-manufacturer", "INFINIX")
            .header("x-device-model", "Infinix X6711")
            .header("x-device-product", "X6711-GL")
            .header("device-version", "34")
            .header("user-agent", "okhttp/4.12.0")
    }

    /**
     * 1. Home Tab Endpoint:
     * GET /frv2-api/homepage/v2/tab/index?tab_key=503&position_index=10000&rec_trigger=1&user_new_theater=false&is_app_coldstart=false
     * Returns: Pair(List<MovieItem>, nextCursor)
     */
    suspend fun fetchHomeTab(tabKey: String = "503"): Pair<List<MovieItem>, String?> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/frv2-api/homepage/v2/tab/index?tab_key=$tabKey&position_index=10000&rec_trigger=1&user_new_theater=false&is_app_coldstart=false"
        try {
            val req = buildRequestBuilder(url).get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext Pair(emptyList(), null)
            if (!resp.isSuccessful) {
                Log.e(TAG, "fetchHomeTab failed: HTTP ${resp.code}")
                return@withContext Pair(emptyList(), null)
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), null)
            val modules = dataObj.optJSONArray("items") ?: JSONArray()
            val pageInfo = dataObj.optJSONObject("page_info")
            val nextCursor = pageInfo?.optString("next", "")?.ifBlank { null }

            val dramaList = mutableListOf<MovieItem>()
            for (i in 0 until modules.length()) {
                val mod = modules.optJSONObject(i) ?: continue
                val itemsArr = mod.optJSONArray("items") ?: continue
                for (j in 0 until itemsArr.length()) {
                    val item = itemsArr.optJSONObject(j) ?: continue
                    parseDramaItem(item)?.let { dramaList.add(it) }
                }
            }

            Pair(dramaList, nextCursor)
        } catch (e: Exception) {
            Log.e(TAG, "fetchHomeTab error", e)
            Pair(emptyList(), null)
        }
    }

    /**
     * 2. Feed / Pagination Endpoint:
     * POST /frv2-api/homepage/v2/tab/feed
     * Body: {"next": nextCursor, "module_key": "1036", "user_new_theater": false}
     * Returns: Pair(List<MovieItem>, Pair(nextCursor, hasMore))
     */
    suspend fun fetchFeed(
        nextCursor: String,
        moduleKey: String = "1036"
    ): Pair<List<MovieItem>, Pair<String?, Boolean>> = withContext(Dispatchers.IO) {
        if (nextCursor.isBlank()) return@withContext Pair(emptyList(), Pair(null, false))
        val url = "$BASE_URL/frv2-api/homepage/v2/tab/feed"
        try {
            val jsonBody = JSONObject().apply {
                put("next", nextCursor)
                put("module_key", moduleKey)
                put("user_new_theater", false)
            }.toString()

            val req = buildRequestBuilder(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext Pair(emptyList(), Pair(null, false))
            if (!resp.isSuccessful) {
                Log.e(TAG, "fetchFeed failed: HTTP ${resp.code}")
                return@withContext Pair(emptyList(), Pair(null, false))
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), Pair(null, false))
            val itemsArr = dataObj.optJSONArray("items") ?: JSONArray()
            val pageInfo = dataObj.optJSONObject("page_info")
            val newNextCursor = pageInfo?.optString("next", "")?.ifBlank { null }
            val hasMore = pageInfo?.optBoolean("has_more", false) ?: false

            val list = mutableListOf<MovieItem>()
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                parseDramaItem(item)?.let { list.add(it) }
            }

            Pair(list, Pair(newNextCursor, hasMore))
        } catch (e: Exception) {
            Log.e(TAG, "fetchFeed error", e)
            Pair(emptyList(), Pair(null, false))
        }
    }

    /**
     * 3. Drama Info & Playback (Episode List) Endpoint:
     * GET /frv2-api/drama/info_v2?series_id={seriesId}&clip_content=
     */
    suspend fun fetchEpisodes(seriesId: String): List<FreeReelsEpisodeItem> = withContext(Dispatchers.IO) {
        if (seriesId.isBlank()) return@withContext emptyList()
        val url = "$BASE_URL/frv2-api/drama/info_v2?series_id=$seriesId&clip_content="
        try {
            val req = buildRequestBuilder(url).get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (!resp.isSuccessful) {
                Log.e(TAG, "fetchEpisodes failed: HTTP ${resp.code}")
                return@withContext emptyList()
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val infoObj = dataObj.optJSONObject("info") ?: return@withContext emptyList()
            val epArr = infoObj.optJSONArray("episode_list") ?: JSONArray()

            val episodes = mutableListOf<FreeReelsEpisodeItem>()
            for (i in 0 until epArr.length()) {
                val epObj = epArr.optJSONObject(i) ?: continue
                val epId = epObj.optString("id", "")
                val name = epObj.optString("name", "Episode ${i + 1}")
                val index = epObj.optInt("index", i + 1)
                val duration = epObj.optInt("duration", 0)
                val videoUrl = epObj.optString("video_url", "")
                val h264M3u8 = epObj.optString("external_audio_h264_m3u8", "")
                val m3u8Url = epObj.optString("m3u8_url", "")
                val h265M3u8 = epObj.optString("external_audio_h265_m3u8", "")
                val unlock = epObj.optBoolean("unlock", true)

                episodes.add(
                    FreeReelsEpisodeItem(
                        id = epId,
                        name = name,
                        index = index,
                        duration = duration,
                        videoUrl = videoUrl,
                        externalAudioH264M3u8 = h264M3u8,
                        m3u8Url = m3u8Url,
                        externalAudioH265M3u8 = h265M3u8,
                        unlock = unlock
                    )
                )
            }

            episodes.sortBy { it.index }
            episodes
        } catch (e: Exception) {
            Log.e(TAG, "fetchEpisodes error for seriesId=$seriesId", e)
            emptyList()
        }
    }

    /**
     * 4. Search Endpoint:
     * POST /frv2-api/search/drama
     */
    suspend fun searchDramas(keyword: String): List<MovieItem> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val url = "$BASE_URL/frv2-api/search/drama"
        try {
            val jsonBody = JSONObject().apply {
                put("keyword", keyword.trim())
                put("audio_tab_key", "mix")
                put("next", "")
            }.toString()

            val req = buildRequestBuilder(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (!resp.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val itemsArr = dataObj.optJSONArray("items")
                ?: dataObj.optJSONArray("drama_list")
                ?: dataObj.optJSONArray("search_items")
                ?: JSONArray()

            val list = mutableListOf<MovieItem>()
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                parseDramaItem(item)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "searchDramas error for $keyword", e)
            emptyList()
        }
    }

    /**
     * Search Keywords / Query Suggestions Endpoint:
     * POST /frv2-api/search/keywords
     * Payload: {"keyword": keyword}
     */
    suspend fun fetchSearchKeywords(keyword: String): List<String> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val url = "$BASE_URL/frv2-api/search/keywords"
        try {
            val jsonBody = JSONObject().apply {
                put("keyword", keyword.trim())
            }.toString()

            val req = buildRequestBuilder(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (!resp.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val itemsArr = dataObj.optJSONArray("keywords")
                ?: dataObj.optJSONArray("items")
                ?: JSONArray()

            val suggestions = mutableListOf<String>()
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i)
                val kw = if (item != null) {
                    item.optString("keyword", "").ifBlank { item.optString("word", "") }.trim()
                } else {
                    itemsArr.optString(i, "").trim()
                }
                if (kw.isNotBlank()) {
                    suggestions.add(kw)
                }
            }
            suggestions.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "fetchSearchKeywords error for $keyword", e)
            emptyList()
        }
    }

    /**
     * Helper to parse a FreeReels drama item JSON into MovieItem
     */
    private fun parseDramaItem(item: JSONObject): MovieItem? {
        val key = item.optString("key", "")
            .ifBlank { item.optString("series_id", "") }
            .ifBlank { item.optString("id", "") }
            .ifBlank { item.optString("drama_id", "") }
        val title = item.optString("title", "").ifBlank { item.optString("name", "") }
        if (key.isBlank() && title.isBlank()) return null

        val cover = item.optString("cover", "")
        val desc = item.optString("desc", "")
        val epCount = item.optInt("episode_count", 0)

        // Parse tags
        val tagList = mutableListOf<String>()
        val tagArr = item.optJSONArray("tag") ?: item.optJSONArray("series_tag")
        if (tagArr != null) {
            for (k in 0 until tagArr.length()) {
                val t = tagArr.optString(k, "").trim()
                if (t.isNotBlank()) tagList.add(t)
            }
        }
        val tagString = if (tagList.isNotEmpty()) tagList.joinToString(" • ") else "Short Drama"

        // First episode info if available (checked both episode_info and episode from search)
        val epInfo = item.optJSONObject("episode_info") ?: item.optJSONObject("episode")
        val directStream = if (epInfo != null) {
            val epIndex = epInfo.optInt("index", 1)
            // STRICT SAFETY: Only use as directUrl if this is explicitly Episode 1!
            // If it is another episode (e.g. latest episode or teaser), do NOT set as directUrl!
            if (epIndex == 1) {
                val vUrl = epInfo.optString("video_url", "")
                val h264 = epInfo.optString("external_audio_h264_m3u8", "")
                val m3u8 = epInfo.optString("m3u8_url", "")
                val h265 = epInfo.optString("external_audio_h265_m3u8", "")
                vUrl.ifBlank { h264.ifBlank { m3u8.ifBlank { h265 } } }
            } else ""
        } else ""

        return MovieItem(
            id = key,
            title = title,
            description = desc,
            coverUrl = cover,
            backdropUrl = cover,
            genre = tagString,
            totalEpisodes = epCount,
            isShort = true,
            isSeries = true,
            isFreeReelsServer = true,
            source = "freereels",
            directUrl = directStream,
            corner = if (epCount > 0) "$epCount Eps" else "FreeReels",
            subjectType = 7
        )
    }
}

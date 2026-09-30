package com.example.data.api

import android.util.Log
import com.example.data.model.CategorySection
import com.example.data.model.HeroBanner
import com.example.data.model.HomeFeedData
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import com.example.data.model.VskitEpisodeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object VskitShortsApiClient {
    private const val TAG = "VskitShortsApiClient"
    private const val BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private const val SITE_DOMAIN = "https://vskit.online"
    private const val SITE_TYPE = "VskitWeb"
    private const val AUTH_BEARER =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjc5MTY5NjY1MDE2OTIyMDQ4NDgsImF0cCI6MywiZXh0IjoiMTc4OTAxNDE4OSIsImV4cCI6MTc5Njc5MDE4OSwiaWF0IjoxNzg5MDEzODg5fQ.9-OmrTY-your3sdzcSvH-YjFc-WgmhJL7YmtiaDY42k"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("authorization", AUTH_BEARER)
            .addHeader("X-Site-Domain", SITE_DOMAIN)
            .addHeader("X-Site-Type", SITE_TYPE)
            .addHeader("X-PM-Level", "3")
            .addHeader("X-PM-Active", "true")
            .addHeader("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}")
            .addHeader("x-request-lang", "en")
            .addHeader("origin", "https://movieboxph.org")
            .addHeader("referer", "https://movieboxph.org/quick-shorts")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json, text/plain, */*")
            .build()
    }

    private const val FM_OPERATING_URL = "https://api.fmonline.app/wefeed-fm-bff/shorts/operating?tabId=3&version="
    private const val FM_USER_AGENT =
        "com.funbase.xradio/51000002 (Linux; U; Android 14; en_IN; Infinix X6711; Build/UP1A.231005.007; Cronet/151.0.7922.83)"
    private const val FM_CLIENT_INFO =
        "{\"package_name\":\"com.funbase.xradio\",\"version_name\":\"5.2.03.0911.01\",\"version_code\":51000002,\"os\":\"android\",\"os_version\":\"14\",\"install_ch\":\"google-play\",\"device_id\":\"07c89fc911f2d7013e746f1a5c9cb2b8\",\"install_store\":\"gp\",\"gaid\":\"dfa2b45c-0134-49be-87b0-47203e73a707\",\"brand\":\"Infinix\",\"model\":\"Infinix X6711\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"405872\",\"X-Client-Vpn\":\"true\"}"
    private const val FM_TR_SIGNATURE = "1790279918871|2|GKLc1w8s/tf4UVqiUHsozA=="
    private const val FM_AUTH_BEARER =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjY2MjE1MzIxNTUwMDcwOTI5ODQsImV4cCI6MTc5MjA4NTQyNywiaWF0IjoxNzg0MzA5MTI3fQ.tnk6Dd4Zf0AGywV95jtSEblaRusY7Hm2XY9btBpmGBg"

    private fun buildFmRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("User-Agent", FM_USER_AGENT)
            .addHeader("x-client-vpn", "true")
            .addHeader("x-client-info", FM_CLIENT_INFO)
            .addHeader("x-client-status", "1")
            .addHeader("x-tr-signature", FM_TR_SIGNATURE)
            .addHeader("authorization", FM_AUTH_BEARER)
            .addHeader("priority", "u=1, i")
            .addHeader("Accept", "application/json, text/plain, */*")
            .build()
    }

    private fun buildFmOperatingRequest(): Request {
        return buildFmRequest(FM_OPERATING_URL)
    }

    /**
     * Primary VSKit Homepage API using wefeed-fm-bff/shorts/operating
     * Excludes only "All Quick Shorts { scroll load }" which is handled separately
     */
    suspend fun fetchShortsTabOperations(): HomeFeedData = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.newCall(buildFmOperatingRequest()).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                val root = JSONObject(body)
                val dataObj = root.optJSONObject("data")
                val itemsArray = dataObj?.optJSONArray("items")
                if (itemsArray != null && itemsArray.length() > 0) {
                    val heroBanners = mutableListOf<HeroBanner>()
                    val sections = mutableListOf<CategorySection>()

                    for (i in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.optJSONObject(i) ?: continue
                        val itemType = itemObj.optString("type", "").uppercase()
                        val itemTitle = itemObj.optString("title", "")
                        val opId = itemObj.optString("opId", "$i")

                        if (itemType == "BANNER") {
                            val bannerContainer = itemObj.optJSONObject("banner")
                            val bannersList = bannerContainer?.optJSONArray("banners")
                            if (bannersList != null) {
                                for (b in 0 until bannersList.length()) {
                                    val bObj = bannersList.optJSONObject(b) ?: continue
                                    val imgUrl = bObj.optJSONObject("image")?.optString("url", "") ?: ""
                                    val subjectObj = bObj.optJSONObject("subject")
                                    val subId = subjectObj?.optString("subjectId", "$b") ?: "$b"
                                    val subTitle = subjectObj?.optString("title")
                                        ?.ifBlank { bObj.optString("title") }
                                        ?: "Featured Drama"
                                    val genre = subjectObj?.optString("genre", "Short Drama") ?: "Short Drama"
                                    val coverUrl = subjectObj?.optJSONObject("cover")?.optString("url", "")
                                        ?.ifBlank { imgUrl } ?: imgUrl
                                    val detailUrl = subjectObj?.optString("detailUrl", "") ?: ""

                                    if (imgUrl.isNotBlank() || coverUrl.isNotBlank()) {
                                        heroBanners.add(
                                            HeroBanner(
                                                id = subId,
                                                title = subTitle,
                                                description = genre,
                                                backdropUrl = imgUrl.ifBlank { coverUrl },
                                                posterUrl = coverUrl.ifBlank { imgUrl },
                                                subjectId = subId,
                                                corner = "Shorts",
                                                genre = genre,
                                                detailPath = detailUrl,
                                                isSeries = true
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (itemType == "PLAY_LIST" || itemObj.has("subjects")) {
                            val subjectsArray = itemObj.optJSONArray("subjects") ?: JSONArray()
                            val movies = mutableListOf<MovieItem>()
                            for (s in 0 until subjectsArray.length()) {
                                val sObj = subjectsArray.optJSONObject(s) ?: continue
                                val subId = sObj.optString("subjectId", "")
                                val title = sObj.optString("title", "Drama")
                                val desc = sObj.optString("description", "")
                                val coverObj = sObj.optJSONObject("cover")
                                val coverUrl = coverObj?.optString("url", "") ?: ""
                                val coverWidth = coverObj?.optInt("width", 540) ?: 540
                                val coverHeight = coverObj?.optInt("height", 720) ?: 720
                                val genre = sObj.optString("genre", "Drama")
                                val corner = sObj.optString("corner")
                                    .ifBlank { sObj.optString("tag", "") }
                                val detailUrl = sObj.optString("detailUrl", "")
                                val rating = sObj.optString("imdbRatingValue")
                                    .ifBlank { sObj.optString("imdbRate", "") }
                                val country = sObj.optString("countryName", "Short Drama")
                                val releaseDate = sObj.optString("releaseDate", "")

                                if (subId.isNotBlank() && title.isNotBlank()) {
                                    movies.add(
                                        MovieItem(
                                            id = subId,
                                            title = title,
                                            description = desc,
                                            coverUrl = coverUrl,
                                            backdropUrl = coverUrl,
                                            rating = rating,
                                            ratingCount = 0,
                                            releaseDate = releaseDate,
                                            genre = genre,
                                            country = country,
                                            corner = corner.ifBlank { "Shorts" },
                                            duration = "",
                                            isShort = true,
                                            detailPath = detailUrl,
                                            source = "vskit",
                                            uploadBy = "ShortsTV",
                                            customHeaders = mapOf(
                                                "origin" to "https://movieboxph.org",
                                                "referer" to "https://movieboxph.org/quick-shorts"
                                            ),
                                            coverWidth = coverWidth,
                                            coverHeight = coverHeight,
                                            isVskitServer = true,
                                            subjectType = 7
                                        )
                                    )
                                }
                            }

                            if (movies.isNotEmpty()) {
                                sections.add(
                                    CategorySection(
                                        id = "vskit_$opId",
                                        title = itemTitle.ifBlank { "Curated Shorts" },
                                        type = "VSKIT_SHORTS",
                                        items = movies,
                                        opId = opId,
                                        isVskitSection = true
                                    )
                                )
                            }
                        }
                    }

                    // If hero banners were empty from banner item, take top items from first section
                    if (heroBanners.isEmpty() && sections.isNotEmpty()) {
                        sections.firstOrNull()?.items?.take(5)?.forEach { m ->
                            heroBanners.add(
                                HeroBanner(
                                    id = m.id,
                                    title = m.title,
                                    description = m.description,
                                    backdropUrl = m.coverUrl,
                                    posterUrl = m.coverUrl,
                                    subjectId = m.id,
                                    corner = m.corner,
                                    genre = m.genre,
                                    detailPath = m.detailPath,
                                    isSeries = true
                                )
                            )
                        }
                    }

                    if (heroBanners.isNotEmpty() || sections.isNotEmpty()) {
                        return@withContext HomeFeedData(
                            heroBanners = heroBanners,
                            platforms = emptyList(),
                            sections = sections
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchOperatingHomepage: ${e.message}", e)
        }

        // Resilient Fallback to legacy tab operations
        fetchShortsTabOperationsLegacy()
    }

    /**
     * Fallback legacy tab operations endpoint
     */
    private suspend fun fetchShortsTabOperationsLegacy(): HomeFeedData = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/vskit/tab-operation-list"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext emptyFeedData()
            if (!response.isSuccessful) {
                Log.e(TAG, "fetchShortsTabOperations HTTP ${response.code}")
                return@withContext emptyFeedData()
            }

            val root = JSONObject(body)
            if (root.optInt("code", -1) != 0) {
                return@withContext emptyFeedData()
            }

            val dataObj = root.optJSONObject("data") ?: return@withContext emptyFeedData()
            val listArray = dataObj.optJSONArray("list") ?: return@withContext emptyFeedData()

            val heroBanners = mutableListOf<HeroBanner>()
            val sections = mutableListOf<CategorySection>()

            for (i in 0 until listArray.length()) {
                val secObj = listArray.optJSONObject(i) ?: continue
                val opConfId = secObj.optString("opConfId", "$i")
                val title = secObj.optString("title", "Shorts Section $i")
                val opSeoKey = secObj.optString("opSeoKey", "")
                val novelItemsArray = secObj.optJSONArray("novelItems") ?: JSONArray()

                val items = mutableListOf<MovieItem>()
                for (j in 0 until novelItemsArray.length()) {
                    val itObj = novelItemsArray.optJSONObject(j) ?: continue
                    val movie = parseNovelItem(itObj)
                    items.add(movie)
                }

                // If first section has items, extract top items as Hero Banners for Shorts TV
                if (i == 0 && items.isNotEmpty()) {
                    items.take(5).forEachIndexed { bIdx, bMovie ->
                        heroBanners.add(
                            HeroBanner(
                                id = bMovie.id,
                                title = bMovie.title,
                                description = bMovie.description,
                                backdropUrl = bMovie.coverUrl,
                                posterUrl = bMovie.coverUrl,
                                subjectId = bMovie.id,
                                corner = bMovie.corner,
                                genre = bMovie.genre,
                                detailPath = bMovie.detailPath,
                                isSeries = true
                            )
                        )
                    }
                }

                sections.add(
                    CategorySection(
                        id = "vskit_$opConfId",
                        title = title,
                        type = "VSKIT_SHORTS",
                        items = items,
                        opId = opSeoKey,
                        isVskitSection = true
                    )
                )
            }

            HomeFeedData(
                heroBanners = heroBanners,
                platforms = emptyList(),
                sections = sections
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchShortsTabOperations: ${e.message}", e)
            emptyFeedData()
        }
    }

    /**
     * Fetch recommended shorts list ("Find Your Gem")
     */
    suspend fun fetchShortsRecommendList(
        page: Int = 1,
        perPage: Int = 20
    ): Pair<List<MovieItem>, Boolean> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/vskit/recommend-list?page=$page&perPage=$perPage&novelType=3"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext Pair(emptyList(), false)
            if (!response.isSuccessful) {
                return@withContext Pair(emptyList(), false)
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), false)
            val listArr = dataObj.optJSONArray("list") ?: JSONArray()
            val pagerObj = dataObj.optJSONObject("pager")
            val hasMore = pagerObj?.optBoolean("hasMore", false) ?: false

            val items = mutableListOf<MovieItem>()
            for (i in 0 until listArr.length()) {
                val itObj = listArr.optJSONObject(i) ?: continue
                items.add(parseNovelItem(itObj))
            }
            Pair(items, hasMore)
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchShortsRecommendList: ${e.message}", e)
            Pair(emptyList(), false)
        }
    }

    /**
     * Fetch all episodes with direct video streaming URLs for a Shorts TV subject.
     * Paginates through all pages so every single episode is unlocked and loaded.
     */
    suspend fun fetchShortsEpisodes(subjectId: String): List<VskitEpisodeItem> = withContext(Dispatchers.IO) {
        val cleanId = subjectId.trim()
        if (cleanId.isBlank()) return@withContext emptyList()

        val episodes = mutableListOf<VskitEpisodeItem>()
        val maxPages = 50

        // 1. Primary: Use FM Online API (wefeed-fm-bff/shorts/mini-list)
        try {
            var page = 1
            var hasMore = true
            while (hasMore && page <= maxPages) {
                val url = "https://api.fmonline.app/wefeed-fm-bff/shorts/mini-list?subjectId=$cleanId&page=$page&perPage=50"
                val response = httpClient.newCall(buildFmRequest(url)).execute()
                val body = response.body?.string() ?: break
                if (!response.isSuccessful) break

                val root = JSONObject(body)
                val dataObj = root.optJSONObject("data") ?: break
                val itemsArr = dataObj.optJSONArray("items") ?: JSONArray()
                val pagerObj = dataObj.optJSONObject("pager")
                hasMore = pagerObj?.optBoolean("hasMore", false) ?: false

                for (i in 0 until itemsArr.length()) {
                    val itObj = itemsArr.optJSONObject(i) ?: continue
                    parseEpisodeItem(itObj, episodes.size + 1)?.let { episodes.add(it) }
                }

                if (itemsArr.length() == 0) break
                page++
            }
            if (episodes.isNotEmpty()) {
                return@withContext episodes.distinctBy { it.ep }.sortedBy { it.ep }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FM mini-list error for $cleanId: ${e.message}")
        }

        // 2. Fallback: Use aoneroom API (wefeed-h5api-bff/vskit/shorts/mini-list)
        try {
            var page = 1
            var hasMore = true
            while (hasMore && page <= maxPages) {
                val url = "$BASE_URL/vskit/shorts/mini-list?subjectId=$cleanId&page=$page&perPage=50"
                val response = httpClient.newCall(buildRequest(url)).execute()
                val body = response.body?.string() ?: break
                if (!response.isSuccessful) break

                val root = JSONObject(body)
                val dataObj = root.optJSONObject("data") ?: break
                val itemsArr = dataObj.optJSONArray("items") ?: JSONArray()
                val pagerObj = dataObj.optJSONObject("pager")
                hasMore = pagerObj?.optBoolean("hasMore", false) ?: false

                for (i in 0 until itemsArr.length()) {
                    val itObj = itemsArr.optJSONObject(i) ?: continue
                    parseEpisodeItem(itObj, episodes.size + 1)?.let { episodes.add(it) }
                }

                if (itemsArr.length() == 0) break
                page++
            }
            episodes.distinctBy { it.ep }.sortedBy { it.ep }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchShortsEpisodes: ${e.message}", e)
            episodes.distinctBy { it.ep }.sortedBy { it.ep }
        }
    }

    private fun parseEpisodeItem(itObj: JSONObject, fallbackEp: Int): VskitEpisodeItem? {
        val ep = itObj.optInt("ep", fallbackEp)
        val miniId = itObj.optString("miniId", "")
        val videoObj = itObj.optJSONObject("video")
        val videoAddress = videoObj?.optJSONObject("videoAddress")
        val videoUrl = videoAddress?.optString("url", "")
            ?: videoObj?.optString("url", "")
            ?: ""
        val duration = videoAddress?.optInt("duration", 0) ?: 0
        val resolutions = videoAddress?.optString("resolutions", "720")
            ?.ifBlank { videoAddress.optString("resolution", "720") } ?: "720"
        val coverObj = itObj.optJSONObject("cover") ?: videoObj?.optJSONObject("cover")
        val coverUrl = coverObj?.optString("url", "") ?: ""

        val episodeStreams = mutableListOf<MovieStream>()
        val streamArrays = listOfNotNull(
            videoObj?.optJSONArray("addressList"),
            videoObj?.optJSONArray("videoAddressList"),
            videoObj?.optJSONArray("videoAddresses"),
            videoObj?.optJSONArray("streams"),
            itObj.optJSONArray("addressList"),
            itObj.optJSONArray("videoAddressList"),
            itObj.optJSONArray("streams")
        )
        for (arr in streamArrays) {
            for (s in 0 until arr.length()) {
                val sObj = arr.optJSONObject(s) ?: continue
                val sUrl = sObj.optString("url", "")
                if (sUrl.isNotBlank()) {
                    val resRaw = sObj.optString("resolution")
                        .ifBlank { sObj.optString("resolutions") }
                        .ifBlank { sObj.optInt("resolution", 0).takeIf { it > 0 }?.toString() ?: "720" }
                    val cleanRes = resRaw.split(",").firstOrNull()?.filter { it.isDigit() }?.ifBlank { "720" } ?: "720"
                    episodeStreams.add(
                        MovieStream(
                            id = "vskit_ep${ep}_stream_$s",
                            resolution = cleanRes,
                            format = sObj.optString("format", if (sUrl.contains(".m3u8")) "HLS" else "MP4"),
                            url = sUrl
                        )
                    )
                }
            }
        }

        if (videoUrl.isNotBlank()) {
            val cleanMainRes = resolutions.split(",").firstOrNull()?.filter { it.isDigit() }?.ifBlank { "720" } ?: "720"
            if (episodeStreams.none { it.url == videoUrl }) {
                episodeStreams.add(
                    MovieStream(
                        id = "vskit_ep${ep}_main",
                        resolution = cleanMainRes,
                        format = if (videoUrl.contains(".m3u8")) "HLS" else "MP4",
                        url = videoUrl
                    )
                )
            }

            return VskitEpisodeItem(
                ep = ep,
                miniId = miniId,
                videoUrl = videoUrl,
                coverUrl = coverUrl,
                durationSec = duration,
                resolution = resolutions,
                title = "Episode $ep",
                streams = episodeStreams.sortedByDescending { s ->
                    s.resolution.filter { it.isDigit() }.toIntOrNull() ?: 0
                }
            )
        }
        return null
    }

    /**
     * Search short dramas on Server 2
     */
    suspend fun searchShorts(keyword: String, page: Int = 1, perPage: Int = 20): List<MovieItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$BASE_URL/vskit/search?keyword=$encoded&page=$page&perPage=$perPage"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val listArr = dataObj.optJSONArray("list") ?: JSONArray()

            val results = mutableListOf<MovieItem>()
            for (i in 0 until listArr.length()) {
                val itObj = listArr.optJSONObject(i) ?: continue
                results.add(parseNovelItem(itObj))
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchShorts: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Search suggestion for Shorts TV
     */
    suspend fun fetchSearchSuggestions(keyword: String): List<String> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val items = searchShorts(keyword, page = 1, perPage = 20)
        items.map { it.title }.filter { it.isNotBlank() }.distinct().take(10)
    }

    /**
     * Everyone is searching recommendations on Server 2
     */
    suspend fun fetchEveryoneSearch(): List<MovieItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/vskit/everyonesearch"
        try {
            val response = httpClient.newCall(buildRequest(url)).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val listArr = dataObj.optJSONArray("recommendList") ?: JSONArray()

            val results = mutableListOf<MovieItem>()
            for (i in 0 until listArr.length()) {
                val itObj = listArr.optJSONObject(i) ?: continue
                results.add(parseNovelItem(itObj))
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchEveryoneSearch: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseNovelItem(itObj: JSONObject): MovieItem {
        val subjectId = itObj.optString("subjectId", "")
        val title = itObj.optString("title", "Short Drama")
        val description = itObj.optString("description", "")
        val totalEp = itObj.optInt("totalEpisode", 0)
        val subjectSeoKey = itObj.optString("subjectSeoKey", "")
        val coverObj = itObj.optJSONObject("cover")
        val coverUrl = coverObj?.optString("url", "") ?: ""
        val coverWidth = coverObj?.optInt("width", 540) ?: 540
        val coverHeight = coverObj?.optInt("height", 960) ?: 960

        val tagsArr = itObj.optJSONArray("tags")
        val tagsList = mutableListOf<String>()
        if (tagsArr != null) {
            for (t in 0 until tagsArr.length()) {
                val tag = tagsArr.optString(t, "")
                if (tag.isNotBlank()) tagsList.add(tag)
            }
        }
        val genreString = tagsList.joinToString(" • ")

        return MovieItem(
            id = subjectId,
            title = title,
            description = description,
            coverUrl = coverUrl,
            backdropUrl = coverUrl,
            rating = "",
            ratingCount = 0,
            releaseDate = "",
            releaseYear = "",
            genre = genreString,
            country = "Short Drama",
            corner = if (totalEp > 0) "$totalEp EP" else "Shorts",
            duration = "",
            isShort = true,
            detailPath = subjectSeoKey,
            directUrl = "",
            source = "vskit",
            uploadBy = "ShortsTV",
            customHeaders = mapOf(
                "X-Site-Domain" to SITE_DOMAIN,
                "X-Site-Type" to SITE_TYPE
            ),
            dubs = emptyList(),
            coverWidth = coverWidth,
            coverHeight = coverHeight,
            totalEpisodes = totalEp,
            isVskitServer = true,
            subjectType = 7
        )
    }

    /**
     * Fetch filter shorts dramas from wefeed-h5api-bff/filter (channelId=1012)
     * Supports infinite progressive pagination
     */
    suspend fun fetchFilterShortsList(
        page: Int = 1,
        perPage: Int = 24,
        channelId: Int = 1012
    ): Pair<List<MovieItem>, Boolean> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/filter?page=$page&perPage=$perPage&channelId=$channelId"
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("authorization", AUTH_BEARER)
                .addHeader("origin", "https://movieboxph.org")
                .addHeader("referer", "https://movieboxph.org/quick-shorts")
                .addHeader("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}")
                .addHeader("x-request-lang", "en")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json, text/plain, */*")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Pair(emptyList(), false)
            if (!response.isSuccessful) {
                Log.e(TAG, "fetchFilterShortsList HTTP ${response.code}")
                return@withContext Pair(emptyList(), false)
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), false)
            val subjectListObj = dataObj.optJSONObject("subjectList") ?: return@withContext Pair(emptyList(), false)
            val itemsArr = subjectListObj.optJSONArray("items") ?: JSONArray()
            val pagerObj = subjectListObj.optJSONObject("pager")
            val hasMore = pagerObj?.optBoolean("hasMore", false) ?: false

            val items = mutableListOf<MovieItem>()
            for (i in 0 until itemsArr.length()) {
                val itObj = itemsArr.optJSONObject(i) ?: continue
                items.add(parseFilterSubjectItem(itObj))
            }
            Pair(items, hasMore)
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchFilterShortsList: ${e.message}", e)
            Pair(emptyList(), false)
        }
    }

    private fun parseFilterSubjectItem(itObj: JSONObject): MovieItem {
        val subjectId = itObj.optString("subjectId", "")
        val title = itObj.optString("title", "Short Drama")
        val description = itObj.optString("description", "")
        val coverObj = itObj.optJSONObject("cover")
        val coverUrl = coverObj?.optString("url", "") ?: ""
        val coverWidth = coverObj?.optInt("width", 540) ?: 540
        val coverHeight = coverObj?.optInt("height", 720) ?: 720
        val genre = itObj.optString("genre", "Modern Drama")
        val corner = itObj.optString("corner", "English")
        val detailPath = itObj.optString("detailPath", "")
        val rating = itObj.optString("imdbRatingValue", "")
        val countryName = itObj.optString("countryName", "Short Drama")
        val releaseDate = itObj.optString("releaseDate", "")

        return MovieItem(
            id = subjectId,
            title = title,
            description = description,
            coverUrl = coverUrl,
            backdropUrl = coverUrl,
            rating = rating,
            ratingCount = 0,
            releaseDate = releaseDate,
            genre = genre,
            country = countryName,
            corner = corner.ifBlank { "Shorts" },
            duration = "",
            isShort = true,
            detailPath = detailPath,
            source = "vskit",
            uploadBy = "ShortsTV",
            customHeaders = mapOf(
                "origin" to "https://movieboxph.org",
                "referer" to "https://movieboxph.org/quick-shorts"
            ),
            coverWidth = coverWidth,
            coverHeight = coverHeight,
            isVskitServer = true,
            subjectType = 7
        )
    }

    private fun emptyFeedData(): HomeFeedData {
        return HomeFeedData(
            heroBanners = emptyList(),
            platforms = emptyList(),
            sections = emptyList()
        )
    }
}

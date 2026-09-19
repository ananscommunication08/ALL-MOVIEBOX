package com.example.data.api

import android.util.Log
import com.example.data.model.DubLanguage
import com.example.data.model.MovieItem
import com.example.data.model.MovieStream
import com.example.data.model.SeasonInfo
import com.example.data.model.SubjectDetailResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LookrSubTag(
    val tagName: String,
    val tagDisplayName: String,
    val opId: String
)

data class LookrCategory(
    val tagName: String,
    val tagDisplayName: String,
    val order: Int,
    val subTags: List<LookrSubTag>
)

object LookrApiClient {
    private const val TAG = "LookrApiClient"
    private const val RECOMMEND_BASE_URL = "https://api.lookr.cloud/wefeed-gogo-bff/recommend"
    private const val H5_PLAY_BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play"
    private const val H5_DETAIL_BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/detail"

    private const val AUTH_BEARER =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjU2NTI1NDEwNzg2NjMyMTg4MDAsImV4cCI6MTc5NzA0OTI1NiwiaWF0IjoxNzg5MjcyOTU2fQ.9lDW-RX6eRIdW0GYGIn-Q9HvHRASck7WC8SlTJC0yE0"

    private const val CLIENT_INFO =
        "{\"package_name\":\"com.wecloud.lookr\",\"version_name\":\"3.0.16.0706.03\",\"version_code\":316,\"os\":\"android\",\"os_version\":\"14\",\"install_ch\":\"google-play\",\"device_id\":\"45fc9f58647307f975f5c2e70048a08e\",\"install_store\":\"ps\",\"gaid\":\"ad74fab5dc9bf1f9\",\"brand\":\"Infinix\",\"model\":\"Infinix X6711\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"405872\"}"

    val CATEGORIES = listOf(
        LookrCategory(
            tagName = "Movies",
            tagDisplayName = "Movies",
            order = 1,
            subTags = listOf(
                LookrSubTag("Popular Movies", "Popular Movies", "0"),
                LookrSubTag("Hollywood Movies", "Hollywood Movies", "4674513975244114264"),
                LookrSubTag("Bollywood Movies", "Bollywood Movies", "6017853301595819040"),
                LookrSubTag("For you", "For you", "0")
            )
        ),
        LookrCategory(
            tagName = "Series",
            tagDisplayName = "Series",
            order = 2,
            subTags = listOf(
                LookrSubTag("Trending drama", "Trending drama", "0"),
                LookrSubTag("Indian Drama", "Indian Drama", "933386079317675808"),
                LookrSubTag("Western Drama", "Western Drama", "8398270291826328248"),
                LookrSubTag("Korean Drama", "Korean Drama", "4736632688541585768"),
                LookrSubTag("For you", "For you", "0")
            )
        ),
        LookrCategory(
            tagName = "Anime",
            tagDisplayName = "Anime",
            order = 3,
            subTags = listOf(
                LookrSubTag("Trending Anime", "Trending Anime", "9019462595941650792"),
                LookrSubTag("Burning with Ardour", "Burning with Ardour", "4503689175828416400"),
                LookrSubTag("Adventurers' Saga", "Adventurers' Saga", "3134335833860375240"),
                LookrSubTag("For you", "For you", "0")
            )
        ),
        LookrCategory(
            tagName = "Short TV",
            tagDisplayName = "Short TV",
            order = 4,
            subTags = emptyList()
        )
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Common video headers required to stream Lookr MP4/HLS media from CDN
     */
    val LOOKR_VIDEO_HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "okhttp/4.12.0",
        "Referer" to "https://fzmovienow.top/",
        "Origin" to "https://fzmovienow.top",
        "x-source" to "lookr"
    )

    /**
     * Fetch recommend media items for a given tag, subTag, and opId
     */
    suspend fun fetchRecommend(
        tag: String,
        subTag: String = "",
        opId: String = "0",
        page: Int = 1,
        pageSize: Int = 10
    ): Pair<List<MovieItem>, Boolean> = withContext(Dispatchers.IO) {
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        val effectiveSubTag = if (tag.equals("Short TV", ignoreCase = true)) "" else subTag
        val encodedSubTag = if (effectiveSubTag.isNotBlank()) URLEncoder.encode(effectiveSubTag, "UTF-8") else ""
        val effectiveOpId = if (opId.isNotBlank()) opId else "0"
        val url = "$RECOMMEND_BASE_URL?tag=$encodedTag&subTag=$encodedSubTag&opId=$effectiveOpId&page=$page&pageSize=$pageSize"
        val timestamp = System.currentTimeMillis()

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .addHeader("x-client-info", CLIENT_INFO)
            .addHeader("x-client-status", "0")
            .addHeader("x-tr-signature", "$timestamp|2|")
            .addHeader("authorization", AUTH_BEARER)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                Log.w(TAG, "Recommend response failed code=${response.code}")
                return@withContext Pair(emptyList(), false)
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext Pair(emptyList(), false)
            val itemsArray = dataObj.optJSONArray("items") ?: dataObj.optJSONArray("movieItems") ?: return@withContext Pair(emptyList(), false)

            val results = mutableListOf<MovieItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { obj.optString("subjectId") }
                val name = obj.optString("name").ifBlank { obj.optString("title") }
                val describe = obj.optString("describe").ifBlank { obj.optString("description") }
                val cover = obj.optString("cover").ifBlank {
                    obj.optJSONObject("cover")?.optString("url").orEmpty()
                }
                val tagStr = obj.optString("tag") // e.g. "2024 · Japan · Anime"
                val itemUrl = obj.optString("itemUrl")
                val classification = obj.optString("classification")
                val subjectType = obj.optString("subjectType")
                val rawDetailPath = obj.optString("detailPath")
                val rawCorner = obj.optString("corner")

                val detailPath = rawDetailPath.ifBlank {
                    if (itemUrl.isNotBlank()) {
                        Regex("""[?&]detailPath=([^&]+)""").find(itemUrl)?.groupValues?.get(1)?.let {
                            try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                        }.orEmpty()
                    } else ""
                }

                val effectiveId = id.ifBlank {
                    if (itemUrl.isNotBlank()) {
                        Regex("""[?&]subjectId=([^&]+)""").find(itemUrl)?.groupValues?.get(1).orEmpty()
                    } else ""
                }

                val isShortTv = tag.equals("Short TV", ignoreCase = true) || classification.equals("Short TV", ignoreCase = true)

                // Extract corner episodes if present
                val cornerEp = Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").find(rawCorner)?.groupValues?.get(1)?.toIntOrNull()
                    ?: obj.optInt("episodeCount", 0).takeIf { it > 0 }
                    ?: obj.optInt("maxEp", 0).takeIf { it > 0 }
                    ?: 0

                val rawSubjectType = obj.optInt("subjectType", 0).takeIf { it > 0 }
                    ?: (if (subjectType.equals("Series", ignoreCase = true)) 2 else if (subjectType.equals("Movie", ignoreCase = true)) 1 else 0)

                val effectiveSubjectType = when {
                    rawSubjectType > 0 -> rawSubjectType
                    isShortTv -> 7
                    tag.contains("Music", ignoreCase = true) || classification.contains("Music", ignoreCase = true) -> 6
                    tag.contains("Kids", ignoreCase = true) || classification.contains("Kids", ignoreCase = true) -> 5
                    tag.contains("Sport", ignoreCase = true) || classification.contains("Sport", ignoreCase = true) -> 9
                    cornerEp > 1 || rawCorner.contains("Season", ignoreCase = true) || rawCorner.contains("Series", ignoreCase = true) -> 2
                    else -> 1
                }

                val isSeries = when {
                    effectiveSubjectType == 6 -> false // Music Video Song { no episode and season }
                    effectiveSubjectType == 1 -> false
                    effectiveSubjectType == 2 || effectiveSubjectType == 7 -> true
                    cornerEp > 1 -> true
                    rawCorner.contains("Season", ignoreCase = true) || rawCorner.contains("Series", ignoreCase = true) -> true
                    Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").containsMatchIn(rawCorner) -> true
                    name.contains("Season ", ignoreCase = true) -> true
                    isShortTv -> true
                    else -> false
                }

                // Extract release year from tag string
                val parts = tagStr.split("·")
                val year = parts.firstOrNull()?.trim().orEmpty()
                val country = if (parts.size > 1) parts[1].trim() else ""
                val genre = if (parts.size > 2) parts[2].trim() else ""

                results.add(
                    MovieItem(
                        id = effectiveId,
                        title = name,
                        description = describe,
                        coverUrl = cover,
                        releaseYear = year,
                        country = country,
                        genre = genre.ifBlank { tagStr },
                        directUrl = itemUrl,
                        detailPath = detailPath,
                        source = "lookr",
                        isShort = isShortTv || effectiveSubjectType == 7,
                        totalEpisodes = cornerEp,
                        isSeries = isSeries,
                        corner = rawCorner.ifBlank { if (isSeries) "Series" else "" },
                        customHeaders = LOOKR_VIDEO_HEADERS,
                        subjectType = effectiveSubjectType
                    )
                )
            }

            val pager = dataObj.optJSONObject("pager")
            val hasMore = pager?.optBoolean("hasMore", false) ?: (results.size >= pageSize)
            Pair(results, hasMore)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recommend for tag=$tag subTag=$subTag", e)
            Pair(emptyList(), false)
        }
    }

    /**
     * Fetch full subject details (episodes, seasons, type, dubs, metadata) from Lookr detail API:
     * GET /wefeed-h5api-bff/detail?detailPath={detailPath}
     */
    suspend fun fetchSubjectDetail(
        detailPath: String = "",
        subjectId: String = ""
    ): SubjectDetailResult? = withContext(Dispatchers.IO) {
        if (detailPath.isBlank() && subjectId.isBlank()) return@withContext null

        fun executeDetailCall(dPath: String, sId: String): SubjectDetailResult? {
            val query = buildString {
                if (dPath.isNotBlank()) {
                    append("detailPath=").append(URLEncoder.encode(dPath, "UTF-8"))
                }
                if (sId.isNotBlank()) {
                    if (isNotEmpty()) append("&")
                    append("subjectId=").append(sId)
                }
            }

            val url = "$H5_DETAIL_BASE_URL?$query"
            val request = Request.Builder()
                .url(url)
                .addHeader("pragma", "no-cache")
                .addHeader("cache-control", "no-cache")
                .addHeader("sec-ch-ua-platform", "\"Android\"")
                .addHeader("authorization", AUTH_BEARER)
                .addHeader("sec-ch-ua", "\"Chromium\";v=\"154\", \"Android WebView\";v=\"154\", \"Not A(Brand\";v=\"99\"")
                .addHeader("sec-ch-ua-mobile", "?1")
                .addHeader("x-client-info", "{\"timezone\":\"Asia/Calcutta\"}")
                .addHeader("x-source", "lookr")
                .addHeader("user-agent", "Mozilla/5.0 (Linux; Android 14; Infinix X6711 Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/154.0.8037.0 Mobile Safari/537.36 lookr/3.0.16;")
                .addHeader("accept", "application/json")
                .addHeader("x-client-token", "1789287678,e6566463fe12ca178a4ed38a5fd6b689")
                .addHeader("x-request-lang", "en")
                .addHeader("origin", "https://netnaijafilm.run")
                .addHeader("x-requested-with", "com.wecloud.lookr")
                .addHeader("sec-fetch-site", "cross-site")
                .addHeader("sec-fetch-mode", "cors")
                .addHeader("sec-fetch-dest", "empty")
                .addHeader("referer", "https://netnaijafilm.run/")
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val bytes = response.body?.bytes() ?: return null
                if (!response.isSuccessful || bytes.isEmpty()) {
                    Log.w(TAG, "fetchSubjectDetail failed: code=${response.code} url=$url")
                    return null
                }

                val body = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    java.util.zip.GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    String(bytes, Charsets.UTF_8)
                }

                val root = JSONObject(body)
                val dataObj = root.optJSONObject("data") ?: return null
                val subjObj = dataObj.optJSONObject("subject") ?: JSONObject()
                val resourceObj = dataObj.optJSONObject("resource") ?: JSONObject()

                val realSubjectId = subjObj.optString("subjectId", sId.ifBlank { subjectId })
                val realDetailPath = subjObj.optString("detailPath", dPath.ifBlank { detailPath })
                val title = subjObj.optString("title", "")
                val description = subjObj.optString("description", "")
                val subjectType = subjObj.optInt("subjectType", 1) // 1 = Movie, 2 = Series
                val releaseDate = subjObj.optString("releaseDate", "")
                val releaseYear = releaseDate.take(4)
                val durationSeconds = subjObj.optLong("duration", 0L)
                val durationFormatted = if (durationSeconds > 0) "${durationSeconds / 60}m" else ""
                val genre = subjObj.optString("genre", "")
                val country = subjObj.optString("countryName", "")
                val imdbRating = subjObj.optString("imdbRatingValue", "")
                val imdbRatingCount = subjObj.optInt("imdbRatingCount", 0)
                val subtitles = subjObj.optString("subtitles", "")
                val corner = subjObj.optString("corner", "")

                val coverObj = subjObj.optJSONObject("cover")
                val coverUrl = coverObj?.optString("url", "") ?: ""

                val stillsObj = subjObj.optJSONObject("stills")
                val backdropUrl = stillsObj?.optString("url", "") ?: coverUrl

                val trailerObj = subjObj.optJSONObject("trailer")
                val trailerVideo = trailerObj?.optJSONObject("videoAddress")
                val trailerUrl = trailerVideo?.optString("url", "") ?: ""

                // Parse Dubs
                val dubsList = mutableListOf<DubLanguage>()
                val dubsArray = subjObj.optJSONArray("dubs")
                if (dubsArray != null) {
                    for (i in 0 until dubsArray.length()) {
                        val dObj = dubsArray.optJSONObject(i) ?: continue
                        dubsList.add(
                            DubLanguage(
                                subjectId = dObj.optString("subjectId", ""),
                                lanName = dObj.optString("lanName", ""),
                                lanCode = dObj.optString("lanCode", ""),
                                original = dObj.optBoolean("original", false),
                                type = dObj.optInt("type", 0),
                                detailPath = dObj.optString("detailPath", "")
                            )
                        )
                    }
                }

                // Parse Seasons & Episodes
                val seasonsList = mutableListOf<SeasonInfo>()
                // Parse seasons array from resource.seasons
                val seasonsArray = resourceObj.optJSONArray("seasons")
                val rawSeasonsList = mutableListOf<SeasonInfo>()
                var hasExplicitMultiEpisodesOrSeasons = false

                if (seasonsArray != null) {
                    for (i in 0 until seasonsArray.length()) {
                        val sObj = seasonsArray.optJSONObject(i) ?: continue
                        val seNum = sObj.optInt("se", 0)
                        val maxEp = sObj.optInt("maxEp", 0)
                        val resolutions = mutableListOf<Int>()
                        val resArr = sObj.optJSONArray("resolutions")
                        if (resArr != null) {
                            for (r in 0 until resArr.length()) {
                                val rObj = resArr.optJSONObject(r)
                                val resVal = rObj?.optInt("resolution", 0) ?: 0
                                if (resVal > 0) resolutions.add(resVal)
                            }
                        }
                        if (seNum > 0 || maxEp > 1) {
                            hasExplicitMultiEpisodesOrSeasons = true
                            rawSeasonsList.add(
                                SeasonInfo(
                                    seasonNumber = if (seNum > 0) seNum else 1,
                                    maxEp = if (maxEp > 0) maxEp else 1,
                                    resolutions = resolutions
                                )
                            )
                        }
                    }
                }

                val cornerHasEpisodes = Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").containsMatchIn(corner) ||
                    corner.contains("Season", ignoreCase = true) ||
                    corner.contains("Series", ignoreCase = true)

                val titleHasEpisodesOrSeason = Regex("""\b(Season\s*\d+|Episode\s*\d+|S\d{1,2}\s*E\d{1,2}|S\d{1,2})\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)

                val genreHasSeries = genre.contains("TV Series", ignoreCase = true) ||
                    genre.contains("TV Show", ignoreCase = true) ||
                    genre.contains("Drama Series", ignoreCase = true) ||
                    genre.contains("Anime Series", ignoreCase = true) ||
                    genre.contains("Mini-Series", ignoreCase = true) ||
                    genre.equals("Series", ignoreCase = true)

                val effectiveDetailSubjectType = when {
                    subjectType > 0 -> subjectType
                    corner.contains("Short", ignoreCase = true) -> 7
                    genre.contains("Music", ignoreCase = true) || genre.contains("Song", ignoreCase = true) -> 6
                    genre.contains("Kids", ignoreCase = true) || genre.contains("Cartoon", ignoreCase = true) || corner.contains("Kids", ignoreCase = true) -> 5
                    genre.contains("Sport", ignoreCase = true) || genre.contains("Wrestling", ignoreCase = true) || genre.contains("Live", ignoreCase = true) -> 9
                    genreHasSeries || cornerHasEpisodes || titleHasEpisodesOrSeason || hasExplicitMultiEpisodesOrSeasons -> 2
                    else -> 1
                }

                val isSeries = when {
                    effectiveDetailSubjectType == 6 -> false // Music Video Song { no episode and season }
                    effectiveDetailSubjectType == 1 -> false // EXPLICIT MOVIE!
                    effectiveDetailSubjectType == 2 || effectiveDetailSubjectType == 7 -> true
                    hasExplicitMultiEpisodesOrSeasons -> true
                    cornerHasEpisodes -> true
                    titleHasEpisodesOrSeason -> true
                    genreHasSeries -> true
                    else -> false
                }

                val finalSeasonsList = if (isSeries) {
                    if (rawSeasonsList.isNotEmpty()) {
                        rawSeasonsList.sortedBy { it.seasonNumber }
                    } else {
                        val fallbackCornerMaxEp = Regex("""\b(\d+)\s*(?:EP|ep|Episodes|Ep)\b""").find(corner)?.groupValues?.get(1)?.toIntOrNull()
                            ?: subjObj.optInt("episodeCount", 0).takeIf { it > 0 }
                            ?: subjObj.optInt("totalEpisodes", 0).takeIf { it > 0 }
                            ?: dataObj.optInt("episodeCount", 0).takeIf { it > 0 }
                            ?: resourceObj.optInt("maxEp", 0).takeIf { it > 0 }
                            ?: 12
                        listOf(
                            SeasonInfo(
                                seasonNumber = 1,
                                maxEp = fallbackCornerMaxEp,
                                resolutions = emptyList()
                            )
                        )
                    }
                } else {
                    emptyList()
                }

                return SubjectDetailResult(
                    subjectId = realSubjectId,
                    subjectType = effectiveDetailSubjectType,
                    title = title,
                    description = description,
                    coverUrl = coverUrl,
                    backdropUrl = backdropUrl,
                    releaseDate = releaseDate,
                    releaseYear = releaseYear,
                    genre = genre,
                    country = country,
                    rating = imdbRating,
                    ratingCount = imdbRatingCount,
                    durationFormatted = durationFormatted,
                    corner = corner,
                    subtitles = subtitles,
                    isSeries = isSeries,
                    totalEpisodes = if (isSeries) finalSeasonsList.sumOf { it.maxEp } else 0,
                    seasons = finalSeasonsList,
                    detailPath = realDetailPath,
                    hasResource = subjObj.optBoolean("hasResource", true),
                    trailerUrl = trailerUrl,
                    source = "lookr",
                    uploadBy = resourceObj.optString("uploadBy", "Lookr"),
                    dubs = dubsList,
                    rawJson = body
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Lookr detail for dPath=$dPath, sId=$sId", e)
                return null
            }
        }

        // Try primary query
        var result = executeDetailCall(detailPath, subjectId)
        if (result == null && detailPath.isNotBlank() && subjectId.isNotBlank()) {
            // Try with only subjectId
            result = executeDetailCall("", subjectId)
            if (result == null) {
                // Try with only detailPath
                result = executeDetailCall(detailPath, "")
            }
        }
        result
    }

    /**
     * Fetch video streams for Lookr media item.
     * For movies: se=0, ep=0. For series: se >= 1, ep >= 1.
     * Automatically handles fallbacks so it never fails if se/ep mismatch occurs.
     */
    suspend fun fetchStreams(
        subjectId: String,
        detailPath: String = "",
        se: Int = 0,
        ep: Int = 0
    ): List<MovieStream> = withContext(Dispatchers.IO) {
        if (subjectId.isBlank() && detailPath.isBlank()) return@withContext emptyList()

        // Helper to execute play request with given parameters
        fun executePlayCall(sid: String, dpath: String, callSe: Int, callEp: Int): List<MovieStream> {
            val query = buildString {
                if (sid.isNotBlank()) {
                    append("subjectId=").append(sid)
                }
                if (dpath.isNotBlank()) {
                    if (isNotEmpty()) append("&")
                    append("detailPath=").append(URLEncoder.encode(dpath, "UTF-8"))
                }
                if (callSe > 0 || callEp > 0) {
                    if (isNotEmpty()) append("&")
                    append("se=").append(callSe).append("&ep=").append(callEp)
                } else {
                    if (isNotEmpty()) append("&")
                    append("se=0&ep=0")
                }
            }

            val url = "$H5_PLAY_BASE_URL?$query"
            val request = Request.Builder()
                .url(url)
                .addHeader("origin", "https://fzmovienow.top")
                .addHeader("referer", "https://fzmovienow.top/")
                .addHeader("accept", "application/json")
                .addHeader("x-source", "lookr")
                .addHeader("authorization", AUTH_BEARER)
                .addHeader("user-agent", "okhttp/4.12.0")
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val bytes = response.body?.bytes() ?: return emptyList()
                if (!response.isSuccessful || bytes.isEmpty()) {
                    Log.w(TAG, "fetchStreams failed code=${response.code} for query: $query")
                    return emptyList()
                }

                val body = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    java.util.zip.GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    String(bytes, Charsets.UTF_8)
                }

                val root = JSONObject(body)
                val dataObj = root.optJSONObject("data") ?: return emptyList()
                val streamsArray = dataObj.optJSONArray("streams") ?: return emptyList()

                val streams = mutableListOf<MovieStream>()
                for (i in 0 until streamsArray.length()) {
                    val sObj = streamsArray.optJSONObject(i) ?: continue
                    val id = sObj.optString("id")
                    val streamUrl = sObj.optString("url")
                    val resolutions = sObj.optString("resolutions")
                    val format = sObj.optString("format", "MP4")
                    val size = sObj.optLong("size", 0L)
                    val duration = sObj.optLong("duration", 0L)
                    val codecName = sObj.optString("codecName", "")

                    if (streamUrl.isNotBlank()) {
                        streams.add(
                            MovieStream(
                                id = id,
                                resolution = if (resolutions.endsWith("P", ignoreCase = true)) resolutions else "${resolutions}P",
                                format = format,
                                url = streamUrl,
                                size = size,
                                duration = duration,
                                codecName = codecName
                            )
                        )
                    }
                }
                return streams
            } catch (e: Exception) {
                Log.e(TAG, "Error executing play call for sid=$sid, dpath=$dpath, se=$callSe, ep=$callEp", e)
                return emptyList()
            }
        }

        // 1. Try with requested parameters
        var results = executePlayCall(subjectId, detailPath, se, ep)

        // 2. If caller asked for a specific episode (> 0) and results are empty, preserve the episode!
        if (results.isEmpty() && ep > 0) {
            // Lookr often indexes short dramas and single-season shows with season 0 or season 1
            val altSe = if (se > 0) 0 else 1
            results = executePlayCall(subjectId, detailPath, altSe, ep)

            // Try with subjectId alone
            if (results.isEmpty() && subjectId.isNotBlank() && detailPath.isNotBlank()) {
                results = executePlayCall(subjectId, "", se, ep)
                if (results.isEmpty()) {
                    results = executePlayCall(subjectId, "", altSe, ep)
                }
            }

            // Try with detailPath alone
            if (results.isEmpty() && detailPath.isNotBlank()) {
                results = executePlayCall("", detailPath, se, ep)
                if (results.isEmpty()) {
                    results = executePlayCall("", detailPath, altSe, ep)
                }
            }
        }

        // 3. Resilient fallback if still empty: try ep 0 or 1
        if (results.isEmpty()) {
            if (se > 0 || ep > 0) {
                results = executePlayCall(subjectId, detailPath, 0, 0)
                if (results.isEmpty()) {
                    results = executePlayCall(subjectId, detailPath, 1, 1)
                }
            } else {
                results = executePlayCall(subjectId, detailPath, 1, 1)
            }
        }

        // 4. Fallback: if both subjectId and detailPath were provided, try subjectId only
        if (results.isEmpty() && subjectId.isNotBlank() && detailPath.isNotBlank()) {
            results = executePlayCall(subjectId, "", 0, 0)
            if (results.isEmpty()) {
                results = executePlayCall(subjectId, "", 1, 1)
            }
        }

        // 5. Fallback: try detailPath only
        if (results.isEmpty() && detailPath.isNotBlank()) {
            results = executePlayCall("", detailPath, 0, 0)
            if (results.isEmpty()) {
                results = executePlayCall("", detailPath, 1, 1)
            }
        }

        results
    }

    /**
     * Search suggestions from Lookr
     * GET /wefeed-gogo-bff/subject/search-suggest?keyword={keyword}&perPage=10
     */
    suspend fun fetchSearchSuggestions(keyword: String): List<String> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val encodedKeyword = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "https://api.lookr.cloud/wefeed-gogo-bff/subject/search-suggest?keyword=$encodedKeyword&perPage=10"
        val timestamp = System.currentTimeMillis()

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .addHeader("x-client-info", CLIENT_INFO)
            .addHeader("x-client-status", "0")
            .addHeader("x-tr-signature", "$timestamp|2|")
            .addHeader("authorization", AUTH_BEARER)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                return@withContext emptyList()
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val itemsArray = dataObj.optJSONArray("items") ?: return@withContext emptyList()

            val suggestions = mutableListOf<String>()
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i) ?: continue
                val word = item.optString("word").trim()
                if (word.isNotBlank()) {
                    suggestions.add(word)
                }
            }
            suggestions.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Lookr search suggestions for keyword=$keyword", e)
            emptyList()
        }
    }

    /**
     * Search movies/series/anime from Lookr v3 search
     * POST /wefeed-gogo-bff/subject/search/v3
     * body: {"page":"1","perPage":"10","keyword":"...","subjectType":"0"}
     */
    suspend fun searchMovies(
        keyword: String,
        page: Int = 1,
        perPage: Int = 10
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val url = "https://api.lookr.cloud/wefeed-gogo-bff/subject/search/v3"
        val timestamp = System.currentTimeMillis()

        val jsonBody = JSONObject().apply {
            put("page", page.toString())
            put("perPage", perPage.toString())
            put("keyword", keyword.trim())
            put("subjectType", "0")
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .addHeader("x-client-info", CLIENT_INFO)
            .addHeader("x-client-status", "0")
            .addHeader("x-tr-signature", "$timestamp|2|")
            .addHeader("authorization", AUTH_BEARER)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                Log.w(TAG, "Lookr searchMovies failed code=${response.code}")
                return@withContext emptyList()
            }

            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: return@withContext emptyList()
            val cardsArray = dataObj.optJSONArray("cards") ?: return@withContext emptyList()

            val results = mutableListOf<MovieItem>()
            for (i in 0 until cardsArray.length()) {
                val cardObj = cardsArray.optJSONObject(i) ?: continue
                val subjectObj = cardObj.optJSONObject("subject") ?: continue

                val id = subjectObj.optString("subjectId")
                val title = subjectObj.optString("title")
                val description = subjectObj.optString("description")
                val releaseDate = subjectObj.optString("releaseDate")
                val durationSeconds = subjectObj.optLong("durationSeconds", 0L)
                val genre = subjectObj.optString("genre")
                val country = subjectObj.optString("countryName")
                val imdbRating = subjectObj.optString("imdbRatingValue")

                val coverObj = subjectObj.optJSONObject("cover")
                val coverUrl = coverObj?.optString("url").orEmpty()

                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                val rawCorner = subjectObj.optString("corner")
                val rawSubjType = subjectObj.optInt("subjectType", 0)
                val effectiveSubjectType = when {
                    rawSubjType > 0 -> rawSubjType
                    rawCorner.contains("Short", ignoreCase = true) -> 7
                    genre.contains("Music", ignoreCase = true) -> 6
                    genre.contains("Kids", ignoreCase = true) -> 5
                    genre.contains("Sport", ignoreCase = true) -> 9
                    rawCorner.contains("Season", ignoreCase = true) || rawCorner.contains("Series", ignoreCase = true) -> 2
                    else -> 1
                }
                val isSeries = when {
                    effectiveSubjectType == 6 -> false
                    effectiveSubjectType == 1 -> false
                    effectiveSubjectType == 2 || effectiveSubjectType == 7 -> true
                    else -> false
                }

                val detailPath = subjectObj.optString("detailPath")

                results.add(
                    MovieItem(
                        id = id,
                        title = title,
                        description = description,
                        coverUrl = coverUrl,
                        releaseYear = year,
                        releaseDate = releaseDate,
                        country = country,
                        genre = genre,
                        rating = imdbRating,
                        duration = if (durationSeconds > 0) "${durationSeconds / 60}m" else "",
                        detailPath = detailPath,
                        source = "lookr",
                        isShort = effectiveSubjectType == 7,
                        isSeries = isSeries,
                        corner = rawCorner.ifBlank { if (isSeries) "Series" else "" },
                        customHeaders = LOOKR_VIDEO_HEADERS,
                        subjectType = effectiveSubjectType
                    )
                )
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error performing Lookr search for keyword=$keyword", e)
            emptyList()
        }
    }
}

package com.example.data.repository

import android.content.Context
import com.example.data.api.MovieBoxApiClient
import com.example.data.api.VskitShortsApiClient
import com.example.data.model.HomeFeedData
import com.example.data.model.MovieItem
import com.example.data.model.VskitEpisodeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MovieBoxRepository(private val context: Context) {

    /**
     * Emits the instant local cache first for 0% lag startup,
     * then queries the live MovieBox API and emits updated feed.
     */
    fun getHomeFeedStream(): Flow<HomeFeedData> = flow {
        // 1. Instant emit from cached bundle (< 5ms)
        val initialData = MovieBoxApiClient.loadBundledHomeFeed(context)
        emit(initialData)

        // 2. Fetch live data from network
        try {
            val liveData = MovieBoxApiClient.fetchHomeFeed(context)
            if (liveData.sections.isNotEmpty() || liveData.heroBanners.isNotEmpty()) {
                val existingTitles = liveData.sections.map { it.title.trim().lowercase() }.toSet()
                val additionalSections = initialData.sections.filter { initialSec ->
                    initialSec.items.isNotEmpty() && !existingTitles.contains(initialSec.title.trim().lowercase())
                }
                val mergedFeed = liveData.copy(
                    sections = liveData.sections + additionalSections,
                    heroBanners = if (liveData.heroBanners.isNotEmpty()) liveData.heroBanners else initialData.heroBanners
                )
                emit(mergedFeed)
            }
        } catch (_: Exception) {
            // If offline, initialData already emitted
        }
    }

    suspend fun refreshHomeFeed(): HomeFeedData {
        val initialData = MovieBoxApiClient.loadBundledHomeFeed(context)
        val liveData = MovieBoxApiClient.fetchHomeFeed(context)
        if (liveData.sections.isNotEmpty() || liveData.heroBanners.isNotEmpty()) {
            val existingTitles = liveData.sections.map { it.title.trim().lowercase() }.toSet()
            val additionalSections = initialData.sections.filter { initialSec ->
                initialSec.items.isNotEmpty() && !existingTitles.contains(initialSec.title.trim().lowercase())
            }
            return liveData.copy(
                sections = liveData.sections + additionalSections,
                heroBanners = if (liveData.heroBanners.isNotEmpty()) liveData.heroBanners else initialData.heroBanners
            )
        }
        return initialData
    }

    /**
     * Server 2: Shorts TV feed stream
     */
    fun getShortsTvFeedStream(): Flow<HomeFeedData> = flow {
        try {
            val liveData = VskitShortsApiClient.fetchShortsTabOperations()
            if (liveData.sections.isNotEmpty() || liveData.heroBanners.isNotEmpty()) {
                emit(liveData)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun refreshShortsTvFeed(): HomeFeedData {
        return VskitShortsApiClient.fetchShortsTabOperations()
    }

    suspend fun fetchShortsRecommendList(page: Int = 1, perPage: Int = 20): Pair<List<MovieItem>, Boolean> {
        return VskitShortsApiClient.fetchShortsRecommendList(page, perPage)
    }

    suspend fun fetchFilterShortsList(
        page: Int = 1,
        perPage: Int = 24,
        channelId: Int = 1012
    ): Pair<List<MovieItem>, Boolean> {
        return VskitShortsApiClient.fetchFilterShortsList(page, perPage, channelId)
    }

    suspend fun fetchShortsEpisodes(subjectId: String): List<VskitEpisodeItem> {
        return VskitShortsApiClient.fetchShortsEpisodes(subjectId)
    }

    suspend fun searchShorts(keyword: String, page: Int = 1, perPage: Int = 20): List<MovieItem> {
        return VskitShortsApiClient.searchShorts(keyword, page, perPage)
    }

    suspend fun fetchEveryoneSearchShorts(): List<MovieItem> {
        return VskitShortsApiClient.fetchEveryoneSearch()
    }

    suspend fun fetchShortsSearchSuggestions(keyword: String): List<String> {
        return VskitShortsApiClient.fetchSearchSuggestions(keyword)
    }

    suspend fun fetchSearchSuggestions(keyword: String): List<String> {
        return MovieBoxApiClient.fetchSearchSuggestions(context, keyword)
    }

    suspend fun fetchGenreRanking(
        genreTopId: String,
        page: Int = 1,
        perPage: Int = 20
    ): Pair<List<MovieItem>, Boolean> {
        return MovieBoxApiClient.fetchRankingListContent(context, genreTopId, page, perPage)
    }

    suspend fun searchMovies(keyword: String): List<MovieItem> {
        return MovieBoxApiClient.searchMoviesApi(context, keyword)
    }

    suspend fun searchMoviesPage(
        keyword: String,
        page: Int = 1,
        perPage: Int = 20
    ): Pair<List<MovieItem>, Boolean> {
        return MovieBoxApiClient.searchMoviesPage(context, keyword, page, perPage)
    }

    suspend fun fetchPlayStreams(
        subjectId: String,
        detailPath: String = "",
        isShort: Boolean = false,
        season: Int? = null,
        episode: Int? = null
    ): com.example.data.model.StreamPlayResult {
        return MovieBoxApiClient.fetchPlayStreams(context, subjectId, detailPath, isShort, season, episode)
    }

    /**
     * Server 4: Story TV methods
     */
    suspend fun fetchStoryTvShows(page: Int = 0, size: Int = 50): Pair<List<MovieItem>, Boolean> {
        return com.example.data.api.StoryTvApiClient.fetchExploreShows(page, size)
    }

    suspend fun fetchStoryTvLanguages(): List<com.example.data.api.StoryTvLanguage> {
        return com.example.data.api.StoryTvApiClient.fetchLanguages()
    }

    suspend fun selectStoryTvLanguage(langId: Int): Boolean {
        return com.example.data.api.StoryTvApiClient.selectLanguage(langId)
    }

    suspend fun fetchStoryTvEpisodeList(showId: String): Pair<Int, List<com.example.data.api.StoryTvEpisodeItem>> {
        return com.example.data.api.StoryTvApiClient.fetchEpisodeList(showId)
    }

    suspend fun fetchStoryTvEpisodeMetadata(showId: String, cursor: Int = 0): List<com.example.data.api.StoryTvEpisodeItem> {
        return com.example.data.api.StoryTvApiClient.fetchEpisodeMetadata(showId, cursor)
    }

    suspend fun fetchStoryTvEpisodeStream(showId: String, episodeIndex: Int, forceRefresh: Boolean = false): String? {
        return com.example.data.api.StoryTvApiClient.fetchEpisodeStream(showId, episodeIndex, forceRefresh)
    }

    suspend fun fetchStoryTvStreamQualities(masterUrl: String): List<com.example.data.model.MovieStream> {
        return com.example.data.api.StoryTvApiClient.fetchHlsStreamQualities(masterUrl)
    }

    suspend fun searchStoryTv(query: String): List<MovieItem> {
        return com.example.data.api.StoryTvApiClient.searchShows(query)
    }

    /**
     * Server 5: FreeReels methods
     */
    suspend fun fetchFreeReelsHome(tabKey: String = "503"): Pair<List<MovieItem>, String?> {
        return com.example.data.api.FreeReelsApiClient.fetchHomeTab(tabKey)
    }

    suspend fun fetchFreeReelsFeed(
        nextCursor: String,
        moduleKey: String = "1036"
    ): Pair<List<MovieItem>, Pair<String?, Boolean>> {
        return com.example.data.api.FreeReelsApiClient.fetchFeed(nextCursor, moduleKey)
    }

    suspend fun fetchFreeReelsEpisodes(seriesId: String): List<com.example.data.model.FreeReelsEpisodeItem> {
        return com.example.data.api.FreeReelsApiClient.fetchEpisodes(seriesId)
    }

    suspend fun searchFreeReels(query: String): List<MovieItem> {
        return com.example.data.api.FreeReelsApiClient.searchDramas(query)
    }
}



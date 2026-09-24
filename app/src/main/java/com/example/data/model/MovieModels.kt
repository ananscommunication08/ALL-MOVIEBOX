package com.example.data.model

enum class AppServer(
    val id: String,
    val title: String,
    val badge: String,
    val description: String,
    val logoUrl: String = ""
) {
    SERVER_1("server_1", "MovieBox", "MovieBox", "MovieBox Movies & TV Series", ""),
    SERVER_2("server_2", "VSKit", "VSKit", "VSKit Short Dramas", "https://vskit.online/logo.svg"),
    SERVER_3("server_3", "Lookr", "Lookr", "Lookr Movies & Series", "https://videodownloader.site/assets/brand/omnisave-icon-hd.png?x-oss-process=image/resize%2Cw_600"),
    SERVER_4("server_4", "Story TV", "Story TV", "Story TV Short Dramas", "https://cdn.storytv.asia/wa/prod/splash_logo.svg"),
    SERVER_5("server_5", "FreeReels", "FreeReels", "FreeReels Short Dramas", "https://play-lh.googleusercontent.com/4UD1HiZkmoTR3nzr5Z8qPvKzi7K1JptSmHPZtJ7MN7hi5WE8fNxHumvhRFHZS0r1SQ"),
    SERVER_6("server_6", "JioSaavn", "Music", "JioSaavn Music & Podcasts", "https://play-lh.googleusercontent.com/kl-7LqnjbT0onFUgWNrcz06oRXovfvO_GVYZoLW1jNwl8NlpFx8NCwKgXuvp1hbWdZI")
}

data class FreeReelsEpisodeItem(
    val id: String,
    val name: String,
    val index: Int,
    val duration: Int = 0,
    val videoUrl: String = "",
    val externalAudioH264M3u8: String = "",
    val m3u8Url: String = "",
    val externalAudioH265M3u8: String = "",
    val unlock: Boolean = true
) {
    val playableUrl: String
        get() = videoUrl.ifBlank {
            externalAudioH264M3u8.ifBlank {
                m3u8Url.ifBlank {
                    externalAudioH265M3u8
                }
            }
        }
}

data class VskitEpisodeItem(
    val ep: Int,
    val miniId: String,
    val videoUrl: String,
    val coverUrl: String,
    val durationSec: Int = 0,
    val resolution: String = "480",
    val title: String = "",
    val streams: List<MovieStream> = emptyList()
)

data class MovieItem(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
    val backdropUrl: String = "",
    val rating: String = "",
    val ratingCount: Int = 0,
    val releaseDate: String = "",
    val releaseYear: String = "",
    val genre: String = "",
    val country: String = "",
    val corner: String = "",
    val duration: String = "",
    val isShort: Boolean = false,
    val detailPath: String = "",
    val directUrl: String = "",
    val source: String = "",
    val uploadBy: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val dubs: List<DubLanguage> = emptyList(),
    val coverWidth: Int = 0,
    val coverHeight: Int = 0,
    val totalEpisodes: Int = 0,
    val isSeries: Boolean = false,
    val isVskitServer: Boolean = false,
    val isStoryTvServer: Boolean = false,
    val isFreeReelsServer: Boolean = false,
    val subjectType: Int = 1
) {
    val subjectTypeInfo: SubjectTypeInfo
        get() = SubjectTypeInfo.from(
            code = subjectType,
            isSeriesFallback = isSeries,
            isShortsFallback = isShortsContent || isShort,
            genreOrTag = genre.ifBlank { corner }
        )

    val isLandscapeCover: Boolean
        get() = coverWidth > 0 && coverHeight > 0 && coverWidth > coverHeight

    val isShortsContent: Boolean
        get() = isStoryTvServer ||
                isFreeReelsServer ||
                isVskitServer ||
                subjectType == 7 ||
                (source.equals("ugc-anime.com", ignoreCase = true) && uploadBy.equals("MiniTV", ignoreCase = true)) ||
                (source.contains("ugc-anime", ignoreCase = true) && uploadBy.contains("MiniTV", ignoreCase = true)) ||
                isShort
}

data class HeroBanner(
    val id: String,
    val title: String,
    val description: String = "",
    val backdropUrl: String = "",
    val posterUrl: String = "",
    val subjectId: String = "",
    val rating: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val releaseYear: String = "",
    val country: String = "",
    val corner: String = "",
    val detailPath: String = "",
    val isSeries: Boolean = false,
    val subjectType: Int = 1
)

fun HeroBanner.toMovieItem(): MovieItem {
    val realId = subjectId.takeIf { it.isNotBlank() && it != "0" }
        ?: id.takeIf { it.isNotBlank() && it != "0" }
        ?: ""
    val effectiveSubjType = if (subjectType > 0) subjectType else if (isSeries) 2 else 1
    return MovieItem(
        id = realId,
        title = title,
        description = description,
        coverUrl = posterUrl.ifBlank { backdropUrl },
        backdropUrl = backdropUrl.ifBlank { posterUrl },
        rating = rating,
        genre = genre,
        releaseDate = releaseDate,
        releaseYear = releaseYear,
        country = country,
        corner = corner,
        detailPath = detailPath,
        isSeries = isSeries,
        subjectType = effectiveSubjType,
        isShort = !isSeries && (genre.contains("Short", ignoreCase = true) || corner.contains("Short", ignoreCase = true) || effectiveSubjType == 7)
    )
}

data class MovieStream(
    val id: String,
    val resolution: String,
    val format: String,
    val url: String,
    val size: Long = 0L,
    val duration: Long = 0L,
    val codecName: String = ""
)

data class StreamPlayResult(
    val streams: List<MovieStream> = emptyList(),
    val hasResource: Boolean = false,
    val defaultStream: MovieStream? = null,
    val rawJson: String = "",
    val requestUrl: String = "",
    val httpCode: Int = 200,
    val errorMessage: String = ""
)

data class PlatformItem(
    val name: String,
    val uploadBy: String = ""
)

data class CategorySection(
    val id: String,
    val title: String,
    val type: String,
    val items: List<MovieItem>,
    val genreTopId: String = "",
    val detailPath: String = "",
    val opId: String = "",
    val isVskitSection: Boolean = false
) {
    val isLandscapeDetected: Boolean
        get() {
            val itemsWithDims = items.filter { it.coverWidth > 0 && it.coverHeight > 0 }
            return if (itemsWithDims.isNotEmpty()) {
                val landscapeCount = itemsWithDims.count { it.coverWidth > it.coverHeight }
                landscapeCount * 2 >= itemsWithDims.size
            } else {
                false
            }
        }

    val isHotShortTvSection: Boolean
        get() {
            val normalized = title.replace("🔥", "").replace(" ", "").lowercase()
            return normalized.contains("hotshorttv") || title.contains("Hot Short TV", ignoreCase = true)
        }

    val isShortsSection: Boolean
        get() = isHotShortTvSection || isVskitSection
}

data class HomeFeedData(
    val heroBanners: List<HeroBanner> = emptyList(),
    val platforms: List<PlatformItem> = emptyList(),
    val sections: List<CategorySection> = emptyList()
)

data class SeasonInfo(
    val seasonNumber: Int,
    val maxEp: Int,
    val resolutions: List<Int> = emptyList()
)

data class CastActor(
    val staffId: String = "",
    val name: String = "",
    val character: String = "",
    val avatarUrl: String = ""
)

data class DubLanguage(
    val subjectId: String = "",
    val lanName: String = "",
    val lanCode: String = "",
    val original: Boolean = false,
    val type: Int = 0,
    val detailPath: String = ""
)

data class SubjectDetailResult(
    val subjectId: String = "",
    val subjectType: Int = 1, // 1 = Movie, 2 = TV Series
    val title: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val backdropUrl: String = "",
    val releaseDate: String = "",
    val releaseYear: String = "",
    val genre: String = "",
    val country: String = "",
    val rating: String = "",
    val ratingCount: Int = 0,
    val durationFormatted: String = "",
    val corner: String = "",
    val subtitles: String = "",
    val isSeries: Boolean = false,
    val totalEpisodes: Int = 0,
    val seasons: List<SeasonInfo> = emptyList(),
    val cast: List<CastActor> = emptyList(),
    val detailPath: String = "",
    val hasResource: Boolean = true,
    val trailerUrl: String = "",
    val source: String = "",
    val uploadBy: String = "",
    val dubs: List<DubLanguage> = emptyList(),
    val rawJson: String = ""
) {
    val isShortsFromSource: Boolean
        get() = subjectType == 7 ||
                (source.equals("ugc-anime.com", ignoreCase = true) && uploadBy.equals("MiniTV", ignoreCase = true)) ||
                (source.contains("ugc-anime", ignoreCase = true) && uploadBy.contains("MiniTV", ignoreCase = true)) ||
                (rawJson.contains("ugc-anime.com", ignoreCase = true) && rawJson.contains("MiniTV", ignoreCase = true))

    val subjectTypeInfo: SubjectTypeInfo
        get() = SubjectTypeInfo.from(
            code = subjectType,
            isSeriesFallback = isSeries,
            isShortsFallback = isShortsFromSource,
            genreOrTag = genre.ifBlank { corner }
        )
}

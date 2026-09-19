package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class SubjectTypeInfo(
    val code: Int,
    val title: String,
    val shortBadge: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val isDirectShortsPlayer: Boolean = false,
    val hasEpisodesAndSeasons: Boolean = true
) {
    MOVIE(
        code = 1,
        title = "Movie",
        shortBadge = "Movie",
        icon = Icons.Rounded.Movie,
        primaryColor = Color(0xFFE50914), // Red
        isDirectShortsPlayer = false,
        hasEpisodesAndSeasons = false
    ),
    TV_SERIES(
        code = 2,
        title = "TV/Web Series",
        shortBadge = "Series",
        icon = Icons.Rounded.Tv,
        primaryColor = Color(0xFF3B82F6), // Blue
        isDirectShortsPlayer = false,
        hasEpisodesAndSeasons = true
    ),
    EDUCATIONAL_KIDS(
        code = 5,
        title = "Educational/Learning/Kids",
        shortBadge = "Kids",
        icon = Icons.Rounded.AutoStories,
        primaryColor = Color(0xFF10B981), // Emerald Green
        isDirectShortsPlayer = false,
        hasEpisodesAndSeasons = true
    ),
    MUSIC_VIDEO(
        code = 6,
        title = "Music Video Song",
        shortBadge = "Music",
        icon = Icons.Rounded.MusicNote,
        primaryColor = Color(0xFFA855F7), // Purple
        isDirectShortsPlayer = false,
        hasEpisodesAndSeasons = false // { no episode and season }
    ),
    SHORT_TV_MINI_DRAMA(
        code = 7,
        title = "Short TV / Mini Drama",
        shortBadge = "Shorts",
        icon = Icons.Rounded.Bolt,
        primaryColor = Color(0xFFF59E0B), // Amber / Gold
        isDirectShortsPlayer = true, // { direct open in shorts player }
        hasEpisodesAndSeasons = true
    ),
    LIVE_SPORTS(
        code = 9,
        title = "Live/Sports/Wrestling",
        shortBadge = "Sports",
        icon = Icons.Rounded.SportsMma,
        primaryColor = Color(0xFFFF4757), // Crimson
        isDirectShortsPlayer = false,
        hasEpisodesAndSeasons = false
    );

    companion object {
        fun from(
            code: Int,
            isSeriesFallback: Boolean = false,
            isShortsFallback: Boolean = false,
            genreOrTag: String = ""
        ): SubjectTypeInfo {
            return when (code) {
                1 -> MOVIE
                2 -> TV_SERIES
                5 -> EDUCATIONAL_KIDS
                6 -> MUSIC_VIDEO
                7 -> SHORT_TV_MINI_DRAMA
                9 -> LIVE_SPORTS
                else -> {
                    val lower = genreOrTag.lowercase()
                    when {
                        isShortsFallback || lower.contains("short tv") || lower.contains("mini drama") || lower.contains("shorts") -> SHORT_TV_MINI_DRAMA
                        lower.contains("music") || lower.contains("song") -> MUSIC_VIDEO
                        lower.contains("kids") || lower.contains("learning") || lower.contains("educational") || lower.contains("cartoon") -> EDUCATIONAL_KIDS
                        lower.contains("sport") || lower.contains("wrestling") || lower.contains("wwe") || lower.contains("live") -> LIVE_SPORTS
                        isSeriesFallback || lower.contains("series") || lower.contains("tv show") -> TV_SERIES
                        else -> MOVIE
                    }
                }
            }
        }
    }
}

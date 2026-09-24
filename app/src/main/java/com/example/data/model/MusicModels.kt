package com.example.data.model

import java.io.Serializable

enum class MediaType {
    SONG, ALBUM, PLAYLIST, ARTIST
}

data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val imageUrl: String = "",
    val type: MediaType = MediaType.SONG,
    val explicitContent: Boolean = false,
    val extraInfo: String = "",
    val language: String = "",
    val followerCount: Long = 0,
    val songCount: Int = 0,
    val encryptedMediaUrl: String = ""
) : Serializable {
    fun getHighQualityImage(): String {
        return imageUrl
            .replace("150x150.jpg", "500x500.jpg")
            .replace("50x50.jpg", "500x500.jpg")
            .replace("150x150.png", "500x500.png")
            .replace("50x50.png", "500x500.png")
            .replace("150x150.webp", "500x500.webp")
            .replace("50x50.webp", "500x500.webp")
            .replace("http://", "https://")
    }

    fun toSaavnSongItem(): SaavnSongItem {
        return SaavnSongItem(
            id = id,
            title = title,
            subtitle = subtitle,
            artist = subtitle,
            image = getHighQualityImage(),
            encryptedMediaUrl = encryptedMediaUrl,
            language = language
        )
    }
}

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

data class SaavnSongItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val durationSec: Int = 0,
    val image: String = "",
    val encryptedMediaUrl: String = "",
    val hasLyrics: Boolean = false,
    val lyricsId: String = "",
    val copyright: String = "",
    val releaseDate: String = "",
    val year: String = "",
    val language: String = "",
    val playCount: Long = 0,
    val directStreamUrl: String = "",
    val isDownloaded: Boolean = false,
    val localFilePath: String = ""
) : Serializable {
    val durationFormatted: String
        get() {
            if (durationSec <= 0) return "0:00"
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class SaavnAlbumItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val artist: String = "",
    val year: String = "",
    val language: String = "",
    val songCount: Int = 0
) : Serializable

data class SaavnPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val songCount: Int = 0,
    val followerCount: Long = 0
) : Serializable

data class SaavnArtistItem(
    val id: String,
    val name: String,
    val image: String = "",
    val role: String = ""
) : Serializable

data class SaavnHomeData(
    val trendingItems: List<MediaItem> = emptyList(),
    val topCharts: List<MediaItem> = emptyList(),
    val featuredPlaylists: List<MediaItem> = emptyList(),
    val newReleases: List<MediaItem> = emptyList(),
    val chartToppers: List<SaavnSongItem> = emptyList(),
    val trendingSongs: List<SaavnSongItem> = emptyList(),
    val topPlaylists: List<SaavnPlaylistItem> = emptyList(),
    val newAlbums: List<SaavnAlbumItem> = emptyList(),
    val charts: List<SaavnPlaylistItem> = emptyList()
)

data class DownloadedSongItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationSec: Int,
    val bitrateKbps: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
) : Serializable

# MovieBox Server: Complete ExoPlayer (AndroidX Media3) Implementation Guide

Yeh document explain karta hai ki **MovieBox Server** ki movies, TV series aur short reels ko **ExoPlayer (AndroidX Media3)** me kaise fetch aur play kiya jata hai. Isme exact API endpoints, request headers, data parsing, aur complete production-ready Kotlin code diya gaya hai.

---

## 1. Complete Architecture Flow (Click to Play)

```
[User Selects Item / Episode]
              │
              ▼
    1. Determine Parameters:
       - Movie:  se = 0, ep = 0
       - Series: se = SeasonNumber, ep = EpisodeNumber
              │
              ▼
    2. Call Play Stream API:
       GET https://movieboxph.org/wefeed-h5api-bff/subject/play?subjectId={id}&se={se}&ep={ep}
       (with Origin, Referer & User-Agent headers)
              │
              ▼
    3. Parse Video Streams:
       - MP4 Streams (1080p, 720p, 480p) -> ProgressiveMediaSource
       - HLS Streams (.m3u8)             -> HlsMediaSource
       - DASH Streams (.mpd)             -> DashMediaSource
              │
              ▼
    4. Configure ExoPlayer HttpDataSource:
       - Set Referer: https://movieboxph.org/
       - Set Origin: https://movieboxph.org
       - Set User-Agent: Chrome/152.0.0.0
              │
              ▼
    5. Feed MediaSource to ExoPlayer:
       exoPlayer.setMediaSource(mediaSource)
       exoPlayer.prepare()
       exoPlayer.play()
```

---

## 2. API Endpoints & Request Headers

### A. Endpoints
* **Primary URL:** `https://movieboxph.org/wefeed-h5api-bff/subject/play`
* **Fallback URL:** `https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play`
* **HTTP Method:** `GET`

### B. Query Parameters
| Parameter | Type | Required | Values |
| :--- | :---: | :---: | :--- |
| `subjectId` | String | Yes | Content Unique Identifier (e.g., `8906247916759695608`) |
| `se` | Integer | Yes | **`0`** for Movie;<br>**`1`**, **`2`**, ... for Series Season Number |
| `ep` | Integer | Yes | **`0`** for Movie;<br>**`1`**, **`2`**, ... for Series Episode Number |
| `detailPath` | String | Optional | Resource Slug (e.g., `avatar-fire-and-ash`) |

### C. Request Headers (API Call)
```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
Origin: https://movieboxph.org
Referer: https://movieboxph.org/
Accept: */*
x-client-info: {"timezone":"Asia/Calcutta"}
x-request-lang: en
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio
```

---

## 3. Play API Response Structure

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "hasResource": true,
    "streams": [
      {
        "id": "stream_1080p",
        "resolution": "1080",
        "format": "MP4",
        "url": "https://cdn-video.aoneroom.com/storage/stream/video_1080p.mp4?auth_key=...",
        "size": 2415919104,
        "duration": 7200,
        "codecName": "h264"
      },
      {
        "id": "stream_720p",
        "resolution": "720",
        "format": "MP4",
        "url": "https://cdn-video.aoneroom.com/storage/stream/video_720p.mp4?auth_key=...",
        "size": 1288490188,
        "duration": 7200,
        "codecName": "h264"
      }
    ],
    "hls": [
      {
        "id": "hls_1080",
        "resolution": "1080",
        "format": "HLS",
        "url": "https://cdn-video.aoneroom.com/storage/stream/master.m3u8?auth_key=..."
      }
    ],
    "dash": [
      {
        "id": "dash_1080",
        "resolution": "1080",
        "format": "DASH",
        "url": "https://cdn-video.aoneroom.com/storage/stream/manifest.mpd?auth_key=..."
      }
    ]
  }
}
```

---

## 4. Production ExoPlayer Implementation (Complete Kotlin Code)

Yeh hamare mobile app ka exact working code hai jo ExoPlayer (Media3) me video play karta hai:

### Step 1: MediaSource Factory with Custom HTTP Headers
ExoPlayer ke default HTTP loader me headers inject kiye jaate hain:

```kotlin
package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor

object MovieBoxMediaSourceHelper {

    private const val STREAM_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
    private const val STREAM_REFERER = "https://movieboxph.org/"
    private const val STREAM_ORIGIN = "https://movieboxph.org"

    @OptIn(UnstableApi::class)
    fun createMediaSource(
        context: Context,
        streamUrl: String,
        format: String = "MP4"
    ): MediaSource {
        val uri = Uri.parse(streamUrl)

        // 1. Exact HTTP Headers for CDN streaming
        val requestHeaders = mapOf(
            "Referer" to STREAM_REFERER,
            "Origin" to STREAM_ORIGIN,
            "Accept" to "*/*"
        )

        // 2. Configure HttpDataSource
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)

        // 3. Wrap in DefaultDataSource (supports file, assets & http)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .build()

        val lowerFormat = format.lowercase()
        val path = uri.path?.lowercase() ?: ""

        // 4. Return appropriate MediaSource based on format
        return when {
            lowerFormat.contains("dash") || path.endsWith(".mpd") -> {
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            lowerFormat.contains("hls") || path.endsWith(".m3u8") -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                // Progressive MP4 with edit-lists workaround for smooth audio-video sync
                val extractorsFactory = DefaultExtractorsFactory()
                    .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }
}
```

---

### Step 2: ExoPlayer Builder & Playback Control

```kotlin
package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

class MovieBoxPlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    @OptIn(UnstableApi::class)
    fun initPlayer(onStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true) // Auto-fallback if hardware codec fails
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
            playWhenReady = true
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> onStateChanged(false, true)
                        Player.STATE_READY -> onStateChanged(isPlaying, false)
                        Player.STATE_ENDED -> onStateChanged(false, false)
                        Player.STATE_IDLE -> onStateChanged(false, false)
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    onStateChanged(playing, false)
                }
            })
        }
    }

    /**
     * Play movie or episode stream URL
     */
    fun playStream(streamUrl: String, format: String = "MP4", startPositionMs: Long = 0L) {
        val player = exoPlayer ?: return
        val mediaSource = MovieBoxMediaSourceHelper.createMediaSource(context, streamUrl, format)

        player.setMediaSource(mediaSource)
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.play()
    }

    /**
     * Hot-swap video quality (e.g. 1080p to 720p) preserving current seek position
     */
    fun switchQuality(newStreamUrl: String, format: String = "MP4") {
        val player = exoPlayer ?: return
        val currentPosition = player.currentPosition
        val mediaSource = MovieBoxMediaSourceHelper.createMediaSource(context, newStreamUrl, format)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.seekTo(currentPosition)
        player.play()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
```

---

## 5. Important Rules & Fallbacks (Jo Mobile App me Handle kiye gaye hain)

1. **Movie vs Series Fallback:**
   - Movies ke liye `se=0&ep=0` bheja jata hai. Agar kisi content me `se=0&ep=0` se 0 streams aati hain, to player automatically `se=1&ep=1` try karta hai (aur iske opposite bhi).
2. **Quality Selection:**
   - Response ke `data.streams` array me se sabse pehle highest resolution (`1080`) choose kiya jata hai.
   - Agar network slow ho, to user UI me 720p, 480p ya 360p choose kar sakta hai. `switchQuality()` function playback position preserve karke instant switch karta hai.
3. **Episode Auto-Play Next:**
   - Jab `Player.STATE_ENDED` trigger hota hai aur content Series hai, to player `currentEp + 1` ka stream fetch karke agla episode automatically play kar deta hai.

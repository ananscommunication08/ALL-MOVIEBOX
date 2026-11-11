# MovieBox Server: Native App Integration Specification (Detail & Media Player)

Yeh technical specification document native applications (Android ExoPlayer / Media3, iOS AVPlayer, Flutter, React Native, aur Python/Desktop) ke liye standard API contracts aur player headers configuration define karta hai.

---

## 1. Detail Screen API

Native app me movie ya series ki complete details (synopsis, cast, release info, seasons aur episodes list) fetch karne ke liye standard GET request.

### A. Endpoint Details
* **Base URL:** `https://h5-api.aoneroom.com/wefeed-h5api-bff/detail`
* **Fallback URL:** `https://movieboxph.org/wefeed-h5api-bff/detail`
* **HTTP Method:** `GET`

### B. Query Parameters
| Parameter | Type | Required | Description | Example |
| :--- | :---: | :---: | :--- | :--- |
| `subjectId` | String | Yes (ya `detailPath`) | Unique Content ID | `8906247916759695608` |
| `detailPath` | String | Optional | Resource Slug | `avatar-fire-and-ash-xoz8zE` |

*URL Example:*
```
https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?subjectId=8906247916759695608
```

### C. Standard Request Headers
```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
Origin: https://movieboxph.org
Referer: https://movieboxph.org/
Accept: */*
x-client-info: {"timezone":"Asia/Calcutta"}
x-request-lang: en
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio
```

### D. Data Mapping in Native Models
* **Title:** `data.subject.title` (String)
* **Description:** `data.subject.description` (String)
* **Cover Poster:** `data.subject.cover.url` (String)
* **Backdrop Banner:** `data.subject.stills.url` (String)
* **Rating:** `data.subject.imdbRatingValue` (String, default: "7.8")
* **Release Year:** `data.subject.releaseDate` (String)
* **Subject Type:** `data.subject.subjectType` (Int: `1` = Movie, `2` = TV Series, `7` = Short Drama)
* **Seasons & Episodes Array:** `data.resource.seasons`
  ```json
  [
    {
      "se": 1,
      "maxEp": 12,
      "resolutions": [ { "resolution": 720 }, { "resolution": 1080 } ]
    }
  ]
  ```
* **Cast List:** `data.stars` (Array of `{ name, character, avatarUrl }`)

---

## 2. Player Stream API

Native player ke liye video streams (MP4, HLS, DASH) fetch karne ka endpoint.

### A. Endpoint Details
* **Base URL:** `https://movieboxph.org/wefeed-h5api-bff/subject/play`
* **Fallback URL:** `https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play`
* **HTTP Method:** `GET`

### B. Query Parameters
| Parameter | Type | Required | Values |
| :--- | :---: | :---: | :--- |
| `subjectId` | String | Yes | Content Unique ID |
| `se` | Integer | Yes | `0` for Movies; `1..N` for Series Season |
| `ep` | Integer | Yes | `0` for Movies; `1..N` for Series Episode |
| `detailPath` | String | Optional | Resource Slug |

*Call Examples:*
* **Movie:** `.../subject/play?subjectId=8906247916759695608&se=0&ep=0`
* **Series (S1 E1):** `.../subject/play?subjectId=536473933005177184&se=1&ep=1`

### C. Standard Request Headers
```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
Origin: https://movieboxph.org
Referer: https://movieboxph.org/
Accept: */*
x-client-info: {"timezone":"Asia/Calcutta"}
x-request-lang: en
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEzNzc5NDUxNDIxODgwNDAwNDgsImF0cCI6MywiZXh0IjoiMTc4OTkwNTk2NyIsImV4cCI6MTc5NzY4MTk2NywiaWF0IjoxNzg5OTA1NjY3fQ.nu-oeO3EcNKS1WUqxDeH2MKM7-v9-l1EPTvSmlQNiio
```

### D. Stream Response Structure
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "hasResource": true,
    "streams": [
      {
        "resolution": "1080",
        "format": "MP4",
        "url": "https://cdn-video.aoneroom.com/...",
        "size": 2415919104
      },
      {
        "resolution": "720",
        "format": "MP4",
        "url": "https://cdn-video.aoneroom.com/...",
        "size": 1288490188
      }
    ],
    "hls": [
      {
        "resolution": "1080",
        "format": "HLS",
        "url": "https://cdn-video.aoneroom.com/.../master.m3u8"
      }
    ]
  }
}
```

---

## 3. Native Media Player Client Configuration

Native apps (ExoPlayer, AVPlayer, Flutter, VLC) me kisi proxy server ki zarurat nahi hoti. Player ke HTTP Data Source me seedhe headers pass kiye jate hain:

### Required HTTP Headers for Stream Requests:
```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
Referer: https://movieboxph.org/
Origin: https://movieboxph.org
```

---

## 4. Native Platform Code Examples

### A. Android (Kotlin / ExoPlayer & Google Media3)
Native Android me `DefaultHttpDataSource.Factory` use karein:

```kotlin
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

val headers = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36",
    "Referer" to "https://movieboxph.org/",
    "Origin" to "https://movieboxph.org"
)

val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    .setUserAgent(headers["User-Agent"]!!)
    .setDefaultRequestProperties(headers)

val mediaSourceFactory = DefaultMediaSourceFactory(context)
    .setDataSourceFactory(httpDataSourceFactory)

val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()

val mediaItem = MediaItem.fromUri(streamUrl)
player.setMediaItem(mediaItem)
player.prepare()
player.play()
```

---

### B. Flutter (`video_player` package)
Flutter app me `VideoPlayerController.networkUrl` me headers map pass karein:

```dart
import 'package:video_player/video_player.dart';

final controller = VideoPlayerController.networkUrl(
  Uri.parse(streamUrl),
  httpHeaders: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36',
    'Referer': 'https://movieboxph.org/',
    'Origin': 'https://movieboxph.org',
  },
);

await controller.initialize();
controller.play();
```

---

### C. React Native (`react-native-video`)
React Native app me `source` prop ke andar `headers` provide karein:

```tsx
import Video from 'react-native-video';

<Video
  source={{
    uri: streamUrl,
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36',
      'Referer': 'https://movieboxph.org/',
      'Origin': 'https://movieboxph.org',
    },
  }}
  controls={true}
  resizeMode="contain"
  style={{ width: '100%', height: 300 }}
/>
```

---

### D. iOS (Swift / `AVPlayer`)
iOS app me `AVURLAsset` initialize karte waqt `AVURLAssetHTTPHeaderFieldsKey` set karein:

```swift
import AVFoundation

let streamUri = URL(string: streamUrl)!
let assetHeaders: [String: String] = [
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36",
    "Referer": "https://movieboxph.org/",
    "Origin": "https://movieboxph.org"
]

let asset = AVURLAsset(url: streamUri, options: ["AVURLAssetHTTPHeaderFieldsKey": assetHeaders])
let playerItem = AVPlayerItem(asset: asset)
let player = AVPlayer(playerItem: playerItem)
player.play()
```

---

### E. Python Desktop / VLC (`python-vlc` or `mpv`)

```python
import vlc

instance = vlc.Instance(
    '--http-user-agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"',
    '--http-referrer="https://movieboxph.org/"'
)

player = instance.media_player_new()
media = instance.media_new(stream_url)
player.set_media(media)
player.play()
```

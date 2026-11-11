# Story TV Server: Complete API & Layout Specification

Yeh document **Story TV Server (Server 4)** ka poora API structure, required request headers, response parsing, aur Android/Web layout architecture define karta hai.

---

## 1. Overview & Content Nature
* **Server Name:** Story TV (Asian Mini-Dramas / Micro-Series)
* **Format:** Vertical Short Dramas (DramaBox / ReelShort format)
* **Languages Supported:** Hindi (Default, `langId: 2`), Telugu (`6`), Tamil (`7`), Kannada (`8`), Malayalam (`9`)
* **Episodes Count:** Usually 40 to 100+ episodes per show (each episode 1–2 minutes)
* **Stream Type:** Signed HLS Master Playlists (`master.m3u8?hdnts=...`) with adaptive video & Hindi/Regional audio.

---

## 2. API Endpoints & Base Configuration

### A. Base Configuration
* **API Base URL:** `https://elitemods-tv.onrender.com`
* **CDN Stream Domain:** `https://cdn.storytv.asia`
* **Static Referer:** `https://storytv.asia/`
* **Static Origin:** `https://storytv.asia`

### B. Standard Request Headers (Har API call me zaroori hain)
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJjcmVhdGVkRGF0ZSI6IkZyaSBKdW4gMTIgMDc6MDQ6MTAgVVRDIDIwMjYiLCJzZXNzaW9uSWQiOiIxNTI0MDExNDMiLCJkZXZpY2VJZCI6IjI4MDhkNDliN2UwOGI3MTYiLCJzdWIiOiIxMTYwMDQ2NDciLCJleHAiOjE3ODE1MDcwNTB9._GKqF_5WtYkgAIJVhkt3L27t9fvgLkFtfgaOLSPsrOA
appVersion: 62
os: Android 13 (API 33)
deviceId: a6229bad5c179d51
platform: 0
iamsinu: iamsinu
network_type: WIFI
Accept: application/json
Content-Type: application/json
User-Agent: ktor-client
```

---

## 3. Core API Endpoints

### Endpoint 1: Available Languages List
Sabhi regional languages ki list fetch karne ke liye:
* **URL:** `GET /userservice/v1/languages`
* **Response Sample:**
```json
{
  "code": 0,
  "data": {
    "languages": [
      {
        "langId": 2,
        "title": "Hindi",
        "thumbnailUrl": "https://cdn.storytv.asia/ta/lang/test/hindi.webp",
        "isPreSelected": true
      },
      {
        "langId": 6,
        "title": "Telugu",
        "thumbnailUrl": "https://cdn.storytv.asia/ta/lang/test/telugu.webp",
        "isPreSelected": false
      },
      {
        "langId": 7,
        "title": "Tamil",
        "thumbnailUrl": "https://cdn.storytv.asia/ta/lang/test/tamil.webp",
        "isPreSelected": false
      },
      {
        "langId": 8,
        "title": "Kannada",
        "thumbnailUrl": "https://cdn.storytv.asia/ta/lang/test/kannada.webp",
        "isPreSelected": false
      },
      {
        "langId": 9,
        "title": "Malayalam",
        "thumbnailUrl": "https://cdn.storytv.asia/ta/lang/test/malayalam.webp",
        "isPreSelected": false
      }
    ]
  }
}
```

---

### Endpoint 2: Select Language
User jab language tab change kare:
* **URL:** `POST /userservice/v1/language/select?langId={langId}`
* **Payload:** `{"langId": 2}`
* **Success Response:** `{"code": 0, "message": "Success"}`

---

### Endpoint 3: Explore Shows Feed (Home / Listing Grid)
Dramas ki list fetch karne ke liye (supports infinite pagination):
* **URL:** `GET /feedservice/v2/explore/shows?page={page}&size={size}&intCount=0`
  * Example: `page=0&size=20`
* **Response Structure:**
```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "data": {
          "showInfo": {
            "id": "2924",
            "title": "CEO Biwi Ki Asli Aukaat",
            "imageUrl": "https://cdn.storytv.asia/tm/2924/cover.webp",
            "numOfEpisodes": 69,
            "metadata": {
              "genre": "Romance",
              "tags": ["Billionaire", "Revenge", "Love"]
            }
          },
          "contentInfo": {
            "index": 1,
            "epsTitle": "Episode 1",
            "epsUrl": "https://cdn.storytv.asia/tm/2924/101139/82eb4351/master.m3u8?hdnts=exp=..."
          }
        }
      }
    ]
  }
}
```

---

### Endpoint 4: Episode List (All Episodes Index)
Show ke total episodes count aur titles laane ke liye:
* **URL:** `GET /feedservice/v1/episode/list/{showId}`
  * Example: `/feedservice/v1/episode/list/2924`
* **Response:**
```json
{
  "code": 0,
  "data": {
    "epCount": 69,
    "data": [
      { "index": 1, "epTitle": "Episode 1" },
      { "index": 2, "epTitle": "Episode 2" },
      { "index": 69, "epTitle": "Episode 69" }
    ]
  }
}
```

---

### Endpoint 5: Episode Video Streaming URLs (Batches of 5)
Story TV streams 5 episodes ke batch me deta hai:
* **URL:** `GET /feedservice/v1/episode/metadata/{showId}?cursor={cursor}&dir=NEXT`
  * Ep 1..5: `cursor=0`
  * Ep 6..10: `cursor=5`
  * Ep 11..15: `cursor=10`
* **Response Sample:**
```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "index": 1,
        "contentId": "101139",
        "epsTitle": "CEO Biwi Ki Asli Aukaat · Ep 1",
        "epsUrl": "https://cdn.storytv.asia/tm/2924/101139/82eb4351/master.m3u8?hdnts=exp=1794152972~acl=/tm/2924/101139/82eb4351/*~hmac=...",
        "thumb": "https://cdn.storytv.asia/tm/2924/101139/thumb.webp"
      }
    ]
  }
}
```

---

### Endpoint 6: Search Dramas
* **URL:** `GET /searchservice/v1/search/{query}`
  * Example: `/searchservice/v1/search/CEO`
* **Response:** Matching shows array in `data.content`.

---

## 4. Video Stream Playback Headers (ExoPlayer & Web)

Video stream links HLS `.m3u8` playlists hain jisme Akamai CDN token (`?hdnts=exp=...`) signed hota hai.
ExoPlayer me ye headers zaroori hain:

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36
Referer: https://storytv.asia/
Origin: https://storytv.asia
Accept: */*
```

---

## 5. UI Layout Architecture (App Screens)

Story TV content vertical short-format ka hota hai, isliye iska layout regular movies se alag hota hai:

### Layout 1: Server Home Screen (Explore Feed)
```
┌─────────────────────────────────────────────────────────────┐
│ [Server Tabs: MovieBox | VSKit | Lookr | Story TV* | ...]   │
├─────────────────────────────────────────────────────────────┤
│ 🌐 [Hindi (✓)] [Telugu] [Tamil] [Kannada] [Malayalam]        │
├─────────────────────────────────────────────────────────────┤
│ 2-Column Vertical Drama Grid:                                │
│ ┌──────────────────────┐    ┌──────────────────────┐        │
│ │ [Genre: Romance]     │    │ [Genre: Revenge]     │        │
│ │                      │    │                      │        │
│ │                      │    │                      │        │
│ │   POSTER IMAGE       │    │   POSTER IMAGE       │        │
│ │   (Aspect 9:16)      │    │   (Aspect 9:16)      │        │
│ │                      │    │                      │        │
│ │ [▶ 69 Episodes]      │    │ [▶ 45 Episodes]      │        │
│ └──────────────────────┘    └──────────────────────┘        │
│  CEO Biwi Ki Asli Aukaat     The Billionaire Groom           │
│  Romance • Revenge           Drama • Family                  │
│                                                             │
│ (Infinite Scroll: Load more shows automatically on scroll)  │
└─────────────────────────────────────────────────────────────┘
```

### Layout 2: Vertical Reel / Shorts Player (Best Experience)
Story TV dramas ka primary player **Reels/Shorts Player** hota hai:
1. **Full-screen 9:16 Video Player**: Direct vertical video play hota hai bina black bars ke.
2. **Swipe Up / Down Gestures**:
   * Swipe Up ➔ Agla episode (`Episode 2`) instant start hota hai.
   * Swipe Down ➔ Pichhla episode (`Episode 1`) chalta hai.
3. **Episode Drawer (Bottom Sheet)**:
   * Neeche button par tap karne par 1 se 69 episodes ki vertical grid khulti hai.
   * User kisi bhi episode par tap karke instant jump kar sakta hai.
4. **Auto-Play Next**:
   * Episode khatam hote hi agla episode automatically next frame par scroll hoke play ho jata hai.

### Layout 3: Traditional Detail Page (TV & Tablet Layout)
* Top: Landscape Banner + Poster + Title + Total Episodes Badge.
* Middle: Genre Tags (`Billionaire`, `Revenge`) + Synopsis.
* Bottom: Season 1 Ep Selector tabs (1–30, 31–60, 61–90) aur episode cards.

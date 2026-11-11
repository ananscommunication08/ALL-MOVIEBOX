# MovieBox Server: Card Type Identification & Subject Mapping Specification

## 1. Overview
MovieBox Server API (`wefeed-h5api-bff`) me kisi bhi card item ko identify karne ke liye do main fields hote hain:
1. `subjectId` (Unique Content ID): Yeh specific movie, series ya track ka unique identifier hota hai (jaise Avatar ki ID `8906247916759695608` hai). Iska use `/detail?subjectId={subjectId}` aur `/subject/play?subjectId={subjectId}` me hota hai.
2. `subjectType` (Card Category Enum): Yeh card ke type/format ko define karta hai ki content Movie hai, Series hai, Short Drama hai, Music hai ya Promo Ad hai.

---

## 2. Complete `subjectType` Specification Table

| `subjectType` | Content Category | Format / Nature | UI & App Behavior | Video Player Call | Real Sample `subjectId` |
| :---: | :--- | :--- | :--- | :---: | :--- |
| **`1`** | **Movie** | Full-length feature film | Direct "Play" button, single video stream | `se=0&ep=0` | `8906247916759695608` (*Avatar*)<br>`4192178861456740576` (*Resident Evil*) |
| **`2`** | **TV Series** | Multi-season episodic drama | Season dropdown, episode grid (Ep 1, 2, 3...) | `se=1&ep=1`<br>(Season & Ep dynamic) | `2862720383000763928` (*The Remarried Empress*)<br>`536473933005177184` (*Lupin*) |
| **`3`** | **Documentary** | Standalone documentary film | Direct single video stream | `se=0&ep=0` | Documentary features |
| **`4`** | **TV Show / Variety** | Episodic reality / variety show | Multi-episode selector | `se=1&ep=1` | TV Variety shows |
| **`5`** | **Anime** | Japanese/Global animation series | Episode selector, anime tags | `se=1&ep=1` | Animated episodic series |
| **`6`** | **Music / Concert** | Concerts, DJ sets, music videos | Audio-video single stream | `se=0&ep=0` | `4382669182786307656` (*Afrobeats DJ Mix*) |
| **`7`** | **Short Drama / Reels** | Vertical micro-drama (DramaBox format) | Vertical player, swipe/select 50–100 episodes | `se=1&ep=1` | `8616684100902086880` (*Holy Covenant Prophet*)<br>`852608360565434872` (*Rich Girl Reborn*) |
| **`9`** | **Sports / Match** | Match replay, highlights, event | Single match stream | `se=0&ep=0` | Football / Boxing replays |
| **`0`** | **Promo / Ad Banner** | Marketing, VIP event, external link | Non-playable banner; opens URL or in-app modal | *Do not call play API* | `0` (*"Get FREE Premium!"*) |

---

## 3. High-Level Categorization (App Grouping)

App UI rendering aur Video Player call ke liye in sabhi types ko 3 main groups me divide kiya jata hai:

### A. Single Video Streams (`se=0&ep=0`)
* **Types:** `1` (Movie), `3` (Documentary), `6` (Music), `9` (Sports)
* **Action:** Direct single video player open hoga, episode selector ki zarurat nahi hoti.

### B. Episodic Multi-Part Series (`se=1&ep=1`, `se=1&ep=2`...)
* **Types:** `2` (TV Series), `4` (Variety Show), `5` (Anime), `7` (Shorts/Reels)
* **Action:** Detail API (`/detail?subjectId=...`) se `data.resource.seasons` padh kar Season aur Episode grid render hoga.

### C. Non-Playable Promos / Banners
* **Type:** `0`
* **Action:** Click par external web link ya VIP upgrade banner open hoga.

---

## 4. Fallback Detection via Auxiliary Card Fields
Agar kisi card me `subjectType` missing ya undefined ho, to in fields se identify kiya jata hai:
1. `item.corner`:
   - Agar `"EP"`, `"Season"`, ya `"Update"` hai ➔ **`2` (Series)**.
   - Agar `"SHORT"` hai ➔ **`7` (Short Drama)**.
2. `item.genre`:
   - Agar `"SERIES"`, `"TV SHOW"` hai ➔ **`2` (Series)**.
   - Agar `"MUSIC"` hai ➔ **`6` (Music)**.
   - Agar `"ANIMATION"` ya `"ANIME"` hai ➔ **`5` (Anime)**.

---

## 5. Universal Ready-To-Use Parsers

### A. JavaScript / TypeScript (For Web & React / Vue / Angular)

```typescript
export interface CardIdentity {
  category: "MOVIE" | "SERIES" | "SHORT_DRAMA" | "ANIME" | "MUSIC" | "DOCUMENTARY" | "SPORTS" | "PROMO";
  isSeries: boolean;
  isPlayable: boolean;
  defaultPlayParams: { se: number; ep: number };
  badgeText: string;
}

export function identifyMovieBoxCard(item: any): CardIdentity {
  const type = Number(item.subjectType ?? (item.type || 1));
  const corner = String(item.corner || "").toUpperCase();
  const genre = String(item.genre || "").toUpperCase();

  // 1. Promo Ads
  if (type === 0 || item.subjectId === "0" || !item.subjectId) {
    return {
      category: "PROMO",
      isSeries: false,
      isPlayable: false,
      defaultPlayParams: { se: 0, ep: 0 },
      badgeText: "Promo"
    };
  }

  // 2. Movies (Feature films)
  if (type === 1) {
    return {
      category: "MOVIE",
      isSeries: false,
      isPlayable: true,
      defaultPlayParams: { se: 0, ep: 0 },
      badgeText: "Movie"
    };
  }

  // 3. Short Dramas / Vertical Reels
  if (type === 7 || corner.includes("SHORT") || genre.includes("SHORT")) {
    return {
      category: "SHORT_DRAMA",
      isSeries: true,
      isPlayable: true,
      defaultPlayParams: { se: 1, ep: 1 },
      badgeText: "Short Reel"
    };
  }

  // 4. Anime Series
  if (type === 5 || genre.includes("ANIME") || genre.includes("ANIMATION")) {
    return {
      category: "ANIME",
      isSeries: true,
      isPlayable: true,
      defaultPlayParams: { se: 1, ep: 1 },
      badgeText: "Anime"
    };
  }

  // 5. TV / Web Series
  if (type === 2 || type === 4 || corner.includes("EP") || corner.includes("SEASON") || genre.includes("SERIES")) {
    return {
      category: "SERIES",
      isSeries: true,
      isPlayable: true,
      defaultPlayParams: { se: 1, ep: 1 },
      badgeText: "TV Series"
    };
  }

  // 6. Music / Concerts
  if (type === 6 || genre.includes("MUSIC") || genre.includes("CONCERT")) {
    return {
      category: "MUSIC",
      isSeries: false,
      isPlayable: true,
      defaultPlayParams: { se: 0, ep: 0 },
      badgeText: "Music"
    };
  }

  // 7. Documentary
  if (type === 3 || genre.includes("DOCUMENTARY")) {
    return {
      category: "DOCUMENTARY",
      isSeries: false,
      isPlayable: true,
      defaultPlayParams: { se: 0, ep: 0 },
      badgeText: "Documentary"
    };
  }

  // 8. Sports
  if (type === 9 || genre.includes("SPORT")) {
    return {
      category: "SPORTS",
      isSeries: false,
      isPlayable: true,
      defaultPlayParams: { se: 0, ep: 0 },
      badgeText: "Sports"
    };
  }

  // Default Fallback: Treat as Movie
  return {
    category: "MOVIE",
    isSeries: false,
    isPlayable: true,
    defaultPlayParams: { se: 0, ep: 0 },
    badgeText: "Movie"
  };
}
```

### B. Python 3 (For Python Desktop Apps, Django, FastAPI, Flask)

```python
def identify_moviebox_card(item: dict) -> dict:
    subject_id = str(item.get("subjectId") or item.get("id") or "0")
    subject_type = int(item.get("subjectType") or item.get("type") or 1)
    corner = str(item.get("corner") or "").upper()
    genre = str(item.get("genre") or "").upper()

    if subject_type == 0 or subject_id == "0":
        return {
            "category": "PROMO",
            "is_series": False,
            "is_playable": False,
            "play_params": {"se": 0, "ep": 0},
            "badge": "Promo"
        }

    if subject_type == 1:
        return {
            "category": "MOVIE",
            "is_series": False,
            "is_playable": True,
            "play_params": {"se": 0, "ep": 0},
            "badge": "Movie"
        }

    if subject_type == 7 or "SHORT" in corner or "SHORT" in genre:
        return {
            "category": "SHORT_DRAMA",
            "is_series": True,
            "is_playable": True,
            "play_params": {"se": 1, "ep": 1},
            "badge": "Short Reel"
        }

    if subject_type == 5 or "ANIME" in genre:
        return {
            "category": "ANIME",
            "is_series": True,
            "is_playable": True,
            "play_params": {"se": 1, "ep": 1},
            "badge": "Anime"
        }

    if subject_type in (2, 4) or "EP" in corner or "SERIES" in genre:
        return {
            "category": "SERIES",
            "is_series": True,
            "is_playable": True,
            "play_params": {"se": 1, "ep": 1},
            "badge": "TV Series"
        }

    if subject_type == 6 or "MUSIC" in genre:
        return {
            "category": "MUSIC",
            "is_series": False,
            "is_playable": True,
            "play_params": {"se": 0, "ep": 0},
            "badge": "Music"
        }

    # Default fallback
    return {
        "category": "MOVIE",
        "is_series": False,
        "is_playable": True,
        "play_params": {"se": 0, "ep": 0},
        "badge": "Movie"
    }
```

---

## 6. End-to-End Execution Flow (Click to Play)

```
[User Clicks Card]
        │
        ▼
identifyMovieBoxCard(item)
        │
   ┌────┴───────────────────────────┐
   │                                │
[isSeries == false]           [isSeries == true]
   │                                │
   ▼                                ▼
Direct Play:                  Fetch Detail API:
Call /subject/play            GET /detail?subjectId={id}
with se=0 & ep=0                    │
                                    ▼
                              Render Season Selector
                              & Episode Buttons (1..N)
                                    │
                                    ▼
                              User Selects (Season S, Ep E)
                                    │
                                    ▼
                              Call /subject/play
                              with se={S} & ep={E}
```

# MovieBox Server: Fixing "Quality unknown • MP4" in Player

Yeh guide explain karta hai ki Player me **"Quality unknown • MP4"** kyu aa raha hai aur isko **1080p, 720p, 480p, 360p** me sahi karne ke liye exact code changes kya hain.

---

## 1. Asal Kaaran (Root Cause)

Aapke screenshot me 3 options dikh rahe hain:
* `Quality unknown • MP4`
* `Quality unknown • MP4`
* `Quality unknown • MP4`

MovieBox API response ke `streams` array me video stream object ka data aisa aata hai:
```json
[
  {
    "format": "MP4",
    "resolutions": "1080",
    "url": "https://...",
    "size": 2415919104
  },
  {
    "format": "MP4",
    "resolutions": "720",
    "url": "https://...",
    "size": 1288490188
  },
  {
    "format": "MP4",
    "resolutions": "480",
    "url": "https://...",
    "size": 666976066
  }
]
```

### Problem:
MovieBox server key ka naam **`resolutions`** (Plural - aakhiri me 's') bhejta hai.
Lekin doosre app ke code me:
* `meta.optString("resolution")` (Singular bina 's' ke) dhoonda gaya.
* `"resolutions"` key ignore ho gayi, isliye quality value empty reh gayi aur code ne `"Quality unknown"` likh diya!

---

## 2. Exact Code Fix in `PlayerActivity.java`

File: `android-tv/native/PlayerActivity.java`

### Change 1: `collectMovieBoxSources` me `"resolutions"` key allow karein (Line 1021)

**Pehle tha:**
```java
for (String k : new String[]{"resolution","quality","label","name","definition","height","format","mimeType","mime","protocol","type"}) {
    if (obj.has(k)) try { meta.put(k, obj.opt(k)); } catch (Exception ignored) { }
}
```

**Isko badal kar karein:**
```java
// "resolutions" aur "allResolutions" add karein:
for (String k : new String[]{"resolutions","resolution","quality","label","name","definition","height","format","mimeType","mime","protocol","type"}) {
    if (obj.has(k)) try { meta.put(k, obj.opt(k)); } catch (Exception ignored) { }
}
```

---

### Change 2: `addMovieBoxSource` me Quality parse karein (Line 1045)

**Pehle tha:**
```java
String quality = meta.optString("resolution", meta.optString("quality", meta.optString("label", meta.optString("name", ""))));
if (quality.isEmpty()) {
    int height = meta.optInt("height", 0);
    if (height > 0) quality = height + "p";
    else {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(2160|1440|1080|720|576|540|480|360)\\s*p?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(meta.toString() + " " + url);
        quality = m.find() ? m.group(1) + "p" : "Quality unknown";
    }
}
```

**Isko badal kar yeh likhein (Clean & Formatted Quality Parser):**
```java
String rawQuality = meta.optString("resolutions", "");
if (rawQuality.isEmpty()) rawQuality = meta.optString("resolution", "");
if (rawQuality.isEmpty()) rawQuality = meta.optString("quality", "");
if (rawQuality.isEmpty()) rawQuality = meta.optString("label", "");
if (rawQuality.isEmpty()) rawQuality = meta.optString("name", "");

String quality = "";
if (!rawQuality.isEmpty()) {
    // Agar "720,480" jaisa comma ho to pehla number lein
    String firstPart = rawQuality.split(",")[0].trim();
    // Numbers extract karein
    String digits = firstPart.replaceAll("\\D+", "");
    if (!digits.isEmpty()) {
        quality = digits + "p";
    }
}

if (quality.isEmpty()) {
    int height = meta.optInt("height", 0);
    if (height > 0) {
        quality = height + "p";
    } else {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(2160|1440|1080|720|576|540|480|360|240|144)\\s*p?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(meta.toString() + " " + url);
        quality = m.find() ? m.group(1) + "p" : "Auto";
    }
}
```

---

## 3. Exact Code Fix in `renderer/app.js`

File: `renderer/app.js` (Line 376)

**Pehle tha:**
```javascript
const resolution = qualityValue(value.resolution, value.height, value.quality, value.definition,
    value.qualityName, value.definitionName, value.videoQuality, inherited.resolution, inherited.label);
```

**Isko badal kar karein (value.resolutions sabse pehle add karein):**
```javascript
const resolution = qualityValue(value.resolutions, value.resolution, value.height, value.quality, value.definition,
    value.qualityName, value.definitionName, value.videoQuality, inherited.resolution, inherited.label);
```

---

## 4. Sorting Streams (Highest to Lowest Quality)

Streams ko highest quality se lowest quality me sort karne ke liye:
* `2160p (4K)`
* `1440p (2K)`
* `1080p (FHD)`
* `720p (HD)`
* `480p (SD)`
* `360p`
* `240p`
* `144p`

Java comparator:
```java
Collections.sort(sourceList, (a, b) -> {
    int qA = Integer.parseInt(a.optString("quality", "0").replaceAll("\\D+", "0"));
    int qB = Integer.parseInt(b.optString("quality", "0").replaceAll("\\D+", "0"));
    return Integer.compare(qB, qA); // Descending order
});
```

Yeh changes karte hi player me **Quality unknown** hat kar seedhe **1080p • MP4**, **720p • MP4**, **480p • MP4** display hone lagega!

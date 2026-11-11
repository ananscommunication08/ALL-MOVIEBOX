# MovieBox Server: Dub Languages Integration Guide (Detail Page & Player)

Yeh guide explain karta hai ki **MovieBox Server** se Dubbed Languages (Hindi, English, Tamil, Telugu, Spanish, French, etc.) ka data kaise fetch kiya jata hai, Detail Page par UI me kaise render kiya jata hai, aur click karne par dubbed audio stream kaise play ki jati hai.

---

## 1. Dub Languages Ka Data Kahan Milta Hai?

Jab aap kisi Movie ya Series ka **Detail API** call karte hain:
```http
GET https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?subjectId={id}
```
To response ke `data.subject.dubs` array ke andar saari available dubbed versions aati hain:

### Real API Response Sample (e.g. Avatar):
```json
{
  "code": 0,
  "data": {
    "subject": {
      "subjectId": "8906247916759695608",
      "title": "Avatar",
      "detailPath": "avatar-WLDIi21IUBa",
      "dubs": [
        {
          "subjectId": "8906247916759695608",
          "lanName": "Original Audio",
          "lanCode": "en",
          "original": true,
          "type": 0,
          "detailPath": "avatar-WLDIi21IUBa"
        },
        {
          "subjectId": "1654274595068805784",
          "lanName": "Hindi dub",
          "lanCode": "hi",
          "original": false,
          "type": 0,
          "detailPath": "avatar-CFH5SVTAcY1"
        },
        {
          "subjectId": "3387445081316775448",
          "lanName": "Tamil dub",
          "lanCode": "ta",
          "original": false,
          "type": 0,
          "detailPath": "avatar-ar3Z0h8xe24"
        },
        {
          "subjectId": "5331311270480462024",
          "lanName": "Telugu dub",
          "lanCode": "te",
          "original": false,
          "type": 0,
          "detailPath": "avatar-IKoTpCEsPl6"
        },
        {
          "subjectId": "7820019281189918200",
          "lanName": "Spanish dub",
          "lanCode": "es",
          "original": false,
          "type": 0,
          "detailPath": "avatar-Euz4MLOLFj9"
        }
      ]
    }
  }
}
```

### Dub Item Fields:
* **`lanName`**: UI me dikhaane ke liye label (jaise `"Hindi dub"`, `"Original Audio"`, `"Tamil dub"`).
* **`lanCode`**: ISO Language code (`"hi"`, `"en"`, `"ta"`, `"te"`, `"es"`).
* **`original`**: `true` = Original Language, `false` = Dubbed version.
* **`type`**: `0` = **Dubbed Audio**, `1` = Subtitle only.
* **`subjectId`**: Us dubbed movie ka apna unique ID!
* **`detailPath`**: Us dubbed movie ka apna unique path/slug!

---

## 2. Dub Switching Kaise Kaam Karta Hai?

MovieBox me dubbed audio koi alag track nahi hoti, balki MovieBox server par Hindi, Tamil, English ke alag-alag full streams exist karte hain.

Jab user **"Hindi dub"** button par click karta hai:
1. Active movie ki `subjectId` ko `dub.subjectId` (jaise `1654274595068805784`) se replace karein.
2. Active movie ki `detailPath` ko `dub.detailPath` (jaise `avatar-CFH5SVTAcY1`) se replace karein.
3. Ab play API call karein:
   ```http
   GET https://movieboxph.org/wefeed-h5api-bff/subject/play?subjectId=1654274595068805784&detailPath=avatar-CFH5SVTAcY1&se=0&ep=0
   Referer: https://movieboxph.org/play/avatar-CFH5SVTAcY1
   ```
4. MovieBox seedhe Hindi audio wali video streams return kar deta hai!

---

## 3. Implementation Code (For JavaScript / HTML / Android TV Web Apps)

Agar aapka other app JavaScript / HTML based hai (jaise `MOVIEBOX-PC-CHATGPT` repo ka `renderer/app.js`):

### A. Detail View me Dub Languages Chips Render Karein
`movieBoxDetailView(id)` me jahan description ya buttons hain, wahan ye code add karein:

```javascript
// Detail API se dubs array nikalna
const dubs = Array.isArray(subject.dubs) ? subject.dubs : [];
let selectedDub = dubs.find(d => d.original) || dubs[0] || null;

if (dubs.length > 1) {
  const dubContainer = html(`
    <div class="mb-dubs-section" style="margin: 16px 0;">
      <h3 style="font-size: 14px; margin-bottom: 8px; color: #A1A1AA;">Audio / Dub Languages</h3>
      <div class="mb-dubs-list" style="display: flex; gap: 8px; flex-wrap: wrap;"></div>
    </div>
  `);

  const listDiv = dubContainer.querySelector('.mb-dubs-list');

  dubs.forEach((dub) => {
    const isSelected = selectedDub && selectedDub.subjectId === dub.subjectId;
    const btn = html(`
      <button class="btn dub-btn" style="
        padding: 6px 14px;
        font-size: 13px;
        border-radius: 6px;
        cursor: pointer;
        background: ${isSelected ? '#E50914' : '#27272A'};
        color: #FFFFFF;
        border: 1px solid ${isSelected ? '#E50914' : 'rgba(255,255,255,0.1)'};
      ">
        ${esc(dub.lanName || dub.lanCode.toUpperCase())}
      </button>
    `);

    btn.onclick = () => {
      // User ne doosri bhasha select ki
      selectedDub = dub;
      
      // Detail view ko naye dubbed content ke saath reload karein
      // ya seedhe video play karein:
      movieBoxDetailView(dub.subjectId);
    };

    listDiv.appendChild(btn);
  });

  // Action buttons ke upar ya description ke neeche append karein
  $('.mb-actions', page).insertAdjacentElement('afterend', dubContainer);
}
```

---

## 4. Implementation Code (For Android Kotlin / Jetpack Compose)

Agar aapka other app Native Android (Kotlin) me hai:

### A. Data Class:
```kotlin
data class DubLanguage(
    val subjectId: String = "",
    val lanName: String = "",
    val lanCode: String = "",
    val original: Boolean = false,
    val type: Int = 0,
    val detailPath: String = ""
)
```

### B. UI Component (Compose Chips):
```kotlin
@Composable
fun DubLanguagesRow(
    dubs: List<DubLanguage>,
    selectedDub: DubLanguage?,
    onDubSelected: (DubLanguage) -> Unit
) {
    if (dubs.size <= 1) return

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Audio / Dub Languages",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dubs) { dub ->
                val isSelected = (selectedDub?.subjectId == dub.subjectId) ||
                                 (selectedDub == null && dub.original)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFFE50914) else Color(0xFF27272A),
                    modifier = Modifier.clickable { onDubSelected(dub) }
                ) {
                    Text(
                        text = dub.lanName.ifBlank { dub.lanCode.uppercase() },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
```

### C. Switching Logic (DetailScreen / ViewModel):
```kotlin
fun onLanguageChanged(selectedDub: DubLanguage) {
    val newSubjectId = selectedDub.subjectId
    val newDetailPath = selectedDub.detailPath

    // 1. Fetch the new detail for the dubbed version
    viewModelScope.launch {
        val dubbedDetail = MovieBoxApiClient.fetchSubjectDetail(
            context = context,
            detailPath = newDetailPath,
            fallbackSubjectId = newSubjectId
        )
        // 2. Play the new dubbed stream seamlessly
        if (dubbedDetail != null) {
            playMovie(newSubjectId, newDetailPath)
        }
    }
}
```

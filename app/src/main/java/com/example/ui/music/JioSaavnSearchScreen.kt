package com.example.ui.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.JioSaavnApiClient
import com.example.data.model.SaavnAlbumItem
import com.example.data.model.SaavnArtistItem
import com.example.data.model.SaavnPlaylistItem
import com.example.data.model.SaavnSongItem
import com.example.data.music.MusicDownloadManager
import com.example.data.music.MusicPlayerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import kotlinx.coroutines.launch

private val SaavnTeal = Color(0xFF2BC5B4)
private val SearchBgColor = DarkBackground
private val SearchCardBg = DarkSurfaceVariant
private val SearchTeal = SaavnTeal
private val SearchChipUnselected = DarkSurfaceVariant
private val SearchTextMuted = Color(0xFFA1A1AA)

private val POPULAR_SEARCH_KEYWORDS = listOf(
    "Arijit Singh",
    "Top 50 Hindi",
    "Sidhu Moose Wala",
    "Pritam",
    "Diljit Dosanjh",
    "Shreya Ghoshal",
    "Bollywood Romance",
    "Punjabi Hits",
    "Lo-Fi Beats",
    "English Pop",
    "Taylor Swift",
    "Anirudh"
)

private val SEARCH_FILTERS = listOf("All", "Songs", "Albums", "Playlists", "Artists")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JioSaavnSearchScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val downloadManager = remember { MusicDownloadManager.getInstance(context) }

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val favoriteSongIds by playerManager.favoriteSongIds.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearching by remember { mutableStateOf(false) }

    var searchSongResults by remember { mutableStateOf<List<SaavnSongItem>>(emptyList()) }
    var searchAlbumResults by remember { mutableStateOf<List<SaavnAlbumItem>>(emptyList()) }
    var searchPlaylistResults by remember { mutableStateOf<List<SaavnPlaylistItem>>(emptyList()) }
    var searchArtistResults by remember { mutableStateOf<List<SaavnArtistItem>>(emptyList()) }

    var selectedAlbum by remember { mutableStateOf<SaavnAlbumItem?>(null) }
    var selectedPlaylist by remember { mutableStateOf<SaavnPlaylistItem?>(null) }
    var tracklistSongs by remember { mutableStateOf<List<SaavnSongItem>>(emptyList()) }
    var isTracklistLoading by remember { mutableStateOf(false) }

    var selectedSongOptions by remember { mutableStateOf<SaavnSongItem?>(null) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    BackHandler {
        onBackClick()
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun executeSearch(query: String, filter: String = selectedFilter) {
        if (query.isBlank()) {
            searchSongResults = emptyList()
            searchAlbumResults = emptyList()
            searchPlaylistResults = emptyList()
            searchArtistResults = emptyList()
            isSearching = false
            return
        }
        coroutineScope.launch {
            isSearching = true
            when (filter) {
                "Songs" -> {
                    searchSongResults = JioSaavnApiClient.searchSongs(query, page = 1, limit = 30)
                }
                "Albums" -> {
                    searchAlbumResults = JioSaavnApiClient.searchAlbums(query, page = 1, limit = 20)
                }
                "Playlists" -> {
                    searchPlaylistResults = JioSaavnApiClient.searchPlaylists(query, page = 1, limit = 20)
                }
                "Artists" -> {
                    searchArtistResults = JioSaavnApiClient.searchArtists(query, page = 1, limit = 20)
                }
                else -> {
                    searchSongResults = JioSaavnApiClient.searchSongs(query, page = 1, limit = 25)
                    searchAlbumResults = JioSaavnApiClient.searchAlbums(query, page = 1, limit = 10)
                    searchPlaylistResults = JioSaavnApiClient.searchPlaylists(query, page = 1, limit = 10)
                }
            }
            isSearching = false
        }
    }

    fun openAlbum(album: SaavnAlbumItem) {
        selectedAlbum = album
        isTracklistLoading = true
        coroutineScope.launch {
            tracklistSongs = JioSaavnApiClient.getAlbumDetails(album.id)
            isTracklistLoading = false
        }
    }

    fun openPlaylist(playlist: SaavnPlaylistItem) {
        selectedPlaylist = playlist
        isTracklistLoading = true
        coroutineScope.launch {
            tracklistSongs = JioSaavnApiClient.getPlaylistDetails(playlist.id)
            isTracklistLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Search Header matching other servers
            Surface(
                color = DarkSurface.copy(alpha = 0.98f),
                border = BorderStroke(0.5.dp, DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back arrow button
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .testTag("saavn_search_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Input Text Field Box
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    executeSearch(it)
                                },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(SaavnTeal),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    executeSearch(searchQuery)
                                }),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search songs, albums, artists, playlists...",
                                            color = Color(0xFF71717A),
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .testTag("saavn_search_input")
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        executeSearch("")
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFA1A1AA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SEARCH_FILTERS.forEach { filter ->
                            val isSelected = (selectedFilter == filter)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SaavnTeal else DarkSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) SaavnTeal else DarkSurfaceBorder),
                                modifier = Modifier
                                    .clickable {
                                        selectedFilter = filter
                                        if (searchQuery.isNotBlank()) {
                                            executeSearch(searchQuery, filter)
                                        }
                                    }
                                    .testTag("saavn_filter_$filter")
                            ) {
                                Text(
                                    text = filter,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {

            // 3. Search Content: Popular Searches or Live Results
            if (searchQuery.isBlank()) {
                Text(
                    text = "Popular Searches",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(POPULAR_SEARCH_KEYWORDS) { keyword ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = keyword
                                    executeSearch(keyword)
                                    focusManager.clearFocus()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = "Trending",
                                tint = SearchTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = keyword,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = SaavnTeal)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Searching for \"$searchQuery\"...",
                                color = SearchTextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedFilter) {
                            "Albums" -> {
                                if (searchAlbumResults.isEmpty()) {
                                    item { SearchNoResultsView(query = searchQuery) }
                                } else {
                                    items(searchAlbumResults) { album ->
                                        val cleanAlbumTitle = album.title.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim().ifBlank { "Untitled" }
                                        val rawAlbumSub = album.artist.ifBlank { album.subtitle }.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
                                        val cleanAlbumSub = if (rawAlbumSub.isBlank() || rawAlbumSub == "{}") "Album" else "Album • $rawAlbumSub"

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { openAlbum(album) }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = album.image,
                                                contentDescription = cleanAlbumTitle,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cleanAlbumTitle,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = cleanAlbumSub,
                                                    color = SearchTextMuted,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "Playlists" -> {
                                if (searchPlaylistResults.isEmpty()) {
                                    item { SearchNoResultsView(query = searchQuery) }
                                } else {
                                    items(searchPlaylistResults) { playlist ->
                                        val cleanPlaylistTitle = playlist.title.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim().ifBlank { "Untitled" }
                                        val playlistSub = if (playlist.songCount > 0) "${playlist.songCount} Songs" else "Curated Playlist"

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { openPlaylist(playlist) }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = playlist.image,
                                                contentDescription = cleanPlaylistTitle,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cleanPlaylistTitle,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = playlistSub,
                                                    color = SearchTextMuted,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "Artists" -> {
                                if (searchArtistResults.isEmpty()) {
                                    item { SearchNoResultsView(query = searchQuery) }
                                } else {
                                    items(searchArtistResults) { artist ->
                                        val cleanArtistName = artist.name.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim().ifBlank { "Artist" }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    searchQuery = cleanArtistName
                                                    selectedFilter = "Songs"
                                                    executeSearch(cleanArtistName, "Songs")
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = artist.image,
                                                contentDescription = cleanArtistName,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cleanArtistName,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Artist",
                                                    color = SearchTextMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (searchSongResults.isEmpty()) {
                                    item { SearchNoResultsView(query = searchQuery) }
                                } else {
                                    itemsIndexed(searchSongResults) { idx, song ->
                                        val isCurrent = (currentSong?.id == song.id && isPlaying)
                                        val isFav = favoriteSongIds.contains(song.id)

                                        val cleanSongTitle = song.title.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim().ifBlank { "Untitled" }
                                        val rawSongSub = song.artist.ifBlank { song.subtitle.ifBlank { song.album } }.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
                                        val cleanSongSub = if (rawSongSub.isBlank() || rawSongSub == "{}") "JioSaavn Music" else rawSongSub

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    playerManager.playSong(song, searchSongResults)
                                                    playerManager.openPlayer()
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "%02d".format(idx + 1),
                                                color = if (isCurrent) SearchTeal else SearchTextMuted,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.width(28.dp)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            AsyncImage(
                                                model = song.image,
                                                contentDescription = cleanSongTitle,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cleanSongTitle,
                                                    color = if (isCurrent) SearchTeal else Color.White,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = cleanSongSub,
                                                    color = SearchTextMuted,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            IconButton(
                                                onClick = { playerManager.toggleFavorite(song.id) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Favorite",
                                                    tint = if (isFav) Color(0xFFEF4444) else SearchTextMuted.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { selectedSongOptions = song },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "More",
                                                    tint = SearchTextMuted.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // 3-Dots Options Bottom Sheet
        if (selectedSongOptions != null) {
            val song = selectedSongOptions!!
            val isSongDownloaded = downloadedSongs.any { it.id == song.id }

            ModalBottomSheet(
                onDismissRequest = { selectedSongOptions = null },
                containerColor = Color(0xFF1E293B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.image,
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist.ifBlank { song.subtitle },
                                color = SearchTextMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSongOptions = null
                                playerManager.playSong(song, listOf(song))
                                playerManager.openPlayer()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Play Now", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSongOptions = null
                                if (!isSongDownloaded) {
                                    downloadManager.downloadSong(song) { success ->
                                        if (success) Toast.makeText(context, "Downloaded ${song.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSongDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download",
                            tint = if (isSongDownloaded) SearchTeal else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            if (isSongDownloaded) "Already Downloaded (Offline)" else "Download in 320 kbps HQ",
                            color = if (isSongDownloaded) SearchTeal else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSongOptions = null
                                playerManager.addToQueue(song)
                                Toast.makeText(context, "Added to Queue", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Add to Queue", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    val isFav = favoriteSongIds.contains(song.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerManager.toggleFavorite(song.id)
                                selectedSongOptions = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            if (isFav) "Remove from Favorites" else "Add to Favorites",
                            color = if (isFav) Color(0xFFEF4444) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // Tracklist Bottom Sheet
        if (selectedAlbum != null || selectedPlaylist != null) {
            val title = selectedAlbum?.title ?: selectedPlaylist?.title ?: "Tracklist"
            val subtitle = selectedAlbum?.artist ?: selectedPlaylist?.subtitle ?: "JioSaavn"
            val cover = selectedAlbum?.image ?: selectedPlaylist?.image ?: ""

            ModalBottomSheet(
                onDismissRequest = {
                    selectedAlbum = null
                    selectedPlaylist = null
                    tracklistSongs = emptyList()
                },
                containerColor = Color(0xFF1E293B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = cover,
                            contentDescription = title,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                color = SearchTextMuted,
                                fontSize = 13.sp
                            )
                        }

                        if (tracklistSongs.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = SearchTeal,
                                modifier = Modifier
                                    .clickable {
                                        playerManager.playSong(tracklistSongs.first(), tracklistSongs)
                                        playerManager.openPlayer()
                                    }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play All",
                                    tint = Color.Black,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isTracklistLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SearchTeal)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(tracklistSongs) { idx, song ->
                                val isCurrent = (currentSong?.id == song.id && isPlaying)
                                val isFav = favoriteSongIds.contains(song.id)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerManager.playSong(song, tracklistSongs)
                                            playerManager.openPlayer()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "%02d".format(idx + 1),
                                        color = if (isCurrent) SearchTeal else SearchTextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(28.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    AsyncImage(
                                        model = song.image,
                                        contentDescription = song.title,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (isCurrent) SearchTeal else Color.White,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist.ifBlank { song.subtitle },
                                            color = SearchTextMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { playerManager.toggleFavorite(song.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (isFav) Color(0xFFEF4444) else SearchTextMuted.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchNoResultsView(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No results found for \"$query\"",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Try searching for a different song, artist, or album",
                color = SearchTextMuted,
                fontSize = 13.sp
            )
        }
    }
}

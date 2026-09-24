package com.example.ui.music

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.JioSaavnApiClient
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.SaavnHomeData
import com.example.data.model.SaavnSongItem
import com.example.data.music.MusicDownloadManager
import com.example.data.music.MusicPlayerManager
import com.example.ui.music.components.CardBackground
import com.example.ui.music.components.DarkBackground
import com.example.ui.music.components.MediaItemCard
import com.example.ui.music.components.SaavnTeal
import com.example.ui.music.components.TextPrimary
import com.example.ui.music.components.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JioSaavnServerView(
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

    var homeData by remember { mutableStateOf<SaavnHomeData?>(null) }
    var isLoadingFeed by remember { mutableStateOf(true) }

    // Tracklist BottomSheet state (for Album or Playlist click)
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var tracklistSongs by remember { mutableStateOf<List<SaavnSongItem>>(emptyList()) }
    var isTracklistLoading by remember { mutableStateOf(false) }

    // 3-dots Song Options Bottom Sheet
    var selectedSongOptions by remember { mutableStateOf<SaavnSongItem?>(null) }

    // Load home feed
    LaunchedEffect(Unit) {
        isLoadingFeed = true
        homeData = JioSaavnApiClient.fetchHomeFeed()
        isLoadingFeed = false
    }

    // Handle MediaItem Clicks (Song, Album, Playlist, Artist)
    fun onMediaItemClick(item: MediaItem) {
        when (item.type) {
            MediaType.SONG -> {
                val songItem = item.toSaavnSongItem()
                val playlist = homeData?.chartToppers.orEmpty().ifEmpty { listOf(songItem) }
                playerManager.playSong(songItem, playlist)
                playerManager.openPlayer()
            }
            MediaType.ALBUM -> {
                selectedMediaItem = item
                isTracklistLoading = true
                coroutineScope.launch {
                    tracklistSongs = JioSaavnApiClient.getAlbumDetails(item.id)
                    isTracklistLoading = false
                }
            }
            MediaType.PLAYLIST -> {
                selectedMediaItem = item
                isTracklistLoading = true
                coroutineScope.launch {
                    tracklistSongs = JioSaavnApiClient.getPlaylistDetails(item.id)
                    isTracklistLoading = false
                }
            }
            MediaType.ARTIST -> {
                selectedMediaItem = item
                isTracklistLoading = true
                coroutineScope.launch {
                    tracklistSongs = JioSaavnApiClient.searchSongs(item.title, page = 1, limit = 25)
                    isTracklistLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (isLoadingFeed) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SaavnTeal)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Loading JioSaavn Music...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                // 1. Top Greeting Header (Clean, professional, NO search bar here!)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = "Welcome Back",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Discover Music",
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }

                // 2. Section: Trending Now (Horizontal Scroll)
                val trending = homeData?.trendingItems.orEmpty()
                if (trending.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = "Trending Now",
                            rightLabel = "${trending.size} items",
                            onRightClick = {
                                val firstSong = trending.firstOrNull { it.type == MediaType.SONG }
                                if (firstSong != null) {
                                    onMediaItemClick(firstSong)
                                }
                            }
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(trending) { item ->
                                MediaItemCard(
                                    item = item,
                                    onClick = { onMediaItemClick(item) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                // 3. Section: Top Charts (Horizontal Scroll)
                val charts = homeData?.topCharts.orEmpty()
                if (charts.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = "Top Charts",
                            rightLabel = "Official JioSaavn"
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(charts) { chart ->
                                MediaItemCard(
                                    item = chart,
                                    onClick = { onMediaItemClick(chart) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                // 4. Section: Featured Playlists (Horizontal Scroll)
                val playlists = homeData?.featuredPlaylists.orEmpty()
                if (playlists.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = "Top Playlists",
                            rightLabel = "Handpicked for you"
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(playlists) { playlist ->
                                MediaItemCard(
                                    item = playlist,
                                    onClick = { onMediaItemClick(playlist) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                // 5. Section: New Releases (Horizontal Scroll)
                val newReleases = homeData?.newReleases.orEmpty()
                if (newReleases.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = "New Releases",
                            rightLabel = "Fresh hits"
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(newReleases) { release ->
                                MediaItemCard(
                                    item = release,
                                    onClick = { onMediaItemClick(release) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Bottom spacer to ensure all content stays fully clear of MiniPlayer and Navigation Bar
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // =========================================================================
        // 3-DOTS OPTIONS BOTTOM SHEET
        // =========================================================================
        if (selectedSongOptions != null) {
            val song = selectedSongOptions!!
            val isSongDownloaded = downloadedSongs.any { it.id == song.id }

            ModalBottomSheet(
                onDismissRequest = { selectedSongOptions = null },
                containerColor = CardBackground
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
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist.ifBlank { song.subtitle },
                                color = TextSecondary,
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
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Play Now", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                            tint = if (isSongDownloaded) SaavnTeal else TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            if (isSongDownloaded) "Already Downloaded (Offline)" else "Download in 320 kbps HQ",
                            color = if (isSongDownloaded) SaavnTeal else TextPrimary,
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
                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Add to Queue", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                            tint = if (isFav) Color(0xFFEF4444) else TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            if (isFav) "Remove from Favorites" else "Add to Favorites",
                            color = if (isFav) Color(0xFFEF4444) else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // =========================================================================
        // TRACKLIST BOTTOM SHEET (FOR ALBUMS / PLAYLISTS)
        // =========================================================================
        if (selectedMediaItem != null) {
            val item = selectedMediaItem!!

            ModalBottomSheet(
                onDismissRequest = {
                    selectedMediaItem = null
                    tracklistSongs = emptyList()
                },
                containerColor = CardBackground
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
                            model = item.getHighQualityImage(),
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle.ifBlank { item.type.name },
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        if (tracklistSongs.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = SaavnTeal,
                                modifier = Modifier
                                    .clickable {
                                        selectedMediaItem = null
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
                            CircularProgressIndicator(color = SaavnTeal)
                        }
                    } else if (tracklistSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No tracks found", color = TextSecondary)
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
                                            selectedMediaItem = null
                                            playerManager.playSong(song, tracklistSongs)
                                            playerManager.openPlayer()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "%02d".format(idx + 1),
                                        color = if (isCurrent) SaavnTeal else TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(32.dp)
                                    )

                                    AsyncImage(
                                        model = song.image,
                                        contentDescription = song.title,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (isCurrent) SaavnTeal else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist.ifBlank { song.subtitle },
                                            color = TextSecondary,
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
                                            tint = if (isFav) Color(0xFFEF4444) else TextSecondary.copy(alpha = 0.7f),
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
private fun HomeSectionHeader(
    title: String,
    rightLabel: String? = null,
    onRightClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (!rightLabel.isNullOrBlank()) {
            Text(
                text = rightLabel,
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.then(
                    if (onRightClick != null) Modifier.clickable { onRightClick() } else Modifier
                )
            )
        }
    }
}

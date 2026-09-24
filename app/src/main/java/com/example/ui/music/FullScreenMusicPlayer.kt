package com.example.ui.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.RepeatMode
import com.example.data.music.MusicDownloadManager
import com.example.data.music.MusicPlayerManager

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenMusicPlayer(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val downloadManager = MusicDownloadManager.getInstance(context)

    val isPlayerOpen by playerManager.isPlayerOpen.collectAsState()
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val positionMs by playerManager.playbackPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val queue by playerManager.queue.collectAsState()
    val queueIndex by playerManager.queueIndex.collectAsState()
    val repeatMode by playerManager.repeatMode.collectAsState()
    val isShuffle by playerManager.isShuffle.collectAsState()
    val currentBitrate by playerManager.currentBitrate.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentLyrics by playerManager.currentLyrics.collectAsState()
    val isLoadingLyrics by playerManager.isLoadingLyrics.collectAsState()

    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val downloadedSongs by downloadManager.downloadedSongs.collectAsState()

    // Dialog & Sheet States
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // User slider dragging
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    if (!isPlayerOpen || currentSong == null) return

    val song = currentSong!!
    val isDownloaded = downloadedSongs.any { it.id == song.id }
    val downloadProgress = activeDownloads[song.id]

    BackHandler {
        if (showLyricsSheet) {
            showLyricsSheet = false
        } else if (showQueueSheet) {
            showQueueSheet = false
        } else {
            playerManager.closePlayer()
        }
    }

    // Vinyl rotation
    val infiniteTransition = rememberInfiniteTransition(label = "player_vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "player_rotation"
    )

    // Animated Music Waves around the circular thumbnail
    val wave1Transition = rememberInfiniteTransition(label = "wave1")
    val wave1Scale by wave1Transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "wave1_scale"
    )
    val wave1Alpha by wave1Transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "wave1_alpha"
    )

    val wave2Transition = rememberInfiniteTransition(label = "wave2")
    val wave2Scale by wave2Transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = 400, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "wave2_scale"
    )
    val wave2Alpha by wave2Transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = 400, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "wave2_alpha"
    )

    val wave3Transition = rememberInfiniteTransition(label = "wave3")
    val wave3Scale by wave3Transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, delayMillis = 800, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "wave3_scale"
    )
    val wave3Alpha by wave3Transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, delayMillis = 800, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "wave3_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF281335),
                        Color(0xFF140D20),
                        Color(0xFF09080E)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("full_screen_music_player")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. CLEAN TOP HEADER (No Down Arrow, Centered and Sleek)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Subtle drag / tap handle to collapse
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { playerManager.closePlayer() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PLAYING FROM JIOSAAVN",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = song.album.ifBlank { "Top Songs" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. CIRCULAR ROTATING THUMBNAIL WITH ANIMATED MUSIC WAVES
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    // Outer Pulsing Music Wave Rings
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .graphicsLayer(scaleX = wave3Scale, scaleY = wave3Scale, alpha = wave3Alpha)
                            .border(2.dp, Color(0xFF8B5CF6), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .graphicsLayer(scaleX = wave2Scale, scaleY = wave2Scale, alpha = wave2Alpha)
                            .border(2.5.dp, Color(0xFF2BC5B4), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .graphicsLayer(scaleX = wave1Scale, scaleY = wave1Scale, alpha = wave1Alpha)
                            .border(3.dp, Color(0xFF00D26A), CircleShape)
                    )
                }

                // Rotating Circular Thumbnail (Vinyl Disc)
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .shadow(24.dp, CircleShape, ambientColor = Color(0xFF00D26A).copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .border(3.dp, Color(0xFF00D26A).copy(alpha = 0.8f), CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.image)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(if (isPlaying) Modifier.rotate(rotation) else Modifier)
                    )

                    // Vinyl center core and spindle dot
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00D26A))
                        )
                    }

                    if (isBuffering) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00D26A),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. SONG DETAILS (Title, Artist, Like)
            val cleanSongTitle = song.title.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim().ifBlank { "Untitled" }
            val rawArtistStr = song.artist.ifBlank { song.subtitle.ifBlank { song.album } }.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
            val cleanSongArtist = if (rawArtistStr.isBlank() || rawArtistStr == "{}") "JioSaavn Music" else rawArtistStr

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanSongTitle,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleanSongArtist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Download Button on right of title
                if (downloadProgress != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            color = Color(0xFF00D26A),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "$downloadProgress%",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isDownloaded) {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Song downloaded for offline playback", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("player_downloaded_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF00D26A),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Starting download at ${currentBitrate}kbps...", Toast.LENGTH_SHORT).show()
                            downloadManager.downloadSong(song, currentBitrate) { success ->
                                if (success) {
                                    Toast.makeText(context, "Downloaded: ${song.title}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("player_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Song",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. SEEK BAR & TIMESTAMPS (Normal thin sleek seekbar)
            Column(modifier = Modifier.fillMaxWidth()) {
                val effectiveDuration = if (durationMs > 0L) durationMs else (song.durationSec * 1000L)
                val currentSliderValue = if (isDraggingSlider) {
                    dragSliderValue
                } else {
                    if (effectiveDuration > 0L) (positionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) else 0f
                }

                Slider(
                    value = currentSliderValue,
                    onValueChange = {
                        isDraggingSlider = true
                        dragSliderValue = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        val targetMs = (dragSliderValue * effectiveDuration).toLong()
                        playerManager.seekTo(targetMs)
                    },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00D26A))
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(3.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF00D26A),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("player_seekbar")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentDisplayMs = if (isDraggingSlider) (dragSliderValue * effectiveDuration).toLong() else positionMs
                    Text(
                        text = formatTime(currentDisplayMs),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(effectiveDuration),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. MAIN CONTROLS ROW (Only 3 buttons: Prev, Play/Pause, Next. Next/Prev hidden if queue has 1 song)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (queue.size > 1) {
                    // Previous
                    IconButton(
                        onClick = { playerManager.playPrevious() },
                        modifier = Modifier.size(52.dp).testTag("player_prev")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                }

                // Play / Pause (Large Center Button)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00D26A))
                        .clickable { playerManager.togglePlayPause() }
                        .testTag("player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(42.dp)
                    )
                }

                if (queue.size > 1) {
                    Spacer(modifier = Modifier.width(32.dp))
                    // Next
                    IconButton(
                        onClick = { playerManager.playNext() },
                        modifier = Modifier.size(52.dp).testTag("player_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. BOTTOM ACTIONS ROW (Lyrics, Audio Quality in Middle, Queue)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lyrics button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            playerManager.fetchLyrics()
                            showLyricsSheet = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = Color(0xFF00D26A),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lyrics",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Audio Quality Button (in the middle between Lyrics & Queue)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF00D26A).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFF00D26A).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { showQualityDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("player_quality_badge"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Audio Quality",
                        tint = Color(0xFF00D26A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${currentBitrate}k HQ",
                        color = Color(0xFF00D26A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Queue button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { showQueueSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Queue (${queue.size})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // QUALITY SELECTOR DIALOG
    if (showQualityDialog) {
        val qualities = listOf(
            Triple("320", "320 kbps (High Quality HQ)", "Best audio fidelity with rich bass"),
            Triple("160", "160 kbps (Standard)", "Balanced sound with fast streaming"),
            Triple("96", "96 kbps (Data Saver)", "Saves mobile data on slow networks")
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = Color(0xFF1F1B2E),
            title = {
                Text("Select Streaming & Download Quality", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    qualities.forEach { (bitrate, label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerManager.setQuality(bitrate)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentBitrate == bitrate),
                                onClick = {
                                    playerManager.setQuality(bitrate)
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00D26A))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(desc, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close", color = Color(0xFF00D26A))
                }
            }
        )
    }

    // LYRICS BOTTOM SHEET
    if (showLyricsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet = false },
            containerColor = Color(0xFF140D20),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist.ifBlank { song.subtitle },
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (currentLyrics != null && !isLoadingLyrics && currentLyrics != "No lyrics available for this song.") {
                        Text(
                            text = "Full Lyrics",
                            color = Color(0xFF00D26A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoadingLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00D26A), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Finding lyrics for ${song.title}...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (currentLyrics.isNullOrBlank() || currentLyrics == "No lyrics available for this song.") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No lyrics found for this track.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { playerManager.fetchLyrics(force = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D26A))
                            ) {
                                Text("Retry Search", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = currentLyrics!!,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 16.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }

    // QUEUE BOTTOM SHEET
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = Color(0xFF140D20),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Now Playing & Queue (${queue.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(queue) { index, item ->
                        val isCurrent = (index == queueIndex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) Color(0xFF00D26A).copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    playerManager.playSong(item, queue)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) Color(0xFF00D26A) else Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            AsyncImage(
                                model = item.image,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = if (isCurrent) Color(0xFF00D26A) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist.ifBlank { item.subtitle },
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = item.durationFormatted,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

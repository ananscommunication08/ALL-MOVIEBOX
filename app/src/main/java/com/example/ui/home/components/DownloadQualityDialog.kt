package com.example.ui.home.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.download.MovieDownloadManager
import com.example.data.model.MovieStream
import com.example.ui.theme.MovieBoxRed

data class DownloadQualityOption(
    val qualityLabel: String,
    val resolution: String,
    val description: String,
    val estimatedSize: String,
    val stream: MovieStream?
)

/**
 * Quality & Episode Selection Dialog for downloading movies, TV series, or short reels.
 * For TV series and shorts player, allows toggling a batch Episode Grid mode (with Season tabs
 * S01, S02... and multi-episode selection with Select All).
 */
@Composable
fun DownloadQualityDialog(
    show: Boolean,
    movieTitle: String,
    currentDubLabel: String,
    availableStreams: List<MovieStream>,
    onDismissRequest: () -> Unit,
    onDownloadConfirmed: (quality: String, downloadUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    isSeriesOrShorts: Boolean = false,
    availableSeasons: List<Int> = emptyList(),
    seasonEpisodesMap: Map<Int, List<Int>> = emptyMap(),
    currentSeason: Int = 1,
    currentEpisode: Int = 1,
    onBatchDownloadConfirmed: ((quality: String, downloadUrl: String, selectedEpisodes: List<Pair<Int, Int>>) -> Unit)? = null
) {
    if (!show) return

    // Build quality options based on available streams of current dub
    val qualityOptions = remember(availableStreams) {
        val list = mutableListOf<DownloadQualityOption>()
        if (availableStreams.isNotEmpty()) {
            availableStreams.forEach { stream ->
                val digits = stream.resolution.split(",").firstOrNull()?.filter { it.isDigit() }?.ifBlank { "720" } ?: "720"
                val (label, desc, defaultEst) = when {
                    digits.contains("2160") || digits.contains("4k") -> Triple("2160p", "4K Ultra HD (Highest Quality)", "1.8 GB")
                    digits.contains("1440") || digits.contains("2k") -> Triple("1440p", "2K Quad HD", "1.1 GB")
                    digits.contains("1080") -> Triple("1080p", "Full HD (High Quality)", "650 MB")
                    digits.contains("720") -> Triple("720p", "HD (Standard Recommended)", "380 MB")
                    digits.contains("480") -> Triple("480p", "SD (Data Saver)", "210 MB")
                    digits.contains("360") -> Triple("360p", "Low (Fast Download)", "120 MB")
                    else -> Triple("${digits}p", "Standard", "300 MB")
                }

                val sizeText = if (stream.size > 0) {
                    MovieDownloadManager.formatBytes(stream.size)
                } else {
                    "~$defaultEst"
                }

                // Avoid duplicates in label
                if (list.none { it.qualityLabel == label }) {
                    list.add(
                        DownloadQualityOption(
                            qualityLabel = label,
                            resolution = stream.resolution,
                            description = desc,
                            estimatedSize = sizeText,
                            stream = stream
                        )
                    )
                }
            }
        }

        // Fallback default options if no streams yet loaded
        if (list.isEmpty()) {
            list.add(DownloadQualityOption("2160p", "2160", "4K Ultra HD (Highest Quality)", "~1.8 GB", null))
            list.add(DownloadQualityOption("1080p", "1080", "Full HD (High Quality)", "~650 MB", null))
            list.add(DownloadQualityOption("720p", "720", "HD (Standard Recommended)", "~380 MB", null))
            list.add(DownloadQualityOption("480p", "480", "SD (Data Saver)", "~210 MB", null))
        }
        list.sortedByDescending { opt ->
            opt.qualityLabel.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    var selectedOption by remember(qualityOptions) {
        mutableStateOf(qualityOptions.first())
    }

    // Grid mode: active by default for TV Series and Shorts
    var isGridMode by remember(isSeriesOrShorts) {
        mutableStateOf(isSeriesOrShorts)
    }

    // Normalized seasons list (e.g. [1, 2, 3...])
    val safeSeasons = remember(availableSeasons, seasonEpisodesMap, currentSeason) {
        val list = when {
            availableSeasons.isNotEmpty() -> availableSeasons.filter { it > 0 }
            seasonEpisodesMap.isNotEmpty() -> seasonEpisodesMap.keys.filter { it > 0 }.sorted()
            else -> listOf(currentSeason.coerceAtLeast(1))
        }
        if (list.isEmpty()) listOf(1) else list
    }

    // Active season tab currently being viewed in the grid
    var currentTabSeason by remember(safeSeasons, currentSeason) {
        mutableIntStateOf(
            if (safeSeasons.contains(currentSeason)) currentSeason else safeSeasons.first()
        )
    }

    // Episodes available for the current season tab
    val currentSeasonEpisodes = remember(currentTabSeason, seasonEpisodesMap) {
        val mapped = seasonEpisodesMap[currentTabSeason]
        if (!mapped.isNullOrEmpty()) {
            mapped
        } else {
            (1..12).toList()
        }
    }

    // Set of selected episodes across seasons: Set of Pair(seasonNumber, episodeNumber)
    // Default selected: current playing season & episode
    var selectedEpisodes by remember(currentSeason, currentEpisode) {
        val defaultEp = if (currentEpisode > 0) currentEpisode else 1
        val defaultSeason = if (currentSeason > 0) currentSeason else 1
        mutableStateOf(setOf(Pair(defaultSeason, defaultEp)))
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val maxDialogHeight = (screenHeight - if (isLandscape) 20.dp else 48.dp).coerceAtLeast(260.dp)
    val maxDialogWidth = if (isLandscape) {
        minOf(580.dp, screenWidth - 32.dp)
    } else {
        if (isGridMode) 440.dp else 380.dp
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1B2430), // Dark slate navy
            border = BorderStroke(1.dp, Color(0xFF2D3748)),
            modifier = modifier
                .fillMaxWidth(if (isLandscape) 0.88f else 0.95f)
                .widthIn(max = maxDialogWidth)
                .heightIn(max = maxDialogHeight)
                .padding(horizontal = 6.dp)
                .testTag("download_quality_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (isLandscape) 10.dp else 16.dp,
                        bottom = if (isLandscape) 6.dp else 8.dp,
                        start = if (isLandscape) 14.dp else 18.dp,
                        end = if (isLandscape) 14.dp else 18.dp
                    )
            ) {
                // Header: Download Icon + Title + Grid Mode Toggle (only for TV series & shorts)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MovieBoxRed.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MovieBoxRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGridMode) "Download Episodes" else "Download Quality",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentDubLabel.isNotBlank()) {
                                Text(
                                    text = "Audio: $currentDubLabel",
                                    color = MovieBoxRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (isSeriesOrShorts && isGridMode) {
                                Text(
                                    text = "Batch Select",
                                    color = Color(0xFF818CF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Grid Toggle Icon Button (only visible for TV series & shorts player, default active)
                    if (isSeriesOrShorts) {
                        IconButton(
                            onClick = { isGridMode = !isGridMode },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isGridMode) MovieBoxRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                    CircleShape
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isGridMode) MovieBoxRed else Color.White.copy(alpha = 0.15f)
                                    ),
                                    CircleShape
                                )
                                .testTag("download_dialog_grid_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = "Toggle batch episode grid",
                                tint = if (isGridMode) MovieBoxRed else Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isGridMode) {
                    // Standard Single Episode Quality Radio List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        qualityOptions.forEach { option ->
                            val isSelected = (selectedOption.qualityLabel == option.qualityLabel)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedOption = option }
                                    .background(
                                        if (isSelected) Color(0xFF243042) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) MovieBoxRed else Color(0xFF94A3B8),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(MovieBoxRed, CircleShape)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = option.qualityLabel,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = option.estimatedSize,
                                            color = if (isSelected) MovieBoxRed else Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = option.description,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // BATCH EPISODE GRID MODE (Matches user screenshot: Season tabs S01, S02... and number grid)

                    // 1. Quality Chips Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quality:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(qualityOptions) { opt ->
                                val isOptSelected = opt.qualityLabel == selectedOption.qualityLabel
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isOptSelected) MovieBoxRed else Color(0xFF243042),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isOptSelected) MovieBoxRed else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.clickable { selectedOption = opt }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isLandscape) 3.dp else 5.dp)
                                    ) {
                                        Text(
                                            text = opt.qualityLabel,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = opt.estimatedSize,
                                            color = if (isOptSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

                    // 2. Season Tabs Row (S01, S02, S03... with underline bar for selected season)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(safeSeasons) { sNum ->
                            val sLabel = "S" + sNum.toString().padStart(2, '0')
                            val isTabSelected = currentTabSeason == sNum
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { currentTabSeason = sNum }
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = sLabel,
                                    fontSize = if (isLandscape) 14.sp else 16.sp,
                                    fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isTabSelected) Color.White else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .height(3.dp)
                                        .width(32.dp)
                                        .background(
                                            if (isTabSelected) Color(0xFF818CF8) else Color.Transparent,
                                            shape = RoundedCornerShape(1.5.dp)
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

                    // 3. Sub-header Toolbar: Selection summary and Select All button
                    val allCurrentSeasonSelected = currentSeasonEpisodes.isNotEmpty() && currentSeasonEpisodes.all { ep ->
                        selectedEpisodes.contains(currentTabSeason to ep)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLandscape) 2.dp else 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected: ${selectedEpisodes.size} ep",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (allCurrentSeasonSelected) MovieBoxRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (allCurrentSeasonSelected) MovieBoxRed else Color(0xFF475569)),
                            modifier = Modifier
                                .clickable {
                                    val newSet = selectedEpisodes.toMutableSet()
                                    if (allCurrentSeasonSelected) {
                                        currentSeasonEpisodes.forEach { ep -> newSet.remove(currentTabSeason to ep) }
                                    } else {
                                        currentSeasonEpisodes.forEach { ep -> newSet.add(currentTabSeason to ep) }
                                    }
                                    selectedEpisodes = newSet
                                }
                                .testTag("download_dialog_select_all")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = if (allCurrentSeasonSelected) MovieBoxRed else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (allCurrentSeasonSelected) "Deselect S${currentTabSeason.toString().padStart(2, '0')}" else "Select All S${currentTabSeason.toString().padStart(2, '0')}",
                                    color = if (allCurrentSeasonSelected) MovieBoxRed else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 4. Episode Grid: Scrollable Number Boxes with weighted height so CANCEL/OK buttons are always visible
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isLandscape) 8 else 6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 4.dp)
                            .testTag("download_episode_grid")
                    ) {
                        items(currentSeasonEpisodes) { ep ->
                            val isEpSelected = selectedEpisodes.contains(currentTabSeason to ep)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isEpSelected) Color(0xFF3F51B5).copy(alpha = 0.35f) else Color(0xFF243042),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isEpSelected) Color(0xFF818CF8) else Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .height(if (isLandscape) 38.dp else 44.dp)
                                    .clickable {
                                        val newSet = selectedEpisodes.toMutableSet()
                                        val key = currentTabSeason to ep
                                        if (newSet.contains(key)) {
                                            newSet.remove(key)
                                        } else {
                                            newSet.add(key)
                                        }
                                        selectedEpisodes = newSet
                                    }
                                    .testTag("episode_grid_item_${currentTabSeason}_$ep")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = ep.toString(),
                                        color = if (isEpSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = if (isLandscape) 13.sp else 14.sp,
                                        fontWeight = if (isEpSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 10.dp))

                // Bottom Buttons: CANCEL & OK
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("download_dialog_cancel")
                    ) {
                        Text(
                            text = "CANCEL",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            val streamUrl = selectedOption.stream?.url
                                ?: availableStreams.firstOrNull()?.url
                                ?: ""
                            if (isGridMode) {
                                val listToDownload = if (selectedEpisodes.isNotEmpty()) {
                                    selectedEpisodes.sortedWith(compareBy({ it.first }, { it.second }))
                                } else {
                                    listOf(Pair(currentSeason, currentEpisode))
                                }
                                if (onBatchDownloadConfirmed != null) {
                                    onBatchDownloadConfirmed(selectedOption.qualityLabel, streamUrl, listToDownload)
                                } else {
                                    onDownloadConfirmed(selectedOption.qualityLabel, streamUrl)
                                }
                            } else {
                                onDownloadConfirmed(selectedOption.qualityLabel, streamUrl)
                            }
                            onDismissRequest()
                        },
                        modifier = Modifier.testTag("download_dialog_ok")
                    ) {
                        val okText = if (isGridMode && selectedEpisodes.size > 1) {
                            "OK (${selectedEpisodes.size})"
                        } else {
                            "OK"
                        }
                        Text(
                            text = okText,
                            color = MovieBoxRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


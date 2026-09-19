package com.example.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.StoryTvLanguage
import com.example.data.model.MovieItem
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Server 4: Story TV Server Screen
 * Features:
 * - Language selection chips (Hindi, Telugu, Tamil, Kannada, Malayalam)
 * - 2-Column drama poster grid with episode count & genre
 * - Infinite auto-pagination
 * - ALL DRAMAS DIRECTLY OPEN IN SHORTS REEL PLAYER
 */
@Composable
fun StoryTvServerView(
    languages: List<StoryTvLanguage>,
    selectedLanguage: StoryTvLanguage?,
    items: List<MovieItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onSelectLanguage: (StoryTvLanguage) -> Unit,
    onMovieClick: (MovieItem, List<MovieItem>, Boolean) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    // Reset scroll when language changes
    LaunchedEffect(selectedLanguage?.langId) {
        gridState.scrollToItem(0)
    }

    // Continuous auto-pagination as user scrolls
    LaunchedEffect(gridState, hasMore, isLoading, isLoadingMore) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
            .distinctUntilChanged()
            .collect { isNearBottom ->
                if (isNearBottom && hasMore && !isLoading && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        // 1. Language selector horizontal bar
        if (languages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Languages",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(16.dp)
                )

                languages.forEach { lang ->
                    val isSelected = selectedLanguage?.langId == lang.langId
                    Surface(
                        onClick = { onSelectLanguage(lang) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFFE50914) else Color(0xFF1E222B),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3440)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = lang.title,
                                color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 2. Dramas Grid
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Loading Story TV...",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No dramas found",
                    color = Color(0xFFA1A1AA),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item -> "storytv_${item.id}_$index" }
                ) { index, item ->
                    StoryTvMediaCard(
                        item = item,
                        rank = index + 1,
                        onClick = {
                            // STRICT REQUIREMENT: ALL OPEN SHORTS PLAYER
                            onMovieClick(item, items, true)
                        }
                    )
                }

                if (isLoadingMore) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFE50914),
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Story TV Poster Card
 * Clean presentation:
 * - No dark shadow/filter on poster image
 * - No rank (#) number
 * - Real drama tag (e.g. Hidden Identity) on poster
 * - ONLY title below poster for clean homepage view
 */
@Composable
fun StoryTvMediaCard(
    item: MovieItem,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E222B))
        ) {
            // Crisp image without any dark shadow or filter
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Real Drama Tag Pill on Top-Right (e.g. Hidden Identity)
            val realTag = item.genre.ifBlank {
                item.corner.takeIf { !it.contains("Drama", ignoreCase = true) && !it.contains("Short", ignoreCase = true) } ?: ""
            }.ifBlank { "Drama" }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = realTag,
                    color = Color(0xFFFFD700),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Shorts Player Play Action Icon on Bottom-End
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(Color(0xFFE50914), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play in Shorts",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ONLY Title below card for clear homepage view
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.example.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.example.data.api.LookrCategory
import com.example.data.api.LookrSubTag
import com.example.data.model.MovieItem
import com.example.ui.components.SubjectTypeBadge
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Server 3: Lookr Server Screen
 * Features Category Tabs (Movies, Series, Anime, Short TV),
 * SubTag filter chips (Popular Movies, Hollywood Movies, etc.),
 * and a 2-column ranked grid layout with continuous infinite pagination.
 */
@Composable
fun LookrServerView(
    categories: List<LookrCategory>,
    selectedCategory: LookrCategory,
    selectedSubTag: LookrSubTag,
    items: List<MovieItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onSelectCategory: (LookrCategory) -> Unit,
    onSelectSubTag: (LookrSubTag) -> Unit,
    onMovieClick: (MovieItem, List<MovieItem>, Boolean) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    // Clean State Reset: Kisi bhi nayi category ya sub-category ko select karne par scroll position top par reset ho jati hai
    LaunchedEffect(selectedCategory.tagName, selectedSubTag.tagName) {
        gridState.scrollToItem(0)
    }

    // Scroll Position Tracking & Continuous Next Page Fetching:
    // LazyVerticalGrid ko gridState aur snapshotFlow ke sath bind kiya gaya hai.
    // Jaise hi user scroll karte hue bottom ke paas pahunchta hai (lastVisibleItem >= totalItems - 4),
    // app automatically agla page (page = currentPage + 1) Lookr Recommend API (/wefeed-gogo-bff/recommend) se fetch karta hai aur items append karta rehta hai.
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
            .background(Color(0xFF0F1117))
    ) {
        // ==========================================
        // TOP CATEGORY TABS (Movies, Series, Anime, Short TV)
        // ==========================================
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { category ->
                val isSelected = category.tagName == selectedCategory.tagName
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelectCategory(category) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = category.tagDisplayName,
                        fontSize = 17.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFFE50914) else Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE50914))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }

        // ==========================================
        // SUBTAG FILTER CHIPS
        // ==========================================
        if (selectedCategory.subTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(selectedCategory.subTags) { subTag ->
                    val isSelected = subTag.tagName == selectedSubTag.tagName
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFFE50914) else Color(0xFF1E222B),
                        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier
                            .clickable { onSelectSubTag(subTag) }
                    ) {
                        Text(
                            text = subTag.tagDisplayName,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2-COLUMN MEDIA GRID
        // ==========================================
        if (isLoading && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFE50914),
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item -> "${item.id}_${index + 1}" }
                ) { index, item ->
                    LookrMediaCard(
                        item = item,
                        rank = index + 1,
                        onClick = { onMovieClick(item, items, item.subjectType == 7 || item.isShort) }
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
 * 2-Column Lookr Poster Card with Rank Badge (1, 2, 3, 4...) on top-left and distinct SubjectTypeBadge.
 */
@Composable
fun LookrMediaCard(
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Rank Badge on Top-Left (Strictly 1, 2, 3, 4... serial in RED)
            val badgeColor = Color(0xFFE50914)

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(
                        color = badgeColor,
                        shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = rank.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Distinct Subject Type Badge on Bottom-Start
            SubjectTypeBadge(
                subjectTypeInfo = item.subjectTypeInfo,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtitle e.g. "2024 · Japan · Anime" with Subject Type Icon
        val subtitle = item.genre.ifBlank {
            if (item.releaseYear.isNotBlank()) "${item.releaseYear} · Lookr" else "Lookr Media"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = item.subjectTypeInfo.icon,
                contentDescription = item.subjectTypeInfo.title,
                tint = item.subjectTypeInfo.primaryColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

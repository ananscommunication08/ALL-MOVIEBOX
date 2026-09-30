package com.example.ui.music.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.data.model.MediaType

val SaavnTeal = Color(0xFF2BC5B4)
val DarkBackground = Color(0xFF0F172A)
val CardBackground = Color(0xFF1E293B)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

@Composable
fun MediaItemCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(120),
        label = "saavn_card_scale"
    )

    // Type definition and badge styling (NO play icon for music card!)
    val (typeLabel, badgeColor, badgeIcon) = when (item.type) {
        MediaType.ALBUM -> Triple("Album", Color(0xFF8B5CF6), Icons.Default.Album)
        MediaType.PLAYLIST -> Triple("Playlist", SaavnTeal, Icons.Default.QueueMusic)
        MediaType.ARTIST -> Triple("Artist", Color(0xFFEC4899), Icons.Default.Person)
        MediaType.SONG -> Triple("Song", Color(0xFF3B82F6), Icons.Default.MusicNote)
    }

    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            AsyncImage(
                model = item.getHighQualityImage(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Prominent Badge (Custom Badge or Type Identifier)
            if (item.badge.isNotBlank()) {
                val badgeBg = when (item.badge) {
                    "FREE HITS" -> Color(0xFF00D26A)
                    "BHAKTI" -> Color(0xFFFF9800)
                    "90s RETRO" -> Color(0xFFE91E63)
                    "POP HIT" -> Color(0xFF00B0FF)
                    "POP ALBUM" -> Color(0xFF8B5CF6)
                    "ALBUM" -> Color(0xFF8B5CF6)
                    else -> badgeColor
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg.copy(alpha = 0.95f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.badge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = typeLabel,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val cleanTitle = item.title.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
        val rawSub = item.subtitle.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
        val cleanSub = when {
            rawSub.isBlank() || rawSub == "{}" || rawSub.equals(cleanTitle, ignoreCase = true) -> typeLabel
            else -> rawSub
        }

        // Title
        Text(
            text = cleanTitle.ifBlank { "Untitled" },
            color = if (isFocused) SaavnTeal else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle / Artist with type indicator
        Text(
            text = cleanSub,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

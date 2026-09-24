package com.example.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    Column(
        modifier = modifier
            .width(148.dp)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
        ) {
            AsyncImage(
                model = item.getHighQualityImage(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Type Icon Badge (SONG, ALBUM, PLAYLIST, ARTIST)
            val badgeIcon = when (item.type) {
                MediaType.ALBUM -> Icons.Default.Album
                MediaType.PLAYLIST -> Icons.Default.QueueMusic
                MediaType.ARTIST -> Icons.Default.Person
                MediaType.SONG -> Icons.Default.MusicNote
            }
            val badgeColor = when (item.type) {
                MediaType.ALBUM -> Color(0xFF8B5CF6)     // Purple
                MediaType.PLAYLIST -> SaavnTeal          // Teal
                MediaType.ARTIST -> Color(0xFFEC4899)    // Pink
                MediaType.SONG -> Color(0xFF3B82F6)      // Blue
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.9f))
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = item.type.name,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val cleanTitle = item.title.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
        val rawSub = item.subtitle.replace(Regex("""\{.*?\}"""), "").replace("{", "").replace("}", "").trim()
        val cleanSub = when {
            rawSub.isBlank() || rawSub == "{}" || rawSub.equals(cleanTitle, ignoreCase = true) -> item.type.name
            else -> rawSub
        }

        // Title
        Text(
            text = cleanTitle.ifBlank { "Untitled" },
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle / Artist
        Text(
            text = cleanSub,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

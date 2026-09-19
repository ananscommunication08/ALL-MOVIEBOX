package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubjectTypeInfo

@Composable
fun SubjectTypeBadge(
    subjectTypeInfo: SubjectTypeInfo,
    modifier: Modifier = Modifier,
    iconSize: Dp = 11.dp,
    fontSize: TextUnit = 9.sp,
    showLabel: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xE0101216),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
        ) {
            Icon(
                imageVector = subjectTypeInfo.icon,
                contentDescription = subjectTypeInfo.title,
                tint = subjectTypeInfo.primaryColor,
                modifier = Modifier.size(iconSize)
            )
            if (showLabel) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = subjectTypeInfo.shortBadge.uppercase(),
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SubjectTypeDetailChip(
    subjectTypeInfo: SubjectTypeInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = subjectTypeInfo.primaryColor.copy(alpha = 0.16f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = subjectTypeInfo.icon,
                contentDescription = subjectTypeInfo.title,
                tint = subjectTypeInfo.primaryColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = subjectTypeInfo.title,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

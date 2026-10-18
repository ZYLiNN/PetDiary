package com.zylinn.petdiary.ui.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.File
import kotlin.math.absoluteValue

// Warm tones that keep white initials readable (large-text contrast ≥ 3:1).
private val avatarColors = listOf(
    Color(0xFF8B5E3C), Color(0xFF5E7F64), Color(0xFFA0522D),
    Color(0xFF6E5F95), Color(0xFF3F6E9A), Color(0xFF9A4A68),
)

/** Circular avatar: the pet photo, or a colored circle with the first character of the name. */
@Composable
fun PetAvatar(
    name: String,
    seed: String,
    photo: File?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val ring = Modifier.border(3.dp, MaterialTheme.colorScheme.surfaceVariant, shape)
    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).then(ring),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(avatarColors[seed.hashCode().absoluteValue % avatarColors.size])
                .then(ring),
        ) {
            Text(
                text = name.firstGrapheme(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

/** First user-perceived character, so emoji names don't get split in half. */
private fun String.firstGrapheme(): String {
    if (isEmpty()) return ""
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(this)
    return substring(0, it.next())
}

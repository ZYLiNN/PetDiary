package com.zylinn.petdiary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zylinn.petdiary.R

// Nunito covers Latin and digits; Chinese glyphs fall back to the system Noto Sans CJK.
private fun nunito(weight: Int) = Font(
    resId = R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))

private val base = Typography()

private fun TextStyle.nunito() = copy(fontFamily = Nunito)

internal val PetDiaryTypography = Typography(
    displayLarge = base.displayLarge.nunito(),
    displayMedium = base.displayMedium.nunito(),
    displaySmall = base.displaySmall.nunito(),
    headlineLarge = base.headlineLarge.nunito().copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.nunito().copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.nunito().copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.nunito().copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.nunito().copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.nunito().copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.nunito(),
    bodyMedium = base.bodyMedium.nunito(),
    bodySmall = base.bodySmall.nunito(),
    labelLarge = base.labelLarge.nunito().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.nunito().copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.nunito().copy(fontWeight = FontWeight.SemiBold),
)

// MASTER.md: record card 14dp, FAB 18dp, sheet 24dp, pet tile 26dp
internal val PetDiaryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

val LocalRecordTypes = staticCompositionLocalOf { LightRecordTypes }

@Composable
fun PetDiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRecordTypes provides if (darkTheme) DarkRecordTypes else LightRecordTypes) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PetDiaryTypography,
            shapes = PetDiaryShapes,
            content = content,
        )
    }
}

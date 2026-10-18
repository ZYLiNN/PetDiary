package com.zylinn.petdiary.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Style C「奶茶柔和」— values mirror docs/design/MASTER.md

internal val LightColors = lightColorScheme(
    primary = Color(0xFF8B5E3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFE5DA),
    onPrimaryContainer = Color(0xFF3B2A1E),
    secondary = Color(0xFF6B8F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE5DA),
    onSecondaryContainer = Color(0xFF3B2A1E),
    background = Color(0xFFF7F2EC),
    onBackground = Color(0xFF3B2A1E),
    surface = Color(0xFFFFFDFA),
    onSurface = Color(0xFF3B2A1E),
    surfaceVariant = Color(0xFFEFE5DA),
    onSurfaceVariant = Color(0xFF74655A),
    surfaceContainer = Color(0xFFFFFDFA),
    outline = Color(0xFFE6D9CB),
    outlineVariant = Color(0xFFE6D9CB),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD4A373),
    onPrimary = Color(0xFF181512),
    primaryContainer = Color(0xFF2F2924),
    onPrimaryContainer = Color(0xFFF3ECE4),
    secondary = Color(0xFF8DB394),
    onSecondary = Color(0xFF181512),
    secondaryContainer = Color(0xFF2F2924),
    onSecondaryContainer = Color(0xFFF3ECE4),
    background = Color(0xFF181512),
    onBackground = Color(0xFFF3ECE4),
    surface = Color(0xFF24201C),
    onSurface = Color(0xFFF3ECE4),
    surfaceVariant = Color(0xFF2F2924),
    onSurfaceVariant = Color(0xFFBBAE9F),
    surfaceContainer = Color(0xFF24201C),
    outline = Color(0xFF3A332C),
    outlineVariant = Color(0xFF3A332C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/**
 * Record-type colors, always paired with an icon. Dark mode uses lighter tints of the
 * same hues so icons keep ≥3:1 contrast on dark cards.
 */
class RecordTypePalette(
    val feeding: Color,
    val medicine: Color,
    val vaccine: Color,
    val deworming: Color,
    val weight: Color,
    val vet: Color,
    val grooming: Color,
    val abnormal: Color,
) {
    val all get() = listOf(feeding, medicine, vaccine, deworming, weight, vet, grooming, abnormal)
}

val LightRecordTypes = RecordTypePalette(
    feeding = Color(0xFFD97706),
    medicine = Color(0xFF7C3AED),
    vaccine = Color(0xFF2563EB),
    deworming = Color(0xFF16A34A),
    weight = Color(0xFF0891B2),
    vet = Color(0xFFDC2626),
    grooming = Color(0xFFDB2777),
    abnormal = Color(0xFFEA580C),
)

val DarkRecordTypes = RecordTypePalette(
    feeding = Color(0xFFD97706),
    medicine = Color(0xFFA78BFA),
    vaccine = Color(0xFF60A5FA),
    deworming = Color(0xFF16A34A),
    weight = Color(0xFF0891B2),
    vet = Color(0xFFF87171),
    grooming = Color(0xFFF472B6),
    abnormal = Color(0xFFEA580C),
)

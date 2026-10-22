package com.zylinn.petdiary.ui.theme

import androidx.compose.ui.graphics.Color
import com.zylinn.petdiary.data.pet.PetColor

/** How a pet's colour is drawn: [fill] behind its text, [ink] for the text, [line] for borders and dots. */
class PetSwatch(val fill: Color, val ink: Color, val line: Color)

/** Shades for the calendar. Text on the fill always stays readable in both themes (checked by a unit test). */
object PetPalette {

    private fun s(fill: Long, ink: Long, line: Long) = PetSwatch(Color(fill), Color(ink), Color(line))

    private val light = mapOf(
        PetColor.ORANGE to s(0xFFFFD6A5, 0xFF7A3D00, 0xFFF0B36D),
        PetColor.WHITE to s(0xFFFFFFFF, 0xFF4A3F36, 0xFFCDBFAE),
        PetColor.BLUE to s(0xFFCFE3FA, 0xFF1B4678, 0xFF8DB6E8),
        PetColor.PINK to s(0xFFFAD2E0, 0xFF7A1F46, 0xFFEB9DBB),
        PetColor.GREEN to s(0xFFCDEBC5, 0xFF1F5A1A, 0xFF8CC783),
        PetColor.PURPLE to s(0xFFE0D6F7, 0xFF4A2D8A, 0xFFB5A1E8),
        PetColor.YELLOW to s(0xFFFFF1A8, 0xFF5E4B00, 0xFFE6CE5C),
        PetColor.GRAY to s(0xFFDADCE0, 0xFF33363B, 0xFFA9ADB3),
    )

    private val dark = mapOf(
        PetColor.ORANGE to s(0xFFE59A4A, 0xFF2B1500, 0xFFE59A4A),
        PetColor.WHITE to s(0xFFE9E2D9, 0xFF2F2820, 0xFFE9E2D9),
        PetColor.BLUE to s(0xFF7FA9E0, 0xFF0A2243, 0xFF7FA9E0),
        PetColor.PINK to s(0xFFE58FB1, 0xFF3A0F22, 0xFFE58FB1),
        PetColor.GREEN to s(0xFF7FBF74, 0xFF0F2B0C, 0xFF7FBF74),
        PetColor.PURPLE to s(0xFFA995E0, 0xFF1E0F4A, 0xFFA995E0),
        PetColor.YELLOW to s(0xFFE3C94B, 0xFF2B2300, 0xFFE3C94B),
        PetColor.GRAY to s(0xFFA9ADB3, 0xFF1A1C1F, 0xFFA9ADB3),
    )

    fun swatch(color: PetColor, darkTheme: Boolean): PetSwatch = (if (darkTheme) dark else light).getValue(color)

    /** The family's shared purchases (D84): a warm stone grey, apart from the pets' own colors. */
    fun familySwatch(darkTheme: Boolean): PetSwatch =
        if (darkTheme) s(0xFF44403C, 0xFFF5F5F4, 0xFFA8A29E) else s(0xFFE7E5E4, 0xFF292524, 0xFF78716C)
}

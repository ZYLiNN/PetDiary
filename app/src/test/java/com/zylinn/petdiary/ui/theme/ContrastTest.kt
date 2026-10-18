package com.zylinn.petdiary.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** C-06: text must reach WCAG AA contrast (4.5:1); record-type icons need 3:1 against cards. */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return (maxOf(l1, l2) / minOf(l1, l2))
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        assertTrue("$name contrast %.2f < $min".format(r), r >= min)
    }

    private fun checkTextPairs(label: String, s: ColorScheme) {
        assertContrast("$label onBackground/background", s.onBackground, s.background, 4.5)
        assertContrast("$label onSurface/surface", s.onSurface, s.surface, 4.5)
        assertContrast("$label onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, 4.5)
        assertContrast("$label onSurfaceVariant/background", s.onSurfaceVariant, s.background, 4.5)
        assertContrast("$label onPrimary/primary", s.onPrimary, s.primary, 4.5)
        assertContrast("$label onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5)
    }

    @Test fun lightTextContrast() = checkTextPairs("light", LightColors)

    @Test fun darkTextContrast() = checkTextPairs("dark", DarkColors)

    @Test fun recordTypeIconsOnLightCards() {
        LightRecordTypes.all.forEachIndexed { i, c ->
            assertContrast("type[$i] on light surface", c, LightColors.surface, 3.0)
        }
    }

    @Test fun recordTypeIconsOnDarkCards() {
        DarkRecordTypes.all.forEachIndexed { i, c ->
            assertContrast("type[$i] on dark surface", c, DarkColors.surface, 3.0)
        }
    }
}

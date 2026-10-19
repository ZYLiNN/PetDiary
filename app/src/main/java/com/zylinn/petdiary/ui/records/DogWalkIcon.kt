package com.zylinn.petdiary.ui.records

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A dog on a leash, for the "散步" type (D33): a pet-walk, not a person-jogging, symbol. Tinted by the caller. */
val DogWalkIcon: ImageVector by lazy {
    ImageVector.Builder("DogWalk", 24.dp, 24.dp, 24f, 24f).apply {
        val ink = SolidColor(Color.Black)
        // body
        path(fill = ink) {
            moveTo(9f, 10f); horizontalLineTo(17f)
            curveTo(18.1f, 10f, 19f, 10.9f, 19f, 12f); verticalLineTo(13.5f)
            curveTo(19f, 14.6f, 18.1f, 15.5f, 17f, 15.5f); horizontalLineTo(9f)
            curveTo(7.9f, 15.5f, 7f, 14.6f, 7f, 13.5f); verticalLineTo(12f)
            curveTo(7f, 10.9f, 7.9f, 10f, 9f, 10f); close()
        }
        // head
        path(fill = ink) {
            moveTo(17.6f, 8.2f)
            arcTo(2.4f, 2.4f, 0f, true, true, 22.4f, 8.2f)
            arcTo(2.4f, 2.4f, 0f, true, true, 17.6f, 8.2f); close()
        }
        // snout + ear
        path(fill = ink) { moveTo(20.5f, 7.4f); horizontalLineTo(23.4f); verticalLineTo(9.6f); horizontalLineTo(20.5f); close() }
        path(fill = ink) { moveTo(18.2f, 6.4f); lineTo(17.9f, 3.6f); lineTo(20.2f, 5.6f); close() }
        // legs
        for (x in listOf(8.4f, 10.8f, 14f, 16.4f)) {
            path(fill = ink) { moveTo(x, 15f); horizontalLineTo(x + 1.5f); verticalLineTo(20.5f); horizontalLineTo(x); close() }
        }
        // tail
        path(stroke = ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(7.2f, 11.2f); lineTo(4.6f, 8.6f) }
        // leash: from the collar up to a hand loop
        path(stroke = ink, strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round) {
            moveTo(18.3f, 10f); quadTo(10f, 2.2f, 4.2f, 4.6f)
        }
        path(stroke = ink, strokeLineWidth = 1.2f) {
            moveTo(4.2f, 4.6f)
            arcTo(1.5f, 1.5f, 0f, true, false, 1.2f, 4.6f)
            arcTo(1.5f, 1.5f, 0f, true, false, 4.2f, 4.6f); close()
        }
    }.build()
}

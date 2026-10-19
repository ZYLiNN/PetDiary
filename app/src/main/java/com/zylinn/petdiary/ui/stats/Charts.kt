package com.zylinn.petdiary.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.domain.StatMetric
import com.zylinn.petdiary.domain.StatsCalculator
import com.zylinn.petdiary.domain.StatsResult
import kotlin.math.ceil

/** One color per pet; dark mode uses lighter tints. Pets beyond the palette wrap around (with a different marker shape). */
private val lightPalette = listOf(0xFF8B5E3C, 0xFF0F766E, 0xFF2563EB, 0xFFD97706, 0xFFDB2777, 0xFF7C3AED, 0xFF4D7C0F, 0xFFDC2626).map(::Color)
private val darkPalette = listOf(0xFFD4A373, 0xFF2DD4BF, 0xFF60A5FA, 0xFFFBBF24, 0xFFF472B6, 0xFFA78BFA, 0xFFA3E635, 0xFFF87171).map(::Color)

fun petColor(index: Int, dark: Boolean): Color = (if (dark) darkPalette else lightPalette)[index % lightPalette.size]

/** Marker shapes differ per pet so the lines stay distinguishable without color. */
enum class Marker { CIRCLE, SQUARE, DIAMOND, TRIANGLE }

fun petMarker(index: Int) = Marker.entries[(index / lightPalette.size + index) % Marker.entries.size]

private fun formatAxis(v: Double, metric: StatMetric): String = when {
    metric == StatMetric.COST && v >= 1000 -> RecordFormat.number(v / 1000) + "k"
    metric == StatMetric.COST || v >= 100 -> v.toLong().toString()
    else -> RecordFormat.number(v)
}

private fun lineAxis(metric: StatMetric, values: List<Double>): StatsCalculator.AxisScale =
    when {
        values.isEmpty() -> StatsCalculator.axisFromZero(0.0)
        metric == StatMetric.WEIGHT -> StatsCalculator.axisAround(values.min(), values.max())
        else -> StatsCalculator.axisFromZero(values.max())
    }

private fun DrawScope.drawMarker(marker: Marker, c: Offset, r: Float, color: Color) {
    when (marker) {
        Marker.CIRCLE -> drawCircle(color, r, c)
        Marker.SQUARE -> drawRect(color, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
        Marker.DIAMOND -> drawPath(Path().apply { moveTo(c.x, c.y - r * 1.3f); lineTo(c.x + r * 1.3f, c.y); lineTo(c.x, c.y + r * 1.3f); lineTo(c.x - r * 1.3f, c.y); close() }, color)
        Marker.TRIANGLE -> drawPath(Path().apply { moveTo(c.x, c.y - r * 1.3f); lineTo(c.x + r * 1.2f, c.y + r); lineTo(c.x - r * 1.2f, c.y + r); close() }, color)
    }
}

private fun DrawScope.drawGridAndLabels(
    axis: StatsCalculator.AxisScale, metric: StatMetric, result: StatsResult, measurer: TextMeasurer,
    left: Float, top: Float, plotW: Float, plotH: Float, grid: Color, text: Color, slotCenter: (Int) -> Float,
) {
    val style = TextStyle(color = text, fontSize = 10.sp)
    val ticks = axis.ticks
    for (i in 0..ticks) {
        val y = top + plotH - plotH * i / ticks
        drawLine(grid, Offset(left, y), Offset(left + plotW, y), strokeWidth = 1f)
        val label = measurer.measure(formatAxis(axis.value(i), metric), style)
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
    }
    val n = result.buckets.size
    val step = maxOf(1, ceil(n / 6.0).toInt())
    result.buckets.forEachIndexed { i, b ->
        if (i % step == 0 || i == n - 1 && (n - 1) % step >= step / 2 + 1) {
            val label = measurer.measure(b.label, style)
            val x = (slotCenter(i) - label.size.width / 2f).coerceIn(left, left + plotW - label.size.width)
            drawText(label, topLeft = Offset(x, top + plotH + 4.dp.toPx()))
        }
    }
}

@Composable
private fun isDark() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Line chart (feeding, walk, weight): one line per pet, gaps where there is no data. */
@Composable
fun LineChart(result: StatsResult, pets: List<StatsPet>, description: String, modifier: Modifier = Modifier) {
    val dark = isDark()
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val axis = lineAxis(result.metric, result.series.flatMap { s -> s.values.filterNotNull() })
    Canvas(modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
        val left = 40.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - left - 8.dp.toPx()
        val plotH = size.height - top - 24.dp.toPx()
        val n = result.buckets.size
        fun x(i: Int) = if (n == 1) left + plotW / 2 else left + plotW * i / (n - 1)
        fun y(v: Double) = top + plotH - (plotH * ((v - axis.lo) / (axis.hi - axis.lo))).toFloat()
        drawGridAndLabels(axis, result.metric, result, measurer, left, top, plotW, plotH, grid, text, ::x)
        result.series.forEach { s ->
            val p = pets.first { it.id == s.petId }
            val color = petColor(p.colorIndex, dark)
            val path = Path()
            var open = false
            s.values.forEachIndexed { i, v ->
                if (v == null) { open = false; return@forEachIndexed }
                if (!open) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                open = true
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            if (n <= 31 || s.values.count { it != null } <= 24) {
                s.values.forEachIndexed { i, v -> if (v != null) drawMarker(petMarker(p.colorIndex), Offset(x(i), y(v)), 3.5.dp.toPx(), color) }
            }
        }
    }
}

/** Stacked bars (cost, counts): one segment per pet in each time bucket. */
@Composable
fun StackedBarChart(result: StatsResult, pets: List<StatsPet>, description: String, modifier: Modifier = Modifier) {
    val dark = isDark()
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val totals = result.buckets.indices.map { i -> result.series.sumOf { it.values[i] ?: 0.0 } }
    val axis = StatsCalculator.axisFromZero(totals.maxOrNull() ?: 0.0)
    Canvas(modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
        val left = 40.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - left - 8.dp.toPx()
        val plotH = size.height - top - 24.dp.toPx()
        val n = result.buckets.size
        val slot = plotW / n
        fun cx(i: Int) = left + slot * i + slot / 2
        drawGridAndLabels(axis, result.metric, result, measurer, left, top, plotW, plotH, grid, text, ::cx)
        val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        for (i in 0 until n) {
            var base = top + plotH
            result.series.forEach { s ->
                val v = s.values[i] ?: 0.0
                if (v > 0) {
                    val h = (plotH * (v / axis.hi)).toFloat()
                    val color = petColor(pets.first { it.id == s.petId }.colorIndex, dark)
                    drawRect(color, Offset(cx(i) - barW / 2, base - h), Size(barW, h))
                    // A thin gap between stacked segments keeps neighbouring colors apart.
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(cx(i) - barW / 2, base - h), Offset(cx(i) + barW / 2, base - h), 1f)
                    base -= h
                }
            }
        }
    }
}

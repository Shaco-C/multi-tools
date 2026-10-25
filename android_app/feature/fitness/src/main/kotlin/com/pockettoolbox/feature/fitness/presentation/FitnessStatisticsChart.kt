package com.pockettoolbox.feature.fitness.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettoolbox.feature.fitness.domain.FitnessCount
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Four equal integer intervals; use Long so large historical counts cannot overflow. */
internal fun fitnessChartMaximum(value: Int): Long {
    val rawStep = value.coerceAtLeast(1) / 4.0
    val power = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * power >= rawStep }.times(power).coerceAtLeast(1.0)
    return ceil(step).toLong() * 4
}

/** All bars share one numeric axis and viewport. Long exercise lists are paged by the caller. */
@Composable
internal fun FitnessStatisticsChart(rows: List<FitnessCount>, byDays: Boolean, maximum: Long,
    selected: String?, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    val unit = if (byDays) "天次" else "组 / 段"
    val description = "训练统计坐标图，横轴$unit，量程0到$maximum；" + rows.joinToString("；") { "${it.name} ${if (byDays) it.days else it.segments}" }
    Canvas(Modifier.fillMaxWidth().height(280.dp).testTag("fitness-statistics-chart")
        .semantics { contentDescription = description }
        .pointerInput(rows, byDays) {
            detectTapGestures { point ->
                val rowHeight = (size.height - 48.dp.toPx()) / rows.size.coerceAtLeast(1)
                val index = floor((point.y - 12.dp.toPx()) / rowHeight).toInt()
                if (point.y >= 12.dp.toPx() && index in rows.indices) select(rows[index].key)
            }
        }) {
        val left = 88.dp.toPx()
        val right = size.width - 34.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 36.dp.toPx()
        val plotWidth = (right - left).coerceAtLeast(1f)
        val rowHeight = (bottom - top) / rows.size.coerceAtLeast(1)
        val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp)
        repeat(5) { tick ->
            val x = left + plotWidth * tick / 4
            drawLine(colors.outlineVariant, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            val label = measurer.measure((maximum * tick / 4).toString(), labelStyle)
            drawText(label, topLeft = Offset(x - label.size.width / 2, bottom + 6.dp.toPx()))
        }
        drawLine(colors.outline, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
        drawLine(colors.outline, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        rows.forEachIndexed { index, row ->
            val center = top + rowHeight * (index + .5f)
            val isSelected = row.key == selected
            if (isSelected) drawRoundRect(colors.primaryContainer.copy(alpha = .45f),
                Offset(0f, center - rowHeight / 2 + 1.dp.toPx()), Size(size.width, rowHeight - 2.dp.toPx()), CornerRadius(5.dp.toPx()))
            val label = measurer.measure(row.name, labelStyle.copy(color = colors.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (left - 8.dp.toPx()).toInt()))
            drawText(label, topLeft = Offset(0f, center - label.size.height / 2))
            val value = if (byDays) row.days else row.segments
            val width = plotWidth * (value.toDouble() / maximum).coerceIn(0.0, 1.0).toFloat()
            drawRoundRect(if (isSelected) colors.secondary else colors.secondary.copy(alpha = .65f),
                Offset(left, center - 6.dp.toPx()), Size(width, 12.dp.toPx()), CornerRadius(3.dp.toPx()))
            val number = measurer.measure(value.toString(), labelStyle.copy(color = colors.onSurface, fontWeight = FontWeight.Bold))
            val numberX = (left + width + 4.dp.toPx()).coerceAtMost(size.width - number.size.width)
            drawText(number, topLeft = Offset(numberX, center - number.size.height / 2))
        }
        val axis = measurer.measure(unit, labelStyle)
        drawText(axis, topLeft = Offset(size.width - axis.size.width, size.height - axis.size.height))
    }
}

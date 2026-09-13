package com.pockettoolbox.feature.electricity.presentation.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.presentation.ElectricityHeader
import com.pockettoolbox.feature.electricity.presentation.ElectricitySection
import com.pockettoolbox.feature.electricity.presentation.ElectricitySectionBar
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
internal fun ElectricityTrendRoute(
    repository: ElectricityBillRepository,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    viewModel: ElectricityTrendViewModel = viewModel(
        factory = ElectricityTrendViewModelFactory(repository),
    ),
) {
    ElectricityTrendScreen(
        state = viewModel.uiState,
        onBack = onBack,
        onNavigate = onNavigate,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun ElectricityTrendScreen(
    state: ElectricityTrendUiState,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    onRetry: () -> Unit,
) {
    val errorMessage = state.errorMessage
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) {
            item(key = "header") {
                ElectricityHeader(onBack = onBack, backDescription = "返回计算页")
                ElectricitySectionBar(active = ElectricitySection.Trend, onSelect = onNavigate)
                Spacer(Modifier.height(22.dp))
            }

            when {
                state.isLoading -> item(key = "loading") { TrendLoading() }
                errorMessage != null -> item(key = "error") { TrendError(errorMessage, onRetry) }
                state.points.isEmpty() -> item(key = "empty") {
                    EmptyTrend(onStartCalculating = { onNavigate(ElectricitySection.Calculator) })
                }
                else -> {
                    item(key = "summary") {
                        TrendSummary(state)
                        Spacer(Modifier.height(18.dp))
                        TrendChartCard(state.points)
                        if (state.points.size == 1) {
                            Text(
                                "再保存一个月份后即可看到费用变化线。",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "月份明细",
                            modifier = Modifier.padding(top = 26.dp, bottom = 3.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "仅统计每份账单中标记为“我的电表”的费用",
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(
                        items = state.points.asReversed(),
                        key = ElectricityTrendPoint::billId,
                    ) { point ->
                        TrendMonthRow(point, Modifier.padding(bottom = 10.dp))
                    }
                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TrendSummary(state: ElectricityTrendUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolboxColors.NavyDeep, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Text("我的费用趋势", color = Color(0xFFB7CED4), style = MaterialTheme.typography.labelLarge)
        Text(
            "¥ ${state.latestAmount?.formatYuan()}",
            modifier = Modifier.padding(top = 7.dp),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            latestChangeText(state),
            modifier = Modifier.padding(top = 4.dp),
            color = if ((state.latestChangeCents ?: 0L) > 0L) Color(0xFFFFC6BD) else ToolboxColors.TealBright,
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(Modifier.padding(vertical = 15.dp), color = Color.White.copy(alpha = 0.12f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryMetric("已记录", "${state.points.size} 个月", Modifier.weight(1f))
            SummaryMetric("平均费用", "¥ ${state.averageAmount?.formatYuan()}", Modifier.weight(1f), Alignment.CenterHorizontally)
            SummaryMetric("最高费用", "¥ ${state.highestAmount?.formatYuan()}", Modifier.weight(1f), Alignment.End)
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(label, color = Color(0xFFB7CED4), style = MaterialTheme.typography.labelSmall)
        Text(value, modifier = Modifier.padding(top = 3.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrendChartCard(allPoints: List<ElectricityTrendPoint>) {
    val points = allPoints.takeLast(MAX_CHART_POINTS)
    val lineColor = ToolboxColors.Teal
    val dotColor = ToolboxColors.TealBright
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = labelColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
    val description = points.joinToString("，") {
        "${it.billingMonth.year}年${it.billingMonth.monthValue}月${it.ownerAmount.formatYuan()}元"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最近 ${points.size} 条记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (allPoints.size > MAX_CHART_POINTS) {
                    Text("共 ${allPoints.size} 条", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(top = 18.dp)
                    .semantics { contentDescription = "我的费用折线图：$description" },
            ) {
                val min = points.minOf { it.ownerAmount.cents }
                val max = points.maxOf { it.ownerAmount.cents }
                val firstMonth = points.first().billingMonth
                val monthSpan = ChronoUnit.MONTHS.between(firstMonth, points.last().billingMonth).coerceAtLeast(1L)
                val horizontalInset = 8.dp.toPx()
                val verticalInset = 38.dp.toPx()
                val bottomInset = 12.dp.toPx()
                val plotWidth = size.width - horizontalInset * 2
                val plotHeight = size.height - verticalInset - bottomInset

                repeat(3) { index ->
                    val y = verticalInset + plotHeight * index / 2f
                    drawLine(gridColor, Offset(horizontalInset, y), Offset(size.width - horizontalInset, y), strokeWidth = 1.dp.toPx())
                }

                fun position(point: ElectricityTrendPoint): Offset {
                    val monthOffset = ChronoUnit.MONTHS.between(firstMonth, point.billingMonth)
                    val x = if (points.size == 1) {
                        size.width / 2f
                    } else {
                        horizontalInset + plotWidth * monthOffset.toFloat() / monthSpan.toFloat()
                    }
                    val y = if (max == min) {
                        verticalInset + plotHeight / 2f
                    } else {
                        verticalInset + plotHeight * (max - point.ownerAmount.cents).toFloat() / (max - min).toFloat()
                    }
                    return Offset(x.toFloat(), y)
                }

                points.zipWithNext().forEach { (start, end) ->
                    // 缺少中间月份时不连线，避免把不连续账单表现成连续趋势。
                    if (ChronoUnit.MONTHS.between(start.billingMonth, end.billingMonth) == 1L) {
                        drawLine(lineColor, position(start), position(end), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                points.forEachIndexed { index, point ->
                    val center = position(point)
                    drawCircle(color = dotColor, radius = 5.dp.toPx(), center = center)
                    drawCircle(color = lineColor, radius = 2.dp.toPx(), center = center)

                    val label = textMeasurer.measure(point.ownerAmount.formatYuan(), style = labelStyle)
                    val horizontalPadding = 4.dp.toPx()
                    val verticalPadding = 2.dp.toPx()
                    val labelLeft = (center.x - label.size.width / 2f).coerceIn(
                        horizontalPadding,
                        (size.width - label.size.width - horizontalPadding).coerceAtLeast(horizontalPadding),
                    )
                    val stagger = if (index % 2 == 0) 8.dp.toPx() else 25.dp.toPx()
                    val labelTop = (center.y - label.size.height - stagger).coerceAtLeast(verticalPadding)
                    drawRoundRect(
                        color = labelBackground,
                        topLeft = Offset(labelLeft - horizontalPadding, labelTop - verticalPadding),
                        size = Size(
                            label.size.width + horizontalPadding * 2,
                            label.size.height + verticalPadding * 2,
                        ),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                    )
                    drawText(label, topLeft = Offset(labelLeft, labelTop))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().billingMonth.shortLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (points.size > 1) {
                    Text(points.last().billingMonth.shortLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "缺少账单的月份会留空，不会虚构费用数据。",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TrendMonthRow(point: ElectricityTrendPoint, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${point.billingMonth.year} 年 ${point.billingMonth.monthValue} 月", fontWeight = FontWeight.Bold)
            Text("¥ ${point.ownerAmount.formatYuan()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun TrendLoading() {
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ToolboxColors.Teal)
    }
}

@Composable
private fun EmptyTrend(onStartCalculating: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("还没有费用趋势", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "保存账单后，这里只会统计“我的电表”对应的费用。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onStartCalculating, modifier = Modifier.padding(top = 20.dp)) { Text("去计算电费") }
    }
}

@Composable
private fun TrendError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("费用趋势读取失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text("重新读取") }
    }
}

private fun latestChangeText(state: ElectricityTrendUiState): String {
    val change = state.latestChangeCents ?: return "保存第二个月后显示环比变化"
    val sign = when {
        change > 0L -> "+"
        change < 0L -> "-"
        else -> ""
    }
    val amount = BigDecimal.valueOf(abs(change)).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)
    val percent = state.latestChangePercent?.stripTrailingZeros()?.toPlainString()
    return if (percent == null) {
        "较上次账单 $sign¥ $amount（上次费用为 0）"
    } else {
        "较上次账单 $sign¥ $amount（${if (change > 0L) "+" else ""}$percent%）"
    }
}

private fun YearMonth.shortLabel() = "${year}/${monthValue.toString().padStart(2, '0')}"

private const val MAX_CHART_POINTS = 12

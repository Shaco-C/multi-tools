package com.pockettoolbox.feature.diet.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.diet.domain.*
import java.time.format.DateTimeFormatter

internal val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
internal val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun DietCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
    }
}

@Composable
internal fun DietHint(title: String, message: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f), RoundedCornerShape(17.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun DietSummary(values: List<Pair<String, Int>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { (label, number) ->
            Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(number.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun DietChoice(label: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && choices.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                Text(choices.find { it.first == selected }?.second ?: if (choices.isEmpty()) "暂无选项，请先在管理中添加" else "请选择", modifier = Modifier.weight(1f))
                Text("▾", modifier = Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 310.dp)) {
                choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) }) }
            }
        }
    }
}

@Composable
internal fun MealRow(data: DietData, meal: DietMeal, busy: Boolean, onSelect: () -> Unit, onResume: () -> Unit, scope: List<DiscomfortEvent>? = null) {
    val events = (scope ?: data.events).filter { it.mealId == meal.id }
    DietCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandSymbol(data.brandName(meal.brandId))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${data.brandName(meal.brandId)} · ${data.foodName(meal.foodId)}", fontWeight = FontWeight.Bold)
                Text("${meal.eatenOn}进食 · ${if (meal.finished) "已结束" else "可追加"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${if (scope != null) "筛选内 " else ""}${events.size} 次不适 · ${events.map { data.typeName(it.typeId) }.distinct().joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (meal.finished) TextButton(onClick = onResume, enabled = !busy) { Text("恢复本次记录") }
        else TextButton(onClick = onSelect, enabled = !busy) {
            Text(if (data.activeMealId == meal.id) "正在关联这顿饮食" else "切换到这顿饮食")
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
internal fun BrandSymbol(name: String) {
    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        Text(name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun EventList(data: DietData, events: List<DiscomfortEvent>, busy: Boolean, onEdit: (DiscomfortEvent) -> Unit, onDelete: (DiscomfortEvent) -> Unit) {
    var limit by remember(events.map { it.id }) { mutableIntStateOf(30) }
    if (events.isEmpty()) Text("这里还没有不适记录，出现不适时再记就好。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    events.sortedByDescending { it.occurredAt }.take(limit).forEach { event ->
        val meal = data.meals.first { it.id == event.mealId }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${event.localTime().format(TimeFormat)} · ${event.localTime().toLocalDate()}", fontWeight = FontWeight.Bold)
                Text("${data.typeName(event.typeId)} · ${data.brandName(meal.brandId)} / ${data.foodName(meal.foodId)}", style = MaterialTheme.typography.bodySmall)
                Text("进食：${meal.eatenOn}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onEdit(event) }, enabled = !busy) { Icon(Icons.Default.Edit, contentDescription = "修改这次不适", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { onDelete(event) }, enabled = !busy) { Icon(Icons.Default.DeleteOutline, contentDescription = "删除这次不适", modifier = Modifier.size(18.dp)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
    if (events.size > limit) TextButton(onClick = { limit += 30 }) { Text("继续查看（还有 ${events.size - limit} 条）") }
}

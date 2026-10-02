package com.pockettoolbox.feature.diet.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettoolbox.feature.diet.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
internal fun MealDraftDialog(vm: DietViewModel, onManage: () -> Unit) {
    val draft = vm.draft ?: return
    val data = vm.data
    val edit = draft.mealId != null
    val brands = data.brands.filter { !it.archived || (edit && it.id == draft.brandId) }
    val foods = data.foods.filter { it.brandId == draft.brandId && (!it.archived || (edit && it.id == draft.foodId)) }
    AlertDialog(onDismissRequest = vm::cancelDraft, shape = RoundedCornerShape(23.dp),
        title = { Text(if (edit) "修改这顿饮食" else "记下这次不适", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("只记不适饮食，正常饮食不用登记。", style = MaterialTheme.typography.bodySmall)
                if (!edit) {
                    val time = Instant.ofEpochMilli(draft.occurredAt).atOffset(ZoneOffset.ofTotalSeconds(draft.offsetSeconds))
                    Text("自动发生时间：${time.format(DateFormat)} ${time.format(TimeFormat)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                DietChoice("品牌", draft.brandId, brands.map { it.id to it.name }, !vm.busy) { id ->
                    val food = data.foods.firstOrNull { it.brandId == id && !it.archived }
                    vm.updateDraft(draft.copy(brandId = id, foodId = food?.id.orEmpty()))
                }
                DietChoice("食品", draft.foodId, foods.map { it.id to it.name }, !vm.busy) { vm.updateDraft(draft.copy(foodId = it)) }
                TextButton(onClick = onManage, enabled = !vm.busy) { Text("没找到？去管理常用选项 →", style = MaterialTheme.typography.labelSmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("今天" to 0L, "昨天" to 1L, "前天" to 2L).forEach { (label, days) ->
                        val date = LocalDate.now().minusDays(days).toString()
                        FilterChip(selected = draft.eatenOn == date, onClick = { vm.updateDraft(draft.copy(eatenOn = date)) }, enabled = !vm.busy, label = { Text(label) })
                    }
                }
                DietDateField("进食日期", draft.eatenOn, !vm.busy) { vm.updateDraft(draft.copy(eatenOn = it)) }
                if (!edit) DietChoice("不适类型", draft.typeId, data.types.filterNot { it.archived }.map { it.id to it.name }, !vm.busy) { vm.updateDraft(draft.copy(typeId = it)) }
                OutlinedTextField(draft.note, { if (it.length <= 500) vm.updateDraft(draft.copy(note = it)) }, label = { Text("备注（选填）") },
                    placeholder = { Text("例如：十翅一桶，晚上吃的") }, maxLines = 3, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                val mealIds = data.meals.filter { it.foodId == draft.foodId }.map { it.id }.toSet()
                val history = data.events.filter { it.mealId in mealIds }
                if (history.isNotEmpty()) Text("这个食品曾记录 ${history.size} 次不适，关联 ${history.map { it.mealId }.distinct().size} 顿饮食。",
                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                vm.draftError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = vm::saveDraft, enabled = !vm.busy && draft.brandId.isNotBlank() && draft.foodId.isNotBlank()) { Text(if (edit) "保存修改" else "保存这次不适") } },
        dismissButton = { TextButton(onClick = vm::cancelDraft, enabled = !vm.busy) { Text("取消") } },
    )
}

@Composable
internal fun DietDateField(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
            DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth)
                .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text(value) }
    }
}

@Composable
internal fun CatalogDialog(vm: DietViewModel, kind: CatalogKind, id: String?, brandId: String?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val data = vm.data
    val existing = when (kind) { CatalogKind.Brand -> data.brands.find { it.id == id }?.name; CatalogKind.Food -> data.foods.find { it.id == id }?.name; CatalogKind.Type -> data.types.find { it.id == id }?.name }
    var name by rememberSaveable(kind, id) { mutableStateOf(existing.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val label = when (kind) { CatalogKind.Brand -> "品牌"; CatalogKind.Food -> "食品"; CatalogKind.Type -> "不适类型" }
    AlertDialog(onDismissRequest = { if (!vm.busy) onDismiss() }, title = { Text("${if (id == null) "新增" else "修改"}$label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (kind == CatalogKind.Food) Text("添加到：${data.brandName(brandId.orEmpty())}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { if (it.length <= 40) { name = it; error = null } }, label = { Text("${label}名称") }, singleLine = true, enabled = !vm.busy)
                Text(if (id == null) "填写一次，以后直接选择。" else "改名会同步更新历史显示，次数不变。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { vm.saveCatalog(kind, id, name, brandId, onSaved) { error = it } }, enabled = !vm.busy && name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("取消") } })
}

@Composable
internal fun EventEditDialog(vm: DietViewModel, event: DiscomfortEvent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable(event.id) { mutableStateOf(event.localTime().toLocalDate().toString()) }
    var time by rememberSaveable(event.id) { mutableStateOf(event.localTime().toLocalTime().format(TimeFormat)) }
    var typeId by rememberSaveable(event.id) { mutableStateOf(event.typeId) }
    var error by rememberSaveable(event.id) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!vm.busy) onDismiss() }, title = { Text("修改这次不适") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            DietChoice("不适类型", typeId, vm.data.types.filter { !it.archived || it.id == event.typeId }.map { it.id to it.name }, !vm.busy) { typeId = it }
            DietDateField("发生日期", date, !vm.busy) { date = it }
            OutlinedButton(onClick = {
                val parsed = LocalTime.parse(time)
                TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute).format(TimeFormat) }, parsed.hour, parsed.minute, true).show()
            }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("发生时间：$time") }
            Text("自动记录后，可在这里修正类型和时间。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val at = LocalDate.parse(date).atTime(LocalTime.parse(time)).toInstant(ZoneOffset.ofTotalSeconds(event.offsetSeconds)).toEpochMilli()
            vm.editEvent(event.copy(typeId = typeId, occurredAt = at), onDismiss) { error = it }
        }, enabled = !vm.busy) { Text("保存修改") }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("取消") } })
}

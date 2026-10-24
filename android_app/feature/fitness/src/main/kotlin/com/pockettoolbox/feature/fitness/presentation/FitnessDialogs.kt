package com.pockettoolbox.feature.fitness.presentation

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pockettoolbox.feature.fitness.data.FitnessSourceExercise
import com.pockettoolbox.feature.fitness.domain.*
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun FitnessDateField(label: String, date: String, enabled: Boolean = true, onDate: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val initial = runCatching { FitnessRules.date(date) }.getOrElse { FitnessRules.date(fitnessToday()) }
        DatePickerDialog(context, { _, year, month, day -> onDate(java.time.LocalDate.of(year, month + 1, day).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label · $date") }
}

internal fun fitnessEventTime(event: FitnessSetEvent): String = when (event.timeSource) {
    FitnessTimeSource.DateOnly -> "${event.trainingDate} · 仅日期补记"
    FitnessTimeSource.User -> "${event.trainingDate} ${Instant.ofEpochMilli(requireNotNull(event.actualAt)).atZone(ZoneId.of(event.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm"))} · 手填时刻"
    FitnessTimeSource.Realtime -> "${event.trainingDate} · 记录于${Instant.ofEpochMilli(event.recordedAt).atZone(ZoneId.of(event.zoneId)).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}"
}

@Composable
internal fun FitnessBackfillDialog(vm: FitnessViewModel, row: FitnessScheduledItem, onDismiss: () -> Unit) {
    val remaining = (row.item.targetSets ?: 0) - row.completed
    var count by rememberSaveable(row.item.id) { mutableStateOf(remaining.coerceAtLeast(1).toString()) }
    var time by rememberSaveable(row.item.id) { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!vm.busy) onDismiss() }, title = { Text("补记${row.day.date}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${row.item.exercise.name} · 已完成${row.completed}/${row.item.targetSets}\n只记录确认已做的组/段，实际次数可稍后逐组填写。")
        OutlinedTextField(count, { count = it.take(3) }, label = { Text("补记组/段数（最多$remaining）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !vm.busy)
        OutlinedTextField(time, { time = it.take(5) }, label = { Text("实际时刻 HH:mm（选填）") }, supportingText = { Text("留空只记录日期，不推断训练时刻。") }, singleLine = true, enabled = !vm.busy)
        error?.let { FitnessHint(it, true) }
    } }, confirmButton = { TextButton(onClick = {
        try {
            val parsed = FitnessRules.positive(count, remaining, "补记数")
            val at = time.trim().takeIf { it.isNotEmpty() }?.let { text ->
                require(Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(text)) { "时刻格式须为HH:mm" }
                FitnessRules.date(row.day.date).atTime(LocalTime.parse(text)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            vm.complete(row, parsed, backfill = true, actualAt = at, after = onDismiss)
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }, enabled = !vm.busy && remaining > 0) { Text("确认补记") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("取消") } })
}

@Composable
internal fun FitnessRecordDialog(vm: FitnessViewModel, title: String, rows: List<FitnessSetEvent>, onDismiss: () -> Unit,
    onActual: (FitnessSetEvent) -> Unit, onDay: (FitnessSetEvent) -> Unit, onDetail: (String) -> Unit, onMore: (() -> Unit)? = null) {
    var revokeId by rememberSaveable { mutableStateOf<String?>(null) }
    var batchId by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!vm.busy) onDismiss() }, title = { Text(title) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isEmpty()) item { Text("当前范围没有训练记录。") }
            items(rows, key = { it.id }) { event -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${event.exercise.name} · ${if (event.isValid()) "第${event.ordinal}组/段" else "已撤销"}", fontWeight = FontWeight.Bold)
                Text(fitnessEventTime(event), style = MaterialTheme.typography.labelSmall)
                Text("当时目标 ${event.targetSets} × ${event.targetValue}${event.exercise.unit.label}${if (event.exercise.side == FitnessSide.Each) "/侧" else ""}")
                Text(if (event.exercise.side == FitnessSide.Each) "实际：左${event.actual.left ?: "未填"} / 右${event.actual.right ?: "未填"}${event.exercise.unit.label}" else "实际：${event.actual.total ?: "未填"}${event.exercise.unit.label}")
                Row { TextButton(onClick = { onDay(event) }, enabled = !vm.busy) { Text("当日") }; TextButton(onClick = { onDetail(event.exercise.exerciseId) }, enabled = !vm.busy) { Text("教学") }
                    TextButton(onClick = { onActual(event) }, enabled = !vm.busy && event.isValid()) { Text("填实际") } }
                if (event.isValid()) Row { TextButton(onClick = { revokeId = event.id }, enabled = !vm.busy) { Text("撤销本组") }
                    event.batchId?.let { batch -> TextButton(onClick = { batchId = batch }, enabled = !vm.busy) { Text("撤销这批补记") } } }
                HorizontalDivider()
            } }
            if (onMore != null) item { TextButton(onClick = onMore) { Text("加载更多记录") } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("关闭") } })
    if (revokeId != null || batchId != null) AlertDialog(onDismissRequest = { revokeId = null; batchId = null }, title = { Text("确认撤销？") }, text = { Text("撤销会扣回进度与统计，审计记录保留。整批撤销仅影响该批剩余有效记录。") },
        confirmButton = { TextButton(onClick = { val batch = batchId; if (batch != null) vm.revokeBatch(batch) else rows.firstOrNull { it.id == revokeId }?.let(vm::revoke); revokeId = null; batchId = null }, enabled = !vm.busy) { Text("撤销") } },
        dismissButton = { TextButton(onClick = { revokeId = null; batchId = null }) { Text("取消") } })
}

@Composable
internal fun FitnessActualDialog(vm: FitnessViewModel, event: FitnessSetEvent, onDismiss: () -> Unit) {
    var total by rememberSaveable(event.id) { mutableStateOf(event.actual.total?.toString().orEmpty()) }
    var left by rememberSaveable(event.id) { mutableStateOf(event.actual.left?.toString().orEmpty()) }
    var right by rememberSaveable(event.id) { mutableStateOf(event.actual.right?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!vm.busy) onDismiss() }, title = { Text("${event.exercise.name} · 实际${event.exercise.unit.label}数") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("留空表示未记录。清空已有值后保存会移除该实际值；目标值仍保留。")
        if (event.exercise.side == FitnessSide.Each) {
            OutlinedTextField(left, { left = it.take(5) }, label = { Text("左侧") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !vm.busy)
            OutlinedTextField(right, { right = it.take(5) }, label = { Text("右侧") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !vm.busy)
        } else OutlinedTextField(total, { total = it.take(5) }, label = { Text("实际值") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !vm.busy)
        error?.let { FitnessHint(it, true) }
    } }, confirmButton = { TextButton(onClick = {
        try {
            fun parse(value: String) = if (value.isBlank()) null else FitnessRules.positive(value, event.exercise.unit.maximum, "实际值")
            val actual = if (event.exercise.side == FitnessSide.Each) FitnessActual(left = parse(left), right = parse(right)) else FitnessActual(total = parse(total))
            vm.actual(event, actual, onDismiss)
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }, enabled = !vm.busy) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("取消") } })
}

@Composable
internal fun FitnessCustomDialog(vm: FitnessViewModel, original: FitnessExercise?, source: FitnessSourceExercise?, add: Boolean,
    scope: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val editing = original?.personal == true
    val id by rememberSaveable { mutableStateOf(if (editing) requireNotNull(original).id else "USR-${fitnessId()}") }
    var name by rememberSaveable { mutableStateOf(original?.name ?: source?.name.orEmpty()) }
    var purpose by rememberSaveable { mutableStateOf(original?.purpose?.name ?: if (source != null) "" else scope.takeIf { it.startsWith("purpose:") }?.substringAfter(':') ?: FitnessPurpose.Strength.name) }
    var part by rememberSaveable { mutableStateOf(original?.primaryPart?.name ?: scope.takeIf { it.startsWith("part:") }?.substringAfter(':').orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(original?.unit?.name ?: if (source != null) "" else FitnessUnit.Reps.name) }
    var each by rememberSaveable { mutableStateOf(original?.side == FitnessSide.Each) }
    var equipment by rememberSaveable { mutableStateOf(original?.equipment?.firstOrNull().orEmpty()) }
    var keepAlternatives by rememberSaveable { mutableStateOf(editing && (original?.equipment?.size ?: 0) > 1) }
    var introduction by rememberSaveable { mutableStateOf(original?.introduction.orEmpty()) }
    var steps by rememberSaveable { mutableStateOf(original?.steps?.joinToString("\n") ?: source?.instructions?.joinToString("\n").orEmpty()) }
    var aliases by rememberSaveable { mutableStateOf(original?.aliases?.joinToString("、").orEmpty()) }
    var imagePath by rememberSaveable { mutableStateOf(if (editing) original?.images?.firstOrNull()?.path.orEmpty() else "") }
    var changedImage by rememberSaveable { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }; var leave by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun close() { if (!vm.busy) { if (dirty) leave = true else onDismiss() } }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.image({ requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选图片" } }) { image -> imagePath = image.path; changedImage = true; dirty = true }
    }
    AlertDialog(onDismissRequest = ::close, title = { Text(if (editing) "编辑我的动作" else "新建我的动作") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (source != null) FitnessHint("来源资料为英文原始内容。请确认用途、单位、左右口径、主部位和器械；自建内容不代表专业审核通过。")
            if (editing) FitnessHint("修改只用于后续新安排；已有安排和记录采用当时快照。")
            OutlinedTextField(name, { name = it.take(60); dirty = true }, label = { Text("动作名称 *") }, enabled = !vm.busy)
            FitnessChoice("用途 *", purpose, FitnessPurpose.entries.map { it.name to it.label }, { purpose = it; dirty = true }, !vm.busy)
            if (purpose == FitnessPurpose.Strength.name) FitnessChoice("主部位 *", part, FitnessPart.entries.map { it.name to it.label }, { part = it; dirty = true }, !vm.busy)
            FitnessChoice("计量单位 *", unit, FitnessUnit.entries.map { it.name to it.label }, { unit = it; dirty = true }, !vm.busy)
            Row { Checkbox(each, { each = it; dirty = true }, enabled = !vm.busy); Text("每侧分别计量", Modifier.padding(top = 12.dp)) }
            Text("所需器械 *（本组全部具备才匹配；徒手勾选地面空间）")
            if (keepAlternatives) { Text("保留原动作的多组替代器械条件"); TextButton(onClick = { keepAlternatives = false; dirty = true }) { Text("改为单组条件") } }
            else FitnessEquipmentLabels.forEach { (key, label) -> Row { Checkbox(key in equipment, { checked -> equipment = if (checked) equipment + key else equipment - key; dirty = true }, enabled = !vm.busy); Text(label, Modifier.padding(top = 12.dp)) } }
            OutlinedTextField(introduction, { introduction = it.take(6000); dirty = true }, label = { Text("动作介绍（选填）") }, minLines = 2, enabled = !vm.busy)
            OutlinedTextField(steps, { steps = it.take(12000); dirty = true }, label = { Text("动作步骤，一行一步（选填）") }, minLines = 3, enabled = !vm.busy)
            OutlinedTextField(aliases, { aliases = it.take(600); dirty = true }, label = { Text("别名，用顿号分隔（选填）") }, enabled = !vm.busy)
            Text(if (imagePath.isBlank()) "尚未添加个人图片" else "个人图片已保存")
            Row { TextButton(onClick = { imagePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = !vm.busy) { Text("选择图片") }
                TextButton(onClick = { imagePath = ""; changedImage = true; dirty = true }, enabled = !vm.busy && imagePath.isNotBlank()) { Text("移除图片") } }
            val sourceImage = source?.images?.firstOrNull() ?: original?.images?.firstOrNull()?.takeIf { !it.path.startsWith("personal/") }
            if (sourceImage != null) TextButton(onClick = { vm.image({ context.assets.open("fitness/${sourceImage.path}") }) { imagePath = it.path; changedImage = true; dirty = true } }, enabled = !vm.busy) { Text("复制来源首张图到个人资料") }
            error?.let { FitnessHint(it, true) }
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            require(purpose.isNotBlank() && unit.isNotBlank()) { "请选择用途与计量单位" }
            val value = FitnessExercise(id, FitnessRules.name(name), original?.english ?: source?.name.orEmpty(), aliases.split('、').map(String::trim).filter(String::isNotBlank), FitnessPurpose.valueOf(purpose),
                part.takeIf { purpose == FitnessPurpose.Strength.name && it.isNotBlank() }?.let(FitnessPart::valueOf), original?.secondaryParts.orEmpty(), FitnessUnit.valueOf(unit), if (each) FitnessSide.Each else FitnessSide.Total,
                if (keepAlternatives) requireNotNull(original).equipment else listOf(equipment), introduction, steps.lines().map(String::trim).filter(String::isNotBlank), original?.cues.orEmpty(),
                if (editing && !changedImage) requireNotNull(original).images else imagePath.takeIf(String::isNotBlank)?.let { listOf(FitnessImage(it)) }.orEmpty(), "personal-${System.currentTimeMillis()}",
                original?.references ?: source?.let { listOf("https://github.com/yuhonas/free-exercise-db/tree/${vm.catalog?.sourceRevision}/exercises/${it.id}") }.orEmpty(), personal = true, archived = original?.archived ?: false)
            FitnessRules.validateExercise(value)
            vm.saveExercise(value) { if (add) vm.toggleExercise(value); onDismiss() }
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }, enabled = !vm.busy) { Text(if (add) "保存并加入当天" else "保存") } }, dismissButton = { TextButton(onClick = ::close, enabled = !vm.busy) { Text("取消") } })
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("放弃此次填写？") }, text = { Text("个人动作尚未保存。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃") } }, dismissButton = { TextButton(onClick = { leave = false }) { Text("继续填写") } })
}

@Composable
internal fun FitnessAddDialog(vm: FitnessViewModel, exercise: FitnessExercise, onDismiss: () -> Unit,
    onEditor: (FitnessEditorRequest) -> Unit, onOpen: (String, String) -> Unit) {
    val plans = vm.data.plans.filter { it.status == FitnessPlanStatus.Active }
    var target by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(vm.today) }
    var sets by rememberSaveable { mutableStateOf("4") }; var value by rememberSaveable { mutableStateOf(if (exercise.unit == FitnessUnit.Seconds) "30" else "12") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val plan = plans.firstOrNull { it.id == target }
    val existing = plan?.day(date)?.items?.firstOrNull { it.exercise.exerciseId == exercise.id }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加${exercise.name}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FitnessChoice("目标计划", target, listOf("" to "新建单日计划") + plans.map { it.id to it.name }, { target = it; plans.firstOrNull { p -> p.id == it }?.let { p -> date = vm.today.takeIf { today -> today >= p.start && today <= p.end } ?: p.start } })
        FitnessDateField("训练日期", date, onDate = { date = it })
        if (existing == null) {
            OutlinedTextField(sets, { sets = it.take(3) }, label = { Text("组/段数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value, { value = it.take(4) }, label = { Text("${if (exercise.side == FitnessSide.Each) "每侧" else "每组/段"}${exercise.unit.label}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        } else Text(if (existing.status == FitnessItemStatus.Active) "当日已存在此动作，可直接查看原安排。" else "此动作已结束剩余安排，进入编辑可恢复原目标和条目。")
        if (!exercise.matchesEquipment(vm.data.preferences.equipment)) FitnessHint("器械条件与当前偏好不匹配，请先确认实际具备所需器械。", true)
        error?.let { FitnessHint(it, true) }
    } }, confirmButton = { TextButton(onClick = {
        try {
            require(plan == null || date in FitnessRules.dates(plan.start, plan.end)) { "日期须位于所选计划范围内" }
            if (existing?.status == FitnessItemStatus.Active && plan != null) onOpen(plan.id, date)
            else onEditor(FitnessEditorRequest(planId = plan?.id, date = date, exerciseId = exercise.id, step = 2,
                targetSets = if (existing == null) FitnessRules.positive(sets, 99, "组/段数") else existing.targetSets,
                targetValue = if (existing == null) FitnessRules.positive(value, exercise.unit.maximum, "目标") else existing.targetValue))
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }, enabled = !vm.busy) { Text(if (existing?.status == FitnessItemStatus.Active) "查看原安排" else "进入计划编辑") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun FitnessCopySavedDialog(vm: FitnessViewModel, source: FitnessPlanDay, onDismiss: () -> Unit, onEditor: (FitnessEditorRequest) -> Unit) {
    var target by rememberSaveable { mutableStateOf("") }; var date by rememberSaveable { mutableStateOf(vm.today) }
    var overwrite by rememberSaveable { mutableStateOf(false) }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    val plans = vm.data.plans.filter { it.status == FitnessPlanStatus.Active }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("复制${source.date}的安排") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("复制动作和目标，完成进度从0开始；先进入编辑检查，再保存生效。")
        FitnessChoice("目标", target, listOf("" to "新建单日计划") + plans.map { it.id to it.name }, { target = it })
        FitnessDateField("目标日期", date, onDate = { date = it })
        Row { Checkbox(overwrite, { overwrite = it }); Text("允许覆盖原安排", Modifier.padding(top = 12.dp)) }
        error?.let { FitnessHint(it, true) }
    } }, confirmButton = { TextButton(onClick = {
        val plan = plans.firstOrNull { it.id == target }
        if (plan != null && date !in FitnessRules.dates(plan.start, plan.end)) error = "目标日期不在所选计划范围内"
        else onEditor(FitnessEditorRequest(planId = plan?.id, date = date, sourceDay = source, step = 2, overwrite = overwrite))
    }) { Text("进入编辑") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun FitnessRestoreDialog(vm: FitnessViewModel, onRestored: () -> Unit) {
    val archive = vm.pendingRestore ?: return
    AlertDialog(onDismissRequest = vm::dismissRestore, title = { Text("替换本机健身资料？") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("备份：${archive.manifest.plans}个计划、${archive.manifest.events}条记录、${archive.manifest.media.size}张个人图片。")
        Text("当前：${vm.data.plans.size}个计划、${vm.data.personalExercises.size}个个人动作。恢复会整体替换健身资料、偏好、模板和草稿；电费与饮食资料保留。建议先导出当前健身备份。")
        Text("内容版本：${archive.manifest.contentVersion}\n已完成格式、关联、哈希和图片预检。")
    } }, confirmButton = { TextButton(onClick = { vm.restore(onRestored) }, enabled = !vm.busy) { Text("确认替换") } }, dismissButton = { TextButton(onClick = vm::dismissRestore, enabled = !vm.busy) { Text("取消") } })
}

package com.pockettoolbox.feature.fitness.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pockettoolbox.feature.fitness.domain.*

internal fun LazyListScope.fitnessEditor(vm: FitnessViewModel, onPick: (String) -> Unit,
    onDetail: (String) -> Unit, onConfirm: (FitnessConfirmation) -> Unit, onLeave: () -> Unit, onDiscarded: () -> Unit,
    onSaved: (FitnessPlan) -> Unit) {
    val draft = vm.draft ?: return
    val plan = draft.plan
    val day = plan.day(draft.selectedDate) ?: return
    item("editor-progress") {
        FitnessCard {
            Text("${draft.step + 1}/4 · ${listOf("日期与名称", "每日部位", "动作与目标", "检查并保存")[draft.step]}", style = MaterialTheme.typography.titleMedium)
            Text(if (vm.draftSaving) "正在暂存…" else if (vm.draftDirty) "填写待暂存" else "草稿已保存在本机", style = MaterialTheme.typography.labelSmall)
            vm.editorError?.let { FitnessHint(it, true); TextButton(onClick = vm::retryDraft) { Text("重试暂存") } }
            Row { TextButton(onClick = onLeave, enabled = !vm.busy) { Text("暂存离开") }
                TextButton(onClick = { onConfirm(FitnessConfirmation("放弃草稿？", "已生效的原计划和训练记录保留，未提交的填写会清除。") { vm.discardDraft(onDiscarded) }) }, enabled = !vm.busy) { Text("放弃草稿") } }
        }
    }
    if (draft.step == 0) item("editor-range") {
        FitnessCard {
            OutlinedTextField(plan.name, { text -> vm.updateDraft { it.copy(plan = it.plan.copy(name = text.take(60))) } }, label = { Text("计划名称") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
            FitnessDateField("开始日期", plan.start, enabled = !vm.busy) { value -> vm.updateDraft { it.copy(plan = it.plan.copy(start = value)) } }
            FitnessDateField("结束日期", plan.end, enabled = !vm.busy) { value -> vm.updateDraft { it.copy(plan = it.plan.copy(end = value)) } }
            Text("最多366天。未安排日和休息日会保留在时间轴中。")
        }
    }
    if (draft.step in 1..2) item("editor-day") {
        FitnessCard {
            FitnessChoice("选择日期", day.date, plan.days.filter { it.date >= plan.start && it.date <= plan.end }.map { it.date to "${it.date} · ${when (it.kind) { FitnessDayKind.Training -> it.parts.joinToString(" / ") { part -> part.label }.ifBlank { "训练" }; FitnessDayKind.Rest -> "休息"; else -> "未安排" }}" },
                onSelect = { date -> vm.updateDraft { it.copy(selectedDate = date) } }, enabled = !vm.busy)
            if (draft.step == 1) {
                FitnessChoice("当日安排", day.kind.name, FitnessDayKind.entries.map { it.name to when (it) { FitnessDayKind.Training -> "训练"; FitnessDayKind.Rest -> "主动休息"; else -> "暂不安排" } }, onSelect = { selected ->
                    val kind = FitnessDayKind.valueOf(selected)
                    val apply = { vm.updateDay { current -> current.copy(kind = kind, parts = if (kind == FitnessDayKind.Training) current.parts else emptyList(), items = if (kind == FitnessDayKind.Training) current.items else current.items.map { it.copy(status = FitnessItemStatus.Stopped) }) }; Unit }
                    if (kind != FitnessDayKind.Training && day.activeItems.isNotEmpty()) onConfirm(FitnessConfirmation("变更当日安排？", "这会结束当日剩余动作安排，已有完成或撤销记录保留。", apply)) else apply()
                }, enabled = !vm.busy)
                if (day.kind == FitnessDayKind.Training) {
                    Text("选择主练部位（可多选），热身、有氧和拉伸在下一步单独添加。")
                    FitnessPart.entries.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth()) { pair.forEach { part ->
                        Row(Modifier.weight(1f)) { Checkbox(part in day.parts, onCheckedChange = { checked ->
                            val affected = day.activeItems.filter { it.exercise.purpose == FitnessPurpose.Strength && it.exercise.primaryPart == part }
                            val apply = { vm.updateDay { current -> current.copy(parts = if (checked) (current.parts + part).distinct() else current.parts - part,
                                items = current.items.map { if (!checked && it.id in affected.map { row -> row.id }) it.copy(status = FitnessItemStatus.Stopped) else it }) }; Unit }
                            if (!checked && affected.isNotEmpty()) onConfirm(FitnessConfirmation("移除${part.label}？", "对应${affected.size}个动作会结束剩余安排，历史记录保留。", apply)) else apply()
                        }, enabled = !vm.busy); Text(part.label, Modifier.padding(top = 12.dp)) }
                    }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
                    if (day.date >= vm.today && day.parts.isNotEmpty()) {
                        val adjacentDates = setOf(FitnessRules.date(day.date).minusDays(1).toString(), FitnessRules.date(day.date).plusDays(1).toString())
                        val overlaps = (vm.data.plans.filter { it.id != plan.id && it.status == FitnessPlanStatus.Active } + plan).flatMap { it.days }
                            .filter { it.date in adjacentDates && it.kind == FitnessDayKind.Training }
                            .flatMap { candidate -> candidate.parts.intersect(day.parts.toSet()).map { candidate.date to it } }.distinct()
                        if (overlaps.isNotEmpty()) FitnessHint("相邻日已有相同部位安排：${overlaps.joinToString("、") { "${it.first} ${it.second.label}" }}。可考虑调整部位或强度；计划日期无法推算精确恢复时长。")
                    }
                }
            }
            FitnessDayTools(vm, day)
        }
    }
    if (draft.step == 2) {
        item("editor-groups") { FitnessCard {
            Text("先从部位进入选择器，再设置已选动作。")
            day.parts.forEach { part -> OutlinedButton(onClick = { onPick("part:${part.name}") }, modifier = Modifier.fillMaxWidth()) {
                Text("${part.label} · 已选${day.activeItems.count { it.exercise.primaryPart == part && it.exercise.purpose == FitnessPurpose.Strength }}个")
            } }
            FitnessPurpose.entries.filter { it != FitnessPurpose.Strength }.forEach { purpose -> TextButton(onClick = { onPick("purpose:${purpose.name}") }) { Text("添加${purpose.label}") } }
            if (day.parts.isEmpty()) OutlinedButton(onClick = { onPick("all") }) { Text("浏览全部可用动作") }
            if (day.activeItems.isNotEmpty()) FitnessBatchTargets(vm, day)
        } }
        items(day.activeItems, key = { "edit-${it.id}" }) { row -> FitnessCard {
            val exercise = vm.exercises.firstOrNull { it.id == row.exercise.exerciseId }
            if (exercise != null) FitnessExerciseRow(vm, exercise, { onDetail(exercise.id) }) else Text(row.exercise.name)
            val raw = draft.rawTargets[row.id] ?: FitnessTargetInput(row.targetSets?.toString().orEmpty(), row.targetValue?.toString().orEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(raw.sets, { vm.updateTarget(row.id, sets = it) }, label = { Text(if (row.exercise.purpose == FitnessPurpose.Strength) "组数 1—99" else "段数 1—99") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !vm.busy, modifier = Modifier.weight(1f))
                OutlinedTextField(raw.value, { vm.updateTarget(row.id, value = it) }, label = { Text("${if (row.exercise.side == FitnessSide.Each) "每侧" else "每组/段"}${row.exercise.unit.label}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !vm.busy, modifier = Modifier.weight(1f))
            }
            val completed = vm.editorHistory.count { it.itemId == row.id && it.isValid() }
            if (completed > 0) Text("已完成$completed组/段；新的目标不能少于已完成数。")
            Row {
                val index = day.items.indexOf(row)
                TextButton(onClick = { vm.updateDay { current -> val values = current.items.toMutableList(); java.util.Collections.swap(values, index, index - 1); current.copy(items = values) } }, enabled = !vm.busy && index > 0) { Text("上移") }
                TextButton(onClick = { vm.updateDay { current -> val values = current.items.toMutableList(); java.util.Collections.swap(values, index, index + 1); current.copy(items = values) } }, enabled = !vm.busy && index < day.items.lastIndex) { Text("下移") }
                TextButton(onClick = { onConfirm(FitnessConfirmation("移除${row.exercise.name}？", "移除本次安排，历史记录保留。再次添加同一动作会恢复原条目和目标。") { vm.updateDay { FitnessRules.stop(it, row.id) } }) }, enabled = !vm.busy) { Text("移除") }
            }
        } }
    }
    if (draft.step == 3) {
        val issues = runCatching { FitnessRules.issues(plan, vm.editorHistory) }.getOrElse { listOf(FitnessPlanIssue(it.message.orEmpty(), field = "range")) }
        item("editor-review") { FitnessCard {
            Text(plan.name, style = MaterialTheme.typography.titleLarge)
            Text("${plan.start} 至 ${plan.end}\n${plan.days.count { it.activeItems.isNotEmpty() }}个训练日 · ${plan.days.sumOf { it.activeItems.size }}个动作安排")
            if (issues.isEmpty()) FitnessHint("检查完成。保存后即可从今日页面逐组记录。")
            issues.forEach { issue -> TextButton(onClick = {
                vm.updateDraft { it.copy(selectedDate = issue.date ?: it.selectedDate, step = if (issue.field in listOf("name", "range")) 0 else if (issue.field == "part") 1 else 2) }
                if (issue.field.startsWith("part:")) onPick(issue.field)
            }) { Text(issue.message) } }
            Button(onClick = { vm.savePlan(onSaved) }, enabled = !vm.busy && issues.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("保存计划") }
        } }
        items(plan.days.filter { it.date >= plan.start && it.date <= plan.end }, key = { "preview-${it.date}" }) { preview -> FitnessCard {
            Text("${preview.date} · ${if (preview.kind == FitnessDayKind.Rest) "休息" else if (preview.kind == FitnessDayKind.Unplanned) "未安排" else preview.parts.joinToString(" / ") { it.label }}")
            preview.activeItems.forEach { Text("${it.exercise.name} · ${it.measurement()}") }
            TextButton(onClick = { vm.updateDraft { it.copy(selectedDate = preview.date, step = 2) } }) { Text("调整当日") }
        } }
    }
    item("editor-navigation") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.step > 0) OutlinedButton(onClick = { vm.nextStep(draft.step - 1) }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("上一步") }
        if (draft.step < 3) Button(onClick = { vm.nextStep(draft.step + 1) }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text(if (draft.step == 2) "检查全部安排" else "下一步") }
    } }
}

@Composable
private fun FitnessBatchTargets(vm: FitnessViewModel, day: FitnessPlanDay) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("批量填写目标") }
    if (!open) return
    var sets by rememberSaveable { mutableStateOf("4") }; var value by rememberSaveable { mutableStateOf("12") }
    var unit by rememberSaveable { mutableStateOf(FitnessUnit.Reps.name) }; var overwrite by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { open = false }, title = { Text("批量填写当日目标") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FitnessChoice("计量单位", unit, FitnessUnit.entries.map { it.name to it.label }, { unit = it })
        OutlinedTextField(sets, { sets = it.take(3) }, label = { Text("组/段数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value, { value = it.take(4) }, label = { Text("每组/段数值（左右侧动作按每侧）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row { Checkbox(overwrite, { overwrite = it }); Text("覆盖已有目标", Modifier.padding(top = 12.dp)) }
        error?.let { FitnessHint(it, true) }
    } }, confirmButton = { TextButton(onClick = {
        try {
            val selectedUnit = FitnessUnit.valueOf(unit)
            val parsedSets = FitnessRules.positive(sets, 99, "组/段数"); val parsedValue = FitnessRules.positive(value, selectedUnit.maximum, "目标")
            val affected = day.activeItems.filter { it.exercise.unit == selectedUnit }
            require(affected.isNotEmpty()) { "当日没有该计量单位的动作" }
            require(affected.none { (overwrite || it.targetSets == null) && parsedSets < vm.editorHistory.count { event -> event.itemId == it.id && event.isValid() } }) { "组数不能少于已完成数" }
            vm.updateDraft { current -> current.copy(rawTargets = current.rawTargets - affected.map { it.id }.toSet(), plan = current.plan.copy(days = current.plan.days.map { selected -> if (selected.date != day.date) selected else selected.copy(items = selected.items.map { row ->
                if (row.id !in affected.map { it.id }) row else row.copy(targetSets = if (overwrite || row.targetSets == null) parsedSets else row.targetSets, targetValue = if (overwrite || row.targetValue == null) parsedValue else row.targetValue)
            }) })) }
            open = false
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }) { Text("应用") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } })
}

@Composable
private fun FitnessDayTools(vm: FitnessViewModel, day: FitnessPlanDay) {
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var targets by rememberSaveable { mutableStateOf(listOf<String>()) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var templateId by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Row { TextButton(onClick = { mode = "copy"; targets = emptyList(); overwrite = false; error = null }, enabled = day.activeItems.isNotEmpty()) { Text("复制到其他日期") }
        TextButton(onClick = { mode = "parts"; targets = emptyList(); overwrite = false; error = null }) { Text("批量应用部位") } }
    Row { TextButton(onClick = { mode = "save"; templateName = "" }, enabled = day.activeItems.isNotEmpty()) { Text("存为个人模板") }
        TextButton(onClick = { mode = "template"; overwrite = false; error = null; templateId = vm.data.templates.firstOrNull()?.id.orEmpty() }, enabled = vm.data.templates.isNotEmpty()) { Text("使用模板") } }
    val currentMode = mode ?: return
    val draft = vm.draft ?: return
    AlertDialog(onDismissRequest = { if (!vm.busy) mode = null }, title = { Text(when (currentMode) { "copy" -> "复制当日安排"; "parts" -> "应用同样部位"; "save" -> "保存个人模板"; else -> "应用模板" }) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentMode == "save") OutlinedTextField(templateName, { templateName = it.take(60) }, label = { Text("模板名称") })
            if (currentMode == "template") FitnessChoice("选择模板", templateId, vm.data.templates.map { it.id to it.name }, { templateId = it })
            if (currentMode in listOf("copy", "parts")) {
                Text("有完成或撤销历史的日期不可批量覆盖。")
                draft.plan.days.filter { it.date != day.date && it.date >= draft.plan.start && it.date <= draft.plan.end }.forEach { target ->
                    val locked = vm.editorHistory.any { it.trainingDate == target.date }
                    Row { Checkbox(target.date in targets, { checked -> targets = if (checked) targets + target.date else targets - target.date }, enabled = !locked)
                        Text("${target.date}${if (locked) " · 有历史" else if (target.kind != FitnessDayKind.Unplanned) " · 已有安排" else ""}", Modifier.padding(top = 12.dp)) }
                }
            }
            if (currentMode != "save") Row { Checkbox(overwrite, { overwrite = it }); Text("允许覆盖已有安排", Modifier.padding(top = 12.dp)) }
            error?.let { FitnessHint(it, true) }
        }
    }, confirmButton = { TextButton(onClick = {
        if (currentMode == "save") { try { FitnessRules.name(templateName); vm.template(templateName) { mode = null } } catch (failure: IllegalArgumentException) { error = failure.message }; return@TextButton }
        val success = vm.updateDraft { current ->
            val updated = when (currentMode) {
                "copy" -> FitnessRules.copyDays(current.plan, day, targets.toSet(), vm.editorHistory, overwrite)
                "parts" -> FitnessRules.batchParts(current.plan, targets.toSet(), day.kind, day.parts, vm.editorHistory, overwrite)
                else -> {
                    val template = vm.data.templates.first { it.id == templateId }
                    val source = FitnessPlanDay("0001-01-01", FitnessDayKind.Training, template.parts, template.items)
                    FitnessRules.copyDays(current.plan, source, setOf(day.date), vm.editorHistory, overwrite)
                }
            }
            current.copy(plan = updated)
        }
        if (success) mode = null else error = vm.editorError
    }, enabled = !vm.busy) { Text("应用") } }, dismissButton = { TextButton(onClick = { mode = null }, enabled = !vm.busy) { Text("取消") } })
}

internal fun LazyListScope.fitnessPicker(vm: FitnessViewModel, scope: String, query: String, mine: Boolean,
    source: String, selectedOnly: Boolean, limit: Int, onScope: (String) -> Unit, onQuery: (String) -> Unit,
    onMine: (Boolean) -> Unit, onSource: (String) -> Unit, onSelected: () -> Unit, onMore: () -> Unit,
    onDetail: (String) -> Unit, onClear: () -> Unit, onCustom: () -> Unit, onConfirm: (FitnessConfirmation) -> Unit) {
    val draft = vm.draft ?: return
    val day = draft.plan.day(draft.selectedDate) ?: return
    val selected = day.activeItems.map { it.exercise.exerciseId }.toSet()
    val rows = vm.exercises.filter { !it.archived && (if (selectedOnly) it.id in selected else scope == "all" || fitnessScopeMatches(it, scope)) &&
        (!mine || it.matchesEquipment(vm.data.preferences.equipment)) && (source == "all" || if (source == "personal") it.personal else !it.personal) &&
        (query.isBlank() || listOf(it.name, it.english, it.aliases.joinToString(" ")).any { text -> text.contains(query.trim(), true) }) }
    item("picker-filter") { FitnessCard {
        Text("${day.date} · ${if (scope == "all") "全部动作" else fitnessScopeLabel(scope)} · 已选${selected.size}个")
        FitnessChoice("浏览范围", scope, listOf("all" to "全部动作") + FitnessPart.entries.map { "part:${it.name}" to it.label } + FitnessPurpose.entries.filter { it != FitnessPurpose.Strength }.map { "purpose:${it.name}" to it.label }, onScope)
        OutlinedTextField(query, onQuery, label = { Text("搜索动作、别名或英文名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row { Checkbox(mine, onMine); Text("只看我的器械", Modifier.padding(top = 12.dp)) }
        FitnessChoice("来源", source, listOf("all" to "内置＋个人", "builtin" to "内置", "personal" to "个人"), onSource)
        TextButton(onClick = onSelected) { Text(if (selectedOnly) "返回候选" else "只看已选") }
        Text("匹配${rows.size}个；点图片查看教学，勾选加入当天。", style = MaterialTheme.typography.bodySmall)
    } }
    if (rows.isEmpty()) item("picker-empty") { FitnessEmpty("当前筛选没有动作。") {
        TextButton(onClick = onClear) { Text("清空筛选") }; TextButton(onClick = { onScope("all") }) { Text("查看其他部位") }; TextButton(onClick = onCustom) { Text("新建我的动作") }
    } }
    items(rows.take(limit), key = { "pick-${it.id}" }) { exercise -> FitnessExerciseRow(vm, exercise, { onDetail(exercise.id) }, exercise.id in selected, onToggle = {
        val apply = { vm.toggleExercise(exercise); Unit }
        if (exercise.id in selected && vm.editorHistory.any { it.itemId == day.items.firstOrNull { row -> row.exercise.exerciseId == exercise.id }?.id }) onConfirm(FitnessConfirmation("移除已有记录的动作？", "将结束剩余安排，完成和撤销历史保留。", apply))
        else if (exercise.id !in selected && !exercise.matchesEquipment(vm.data.preferences.equipment)) onConfirm(FitnessConfirmation("缺少匹配器械", "此动作的器械条件与你的偏好不一致。确认实际具备稳定、安全的器械后可加入。", apply)) else apply()
    }) }
    if (rows.size > limit) item("picker-more") { OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多（${minOf(limit, rows.size)}/${rows.size}）") } }
}

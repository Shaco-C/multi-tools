package com.pockettoolbox.feature.fitness.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.fitness.domain.*
import java.time.LocalDate

@Composable
internal fun FitnessTimeline(vm: FitnessViewModel, selected: String, plan: FitnessPlan? = null, onDate: (String) -> Unit) {
    FitnessCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { FitnessDateField("查看日期", selected, onDate = onDate) }
            TextButton(onClick = { onDate(vm.today) }) { Text("回到今天") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (-3L..5L).forEach { offset ->
                val date = LocalDate.parse(selected).plusDays(offset).toString()
                val days = if (plan != null) listOfNotNull(plan.day(date)) else vm.data.plans.filter { it.status == FitnessPlanStatus.Active }.mapNotNull { it.day(date) }
                val parts = days.filter { it.kind == FitnessDayKind.Training }.flatMap { it.parts }.distinct()
                val labels = parts.take(2).map { it.label }.ifEmpty {
                    days.flatMap { it.activeItems }.map { it.exercise.purpose.label }.distinct().take(2).ifEmpty {
                        listOf(if (days.isNotEmpty() && days.all { it.kind == FitnessDayKind.Rest }) "休息" else "未安排")
                    }
                }
                OutlinedButton(onClick = { onDate(date) }, modifier = Modifier.widthIn(min = 72.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (date == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                        if (parts.size > 2) Text("+${parts.size - 2}", style = MaterialTheme.typography.labelSmall)
                        Text("●", color = MaterialTheme.colorScheme.secondary)
                        Text(date.substring(5), fontWeight = FontWeight.Bold)
                        Text(if (date == vm.today) "今天" else listOf("一", "二", "三", "四", "五", "六", "日")[LocalDate.parse(date).dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.fitnessToday(
    vm: FitnessViewModel, selectedDate: String, onDate: (String) -> Unit, onEditor: (FitnessEditorRequest) -> Unit,
    onFocus: (FitnessScheduledItem, Int) -> Unit, onPlan: (String, String) -> Unit, onDetail: (String) -> Unit,
    onBackfill: (FitnessScheduledItem) -> Unit, onConfirm: (FitnessConfirmation) -> Unit,
    onRecords: (FitnessScheduledItem) -> Unit, onTodayRecords: () -> Unit,
) {
    val rows = vm.scheduled()
    val grouped = rows.groupBy { it.plan.id }
    val indexes = mutableMapOf<String, Int>()
    var index = 6
    for ((_, group) in grouped) { index++; for (row in group) indexes[row.item.id] = index++ }
    val todayDays = vm.data.plans.filter { it.status == FitnessPlanStatus.Active }.mapNotNull { it.day(vm.today) }
    val todayParts = todayDays.flatMap { it.parts }.distinct()
    val todayRows = vm.data.plans.filter { it.status == FitnessPlanStatus.Active }.flatMap { plan ->
        plan.day(vm.today)?.takeIf { it.kind == FitnessDayKind.Training }?.let { day -> day.activeItems.map { FitnessScheduledItem(plan, day, it, vm.todayProgress[it.id] ?: 0) } }.orEmpty()
    }
    val pending = todayRows.filter { it.completed < (it.item.targetSets ?: 0) }
    val preferred = pending.firstOrNull { it.item.id == vm.focusItemId } ?: pending.firstOrNull { it.item.id == vm.data.preferences.lastItemId } ?: pending.firstOrNull()
    item("today-intro") { Text("${vm.today} · 每完成一组，手动确认一下。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    item("today-primary") {
        FitnessCard {
            val label = when {
                selectedDate != vm.today -> "回到今日训练"
                preferred != null -> "继续：${preferred.item.exercise.name} ${preferred.completed}/${preferred.item.targetSets}"
                vm.todayProgress.isNotEmpty() -> "查看今日记录"
                todayDays.isNotEmpty() && todayDays.all { it.kind == FitnessDayKind.Rest } -> "今天休息 · 查看日程"
                else -> "安排今天"
            }
            val resting = preferred == null && vm.todayProgress.isEmpty() && todayDays.isNotEmpty() && todayDays.all { it.kind == FitnessDayKind.Rest }
            if (resting && selectedDate == vm.today) FitnessDateField("查看日程", selectedDate, onDate = onDate)
            else Button(onClick = {
                when {
                    selectedDate != vm.today -> onDate(vm.today)
                    preferred != null -> onFocus(preferred, indexes[preferred.item.id] ?: 5)
                    vm.todayProgress.isNotEmpty() -> onTodayRecords()
                    else -> onEditor(FitnessEditorRequest(date = vm.today))
                }
            }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(label) }
            OutlinedButton(onClick = { onEditor(FitnessEditorRequest(date = vm.today, range = true)) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("创建多日计划") }
            if (vm.data.plans.isEmpty()) Text("先选部位和动作，填好目标，做完一组点一下。", style = MaterialTheme.typography.bodySmall)
        }
    }
    item("today-timeline") { FitnessTimeline(vm, selectedDate, onDate = onDate) }
    item("today-summary") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = ToolboxColors.Navy)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("连续训练", color = ToolboxColors.TealBright)
                    Text("${vm.streak.days}天", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(if (vm.streak.days == 0) "从下一次训练开始" else if (vm.streak.anchor == vm.today) "截至今天" else "截至昨天", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("今日计划部位")
                    Text(todayParts.joinToString(" · ") { it.label }.ifEmpty { if (todayRows.isNotEmpty()) "活动训练" else if (todayDays.isNotEmpty() && todayDays.all { it.kind == FitnessDayKind.Rest }) "休息" else "未安排" }, fontWeight = FontWeight.Bold)
                    val practiced = todayRows.filter { it.completed > 0 }.mapNotNull { it.item.exercise.primaryPart }.toSet().intersect(todayParts.toSet()).size
                    Text(if (todayParts.isEmpty()) "按自己的节奏安排" else "已练$practiced/${todayParts.size}主部位\n不代表目标全部完成", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    item("today-recovery") {
        var expanded by rememberSaveable { mutableStateOf(false) }
        FitnessCard {
            Text("恢复参考", fontWeight = FontWeight.Bold)
            if (!vm.data.preferences.recoveryEnabled) Text("提醒已关闭，记录和统计保留。")
            else if (vm.recoveryError != null) FitnessHint("恢复参考暂不可用：${vm.recoveryError}", true)
            else {
                if (vm.recovery.isEmpty()) Text("安排同一部位时，可结合最近训练记录、训练负荷和身体感受考虑间隔。")
                Text("同一肌群的力量训练之间可留出一个完整休息日，并结合训练强度与身体感受安排。", style = MaterialTheme.typography.bodySmall)
                val recent = vm.recovery.filter { it.event != null }
                if (recent.isNotEmpty()) Text(FitnessStatistics.recoveryMessage(recent.first(), vm.now, vm.today), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起参考" else "查看各部位记录依据") }
                if (expanded) vm.recovery.forEach { row ->
                    val event = row.event
                    Text("${row.part.label}：${event?.let { "最近记录${it.trainingDate}${if (row.primary) "（主练）" else "（辅助参与）"}${if (it.timeSource == FitnessTimeSource.DateOnly) "，时刻未填" else ""}" } ?: "暂无已记录训练"}", style = MaterialTheme.typography.bodySmall)
                    if (event != null) Text(FitnessStatistics.recoveryMessage(row, vm.now, vm.today), style = MaterialTheme.typography.bodySmall)
                    if (event != null) TextButton(onClick = { onPlan(event.planId, event.trainingDate) }) { Text("查看${row.part.label}记录依据") }
                }
                Text("仅依据已记录训练，不判定恢复完成，也不自动修改计划。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expanded) Text("一般建议来源：Mayo Clinic，Strength training，2026-10-24复核。48小时仅用于近期记录筛查。", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    item("day-heading") {
        Column {
            Text("$selectedDate${if (selectedDate == vm.today) " · 今天" else if (selectedDate > vm.today) " · 未来预览" else " · 历史日期"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onEditor(FitnessEditorRequest(date = selectedDate)) }, enabled = !vm.busy) { Text("安排这一天") }
        }
    }
    if (rows.isEmpty()) item { FitnessEmpty(if (vm.dayProgress.isNotEmpty()) "当前无剩余安排，训练历史已保留。" else if (todayDays.isNotEmpty() && selectedDate == vm.today && todayDays.all { it.kind == FitnessDayKind.Rest }) "今天已安排休息。" else "这一天还没有训练动作。") }
    for ((id, group) in grouped) {
        item("plan-label-$id") { TextButton(onClick = { onPlan(id, selectedDate) }) { Text("${group.first().plan.name} · 查看计划 ›") } }
        items(group, key = { it.item.id }) { row ->
            FitnessWorkout(vm, row, rows, onDetail, onBackfill, onConfirm, onRecords,
                onFocus = { target -> onFocus(target, indexes[target.item.id] ?: 5) })
        }
    }
    item { if (vm.dayEvents.isNotEmpty()) OutlinedButton(onClick = onTodayRecords, enabled = selectedDate == vm.today) { Text("查看今日全部有效记录") } }
}

@Composable
private fun FitnessWorkout(vm: FitnessViewModel, row: FitnessScheduledItem, rows: List<FitnessScheduledItem>, onDetail: (String) -> Unit,
    onBackfill: (FitnessScheduledItem) -> Unit, onConfirm: (FitnessConfirmation) -> Unit, onRecords: (FitnessScheduledItem) -> Unit,
    onFocus: (FitnessScheduledItem) -> Unit) {
    val focused = vm.focusItemId == row.item.id
    val hasFocus = rows.any { it.item.id == vm.focusItemId }
    val compact = hasFocus && !focused && (row.day.date == vm.today || vm.isContinuing(row))
    val done = row.completed >= (row.item.targetSets ?: 0)
    FitnessCard {
        if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(row.item.exercise.name, fontWeight = FontWeight.Bold); Text("${row.completed}/${row.item.targetSets} · ${if (done) "组数已记完" else "尚有剩余"}", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(onClick = { onFocus(row) }) { Text(if (done) "查看动作" else "切到此动作") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val exercise = vm.exercises.firstOrNull { it.id == row.item.exercise.exerciseId }
                Box(Modifier.size(76.dp).clickable { onDetail(row.item.exercise.exerciseId) }) {
                    FitnessMedia(vm.dependencies, exercise?.images?.lastOrNull(), vm.data.mediaGeneration, "查看${row.item.exercise.name}教学", Modifier.fillMaxSize(), width = 76.dp, height = 76.dp)
                }
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = { onDetail(row.item.exercise.exerciseId) }, contentPadding = PaddingValues(0.dp)) { Text(row.item.exercise.name, fontWeight = FontWeight.Bold) }
                    Text(row.item.measurement(), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("${row.completed} / ${row.item.targetSets} ${if (row.item.exercise.purpose == FitnessPurpose.Strength) "组" else "段"}", style = MaterialTheme.typography.headlineMedium)
            LinearProgressIndicator(progress = { (row.completed.toFloat() / (row.item.targetSets ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (row.day.date < vm.today && !vm.isContinuing(row)) onBackfill(row) else vm.complete(row) },
                enabled = !vm.busy && !done && row.day.date <= vm.today, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
                Text(if (row.day.date > vm.today) "未来日期仅预览" else if (done) "目标组数已记录" else if (row.day.date < vm.today && !vm.isContinuing(row)) "补记已完成的组/段" else "完成第${row.completed + 1}${if (row.item.exercise.purpose == FitnessPurpose.Strength) "组" else "段"}")
            }
            if (row.day.date < vm.today && vm.isContinuing(row)) FitnessHint("正在继续${row.day.date}的训练，新组仍记在原日。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onRecords(row) }, enabled = !vm.busy) { Text("组明细") }
                TextButton(onClick = { vm.undoLast(row) }, enabled = !vm.busy && row.completed > 0) { Text("撤销第${row.completed}${if (row.item.exercise.purpose == FitnessPurpose.Strength) "组" else "段"}") }
            }
            TextButton(onClick = { onConfirm(FitnessConfirmation("结束剩余安排", "已完成和撤销记录保留；同日重加会恢复原身份和进度。若本计划尚无记录且无其他安排，将归档保留计划。") { vm.stop(row) }) }, enabled = !vm.busy) { Text("结束此动作剩余安排") }
            if (focused && done) rows.firstOrNull { it.completed < (it.item.targetSets ?: 0) }?.let { next ->
                OutlinedButton(onClick = { onFocus(next) }, modifier = Modifier.fillMaxWidth()) { Text("下一个动作：${next.item.exercise.name}") }
            }
        }
    }
}

internal fun LazyListScope.fitnessPlans(vm: FitnessViewModel, onEditor: (FitnessEditorRequest) -> Unit,
    onOpen: (String, String) -> Unit, onConfirm: (FitnessConfirmation) -> Unit, onResume: () -> Unit) {
    item { Button(onClick = { onEditor(FitnessEditorRequest(date = vm.today, range = true)) }, enabled = !vm.busy) { Text("创建训练计划") } }
    (vm.draft ?: vm.data.draft)?.let { draft -> item {
        FitnessCard {
            Text("未提交草稿：${draft.plan.name.ifBlank { "未命名" }}", fontWeight = FontWeight.Bold)
            Text("${draft.plan.start} — ${draft.plan.end}，不会改变生效安排。")
            Row { TextButton(onClick = onResume) { Text("继续填写") }
                TextButton(onClick = { onConfirm(FitnessConfirmation("放弃草稿", "仅放弃未提交填写，生效计划和记录保留。") { vm.discardDraft {} }) }) { Text("放弃") } }
        }
    } }
    if (vm.data.plans.isEmpty()) item { FitnessEmpty("还没有生效计划，可以先安排今天。") }
    items(vm.data.plans, key = { it.id }) { plan ->
        FitnessCard {
            Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${plan.start} — ${plan.end}", style = MaterialTheme.typography.bodySmall)
            Text("${if (plan.status == FitnessPlanStatus.Archived) "已归档" else if (plan.start > vm.today) "尚未开始" else if (plan.end < vm.today) "日期已结束" else "进行中"} · ${plan.days.count { it.kind == FitnessDayKind.Training }}训练日 · ${plan.days.count { it.kind == FitnessDayKind.Rest }}休息日")
            val next = plan.days.firstOrNull { it.date >= vm.today && it.activeItems.isNotEmpty() }?.date ?: plan.end
            Button(onClick = { onOpen(plan.id, next) }, enabled = !vm.busy) { Text(if (plan.status == FitnessPlanStatus.Archived) "回看安排与记录" else if (next == vm.today) "继续今天" else if (next > vm.today) "查看下一次训练" else "回看计划") }
            if (plan.status == FitnessPlanStatus.Active) TextButton(onClick = { onEditor(FitnessEditorRequest(plan.id, vm.today, step = 0)) }, enabled = !vm.busy) { Text("编辑计划") }
            Row {
                TextButton(onClick = { onConfirm(FitnessConfirmation(if (plan.status == FitnessPlanStatus.Archived) "取消归档" else "归档计划",
                    if (plan.status == FitnessPlanStatus.Archived) "恢复${plan.start}至${plan.end}的原日期安排，不移动到今天。" else "停止新记组和补记，历史仍保留在统计中。") { vm.archive(plan) }) }, enabled = !vm.busy) { Text(if (plan.status == FitnessPlanStatus.Archived) "取消归档" else "归档") }
                TextButton(onClick = { onConfirm(FitnessConfirmation("彻底删除计划及记录", "将删除“${plan.name}”和其全部完成/撤销历史，统计随之减少。希望保留历史请取消并归档。") { vm.delete(plan) }) }, enabled = !vm.busy) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

internal fun LazyListScope.fitnessPlanDetail(vm: FitnessViewModel, planId: String?, date: String, onDate: (String) -> Unit,
    onEditor: (FitnessEditorRequest) -> Unit, onFocus: (FitnessScheduledItem, Int) -> Unit, onDetail: (String) -> Unit,
    onBackfill: (FitnessScheduledItem) -> Unit, onConfirm: (FitnessConfirmation) -> Unit, onCopy: (FitnessPlanDay) -> Unit,
    onRecords: (FitnessScheduledItem) -> Unit) {
    val plan = vm.data.plans.firstOrNull { it.id == planId }
    if (plan == null) { item { FitnessEmpty("计划已不存在，历史不会被重建为假记录。") }; return }
    val day = plan.day(date)
    item { Text("${plan.start} — ${plan.end}${if (plan.status == FitnessPlanStatus.Archived) " · 已归档" else ""}") }
    item { FitnessTimeline(vm, date, plan, onDate) }
    item {
        FitnessCard {
            Text("正在查看$date · ${day?.parts?.joinToString(" · ") { it.label }?.ifEmpty { if (day?.kind == FitnessDayKind.Rest) "休息" else "未安排/活动" } ?: "不在计划内"}", fontWeight = FontWeight.Bold)
            if (plan.status == FitnessPlanStatus.Active && day != null) {
                OutlinedButton(onClick = { onEditor(FitnessEditorRequest(plan.id, date, step = 2)) }, enabled = !vm.busy) { Text("编辑这一天安排") }
                TextButton(onClick = { onEditor(FitnessEditorRequest(plan.id, date, step = 0)) }, enabled = !vm.busy) { Text("编辑计划信息") }
            }
            if (day != null && day.items.isNotEmpty()) TextButton(onClick = {
                val candidates = if (plan.status == FitnessPlanStatus.Archived) day.items else day.activeItems
                onCopy(day.copy(kind = FitnessDayKind.Training, parts = candidates.filter { it.exercise.purpose == FitnessPurpose.Strength }.mapNotNull { it.exercise.primaryPart }.distinct(), items = candidates.map { it.copy(status = FitnessItemStatus.Active) }))
            }) { Text(if (plan.status == FitnessPlanStatus.Archived) "用原安排创建新安排" else "复制这一天") }
            if (plan.day(vm.today)?.activeItems?.isNotEmpty() == true && plan.status == FitnessPlanStatus.Active) TextButton(onClick = { onDate(vm.today) }) { Text("去今天训练") }
        }
    }
    if (plan.status == FitnessPlanStatus.Archived) {
        item { Text("当日原安排", fontWeight = FontWeight.Bold) }
        items(day?.items.orEmpty(), key = { it.id }) { item -> FitnessCard {
            Text(item.exercise.name, fontWeight = FontWeight.Bold); Text(item.measurement())
            TextButton(onClick = { onDetail(item.exercise.exerciseId) }) { Text("查看教学") }
            TextButton(onClick = { vm.records(item.id) }) { Text("实际组记录") }
        } }
    } else {
        val rows = vm.scheduled()
        if (rows.isEmpty()) item { FitnessEmpty("这一天当前无剩余安排；原记录仍可在下方回看。") }
        items(rows, key = { it.item.id }) { row -> FitnessWorkout(vm, row, rows, onDetail, onBackfill, onConfirm, onRecords,
            onFocus = { target -> onFocus(target, 3 + rows.indexOfFirst { it.item.id == target.item.id }.coerceAtLeast(0)) }) }
    }
    item { Text("当日实际记录（最近200条，完整明细可按动作查看）", fontWeight = FontWeight.Bold) }
    items(vm.dayEvents, key = { "event-${it.id}" }) { event -> FitnessCard {
        Text("${event.exercise.name} · 第${event.ordinal}${if (event.exercise.purpose == FitnessPurpose.Strength) "组" else "段"}")
        Text(fitnessEventTime(event), style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { vm.records(event.itemId) }) { Text("查看此动作组明细") }
    } }
}

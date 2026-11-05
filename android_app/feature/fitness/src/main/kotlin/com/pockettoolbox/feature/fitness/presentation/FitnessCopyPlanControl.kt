package com.pockettoolbox.feature.fitness.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pockettoolbox.feature.fitness.domain.*

@Composable
internal fun FitnessCopyPlanControl(vm: FitnessViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var sourceId by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(vm.today) }
    OutlinedButton(onClick = { open = true }, enabled = !vm.busy && vm.data.plans.isNotEmpty()) { Text("从历史计划中复制") }
    if (!open) return
    val source = vm.data.plans.firstOrNull { it.id == sourceId }
    val preview = remember(source, start) { source?.let { runCatching { FitnessPlanCopy.copy(it, start) }.getOrNull() } }
    AlertDialog(onDismissRequest = { open = false }, title = { Text("复制历史计划") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FitnessChoice("来源计划", sourceId, vm.data.plans.map { it.id to "${it.name} · ${it.start}—${it.end}${if (it.status == FitnessPlanStatus.Archived) " · 已归档" else ""}" }, { sourceId = it })
            FitnessDateField("新起始日期", start) { start = it }
            if (source != null && preview != null) Text("${source.start}—${source.end}\n复制为 ${preview.start}—${preview.end} · ${preview.days.size}天")
            preview?.let { plan ->
                Text("训练${plan.days.count { it.kind == FitnessDayKind.Training }}天 · 休息${plan.days.count { it.kind == FitnessDayKind.Rest }}天 · 未安排${plan.days.count { it.kind == FitnessDayKind.Unplanned }}天")
            }
            if (source != null && preview == null) FitnessHint("该起始日期无法容纳原计划周期，请调整日期。", true)
            Text("复制每日安排和目标，完成记录从零开始；已停止的动作不复制。现有草稿需确认替换后才会更换。")
            val unavailable = source?.days.orEmpty().flatMap { it.activeItems }.filter { row -> vm.exercises.none { it.id == row.exercise.exerciseId && !it.archived } }.map { it.exercise.name }.distinct()
            if (unavailable.isNotEmpty()) FitnessHint("以下动作已停用或不在当前动作库，将保留原安排信息，请在草稿中移除或替换：${unavailable.joinToString("、")}")
        }
    }, confirmButton = { TextButton(enabled = preview != null && !vm.busy, onClick = {
        open = false
        vm.requestEditor(FitnessEditorRequest(date = start, range = true, sourcePlanId = sourceId, step = 0)) {}
    }) { Text("复制并调整") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } })
}

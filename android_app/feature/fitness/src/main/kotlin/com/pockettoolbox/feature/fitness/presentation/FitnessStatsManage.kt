package com.pockettoolbox.feature.fitness.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettoolbox.feature.fitness.domain.*

internal fun LazyListScope.fitnessStats(vm: FitnessViewModel, onCount: (FitnessCount) -> Unit,
    onDetail: (String) -> Unit, onRecord: (FitnessSetEvent) -> Unit) {
    val filter = vm.statsFilter
    item("statistics-filter") { FitnessCard {
        Text("训练统计", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30).forEach { days -> OutlinedButton(onClick = { vm.setStats(filter.copy(start = FitnessRules.date(vm.today).minusDays(days - 1L).toString(), end = vm.today)) }) { Text("近${days}天") } }
        }
        FitnessDateField("开始日期", filter.start) { vm.setStats(filter.copy(start = it)) }
        FitnessDateField("结束日期", filter.end) { vm.setStats(filter.copy(end = it)) }
        FitnessChoice("计划范围", filter.planId.orEmpty(), listOf("" to "全部计划（含归档）") + vm.data.plans.map { it.id to "${it.name}${if (it.status == FitnessPlanStatus.Archived) " · 已归档" else ""}" }, { vm.setStats(filter.copy(planId = it.ifBlank { null })) })
        FitnessChoice("明细用途", filter.purpose?.name.orEmpty(), listOf("" to "全部用途") + FitnessPurpose.entries.map { it.name to it.label }, { vm.setStats(filter.copy(purpose = it.takeIf(String::isNotBlank)?.let(FitnessPurpose::valueOf))) })
        FitnessChoice("统计维度", if (vm.statsByExercise) "exercise" else "part", listOf("part" to "按主练部位 / 用途", "exercise" to "按具体动作"), { vm.setStats(filter, it == "exercise") })
    } }
    item("statistics-overview") { FitnessCard {
        Text("所选日期与计划 · 全用途概览", fontWeight = FontWeight.Bold)
        val overview = vm.statsOverview
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("训练日 ${overview.trainingDays}"); Text("力量 ${overview.strengthSets}组") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("有氧 ${overview.cardioSegments}段"); Text("动作 ${overview.exercises}个") }
        Text("训练日按自然日去重，至少完成1组力量或1段有氧计入。热身、拉伸单独统计。撤销记录不计入。", style = MaterialTheme.typography.bodySmall)
    } }
    if (vm.statsCounts.isEmpty()) item("statistics-empty") { FitnessEmpty("该范围尚无有效记录。过去日期可从计划详情明确补记。") }
    items(vm.statsCounts, key = { "count-${it.key}" }) { row -> FitnessCard {
        Text(row.name, style = MaterialTheme.typography.titleMedium)
        Text("${row.days}个训练日 · ${row.segments}${if (row.purpose == FitnessPurpose.Strength) "组" else "段"}")
        if (vm.statsByExercise) {
            if (row.side == FitnessSide.Each) Text("已填实际：左${row.actualLeft}${row.unit.label}（${row.leftFilled}/${row.segments}） · 右${row.actualRight}${row.unit.label}（${row.rightFilled}/${row.segments}）")
            else Text("已填实际累计：${row.actualTotal}${row.unit.label} · 填写覆盖${row.totalFilled}/${row.segments}组/段")
            Text("未填实际值不按目标推算。", style = MaterialTheme.typography.bodySmall)
        }
        Row { TextButton(onClick = { onCount(row) }) { Text("查看组成记录") }
            if (vm.statsByExercise) TextButton(onClick = { onDetail(row.key.substringBefore('|')) }) { Text("动作教学") } }
    } }
    item("statistics-events-title") { Text("最近有效记录（当前范围，最多200条）", style = MaterialTheme.typography.titleMedium) }
    items(vm.statsRecords, key = { "stats-event-${it.id}" }) { event -> FitnessCard {
        Text(event.exercise.name, fontWeight = FontWeight.Bold); Text(fitnessEventTime(event), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onRecord(event) }) { Text("打开当日计划与组明细") }
    } }
}

internal fun LazyListScope.fitnessManage(vm: FitnessViewModel, onCustom: (String?) -> Unit, onSources: () -> Unit, onExit: () -> Unit) {
    item("manage-equipment") { FitnessCard {
        Text("我的器械", style = MaterialTheme.typography.titleLarge)
        Text("筛选采用完整条件。双哑铃包含单哑铃；弹力带动作也可能需要稳定固定点。")
        FitnessEquipmentLabels.forEach { (key, label) -> Row {
            Checkbox(key in vm.data.preferences.equipment, onCheckedChange = { checked ->
                val old = vm.data.preferences
                vm.preferences(old.copy(equipment = if (checked) old.equipment + key else old.equipment - key))
            }, enabled = !vm.busy)
            Text(label, Modifier.padding(top = 12.dp))
        } }
        Row { Checkbox(vm.data.preferences.recoveryEnabled, { vm.preferences(vm.data.preferences.copy(recoveryEnabled = it)) }, enabled = !vm.busy); Text("首页显示恢复参考", Modifier.padding(top = 12.dp)) }
    } }
    item("manage-personal-title") { Row { Text("个人动作", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { onCustom(null) }, enabled = !vm.busy) { Text("新建") } } }
    items(vm.data.personalExercises, key = { "personal-${it.id}" }) { exercise -> FitnessCard {
        Text("${exercise.name}${if (exercise.archived) " · 已停用" else ""}", fontWeight = FontWeight.Bold)
        Row { TextButton(onClick = { onCustom(exercise.id) }, enabled = !vm.busy) { Text("编辑") }
            TextButton(onClick = { vm.saveExercise(exercise.copy(archived = !exercise.archived)) {} }, enabled = !vm.busy) { Text(if (exercise.archived) "重新启用" else "停用") } }
        Text("停用后不再推荐新安排，已存在的计划与记录保留。", style = MaterialTheme.typography.bodySmall)
    } }
    item("manage-backup") { FitnessBackupControls(vm) }
    item("manage-content") { FitnessCard {
        Text("动作内容与来源", style = MaterialTheme.typography.titleLarge)
        val content = vm.catalog
        Text("版本 ${content?.version.orEmpty()}\n${content?.sourceCount ?: 0}条完整来源资料 · ${content?.exercises?.size ?: 0}个中文开发样本 · ${content?.reviewedCount ?: 0}个专业审核通过")
        FitnessHint("当前为开发内容。来源资料的中文校订、器械与计量映射、教学图及肌肉图审核仍在进行，不能将876条来源记录视为876个已完成教学动作。", true)
        TextButton(onClick = onSources, enabled = !vm.busy) { Text("浏览全部来源资料") }
        Text("原始动作与图片：yuhonas/free-exercise-db（Unlicense），固定版本${content?.sourceRevision.orEmpty()}。中文样本图与肌肉示意图的出处见仓库素材说明；肌肉图为示意，存在待补图部位。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onExit, enabled = !vm.busy) { Text("返回工具箱") }
    } }
}

@Composable
private fun FitnessBackupControls(vm: FitnessViewModel) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入备份位置" } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect { requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选备份" } }
    }
    var warning by rememberSaveable { mutableStateOf(false) }
    FitnessCard {
        Text("健身资料备份", style = MaterialTheme.typography.titleLarge)
        Text("包含计划、有效及撤销记录、个人动作与图片、模板、收藏、器械偏好和已暂存草稿。内置动作素材随应用分发。")
        Text("不自动上传。更换电脑、手机或卸载前，请导出ZIP并妥善保存。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { export.launch("pocket-toolbox-fitness-${vm.today}.zip") }, enabled = !vm.busy && !vm.draftDirty) { Text("导出健身ZIP") }
        if (vm.draftDirty) Text("请先完成草稿暂存，再导出。", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { warning = true }, enabled = !vm.busy) { Text("恢复健身ZIP") }
    }
    if (warning) AlertDialog(onDismissRequest = { warning = false }, title = { Text("选择健身备份") }, text = { Text("读取后会先进行预检，并展示具体替换内容，确认后才写入。恢复会替换本机健身资料，请先导出当前资料。") },
        confirmButton = { TextButton(onClick = { warning = false; restore.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("选择ZIP") } }, dismissButton = { TextButton(onClick = { warning = false }) { Text("取消") } })
}

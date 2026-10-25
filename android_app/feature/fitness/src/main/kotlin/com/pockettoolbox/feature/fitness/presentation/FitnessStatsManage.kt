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
        var expanded by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 30).forEach { days -> OutlinedButton(onClick = { vm.setStats(filter.copy(start = FitnessRules.date(vm.today).minusDays(days - 1L).toString(), end = vm.today)) }) { Text("近${days}天") } }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起筛选" else "筛选 / 自定义") }
        }
        Text("${filter.start} — ${filter.end} · ${vm.data.plans.firstOrNull { it.id == filter.planId }?.name ?: "全部计划"}", style = MaterialTheme.typography.labelSmall)
        if (expanded) {
            FitnessDateField("开始日期", filter.start) { vm.setStats(filter.copy(start = it)) }
            FitnessDateField("结束日期", filter.end) { vm.setStats(filter.copy(end = it)) }
            FitnessChoice("计划范围", filter.planId.orEmpty(), listOf("" to "全部计划（含归档）") + vm.data.plans.map { it.id to "${it.name}${if (it.status == FitnessPlanStatus.Archived) " · 已归档" else ""}" }, { vm.setStats(filter.copy(planId = it.ifBlank { null })) })
            FitnessChoice("明细用途", filter.purpose?.name.orEmpty(), listOf("" to "全部用途") + FitnessPurpose.entries.map { it.name to it.label }, { vm.setStats(filter.copy(purpose = it.takeIf(String::isNotBlank)?.let(FitnessPurpose::valueOf))) })
            FitnessChoice("排序", if (vm.statsFixedOrder) "fixed" else "count", listOf("count" to "数量从多到少", "fixed" to "固定部位 / 名称顺序"), { vm.statsOrder(it == "fixed") })
        }
        val overview = vm.statsOverview
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("训练日" to "${overview.trainingDays}天", "力量" to "${overview.strengthSets}组", "有氧" to "${overview.cardioSegments}段", "动作" to "${overview.exercises}个").forEach { (label, value) ->
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
    } }
    val counts = if (vm.statsFixedOrder) vm.statsCounts.sortedWith(compareBy<FitnessCount> {
        if (vm.statsByExercise) 0 else FitnessPart.entries.indexOfFirst { part -> part.name == it.key }.let { index -> if (index < 0) 100 else index }
    }.thenBy { it.name }) else vm.statsCounts.sortedWith(compareByDescending<FitnessCount> { if (vm.statsByDays) it.days else it.segments }.thenBy { it.key })
    item("statistics-chart-card") { FitnessCard {
        val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("part" to "按部位", "exercise" to "按动作", "daily" to "每日记录").forEach { (key, label) ->
                val active = if (key == "daily") vm.statsDaily else !vm.statsDaily && vm.statsByExercise == (key == "exercise")
                FilterChip(selected = active, onClick = { vm.statsView(key == "daily", key == "exercise") }, label = { Text(label) }, colors = chipColors)
            }
        }
        if (!vm.statsDaily) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(filter.purpose?.label ?: "全部用途", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(vm.statsByDays, { vm.statsMetric(true) }, label = { Text("天次") }, colors = chipColors)
                    FilterChip(!vm.statsByDays, { vm.statsMetric(false) }, label = { Text("组 / 段数") }, colors = chipColors)
                }
            }
            if (counts.isNotEmpty()) {
                val scopeKey = "${filter.start}/${filter.end}/${filter.planId}/${filter.purpose}/${vm.statsByExercise}/${vm.statsByDays}/${vm.statsFixedOrder}"
                var page by rememberSaveable(scopeKey) { mutableIntStateOf(0) }
                var selected by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
                val pages = (counts.size + 11) / 12
                val currentPage = page.coerceIn(0, pages - 1)
                val visible = counts.drop(currentPage * 12).take(12)
                val selectedRow = visible.firstOrNull { it.key == selected } ?: visible.first()
                val maximum = fitnessChartMaximum(counts.maxOf { if (vm.statsByDays) it.days else it.segments })
                FitnessStatisticsChart(visible, vm.statsByDays, maximum, selectedRow.key) { selected = it }
                if (pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { page = currentPage - 1; selected = null }, enabled = currentPage > 0) { Text("上一页") }
                    Text("${currentPage + 1} / $pages · 共${counts.size}项", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { page = currentPage + 1; selected = null }, enabled = currentPage + 1 < pages) { Text("下一页") }
                }
                FitnessChoice("当前项目（也可点图选择）", selectedRow.key, visible.map { it.key to it.name }, { selected = it })
                Text("${selectedRow.days}个${if (selectedRow.purpose.countsTrainingDay) "训练" else "活动"}日 · ${selectedRow.segments}${if (selectedRow.purpose == FitnessPurpose.Strength) "组" else "段"}", style = MaterialTheme.typography.bodySmall)
                if (vm.statsByExercise) FitnessActualSummary(selectedRow)
                Row {
                    TextButton(onClick = { onCount(selectedRow) }) { Text("查看组成记录") }
                    if (vm.statsByExercise) TextButton(onClick = { onDetail(selectedRow.key.substringBefore('|')) }) { Text("动作教学") }
                }
            }
        }
        if (counts.isEmpty()) Text("该范围尚无有效记录。过去日期可从计划详情明确补记。")
    } }
    item("statistics-scope-note") { Text("同一天的部位天次不相加作为总训练天数；辅助参与不重复累计主练组数。", style = MaterialTheme.typography.bodySmall) }
    if (vm.statsDaily) {
        item("statistics-events-title") { Text("有效记录（当前范围，最多200条）", style = MaterialTheme.typography.titleMedium) }
        items(vm.statsRecords, key = { "stats-event-${it.id}" }) { event -> FitnessCard {
            Text(event.exercise.name, fontWeight = FontWeight.Bold); Text(fitnessEventTime(event), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onRecord(event) }) { Text("打开当日计划与组明细") }
        } }
    }
}

@Composable
private fun FitnessActualSummary(row: FitnessCount) {
    if (row.side == FitnessSide.Each) Text("已填实际：左${if (row.leftFilled > 0) "${row.actualLeft}${row.unit.label}" else "未填"}（${row.leftFilled}/${row.segments}） · 右${if (row.rightFilled > 0) "${row.actualRight}${row.unit.label}" else "未填"}（${row.rightFilled}/${row.segments}）")
    else Text(if (row.totalFilled == 0) "实际值尚未填写 · 0/${row.segments}组/段已填" else "已填实际累计：${row.actualTotal}${row.unit.label} · 填写覆盖${row.totalFilled}/${row.segments}组/段")
}

internal fun LazyListScope.fitnessManage(vm: FitnessViewModel, onCustom: (String?) -> Unit, onSources: () -> Unit, onExit: () -> Unit) {
    item("manage-equipment") { FitnessCard {
        Text("我的器械", style = MaterialTheme.typography.titleLarge)
        Text("中文样稿按器械组合筛选；来源动作按原器械标签筛选，配套器械请看原图。双哑铃包含单哑铃。")
        FitnessEquipmentLabels.filterKeys { it != "sourceEquipmentUnknown" }.forEach { (key, label) -> Row {
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
        Text("版本 ${content?.version.orEmpty()}\n${content?.selectableExercises?.size ?: 0}个内置可选动作：${content?.sourceCount ?: 0}个来源动作＋${content?.exercises?.size ?: 0}个中文样稿 · ${content?.reviewedCount ?: 0}个专业审核通过")
        FitnessHint("来源动作保留英文说明与原图。中文教学和专业审校尚未完成，器械与动作细节请结合原图确认；记录单位可在首次记组前调整。")
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

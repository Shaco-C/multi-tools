package com.pockettoolbox.feature.fitness.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pockettoolbox.feature.fitness.domain.*

internal fun fitnessScopeMatches(exercise: FitnessExercise, scope: String?, secondary: Boolean = false): Boolean = when {
    scope.isNullOrBlank() -> true
    scope.startsWith("part:") -> exercise.purpose == FitnessPurpose.Strength &&
        (exercise.primaryPart?.name == scope.substringAfter(':') || secondary && exercise.secondaryParts.any { it.name == scope.substringAfter(':') })
    scope.startsWith("purpose:") -> exercise.purpose.name == scope.substringAfter(':')
    else -> false
}

internal fun fitnessScopeLabel(scope: String): String = if (scope.startsWith("part:")) FitnessPart.valueOf(scope.substringAfter(':')).label else FitnessPurpose.valueOf(scope.substringAfter(':')).label

internal fun LazyListScope.fitnessLibrary(vm: FitnessViewModel, onCategory: (String) -> Unit, onSearch: (String) -> Unit, onCustom: () -> Unit) {
    item {
        FitnessCard {
            Text("先认识练哪里，再了解怎么做。", style = MaterialTheme.typography.titleMedium)
            Text("中文样稿${vm.catalog?.exercises?.size ?: 0}项 · 全量来源${vm.catalog?.sourceCount ?: 0}条", style = MaterialTheme.typography.bodySmall)
            Row { Button(onClick = { onSearch("all") }) { Text("全库搜索") }; TextButton(onClick = onCustom) { Text("新建我的动作") } }
            Text("当前内容为开发/待审状态，来源与生产进度可在管理中查看。", style = MaterialTheme.typography.labelSmall)
        }
    }
    val groups = FitnessPart.entries.chunked(3)
    items(groups) { group -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        group.forEach { part ->
            Card(onClick = { onCategory("part:${part.name}") }, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FitnessMuscle(vm.dependencies, part, modifier = Modifier.size(50.dp, 76.dp))
                    Text(part.label, style = MaterialTheme.typography.labelLarge)
                    Text("${vm.exercises.count { !it.archived && it.purpose == FitnessPurpose.Strength && it.primaryPart == part }}个主练", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
    } }
    item { Text("活动分类", fontWeight = FontWeight.Bold) }
    items(listOf(FitnessPurpose.Cardio, FitnessPurpose.Warmup, FitnessPurpose.Stretch)) { purpose ->
        OutlinedButton(onClick = { onCategory("purpose:${purpose.name}") }, modifier = Modifier.fillMaxWidth()) { Text(purpose.label) }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onSearch("favorites") }, modifier = Modifier.weight(1f)) { Text("收藏 · ${vm.exercises.count { !it.archived && it.id in vm.data.favorites }}") }
            OutlinedButton(onClick = { onSearch("recent") }, modifier = Modifier.weight(1f)) { Text("最近练过") }
        }
    }
}

internal fun LazyListScope.fitnessLibraryList(vm: FitnessViewModel, category: String?, mode: String, query: String, mine: Boolean, source: String,
    secondary: Boolean, limit: Int, onQuery: (String) -> Unit, onMine: (Boolean) -> Unit, onSource: (String) -> Unit,
    onSecondary: (Boolean) -> Unit, onMore: () -> Unit, onDetail: (String) -> Unit, onClear: () -> Unit, onGlobal: () -> Unit, onCustom: () -> Unit) {
    val matches = vm.exercises.filter { exercise ->
        !exercise.archived && fitnessScopeMatches(exercise, category, secondary) &&
            (mode != "favorites" || exercise.id in vm.data.favorites) && (mode != "recent" || exercise.id in vm.recentExerciseIds) &&
            (!mine || exercise.matchesEquipment(vm.data.preferences.equipment)) && (source == "all" || exercise.personal == (source == "personal")) &&
            (query.isBlank() || (listOf(exercise.name, exercise.english) + exercise.aliases).any { it.contains(query.trim(), ignoreCase = true) })
    }.let { rows -> if (mode == "recent") rows.sortedBy { vm.recentExerciseIds.indexOf(it.id) } else rows }
    item {
        FitnessCard {
            Text(category?.let(::fitnessScopeLabel) ?: when (mode) { "favorites" -> "我的收藏"; "recent" -> "最近练过"; else -> "搜索全部已整理动作" }, fontWeight = FontWeight.Bold)
            OutlinedTextField(query, onQuery, label = { Text("搜索名称、别名或英文名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (category?.startsWith("part:") == true) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(secondary, onSecondary); Text("也看辅助参与") }
            FitnessChoice("器械匹配", if (mine) "mine" else "all", listOf("mine" to "只看我的器械", "all" to "查看全部器械"), { onMine(it == "mine") })
            FitnessChoice("动作来源", source, listOf("all" to "内置＋自建", "builtin" to "内置样稿", "personal" to "我的动作"), onSource)
            Row { Text("${matches.size}个匹配，已显示${minOf(limit, matches.size)}", Modifier.weight(1f)); TextButton(onClick = onClear) { Text("重置筛选") } }
        }
    }
    if (matches.isEmpty()) item { FitnessEmpty("当前条件下没有动作。") {
        Row { TextButton(onClick = onClear) { Text("重置筛选") }; if (category != null) TextButton(onClick = onGlobal) { Text("转全库搜索") } }
        TextButton(onClick = onCustom) { Text("新建我的动作") }
    } }
    items(matches.take(limit), key = { it.id }) { exercise -> FitnessExerciseRow(vm, exercise, { onDetail(exercise.id) }) }
    if (matches.size > limit) item { OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("再显示20个") } }
}

@Composable
internal fun FitnessGallery(vm: FitnessViewModel, images: List<FitnessImage>, name: String) {
    var frame by rememberSaveable(name) { mutableIntStateOf(0) }
    var zoom by rememberSaveable(name) { mutableStateOf(false) }
    FitnessCard {
        FitnessPager(vm, images, name, frame, { frame = it }, 310)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (images.isEmpty()) "暂无教学图片" else "步骤图 ${frame + 1}/${images.size}", style = MaterialTheme.typography.bodySmall)
            if (images.isNotEmpty()) TextButton(onClick = { zoom = true }) { Text("放大") }
        }
        if (images.size > 1) Text("左右滑动查看下一张，无自动轮播。", style = MaterialTheme.typography.labelSmall)
    }
    if (zoom) Dialog(onDismissRequest = { zoom = false }) {
        FitnessCard { FitnessPager(vm, images, name, frame, { frame = it }, 450); TextButton(onClick = { zoom = false }) { Text("关闭放大") } }
    }
}

@Composable
private fun FitnessPager(vm: FitnessViewModel, images: List<FitnessImage>, name: String, frame: Int, onFrame: (Int) -> Unit, height: Int) {
    if (images.isEmpty()) { FitnessMedia(vm.dependencies, null, vm.data.mediaGeneration, "$name暂无图片", Modifier.fillMaxWidth().height(200.dp)); return }
    val pager = rememberPagerState(initialPage = frame.coerceIn(0, images.lastIndex), pageCount = { images.size })
    LaunchedEffect(frame, images.size) { if (pager.currentPage != frame.coerceIn(0, images.lastIndex)) pager.scrollToPage(frame.coerceIn(0, images.lastIndex)) }
    LaunchedEffect(pager.currentPage) { onFrame(pager.currentPage) }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(height.dp)) { index ->
        FitnessMedia(vm.dependencies, images[index], vm.data.mediaGeneration, "$name步骤图${index + 1}/${images.size}", Modifier.fillMaxSize(), width = 360.dp, height = height.dp, retryEnabled = true)
    }
}

internal fun LazyListScope.fitnessDetail(vm: FitnessViewModel, id: String?, onAdd: () -> Unit, actionLabel: String, onCustom: (String) -> Unit) {
    val exercise = vm.exercises.firstOrNull { it.id == id }
    if (exercise == null) { item { FitnessEmpty("动作教学暂不可用，原训练历史仍按快照保留。") }; return }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(exercise.english, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.favorite(exercise.id) }, enabled = !vm.busy) { Text(if (exercise.id in vm.data.favorites) "已收藏" else "收藏") }
        }
    }
    item { FitnessHint(if (exercise.reviewed) "内容版本：${exercise.contentRevision}" else "内容样稿 · 待专业教学/解剖审校。") }
    item { FitnessGallery(vm, exercise.images, exercise.name) }
    item { FitnessCard {
        Text("这个动作是什么", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(exercise.introduction.ifBlank { "尚未填写介绍" })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            FitnessMuscle(vm.dependencies, exercise.primaryPart, exercise.id, Modifier.size(110.dp, 160.dp))
            Column {
                Text("主要训练部位：${exercise.primaryPart?.label ?: "活动用途"}")
                Text("辅助参与：${exercise.secondaryParts.joinToString("、") { it.label }.ifEmpty { "未标注" }}", style = MaterialTheme.typography.bodySmall)
                Text("${exercise.purpose.label} · ${if (exercise.side == FitnessSide.Each) "每侧" else ""}${exercise.unit.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("参与标签用于教学，不重复累计组数。", style = MaterialTheme.typography.labelSmall)
    } }
    item { FitnessCard {
        Text("准备 → 动作 → 还原", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        exercise.steps.forEachIndexed { index, text -> Text("${index + 1}. $text") }
        if (exercise.steps.isEmpty()) Text("尚未填写步骤")
        exercise.cues.forEach { FitnessHint(it) }
    } }
    item { FitnessCard {
        Text("器械与来源", fontWeight = FontWeight.Bold)
        exercise.equipment.forEach { group -> Text(group.joinToString("＋") { FitnessEquipmentLabels[it] ?: it }) }
        exercise.references.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("版本：${exercise.contentRevision}", style = MaterialTheme.typography.labelSmall)
    } }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, enabled = !vm.busy && (!exercise.archived || actionLabel.startsWith("返回")), modifier = Modifier.fillMaxWidth()) { Text(if (exercise.archived) "已停用 · $actionLabel" else actionLabel) }
            OutlinedButton(onClick = { onCustom(exercise.id) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(if (exercise.personal) "编辑我的动作" else "复制为我的动作") }
        }
    }
}

internal fun LazyListScope.fitnessSources(vm: FitnessViewModel, query: String, limit: Int, onQuery: (String) -> Unit, onMore: () -> Unit, onDetail: (String) -> Unit) {
    val exercisesById = vm.exercises.associateBy { it.id }
    val records = vm.catalog?.sourceRecords.orEmpty().filter { source ->
        val linked = exercisesById[source.linkedExerciseId]
        query.isBlank() || source.name.contains(query, true) || source.primaryMuscles.any { it.contains(query, true) } || linked?.name?.contains(query) == true
    }
    item { FitnessHint("全量${vm.catalog?.sourceCount ?: 0}条已采集资料，按原名、原说明和原图离线浏览。中文/计量/器械整理与逐项审校正在进行；来源记录不直接作为自动推荐。") }
    item { OutlinedTextField(query, onQuery, label = { Text("搜索英文名、肌群或已关联中文名") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
    item { Text("${records.size}条匹配 · 已显示${minOf(limit, records.size)}") }
    items(records.take(limit), key = { it.id }) { source -> Card(onClick = { onDetail(source.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitnessMedia(vm.dependencies, source.images.firstOrNull(), "builtin", source.name, Modifier.size(68.dp), width = 68.dp, height = 68.dp)
            Column(Modifier.weight(1f)) {
                Text(source.name, fontWeight = FontWeight.Bold)
                Text("${source.category ?: "未标用途"} · ${source.equipment ?: "器械待补"}", style = MaterialTheme.typography.bodySmall)
                if (source.images.isEmpty() || source.instructions.isEmpty()) Text("${if (source.images.isEmpty()) "图片待补 " else ""}${if (source.instructions.isEmpty()) "步骤待补" else ""}", color = MaterialTheme.colorScheme.error)
            }
        }
    } }
    if (records.size > limit) item { OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("再显示30条") } }
}

internal fun LazyListScope.fitnessSourceDetail(vm: FitnessViewModel, id: String?, onCustom: (String) -> Unit, onLinked: (String) -> Unit) {
    val source = vm.catalog?.sourceRecords?.firstOrNull { it.id == id }
    if (source == null) { item { FitnessEmpty("来源记录未找到") }; return }
    item { FitnessHint("原始来源资料，尚未完成本项目中文、器械、计量及专业教学复核。") }
    item { Text(source.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
    item { FitnessGallery(vm, source.images, source.name) }
    item { FitnessCard {
        Text("来源标注", fontWeight = FontWeight.Bold)
        Text("主要肌群：${source.primaryMuscles.joinToString("、")}")
        Text("辅助肌群：${source.secondaryMuscles.joinToString("、").ifBlank { "未提供" }}")
        Text("器械：${source.equipment ?: "未提供，不能当作徒手"}")
        Text("源类别：${source.category}；来源难度：${source.level ?: "未知"}。不据此推定适合个人。")
    } }
    item { FitnessCard {
        Text("来源步骤原文", fontWeight = FontWeight.Bold)
        if (source.instructions.isEmpty()) Text("此记录缺步骤，需要另行补齐。")
        source.instructions.forEachIndexed { index, text -> Text("${index + 1}. $text") }
    } }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            source.linkedExerciseId?.let { linked -> OutlinedButton(onClick = { onLinked(linked) }) { Text("查看关联中文样稿（对应关系待审）") } }
            Button(onClick = { onCustom(source.id) }, enabled = !vm.busy) { Text("复制为我的动作并填写计量") }
            Text("来源：free-exercise-db\nID：${source.id}\n版本：${vm.catalog?.sourceRevision}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

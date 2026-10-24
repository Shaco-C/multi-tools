package com.pockettoolbox.feature.fitness.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies
import com.pockettoolbox.feature.fitness.data.FitnessJson
import com.pockettoolbox.feature.fitness.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal data class FitnessConfirmation(val title: String, val message: String, val confirm: () -> Unit)

@Composable
fun FitnessRoute(dependencies: FitnessFeatureDependencies, onExit: () -> Unit,
    vm: FitnessViewModel = viewModel(factory = FitnessViewModelFactory(dependencies))) {
    var page by rememberSaveable { mutableStateOf("Today") }
    var detailOrigin by rememberSaveable { mutableStateOf("LibraryList") }
    var planOrigin by rememberSaveable { mutableStateOf("Plans") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var planId by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(vm.today) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryMode by rememberSaveable { mutableStateOf("category") }
    var query by rememberSaveable { mutableStateOf("") }
    var mine by rememberSaveable { mutableStateOf(true) }
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    var includeSecondary by rememberSaveable { mutableStateOf(false) }
    var libraryLimit by rememberSaveable { mutableIntStateOf(20) }
    var pickerScope by rememberSaveable { mutableStateOf("part:Chest") }
    var pickerQuery by rememberSaveable { mutableStateOf("") }
    var pickerMine by rememberSaveable { mutableStateOf(true) }
    var pickerSource by rememberSaveable { mutableStateOf("all") }
    var pickerSelected by rememberSaveable { mutableStateOf(false) }
    var pickerLimit by rememberSaveable { mutableIntStateOf(20) }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    var sourceLimit by rememberSaveable { mutableIntStateOf(30) }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var customId by rememberSaveable { mutableStateOf<String?>(null) }
    var customSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var customAdd by rememberSaveable { mutableStateOf(false) }
    var customScope by rememberSaveable { mutableStateOf("") }
    var addId by rememberSaveable { mutableStateOf<String?>(null) }
    var actualJson by rememberSaveable { mutableStateOf<String?>(null) }
    var batchRow by remember { mutableStateOf<FitnessScheduledItem?>(null) }
    var copySource by remember { mutableStateOf<FitnessPlanDay?>(null) }
    var confirmation by remember { mutableStateOf<FitnessConfirmation?>(null) }
    var scopedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var scopedScope by remember { mutableStateOf<FitnessRecordScope?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val homeList = rememberLazyListState()
    val plansList = rememberLazyListState()
    val planDetailList = rememberLazyListState()
    val editorList = rememberLazyListState()
    val detailList = rememberLazyListState()
    val statsList = rememberLazyListState()
    val manageList = rememberLazyListState()
    val libraryList = rememberLazyListState()
    val pickerList = rememberLazyListState()
    val sourceList = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun listFor(destination: String) = when (destination) {
        "Plans" -> plansList; "PlanDetail" -> planDetailList; "Editor" -> editorList; "Detail", "SourceDetail" -> detailList
        "Stats" -> statsList; "Manage" -> manageList; else -> homeList
    }
    val mainList = listFor(page)

    fun editor(request: FitnessEditorRequest) { vm.requestEditor(request) { page = "Editor"; scope.launch { editorList.scrollToItem(0) } } }
    fun detail(id: String, origin: String = page) { detailOrigin = origin; detailId = id; page = "Detail"; scope.launch { detailList.scrollToItem(0) } }
    fun openPlan(id: String, selectedDate: String, origin: String = page) {
        planId = id; date = selectedDate; planOrigin = origin; vm.selectDate(date, id); page = "PlanDetail"; scope.launch { planDetailList.scrollToItem(0) }
    }
    fun custom(id: String? = null, raw: String? = null, add: Boolean = false, scopeHint: String = "") {
        customId = id; customSourceId = raw; customAdd = add; customScope = scopeHint; customOpen = true
    }
    fun root(destination: String) {
        val action = {
            page = destination
            scope.launch { listFor(destination).scrollToItem(0) }
            if (destination == "Today") { date = vm.today; planId = null; vm.selectDate(date) }
        }
        if (vm.editorVisible) vm.leaveEditor(action) else action()
    }
    fun back() {
        if (vm.busy) return
        when (page) {
            "Detail" -> page = detailOrigin
            "SourceDetail" -> page = "Sources"
            "Sources" -> page = "Manage"
            "LibraryList", "Search" -> page = "Library"
            "Picker" -> page = "Editor"
            "Editor" -> vm.leaveEditor { page = "Plans" }
            "PlanDetail" -> page = planOrigin.takeIf { it in listOf("Today", "Stats", "Plans") } ?: "Plans"
            "Today" -> onExit()
            else -> root("Today")
        }
    }
    fun recordDay(event: FitnessSetEvent) {
        scopedTitle = null; scopedScope = null; vm.recordScope(null); vm.closeItemRecords()
        openPlan(event.planId, event.trainingDate, if (page == "Stats") "Stats" else "Today")
    }

    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            val result = snackbar.showSnackbar(notice.text, actionLabel = if (notice.undo != null) "撤销" else null, duration = SnackbarDuration.Short, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) notice.undo?.invoke()
        }
    }
    LaunchedEffect(vm) { while (true) { delay(30_000); vm.refreshToday() } }
    LaunchedEffect(vm.loaded, date, planId, page, detailOrigin) {
        if (vm.loaded) vm.selectDate(date, if (page == "PlanDetail" || page == "Detail" && detailOrigin == "PlanDetail") planId else null)
    }
    LaunchedEffect(vm.loaded, page) {
        if (vm.loaded && !vm.editorVisible && vm.draft != null && (page in listOf("Editor", "Picker") || page == "Detail" && detailOrigin in listOf("Editor", "Picker"))) vm.resumeEditor({})
    }
    LaunchedEffect(vm.loaded, page, libraryMode) { if (vm.loaded && page == "Search" && libraryMode == "recent") vm.loadRecent() }
    LaunchedEffect(vm.draft?.plan?.id, vm.draft?.step) { if (page == "Editor") editorList.scrollToItem(0) }
    BackHandler { back() }

    val title = when (page) {
        "Today" -> "今天的训练"; "Plans" -> "训练计划"; "Library" -> "动作库"; "Stats" -> "训练统计"; "Manage" -> "管理"
        "Editor" -> "安排训练"; "Picker" -> "选择动作"; "PlanDetail" -> vm.data.plans.firstOrNull { it.id == planId }?.name ?: "计划详情"
        "Detail" -> vm.exercises.firstOrNull { it.id == detailId }?.name ?: "动作详情"
        "Sources" -> "全量来源资料"; "SourceDetail" -> "来源动作详情"; "Search" -> "全库搜索"; else -> "分类动作"
    }
    val selectedRoot = when (page) { "Editor", "Picker", "PlanDetail" -> "Plans"; "Detail" -> when (detailOrigin) { "Today" -> "Today"; "PlanDetail", "Picker", "Editor" -> "Plans"; "Stats" -> "Stats"; else -> "Library" }; "LibraryList", "Search" -> "Library"; else -> page }
    Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding(), snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                listOf("Today" to ("今日" to Icons.Default.Home), "Plans" to ("计划" to Icons.Default.CalendarMonth),
                    "Library" to ("动作库" to Icons.Default.FitnessCenter), "Stats" to ("统计" to Icons.Default.BarChart)).forEach { (id, value) ->
                    NavigationBarItem(selected = selectedRoot == id, onClick = { if (!vm.busy) root(id) },
                        icon = { Icon(value.second, contentDescription = null) }, label = { Text(value.first) })
                }
            }
        }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight().widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::back, enabled = !vm.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("随身工具箱 / 居家健身", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { root("Manage") }, enabled = !vm.busy) { Icon(Icons.Default.Settings, contentDescription = "管理") }
                }
                if (vm.busy || vm.draftSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.loadError != null) FitnessCard {
                    Text(vm.loadError.orEmpty()); Text("读取失败不会覆盖已有资料。"); Button(onClick = vm::load) { Text("重试") }
                } else if (!vm.loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else {
                    val state = when (page) { "LibraryList", "Search" -> libraryList; "Picker" -> pickerList; "Sources" -> sourceList; else -> mainList }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state,
                        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (page == "Today") fitnessToday(vm, date,
                            onDate = { date = it; vm.selectDate(it) }, onEditor = ::editor,
                            onFocus = { row, index -> vm.focus(row); scope.launch { mainList.animateScrollToItem(index) } },
                            onPlan = { id, day -> openPlan(id, day, "Today") }, onDetail = { detail(it, "Today") },
                            onBackfill = { batchRow = it }, onConfirm = { confirmation = it }, onRecords = { row -> vm.records(row.item.id) },
                            onTodayRecords = { scopedScope = FitnessRecordScope(FitnessFilter(vm.today, vm.today)); vm.recordScope(scopedScope); scopedTitle = "今日记录" })
                        if (page == "Plans") fitnessPlans(vm, ::editor, onOpen = { id, day -> openPlan(id, day, "Plans") }, onConfirm = { confirmation = it }, onResume = { vm.resumeEditor({ page = "Editor" }) })
                        if (page == "PlanDetail") fitnessPlanDetail(vm, planId, date,
                            onDate = { date = it; vm.selectDate(it, planId) }, onEditor = ::editor,
                            onFocus = { row, index -> vm.focus(row); scope.launch { mainList.animateScrollToItem(index) } },
                            onDetail = { detail(it, "PlanDetail") }, onBackfill = { batchRow = it }, onConfirm = { confirmation = it },
                            onCopy = { copySource = it }, onRecords = { vm.records(it.item.id) })
                        if (page == "Library") fitnessLibrary(vm, onCategory = { key -> category = key; libraryMode = "category"; query = ""; mine = true; sourceFilter = "all"; includeSecondary = false; libraryLimit = 20; page = "LibraryList" },
                            onSearch = { mode -> libraryMode = mode; category = null; query = ""; mine = mode == "all"; sourceFilter = "all"; libraryLimit = 20; page = "Search" }, onCustom = { custom() })
                        if (page in listOf("LibraryList", "Search")) fitnessLibraryList(vm, category, libraryMode, query, mine, sourceFilter, includeSecondary, libraryLimit,
                            onQuery = { query = it; libraryLimit = 20; scope.launch { libraryList.scrollToItem(0) } },
                            onMine = { mine = it; libraryLimit = 20 }, onSource = { sourceFilter = it; libraryLimit = 20 },
                            onSecondary = { includeSecondary = it; libraryLimit = 20 }, onMore = { libraryLimit += 20 }, onDetail = { detail(it, page) },
                            onClear = { query = ""; mine = true; sourceFilter = "all"; libraryLimit = 20 },
                            onGlobal = { category = null; libraryMode = "all"; mine = false; page = "Search" }, onCustom = { custom(scopeHint = category.orEmpty()) })
                        if (page == "Editor") fitnessEditor(vm,
                            onPick = { pickerScope = it; pickerQuery = ""; pickerMine = true; pickerSource = "all"; pickerSelected = false; pickerLimit = 20; page = "Picker" },
                            onDetail = { detail(it, "Editor") }, onConfirm = { confirmation = it },
                            onLeave = { vm.leaveEditor { page = "Plans" } },
                            onDiscarded = { page = "Plans" },
                            onSaved = { plan -> openPlan(plan.id, vm.today.takeIf { it >= plan.start && it <= plan.end } ?: plan.start, "Plans") })
                        if (page == "Picker") fitnessPicker(vm, pickerScope, pickerQuery, pickerMine, pickerSource, pickerSelected, pickerLimit,
                            onScope = { pickerScope = it; pickerQuery = ""; pickerLimit = 20 },
                            onQuery = { pickerQuery = it; pickerLimit = 20 }, onMine = { pickerMine = it; pickerLimit = 20 }, onSource = { pickerSource = it; pickerLimit = 20 },
                            onSelected = { pickerSelected = !pickerSelected }, onMore = { pickerLimit += 20 }, onDetail = { detail(it, "Picker") },
                            onClear = { pickerQuery = ""; pickerMine = true; pickerSource = "all"; pickerLimit = 20 }, onCustom = { custom(add = true) }, onConfirm = { confirmation = it })
                        if (page == "Detail") fitnessDetail(vm, detailId, onAdd = {
                            val exercise = vm.exercises.firstOrNull { it.id == detailId }
                            when (detailOrigin) {
                                "Picker" -> { if (exercise != null && vm.draft?.let { draft -> draft.plan.day(draft.selectedDate)?.activeItems?.none { it.exercise.exerciseId == exercise.id } } == true) vm.toggleExercise(exercise); page = "Picker" }
                                "Editor", "Today", "PlanDetail", "Stats" -> page = detailOrigin
                                else -> exercise?.let { addId = it.id }
                            }
                        }, actionLabel = when (detailOrigin) { "Picker" -> "返回选择（未选则加入）"; "Editor" -> "返回动作清单"; "Today", "PlanDetail" -> "返回训练"; "Stats" -> "返回统计"; else -> "加入我的计划" }, onCustom = { custom(id = it) })
                        if (page == "Stats") fitnessStats(vm, onCount = { row ->
                            val recordFilter = vm.statsFilter.copy(purpose = row.purpose)
                            scopedScope = if (vm.statsByExercise) FitnessRecordScope(recordFilter, exerciseId = row.key.substringBefore('|'), unit = row.unit, side = row.side)
                                else FitnessRecordScope(recordFilter, part = FitnessPart.entries.firstOrNull { it.name == row.key })
                            vm.recordScope(scopedScope); scopedTitle = "${row.name} · 组成记录"
                        }, onDetail = { detail(it, "Stats") }, onRecord = ::recordDay)
                        if (page == "Manage") fitnessManage(vm, onCustom = { custom(id = it) }, onSources = { page = "Sources" }, onExit = onExit)
                        if (page == "Sources") fitnessSources(vm, sourceQuery, sourceLimit,
                            onQuery = { sourceQuery = it; sourceLimit = 30 }, onMore = { sourceLimit += 30 }, onDetail = { sourceId = it; page = "SourceDetail" })
                        if (page == "SourceDetail") fitnessSourceDetail(vm, sourceId, onCustom = { custom(raw = it) }, onLinked = { detail(it, "SourceDetail") })
                    }
                    if (page == "Picker") {
                        val count = vm.draft?.let { it.plan.day(it.selectedDate)?.activeItems?.size } ?: 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("已选$count个", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { pickerSelected = !pickerSelected }) { Text(if (pickerSelected) "看候选" else "查看已选") }
                            Button(onClick = { page = "Editor" }) { Text("完成选择") }
                        }
                    }
                }
            }
        }
    }

    confirmation?.let { value -> AlertDialog(onDismissRequest = { if (!vm.busy) confirmation = null }, title = { Text(value.title) }, text = { Text(value.message) },
        confirmButton = { TextButton(onClick = { confirmation = null; value.confirm() }, enabled = !vm.busy) { Text("确认") } },
        dismissButton = { TextButton(onClick = { confirmation = null }, enabled = !vm.busy) { Text("取消") } }) }
    vm.pendingEditor?.let { AlertDialog(onDismissRequest = vm::cancelEditorRequest, title = { Text("先处理未完成草稿") },
        text = { Text("${vm.draft?.plan?.name ?: vm.data.draft?.plan?.name}\n继续原草稿会保留填写；替换只影响未提交草稿，生效计划和记录保留。") },
        confirmButton = { TextButton(onClick = { vm.resumeEditor({ page = "Editor" }) }) { Text("继续原草稿") } },
        dismissButton = { Row { TextButton(onClick = vm::cancelEditorRequest) { Text("取消") }; TextButton(onClick = { vm.replaceEditor { page = "Editor" } }) { Text("替换草稿") } } }) }
    vm.pendingRange?.let { AlertDialog(onDismissRequest = vm::cancelRange, title = { Text("确认日期范围变更") },
        text = { Text("范围外无历史安排将移出草稿；有历史的原日期、动作及记录保留，并结束其剩余安排。") },
        confirmButton = { TextButton(onClick = vm::applyRange) { Text("确认修改") } }, dismissButton = { TextButton(onClick = vm::cancelRange) { Text("继续检查") } }) }
    batchRow?.let { row -> FitnessBackfillDialog(vm, row, onDismiss = { batchRow = null }) }
    vm.itemRecords?.let { rows -> FitnessRecordDialog(vm, "组明细（含撤销）", rows.reversed(), onDismiss = vm::closeItemRecords,
        onActual = { actualJson = FitnessJson.encodeToString(it) }, onDay = ::recordDay, onDetail = { vm.closeItemRecords(); detail(it, page) }) }
    scopedTitle?.let { titleValue -> FitnessRecordDialog(vm, titleValue, vm.scopedRecords,
        onDismiss = { scopedTitle = null; scopedScope = null; vm.recordScope(null) }, onActual = { actualJson = FitnessJson.encodeToString(it) }, onDay = ::recordDay,
        onDetail = { scopedTitle = null; vm.recordScope(null); detail(it, "Stats") },
        onMore = scopedScope?.takeIf { vm.scopedRecords.size >= it.limit && it.limit < 5000 }?.let { value -> { scopedScope = value.copy(limit = value.limit + 200); vm.recordScope(scopedScope) } }) }
    actualJson?.let { encoded -> val event = FitnessJson.decodeFromString<FitnessSetEvent>(encoded); FitnessActualDialog(vm, event) { actualJson = null } }
    if (customOpen) FitnessCustomDialog(vm, vm.exercises.firstOrNull { it.id == customId }, vm.catalog?.sourceRecords?.firstOrNull { it.id == customSourceId }, customAdd, customScope.ifBlank { if (customAdd) pickerScope else "" }) { customOpen = false }
    vm.exercises.firstOrNull { it.id == addId }?.let { exercise -> FitnessAddDialog(vm, exercise, onDismiss = { addId = null },
        onEditor = { request -> addId = null; editor(request) }, onOpen = { id, day -> addId = null; openPlan(id, day, "Library") }) }
    copySource?.let { source -> FitnessCopySavedDialog(vm, source, onDismiss = { copySource = null }, onEditor = { request -> copySource = null; editor(request) }) }
    FitnessRestoreDialog(vm) { page = "Today"; planId = null; date = vm.today }
}

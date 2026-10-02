package com.pockettoolbox.feature.diet.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.diet.domain.*
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun DietRoute(repository: DietRepository, onBack: () -> Unit, vm: DietViewModel = viewModel(factory = DietViewModelFactory(repository))) {
    var section by rememberSaveable { mutableStateOf("记录") }
    var brandId by rememberSaveable { mutableStateOf<String?>(null) }
    var foodId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailScoped by rememberSaveable { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var filterDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var metricName by rememberSaveable { mutableStateOf(CountMetric.Events.name) }
    var manageBrand by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogKind by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogId by rememberSaveable { mutableStateOf<String?>(null) }
    var editEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val snackbar = remember { SnackbarHostState() }
    val data = vm.data
    val filtered = remember(data, filterDays, typeFilter, today) { DietStatistics.events(data, DietFilter(filterDays, typeFilter), today) }
    fun goBack() {
        if (brandId != null) { brandId = null; foodId = null; section = if (detailScoped) "统计" else "品牌" }
        else if (section != "记录") section = "记录"
        else onBack()
    }
    BackHandler(enabled = !vm.draftVisible && catalogKind == null && editEventId == null) { if (!vm.busy) goBack() }
    LaunchedEffect(Unit) { while (true) { delay(60_000); today = LocalDate.now() } }
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            val result = snackbar.showSnackbar(notice.text, actionLabel = if (notice.undo != null) "撤销" else null,
                withDismissAction = true, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) notice.undo?.invoke()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding(), containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).widthIn(max = 920.dp).fillMaxWidth().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { if (!vm.busy) goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("随身工具箱", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("饮食不适记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { vm.openDraft() }, enabled = vm.loaded && !vm.busy) { Icon(Icons.Default.Add, contentDescription = "记录一次新的不适") }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)).padding(4.dp)) {
                listOf("记录", "品牌", "统计", "管理").forEach { label ->
                    Box(Modifier.weight(1f).background(if (section == label) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .selectable(section == label, role = Role.Tab, onClick = { section = label; brandId = null; foodId = null; detailScoped = false })
                        .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontWeight = if (section == label) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 7.dp))
            if (!vm.loaded) {
                Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    if (vm.loadError != null) { Text(vm.loadError.orEmpty()); Button(onClick = vm::load) { Text("重试") } }
                    else CircularProgressIndicator()
                }
            } else key(section, brandId, foodId) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (section) {
                        "记录" -> RecordPage(vm, today, onManage = { section = "管理" }, onBrand = { section = "品牌"; brandId = it; detailScoped = false }, onEdit = { editEventId = it.id })
                        "品牌" -> if (brandId == null) BrandsPage(data, onBrand = { brandId = it; detailScoped = false }, onManage = { section = "管理" })
                            else BrandDetailPage(vm, requireNotNull(brandId), foodId, if (detailScoped) filtered else data.events,
                                if (detailScoped) "${filterDays?.let { "近${it}天" } ?: "全部时间"} · ${typeFilter?.let(data::typeName) ?: "全部类型"}" else null,
                                onFood = { foodId = it }, onSelectMeal = { section = "记录"; brandId = null }, onEdit = { editEventId = it.id })
                        "统计" -> StatisticsPage(data, filtered, CountMetric.valueOf(metricName), filterDays, typeFilter,
                            onMetric = { metricName = it.name }, onDays = { filterDays = it }, onType = { typeFilter = it },
                            onBrand = { brandId = it; foodId = null; detailScoped = true; section = "品牌" })
                        "管理" -> {
                            if (vm.draft != null) DietCard { Text("刚才的记录草稿已保留"); TextButton(onClick = vm::resumeDraft, enabled = !vm.busy) { Text("继续记录") } }
                            ManagePage(vm, manageBrand, onBrand = { manageBrand = it }, onCatalog = { kind, id, parent -> catalogKind = kind.name; catalogId = id; if (parent != null) manageBrand = parent })
                            DietBackupActions(vm)
                        }
                    }
                }
            }
        }
    }

    if (vm.draftVisible) MealDraftDialog(vm, onManage = { manageBrand = vm.draft?.brandId; vm.hideDraftForManage(); section = "管理"; brandId = null })
    catalogKind?.let { kindName ->
        CatalogDialog(vm, CatalogKind.valueOf(kindName), catalogId, manageBrand,
            onDismiss = { catalogKind = null; catalogId = null }, onSaved = {
                if (kindName == CatalogKind.Brand.name && catalogId == null) manageBrand = vm.data.brands.lastOrNull()?.id
                catalogKind = null; catalogId = null
            })
    }
    data.events.firstOrNull { it.id == editEventId }?.let { event -> EventEditDialog(vm, event, onDismiss = { editEventId = null }) }
}

@Composable
private fun RecordPage(vm: DietViewModel, today: LocalDate, onManage: () -> Unit, onBrand: (String) -> Unit, onEdit: (DiscomfortEvent) -> Unit) {
    val data = vm.data
    val meal = data.activeMeal()
    val eventsToday = data.events.filter { it.localTime().toLocalDate() == today }
    DietSummary(listOf("今日不适" to eventsToday.size, "关联饮食" to data.recordedMeals().size, "涉及品牌" to DietStatistics.brands(data).size))
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = ToolboxColors.Navy)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (meal == null) "只记不舒服的那一餐" else "继续记录这顿饮食", color = ToolboxColors.TealBright, style = MaterialTheme.typography.labelMedium)
            if (meal == null) {
                Text("出现不适时，再记下来", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("选择品牌、食品和不适类型。正常饮食不用登记。", color = androidx.compose.ui.graphics.Color(0xFFBCD4DC))
                Button(onClick = { vm.openDraft() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolboxColors.TealBright, contentColor = ToolboxColors.Navy)) { Text("＋ 记录一次不适") }
            } else {
                Text("${data.brandName(meal.brandId)} · ${data.foodName(meal.foodId)}", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${meal.eatenOn}进食${if (meal.note.isBlank()) "" else "\n${meal.note}"}", color = androidx.compose.ui.graphics.Color(0xFFBCD4DC))
                Text("${eventsToday.count { it.mealId == meal.id }} 次 / 今日不适", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("这顿饮食累计 ${data.mealEvents(meal.id).size} 次不适", color = androidx.compose.ui.graphics.Color(0xFFBCD4DC), style = MaterialTheme.typography.bodySmall)
                // Use a light inner surface so dropdown text retains the shared theme's contrast.
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { DietChoice("这次记录", data.defaultTypeId, data.types.filterNot { it.archived }.map { it.id to it.name }, !vm.busy, vm::setDefault) }
                }
                Button(onClick = vm::append, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ToolboxColors.TealBright, contentColor = ToolboxColors.Navy)) {
                    Text("＋ 再记一次${data.typeName(data.defaultTypeId)}", fontWeight = FontWeight.Bold)
                }
                Text("点一下，自动记录当前日期和时分", color = androidx.compose.ui.graphics.Color(0xFFBCD4DC), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.openDraft(edit = meal) }, enabled = !vm.busy) { Text("修改饮食", color = ToolboxColors.TealBright) }
                    TextButton(onClick = { vm.finish(meal, true) }, enabled = !vm.busy) { Text("结束本次记录", color = ToolboxColors.TealBright) }
                }
            }
        }
    }
    OutlinedButton(onClick = { vm.openDraft() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("新的饮食后不适，另记一次") }
    if (data.brands.isEmpty()) DietHint("先维护常用选项", "到管理页添加品牌和品牌下的食品，之后只需选择，不用重复填写。")
    TextButton(onClick = onManage) { Text("管理品牌 / 食品 / 不适类型 →") }
    DietCard { Text("今天的时间线", fontWeight = FontWeight.Bold); EventList(data, eventsToday, vm.busy, onEdit, vm::deleteEvent) }
    meal?.let { TextButton(onClick = { onBrand(it.brandId) }) { Text("点餐前，查看${data.brandName(it.brandId)}的历史 →") } }
    Text("出现过不适的饮食", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    var limit by remember { mutableIntStateOf(20) }
    val recorded = data.recordedMeals()
    recorded.take(limit).forEach { m -> MealRow(data, m, vm.busy, onSelect = { vm.selectMeal(m.id) }, onResume = { vm.finish(m, false) }) }
    if (recorded.size > limit) TextButton(onClick = { limit += 20 }) { Text("查看更多饮食记录") }
    DietHint("给下一次点餐留个提醒", "只记录不适经历，不要求每天记饮食；结束本次记录后，下一顿需要新建，防止误追加。")
}

package com.pockettoolbox.feature.diet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.diet.domain.*

@Composable
internal fun BrandsPage(data: DietData, onBrand: (String) -> Unit, onManage: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(search, { search = it }, label = { Text("点餐前，搜索品牌") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val brands = remember(data, search) { DietStatistics.brands(data).filter { it.name.contains(search.trim(), ignoreCase = true) } }
    if (brands.isEmpty()) DietHint("没有相关不适记录", "常用品牌可以在管理页提前维护。只在出现不适时记录饮食，正常饮食不用记。")
    brands.forEach { brand ->
        Card(onClick = { onBrand(brand.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandSymbol(brand.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(brand.name, fontWeight = FontWeight.Bold)
                    Text("${brand.foods} 种食品 · ${brand.meals} 顿饮食", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${brand.events} 次", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
    OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text("维护品牌和食品") }
    DietHint("没有记录，不等于没有问题", "这里只展示你记录过的不适经历，不会把没有记录的食品标成“安全”。")
}

@Composable
internal fun BrandDetailPage(vm: DietViewModel, brandId: String, foodId: String?, events: List<DiscomfortEvent>, scopeLabel: String?,
    onFood: (String) -> Unit, onSelectMeal: () -> Unit, onEdit: (DiscomfortEvent) -> Unit) {
    val data = vm.data
    val mealsById = data.meals.associateBy { it.id }
    val brandEvents = events.filter { mealsById[it.mealId]?.brandId == brandId }
    val foodIds = brandEvents.map { mealsById.getValue(it.mealId).foodId }.toSet()
    val foods = data.foods.filter { it.id in foodIds }.sortedByDescending { f -> brandEvents.count { mealsById[it.mealId]?.foodId == f.id } }
    val chosen = foods.find { it.id == foodId } ?: foods.firstOrNull()
    Text(data.brandName(brandId), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    scopeLabel?.let { Text("当前筛选：$it", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium) }
    Text("${brandEvents.size} 次不适 · 关联 ${brandEvents.map { it.mealId }.distinct().size} 顿饮食", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (foods.isEmpty()) DietHint("当前筛选下没有记录", "返回统计页调整时间范围或不适类型。")
    if (chosen != null) {
        DietChoice("选择具体食品", chosen.id, foods.map { f -> f.id to "${f.name} · ${brandEvents.count { mealsById[it.mealId]?.foodId == f.id }} 次" }, onSelect = onFood)
        val foodEvents = brandEvents.filter { mealsById[it.mealId]?.foodId == chosen.id }
        val last = foodEvents.maxByOrNull { it.occurredAt }
        DietHint("给下次点餐的提醒", "${chosen.name}曾记录 ${foodEvents.size} 次不适，关联 ${foodEvents.map { it.mealId }.distinct().size} 顿饮食。" +
            (last?.let { "\n最近：${it.localTime().format(DateFormat)} ${it.localTime().format(TimeFormat)} · ${data.typeName(it.typeId)}" } ?: ""))
        OutlinedButton(onClick = { vm.openDraft(brandId, chosen.id) }, enabled = !vm.busy && !chosen.archived && data.brands.any { it.id == brandId && !it.archived }, modifier = Modifier.fillMaxWidth()) { Text("又吃后不适，另记一次") }
        DietCard { Text("${chosen.name}的不适明细", fontWeight = FontWeight.Bold); EventList(data, foodEvents, vm.busy, onEdit, vm::deleteEvent) }
        Text("相关饮食", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        var limit by remember(chosen.id) { mutableIntStateOf(20) }
        val ids = foodEvents.map { it.mealId }.toSet()
        val related = data.meals.filter { it.id in ids }.sortedByDescending { it.eatenOn }
        related.take(limit).forEach { m -> MealRow(data, m, vm.busy,
            onSelect = { vm.selectMeal(m.id); onSelectMeal() }, onResume = { vm.finish(m, false); onSelectMeal() }, scope = if (scopeLabel != null) events else null) }
        if (related.size > limit) TextButton(onClick = { limit += 20 }) { Text("继续查看相关饮食") }
    }
    DietHint("分别记住具体食品", "记录的是餐后关联，不自动判断原因。一次饮食可以有多次不适，同一品牌下的食品分别统计。")
}

@Composable
internal fun StatisticsPage(data: DietData, events: List<DiscomfortEvent>, metric: CountMetric, days: Int?, typeId: String?,
    onMetric: (CountMetric) -> Unit, onDays: (Int?) -> Unit, onType: (String?) -> Unit, onBrand: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = metric == CountMetric.Events, onClick = { onMetric(CountMetric.Events) }, label = { Text("不适次数") })
        FilterChip(selected = metric == CountMetric.Meals, onClick = { onMetric(CountMetric.Meals) }, label = { Text("关联饮食次数") })
    }
    DietChoice("不适类型", typeId ?: "all", listOf("all" to "全部类型") + data.types.map { it.id to it.name }, onSelect = { onType(it.takeUnless { it == "all" }) })
    DietChoice("发生时间范围", days?.toString() ?: "all", listOf("all" to "全部时间", "30" to "近30天", "90" to "近90天"), onSelect = { onDays(it.toIntOrNull()) })
    val brands = remember(data, events, metric) { DietStatistics.brands(data, events, metric) }
    DietSummary(listOf("不适记录" to events.size, "关联饮食" to events.map { it.mealId }.distinct().size, "涉及品牌" to brands.size))
    DietCard {
        Text("品牌不适对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (metric == CountMetric.Events) "每次不适单独计数" else "同一顿饮食出现多次不适，只计一顿", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val max = brands.maxOfOrNull { it.value(metric) } ?: 1
        if (brands.isEmpty()) Text("当前时间和类型下没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        brands.forEach { brand ->
            TextButton(onClick = { onBrand(brand.id) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(brand.name, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.weight(1f).height(25.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))) {
                        Box(Modifier.fillMaxWidth(brand.value(metric).toFloat() / max).fillMaxHeight().background(ToolboxColors.Teal, RoundedCornerShape(7.dp)))
                    }
                    Text(brand.value(metric).toString(), modifier = Modifier.widthIn(min = 24.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text("点击品牌查看对应食品和时间明细，保留当前筛选。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DietCard {
        Text("不适类型分布", fontWeight = FontWeight.Bold)
        data.types.forEach { type ->
            val count = events.count { it.typeId == type.id }
            if (count > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(type.name); Text("$count 次", fontWeight = FontWeight.Bold) }
        }
        if (events.isEmpty()) Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DietHint("次数帮助回顾经历", "只记录不适饮食，所以这里不计算发生率或食品安全排名。统计按不适发生日期筛选，进食日期保留在明细里。")
}

@Composable
internal fun ManagePage(vm: DietViewModel, brandId: String?, onBrand: (String) -> Unit, onCatalog: (CatalogKind, String?, String?) -> Unit) {
    val data = vm.data
    val brand = data.brands.find { it.id == brandId } ?: data.brands.firstOrNull()
    Text("常用选项，一次维护", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("品牌、食品和不适类型集中放在这里，记录时直接选择。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DietCard {
        Text("品牌与食品", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onCatalog(CatalogKind.Brand, null, null) }, enabled = !vm.busy) { Text("＋ 新增品牌") }
        if (data.brands.isEmpty()) Text("先添加你常点的品牌，再维护这个品牌下的食品。", style = MaterialTheme.typography.bodySmall)
        brand?.let {
            DietChoice("选择要维护的品牌", it.id, data.brands.map { b -> b.id to "${b.name}${if (b.archived) "（已停用）" else ""}" }, !vm.busy, onBrand)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCatalog(CatalogKind.Brand, it.id, null) }, enabled = !vm.busy) { Text("修改品牌名") }
                TextButton(onClick = { vm.toggle(CatalogKind.Brand, it.id) }, enabled = !vm.busy) { Text(if (it.archived) "重新启用品牌" else "停用品牌") }
            }
            HorizontalDivider()
            Text("${it.name}的食品", fontWeight = FontWeight.Bold)
            val foods = data.foods.filter { f -> f.brandId == it.id }
            foods.forEach { food -> CatalogRow(food.name, food.archived, vm.busy,
                onRename = { onCatalog(CatalogKind.Food, food.id, food.brandId) }, onToggle = { vm.toggle(CatalogKind.Food, food.id) }) }
            if (foods.isEmpty()) Text("还没有食品，添加一次，以后直接选。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onCatalog(CatalogKind.Food, null, it.id) }, enabled = !vm.busy) { Text("＋ 新增食品") }
        }
    }
    DietCard {
        Text("不适类型", fontWeight = FontWeight.Bold)
        data.types.forEach { type ->
            CatalogRow(type.name + if (type.id == data.defaultTypeId) " · 默认" else "", type.archived, vm.busy,
                onRename = { onCatalog(CatalogKind.Type, type.id, null) }, onToggle = { vm.toggle(CatalogKind.Type, type.id) })
            if (!type.archived && type.id != data.defaultTypeId) TextButton(onClick = { vm.setDefault(type.id) }, enabled = !vm.busy) { Text("将${type.name}设为默认") }
        }
        TextButton(onClick = { onCatalog(CatalogKind.Type, null, null) }, enabled = !vm.busy) { Text("＋ 新增不适类型") }
    }
    DietHint("改名和停用保留历史", "改名会同步更新历史显示，统计仍按原 ID 关联。停用只收起录入选项，已有明细和统计保留，也可以重新启用。")
}

@Composable
private fun CatalogRow(name: String, archived: Boolean, busy: Boolean, onRename: () -> Unit, onToggle: () -> Unit) {
    Column {
        Text(name, fontWeight = FontWeight.Medium)
        if (archived) Text("已停用 · 历史保留", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(onClick = onRename, enabled = !busy) { Text("改名") }
            TextButton(onClick = onToggle, enabled = !busy) { Text(if (archived) "启用" else "停用") }
        }
    }
}

package com.pockettoolbox.feature.diet.domain

import java.time.LocalDate
import java.util.Locale

/** Pure operations: the repository applies each resulting snapshot in one Room transaction. */
object DietRules {
    fun saveCatalog(data: DietData, kind: CatalogKind, id: String, rawName: String, brandId: String? = null): DietData {
        val name = rawName.trim()
        require(name.isNotBlank() && name.length <= 40) { "名称请填写 1 至 40 个字" }
        fun duplicate(otherId: String, otherName: String) = otherId != id && otherName.lowercase(Locale.ROOT) == name.lowercase(Locale.ROOT)
        return when (kind) {
            CatalogKind.Brand -> {
                require(data.brands.none { duplicate(it.id, it.name) }) { "品牌名称已存在，停用的品牌可以重新启用" }
                val existing = data.brands.find { it.id == id }
                data.copy(brands = data.brands.filterNot { it.id == id } + (existing?.copy(name = name) ?: DietBrand(id, name)))
            }
            CatalogKind.Food -> {
                require(data.brands.any { it.id == brandId }) { "请先选择品牌" }
                require(data.foods.none { it.brandId == brandId && duplicate(it.id, it.name) }) { "这个品牌下已有同名食品" }
                val existing = data.foods.find { it.id == id }
                require(existing == null || existing.brandId == brandId) { "不能移动已有食品的品牌" }
                data.copy(foods = data.foods.filterNot { it.id == id } + (existing?.copy(name = name) ?: DietFood(id, requireNotNull(brandId), name)))
            }
            CatalogKind.Type -> {
                require(data.types.none { duplicate(it.id, it.name) }) { "不适类型已存在，停用的类型可以重新启用" }
                val existing = data.types.find { it.id == id }
                data.copy(types = data.types.filterNot { it.id == id } + (existing?.copy(name = name) ?: DiscomfortType(id, name)))
            }
        }
    }

    fun toggleCatalog(data: DietData, kind: CatalogKind, id: String): DietData = when (kind) {
        CatalogKind.Brand -> {
            require(data.brands.any { it.id == id }) { "品牌不存在" }
            data.copy(brands = data.brands.map { if (it.id == id) it.copy(archived = !it.archived) else it })
        }
        CatalogKind.Food -> {
            require(data.foods.any { it.id == id }) { "食品不存在" }
            data.copy(foods = data.foods.map { if (it.id == id) it.copy(archived = !it.archived) else it })
        }
        CatalogKind.Type -> {
            val target = requireNotNull(data.types.find { it.id == id }) { "不适类型不存在" }
            require(target.archived || data.types.count { !it.archived } > 1) { "请至少保留一个可用的不适类型" }
            val types = data.types.map { if (it.id == id) it.copy(archived = !it.archived) else it }
            data.copy(types = types, defaultTypeId = if (id == data.defaultTypeId && !target.archived) types.first { !it.archived }.id else data.defaultTypeId)
        }
    }

    fun setDefault(data: DietData, id: String): DietData {
        require(data.types.any { it.id == id && !it.archived }) { "请选择可用的不适类型" }
        return data.copy(defaultTypeId = id)
    }

    fun recordIncident(data: DietData, meal: DietMeal, event: DiscomfortEvent): DietData {
        require(data.meals.none { it.id == meal.id } && data.events.none { it.id == event.id }) { "记录已存在" }
        require(data.brands.any { it.id == meal.brandId && !it.archived }) { "请选择已启用的品牌" }
        require(data.foods.any { it.id == meal.foodId && it.brandId == meal.brandId && !it.archived }) { "请选择这个品牌下已启用的食品" }
        require(event.mealId == meal.id && !meal.finished) { "饮食关联无效" }
        validateMeal(meal)
        validateEvent(data, meal, event, allowArchived = false)
        return data.copy(meals = data.meals + meal, events = data.events + event, activeMealId = meal.id)
    }

    fun append(data: DietData, event: DiscomfortEvent): DietData {
        val meal = requireNotNull(data.meals.find { it.id == event.mealId }) { "请先选择一顿饮食" }
        require(!meal.finished) { "本次记录已结束，请先恢复或创建新记录" }
        require(data.events.none { it.id == event.id }) { "这条记录已保存" }
        validateEvent(data, meal, event, allowArchived = false)
        return data.copy(events = data.events + event)
    }

    fun editMeal(data: DietData, meal: DietMeal): DietData {
        val old = requireNotNull(data.meals.find { it.id == meal.id }) { "饮食记录不存在" }
        validateMeal(meal)
        require(data.brands.any { it.id == meal.brandId }) { "品牌不存在" }
        require(data.foods.any { it.id == meal.foodId && it.brandId == meal.brandId }) { "请选择对应品牌下的食品" }
        data.mealEvents(meal.id).forEach { validateEvent(data, meal, it, allowArchived = true) }
        return data.copy(meals = data.meals.map { if (it.id == meal.id) meal.copy(finished = old.finished) else it })
    }

    fun editEvent(data: DietData, event: DiscomfortEvent): DietData {
        val old = requireNotNull(data.events.find { it.id == event.id }) { "不适记录不存在" }
        require(old.mealId == event.mealId) { "不能移动事件的饮食关联" }
        require(data.types.any { it.id == event.typeId && (!it.archived || it.id == old.typeId) }) { "请选择已启用的不适类型" }
        validateEvent(data, data.meals.first { it.id == event.mealId }, event, allowArchived = true)
        return data.copy(events = data.events.map { if (it.id == event.id) event else it })
    }

    fun removeEvent(data: DietData, id: String): DietData {
        require(data.events.any { it.id == id }) { "不适记录不存在" }
        val events = data.events.filterNot { it.id == id }
        val remainingMeals = events.map { it.mealId }.toSet()
        val meals = data.meals.filter { it.id in remainingMeals }
        return data.copy(events = events, meals = meals, activeMealId = data.activeMealId?.takeIf { active -> meals.any { it.id == active } })
    }

    /** Undo restores only the deleted row, never rolls back unrelated later writes. */
    fun restoreEvent(data: DietData, meal: DietMeal, event: DiscomfortEvent): DietData {
        require(data.events.none { it.id == event.id }) { "这条记录已恢复" }
        val meals = if (data.meals.any { it.id == meal.id }) data.meals else data.meals + meal
        val currentMeal = meals.first { it.id == meal.id }
        validateEvent(data, currentMeal, event, allowArchived = true)
        return data.copy(meals = meals, events = data.events + event)
    }

    fun finish(data: DietData, id: String, finished: Boolean): DietData {
        require(data.meals.any { it.id == id }) { "饮食记录不存在" }
        return data.copy(meals = data.meals.map { if (it.id == id) it.copy(finished = finished) else it },
            activeMealId = if (finished && data.activeMealId == id) null else if (!finished) id else data.activeMealId)
    }

    fun selectMeal(data: DietData, id: String): DietData {
        require(data.meals.any { it.id == id && !it.finished }) { "请先恢复已结束的记录" }
        return data.copy(activeMealId = id)
    }

    fun validate(data: DietData) {
        fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() && it.length <= 100 } && ids.distinct().size == ids.size) { "备份含重复或无效 ID" } }
        unique(data.brands.map { it.id }); unique(data.foods.map { it.id }); unique(data.types.map { it.id })
        unique(data.meals.map { it.id }); unique(data.events.map { it.id })
        require(data.brands.size <= 2000 && data.foods.size <= 10000 && data.meals.size <= 20000 && data.events.size <= 50000 && data.types.size <= 200) { "备份记录数量超过上限" }
        fun validNames(names: List<String>) { require(names.all { it.isNotBlank() && it == it.trim() && it.length <= 40 } && names.map { it.lowercase(Locale.ROOT) }.distinct().size == names.size) { "备份含无效或重复名称" } }
        validNames(data.brands.map { it.name }); validNames(data.types.map { it.name })
        val brands = data.brands.associateBy { it.id }
        val foods = data.foods.associateBy { it.id }
        val types = data.types.associateBy { it.id }
        val meals = data.meals.associateBy { it.id }
        val eventMeals = data.events.map { it.mealId }.toSet()
        data.foods.groupBy { it.brandId }.forEach { (brandId, foods) ->
            require(brandId in brands) { "食品对应的品牌不存在" }
            validNames(foods.map { it.name })
        }
        require(types[data.defaultTypeId]?.archived == false) { "默认不适类型无效" }
        data.meals.forEach { m ->
            validateMeal(m)
            require(m.brandId in brands && foods[m.foodId]?.brandId == m.brandId) { "饮食的品牌／食品关联无效" }
            require(m.id in eventMeals) { "备份包含没有不适记录的饮食" }
        }
        data.events.forEach { e ->
            require(e.typeId in types) { "不适类型不存在" }
            validateEventTime(requireNotNull(meals[e.mealId]) { "不适对应的饮食不存在" }, e)
        }
        require(data.activeMealId == null || meals[data.activeMealId]?.finished == false) { "当前饮食关联无效" }
    }

    private fun validateMeal(meal: DietMeal) {
        require(runCatching { LocalDate.parse(meal.eatenOn).toString() == meal.eatenOn }.getOrDefault(false)) { "进食日期无效" }
        require(meal.note.length <= 500) { "备注最多 500 个字" }
    }

    private fun validateEvent(data: DietData, meal: DietMeal, event: DiscomfortEvent, allowArchived: Boolean) {
        require(data.types.any { it.id == event.typeId && (allowArchived || !it.archived) }) { "不适类型无效或已停用" }
        validateEventTime(meal, event)
    }

    private fun validateEventTime(meal: DietMeal, event: DiscomfortEvent) {
        require(event.occurredAt > 0) { "发生时间无效" }
        val date = requireNotNull(runCatching { event.localTime().toLocalDate() }.getOrNull()) { "发生时间或时区无效" }
        require(date >= LocalDate.parse(meal.eatenOn)) { "不适日期不能早于进食日期" }
    }
}

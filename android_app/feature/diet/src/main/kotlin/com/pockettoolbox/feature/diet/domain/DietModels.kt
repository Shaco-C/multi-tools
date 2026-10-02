package com.pockettoolbox.feature.diet.domain

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

@Serializable
data class DietBrand(val id: String, val name: String, val archived: Boolean = false)

@Serializable
data class DietFood(val id: String, val brandId: String, val name: String, val archived: Boolean = false)

@Serializable
data class DiscomfortType(val id: String, val name: String, val archived: Boolean = false)

@Serializable
data class DietMeal(
    val id: String,
    val brandId: String,
    val foodId: String,
    val eatenOn: String,
    val note: String = "",
    val finished: Boolean = false,
)

@Serializable
data class DiscomfortEvent(
    val id: String,
    val mealId: String,
    val typeId: String,
    val occurredAt: Long,
    val offsetSeconds: Int,
) {
    fun localTime(): OffsetDateTime = Instant.ofEpochMilli(occurredAt).atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds))
}

@Serializable
data class DietData(
    val brands: List<DietBrand> = emptyList(),
    val foods: List<DietFood> = emptyList(),
    val types: List<DiscomfortType> = listOf(
        DiscomfortType("diarrhea", "腹泻"),
        DiscomfortType("stomach-pain", "胃痛"),
        DiscomfortType("nausea", "恶心"),
    ),
    val meals: List<DietMeal> = emptyList(),
    val events: List<DiscomfortEvent> = emptyList(),
    val activeMealId: String? = null,
    val defaultTypeId: String = "diarrhea",
) {
    fun brandName(id: String) = brands.firstOrNull { it.id == id }?.name.orEmpty()
    fun foodName(id: String) = foods.firstOrNull { it.id == id }?.name.orEmpty()
    fun typeName(id: String) = types.firstOrNull { it.id == id }?.name.orEmpty()
    fun mealEvents(id: String) = events.filter { it.mealId == id }.sortedByDescending { it.occurredAt }
    fun recordedMeals(): List<DietMeal> {
        val recordedIds = events.map { it.mealId }.toSet()
        return meals.filter { it.id in recordedIds }.sortedByDescending { it.eatenOn }
    }
    fun activeMeal() = meals.firstOrNull { it.id == activeMealId && !it.finished }
}

enum class CatalogKind { Brand, Food, Type }
enum class CountMetric { Events, Meals }

data class BrandCount(val id: String, val name: String, val events: Int, val meals: Int, val foods: Int) {
    fun value(metric: CountMetric) = if (metric == CountMetric.Events) events else meals
}

data class DietFilter(val days: Int? = null, val typeId: String? = null) {
    fun matches(event: DiscomfortEvent, today: LocalDate): Boolean {
        val date = event.localTime().toLocalDate()
        return (typeId == null || event.typeId == typeId) &&
            (days == null || (date >= today.minusDays(days.toLong() - 1) && date <= today))
    }
}

object DietStatistics {
    fun events(data: DietData, filter: DietFilter = DietFilter(), today: LocalDate = LocalDate.now()) =
        data.events.filter { filter.matches(it, today) }

    fun brands(data: DietData, events: List<DiscomfortEvent> = data.events, metric: CountMetric = CountMetric.Events): List<BrandCount> {
        val meals = data.meals.associateBy { it.id }
        return events.groupBy { meals.getValue(it.mealId).brandId }.map { (brandId, rows) ->
            BrandCount(brandId, data.brandName(brandId), rows.size, rows.map { it.mealId }.distinct().size,
                rows.map { meals.getValue(it.mealId).foodId }.distinct().size)
        }.sortedWith(compareByDescending<BrandCount> { it.value(metric) }.thenBy { it.name })
    }
}

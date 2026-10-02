package com.pockettoolbox.feature.diet.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.*
import org.junit.Test

internal fun catalogs() = DietData(
    brands = listOf(DietBrand("mcd", "麦当劳"), DietBrand("kfc", "肯德基")),
    foods = listOf(DietFood("wings", "mcd", "辣翅"), DietFood("fries", "mcd", "薯条"), DietFood("burger", "kfc", "汉堡")),
)

internal fun incident(id: String, meal: String, at: String = "2026-10-02T08:00:00+08:00", type: String = "diarrhea"): DiscomfortEvent {
    val time = OffsetDateTime.parse(at)
    return DiscomfortEvent(id, meal, type, time.toInstant().toEpochMilli(), time.offset.totalSeconds)
}

internal fun recorded(): DietData = DietRules.recordIncident(catalogs(), DietMeal("meal", "mcd", "wings", "2026-10-01"), incident("first", "meal"))

class DietRulesTest {
    @Test fun threeSymptomsAreOneMealAndThreeEvents() {
        val data = DietRules.append(DietRules.append(recorded(), incident("second", "meal")), incident("third", "meal"))
        val count = DietStatistics.brands(data).single()
        assertEquals(3, count.events)
        assertEquals(1, count.meals)
        assertEquals(1, count.foods)
        assertEquals("meal", data.activeMeal()?.id)
        assertFalse(DietStatistics.brands(data).any { it.id == "kfc" })
        DietRules.validate(data)
    }

    @Test fun separateMealsStaySeparateEvenWithSameFood() {
        val data = DietRules.recordIncident(recorded(), DietMeal("meal2", "mcd", "wings", "2026-10-02"), incident("second", "meal2"))
        assertEquals(2, DietStatistics.brands(data).single().meals)
        assertEquals("meal2", data.activeMealId)
    }

    @Test fun filtersUseRecordedLocalDateAndCountOnlyMatchingEvents() {
        var data = recorded()
        data = DietRules.append(data, incident("pain", "meal", "2026-10-02T09:00:00+08:00", "stomach-pain"))
        data = DietRules.append(data, incident("older", "meal", "2026-10-01T23:30:00-05:00"))
        val today = LocalDate.parse("2026-10-02")
        val events = DietStatistics.events(data, DietFilter(1, "diarrhea"), today)
        assertEquals(listOf("first"), events.map { it.id })
        assertEquals(1, DietStatistics.brands(data, events).single().events)
    }

    @Test fun renameAndArchiveKeepHistoryButBlockNewMeals() {
        val renamed = DietRules.saveCatalog(recorded(), CatalogKind.Brand, "mcd", "麦当劳（常点）")
        val archived = DietRules.toggleCatalog(renamed, CatalogKind.Brand, "mcd")
        assertEquals("麦当劳（常点）", DietStatistics.brands(archived).single().name)
        assertThrows(IllegalArgumentException::class.java) {
            DietRules.recordIncident(archived, DietMeal("new", "mcd", "wings", "2026-10-01"), incident("new", "new"))
        }
        // A previous meal may still receive follow-up symptoms after the brand is archived.
        assertEquals(2, DietRules.append(archived, incident("second", "meal")).events.size)
        DietRules.validate(archived)
    }

    @Test fun endingRequiresExplicitResumeBeforeAppend() {
        val finished = DietRules.finish(recorded(), "meal", true)
        assertNull(finished.activeMeal())
        assertThrows(IllegalArgumentException::class.java) { DietRules.append(finished, incident("second", "meal")) }
        val resumed = DietRules.finish(finished, "meal", false)
        assertEquals(2, DietRules.append(resumed, incident("second", "meal")).events.size)
    }

    @Test fun deletingLastSymptomRemovesMealAndUndoKeepsLaterChanges() {
        val original = recorded()
        val removed = DietRules.removeEvent(original, "first")
        assertTrue(removed.meals.isEmpty())
        assertNull(removed.activeMealId)
        val later = DietRules.recordIncident(removed, DietMeal("later", "kfc", "burger", "2026-10-02"), incident("later", "later"))
        val restored = DietRules.restoreEvent(later, original.meals.single(), original.events.single())
        assertEquals(2, restored.events.size)
        assertEquals("later", restored.activeMealId)
        DietRules.validate(restored)
    }

    @Test fun undoFirstSaveLeavesAppendedSymptomsIntact() {
        val withSecond = DietRules.append(recorded(), incident("second", "meal"))
        val undo = DietRules.removeEvent(withSecond, "first")
        assertEquals(listOf("second"), undo.events.map { it.id })
        assertEquals(1, undo.meals.size)
    }

    @Test fun defaultTypeMovesWhenArchivedAndLastTypeCannotBeArchived() {
        var data = DietRules.toggleCatalog(catalogs(), CatalogKind.Type, "diarrhea")
        assertEquals("stomach-pain", data.defaultTypeId)
        data = DietRules.toggleCatalog(data, CatalogKind.Type, "nausea")
        val last = data
        assertThrows(IllegalArgumentException::class.java) { DietRules.toggleCatalog(last, CatalogKind.Type, "stomach-pain") }
    }

    @Test fun duplicateNamesAndWrongFoodAssociationAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { DietRules.saveCatalog(catalogs(), CatalogKind.Brand, "other", " 麦当劳 ") }
        assertThrows(IllegalArgumentException::class.java) { DietRules.saveCatalog(catalogs(), CatalogKind.Food, "other", "辣翅", "mcd") }
        assertEquals(4, DietRules.saveCatalog(catalogs(), CatalogKind.Food, "other", "辣翅", "kfc").foods.size)
        assertThrows(IllegalArgumentException::class.java) {
            DietRules.recordIncident(catalogs(), DietMeal("meal", "kfc", "wings", "2026-10-01"), incident("first", "meal"))
        }
    }

    @Test fun editingCannotMoveEatingDateAfterSymptoms() {
        val data = recorded()
        assertThrows(IllegalArgumentException::class.java) { DietRules.editMeal(data, data.meals.single().copy(eatenOn = "2026-10-03")) }
        assertThrows(IllegalArgumentException::class.java) { DietRules.editEvent(data, incident("first", "meal", "2026-09-30T08:00:00+08:00")) }
    }
}

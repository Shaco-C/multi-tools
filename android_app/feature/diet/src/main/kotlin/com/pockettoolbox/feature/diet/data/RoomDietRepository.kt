package com.pockettoolbox.feature.diet.data

import androidx.room.withTransaction
import com.pockettoolbox.feature.diet.domain.DietData
import com.pockettoolbox.feature.diet.domain.DietRepository
import com.pockettoolbox.feature.diet.domain.DietRules
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class RoomDietRepository(private val database: DietDatabase) : DietRepository {
    private val dao = database.dao()
    override val data = dao.observe().filterNotNull().map { it.toData() }

    override suspend fun initialize() {
        database.withTransaction {
            if (dao.snapshot() == null) writeAll(DietData())
        }
    }

    override suspend fun snapshot(): DietData {
        initialize()
        return requireNotNull(dao.snapshot()).toData()
    }

    override suspend fun change(transform: (DietData) -> DietData): DietData = database.withTransaction {
        val old = dao.snapshot()?.toData() ?: DietData().also { writeAll(it) }
        val next = transform(old)
        // Only changed rows are written. Typing in a form never triggers database writes.
        val oldBrands = old.brands.associateBy { it.id }
        val oldFoods = old.foods.associateBy { it.id }
        val oldTypes = old.types.associateBy { it.id }
        val oldMeals = old.meals.associateBy { it.id }
        val oldEvents = old.events.associateBy { it.id }
        dao.brands(next.brands.filter { it != oldBrands[it.id] }.map { DietBrandRow(it) })
        dao.foods(next.foods.filter { it != oldFoods[it.id] }.map { DietFoodRow(it) })
        dao.types(next.types.filter { it != oldTypes[it.id] }.map { DietTypeRow(it) })
        dao.meals(next.meals.filter { it != oldMeals[it.id] }.map { DietMealRow(it) })
        dao.events(next.events.filter { it != oldEvents[it.id] }.map { DietEventRow(it) })
        val eventIds = next.events.map { it.id }.toSet()
        val mealIds = next.meals.map { it.id }.toSet()
        dao.deleteEvents(old.events.map { it.id }.filterNot { it in eventIds })
        dao.deleteMeals(old.meals.map { it.id }.filterNot { it in mealIds })
        dao.settings(DietSettingsRow(activeMealId = next.activeMealId, defaultTypeId = next.defaultTypeId))
        next
    }

    override suspend fun replace(data: DietData) {
        DietRules.validate(data)
        database.withTransaction {
            dao.clearEvents(); dao.clearMeals(); dao.clearFoods(); dao.clearBrands(); dao.clearTypes()
            writeAll(data)
        }
    }

    private suspend fun writeAll(data: DietData) {
        dao.brands(data.brands.map { DietBrandRow(it) })
        dao.foods(data.foods.map { DietFoodRow(it) })
        dao.types(data.types.map { DietTypeRow(it) })
        dao.meals(data.meals.map { DietMealRow(it) })
        dao.events(data.events.map { DietEventRow(it) })
        dao.settings(DietSettingsRow(activeMealId = data.activeMealId, defaultTypeId = data.defaultTypeId))
    }
}

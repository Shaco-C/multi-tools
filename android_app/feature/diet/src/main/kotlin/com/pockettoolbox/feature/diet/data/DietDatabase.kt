package com.pockettoolbox.feature.diet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.pockettoolbox.feature.diet.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diet_settings", primaryKeys = ["id"])
data class DietSettingsRow(val id: Int = 1, val activeMealId: String? = null, val defaultTypeId: String = "diarrhea")

@Entity(tableName = "diet_brands", primaryKeys = ["id"], indices = [Index("ownerId")])
data class DietBrandRow(@Embedded val value: DietBrand, val ownerId: Int = 1)

@Entity(tableName = "diet_foods", primaryKeys = ["id"], indices = [Index("ownerId"), Index("brandId")],
    foreignKeys = [ForeignKey(entity = DietBrandRow::class, parentColumns = ["id"], childColumns = ["brandId"])])
data class DietFoodRow(@Embedded val value: DietFood, val ownerId: Int = 1)

@Entity(tableName = "diet_types", primaryKeys = ["id"], indices = [Index("ownerId")])
data class DietTypeRow(@Embedded val value: DiscomfortType, val ownerId: Int = 1)

@Entity(tableName = "diet_meals", primaryKeys = ["id"], indices = [Index("ownerId"), Index("brandId"), Index("foodId")],
    foreignKeys = [
        ForeignKey(entity = DietBrandRow::class, parentColumns = ["id"], childColumns = ["brandId"]),
        ForeignKey(entity = DietFoodRow::class, parentColumns = ["id"], childColumns = ["foodId"]),
    ])
data class DietMealRow(@Embedded val value: DietMeal, val ownerId: Int = 1)

@Entity(tableName = "diet_events", primaryKeys = ["id"], indices = [Index("ownerId"), Index("mealId"), Index("typeId")],
    foreignKeys = [
        ForeignKey(entity = DietMealRow::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DietTypeRow::class, parentColumns = ["id"], childColumns = ["typeId"]),
    ])
data class DietEventRow(@Embedded val value: DiscomfortEvent, val ownerId: Int = 1)

data class DietSnapshot(
    @Embedded val settings: DietSettingsRow,
    @Relation(parentColumn = "id", entityColumn = "ownerId") val brands: List<DietBrandRow>,
    @Relation(parentColumn = "id", entityColumn = "ownerId") val foods: List<DietFoodRow>,
    @Relation(parentColumn = "id", entityColumn = "ownerId") val types: List<DietTypeRow>,
    @Relation(parentColumn = "id", entityColumn = "ownerId") val meals: List<DietMealRow>,
    @Relation(parentColumn = "id", entityColumn = "ownerId") val events: List<DietEventRow>,
) {
    fun toData() = DietData(brands.map { it.value }, foods.map { it.value }, types.map { it.value },
        meals.map { it.value }, events.map { it.value }, settings.activeMealId, settings.defaultTypeId)
}

@Dao
interface DietDao {
    @Transaction @Query("SELECT * FROM diet_settings WHERE id = 1")
    fun observe(): Flow<DietSnapshot?>
    @Transaction @Query("SELECT * FROM diet_settings WHERE id = 1")
    suspend fun snapshot(): DietSnapshot?
    @Upsert suspend fun settings(value: DietSettingsRow)
    @Upsert suspend fun brands(values: List<DietBrandRow>)
    @Upsert suspend fun foods(values: List<DietFoodRow>)
    @Upsert suspend fun types(values: List<DietTypeRow>)
    @Upsert suspend fun meals(values: List<DietMealRow>)
    @Upsert suspend fun events(values: List<DietEventRow>)
    @Query("DELETE FROM diet_events WHERE id IN (:ids)") suspend fun deleteEvents(ids: List<String>)
    @Query("DELETE FROM diet_meals WHERE id IN (:ids)") suspend fun deleteMeals(ids: List<String>)
    @Query("DELETE FROM diet_events") suspend fun clearEvents()
    @Query("DELETE FROM diet_meals") suspend fun clearMeals()
    @Query("DELETE FROM diet_foods") suspend fun clearFoods()
    @Query("DELETE FROM diet_brands") suspend fun clearBrands()
    @Query("DELETE FROM diet_types") suspend fun clearTypes()
}

@Database(entities = [DietSettingsRow::class, DietBrandRow::class, DietFoodRow::class, DietTypeRow::class, DietMealRow::class, DietEventRow::class], version = 1, exportSchema = true)
abstract class DietDatabase : RoomDatabase() {
    abstract fun dao(): DietDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, DietDatabase::class.java, "diet.db").build()
    }
}

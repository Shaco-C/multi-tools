package com.pockettoolbox.feature.fitness.data

import android.content.Context
import com.pockettoolbox.feature.fitness.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class FitnessSourceExercise(
    val id: String, val name: String, val primaryMuscles: List<String>, val secondaryMuscles: List<String>,
    val equipment: String?, val category: String?, val level: String?, val instructions: List<String>,
    val images: List<FitnessImage>, val linkedExerciseId: String? = null,
)

@Serializable
data class FitnessCatalogDocument(
    val version: String, val sourceCount: Int, val reviewedCount: Int, val sourceRevision: String,
    val exercises: List<FitnessExercise>, val sourceRecords: List<FitnessSourceExercise>,
)

class FitnessCatalog(private val context: Context) {
    private val mutex = Mutex()
    private var cached: FitnessCatalogDocument? = null

    suspend fun load(): FitnessCatalogDocument = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            context.assets.open("fitness/catalog.json").bufferedReader().use { FitnessJson.decodeFromString<FitnessCatalogDocument>(it.readText()) }.also { document ->
                require(document.sourceCount == document.sourceRecords.size && document.sourceRecords.map { it.id }.distinct().size == document.sourceCount) { "全量来源目录损坏" }
                require(document.exercises.map { it.id }.distinct().size == document.exercises.size) { "内置动作身份重复" }
                require(document.reviewedCount == document.exercises.count { it.reviewed }) { "内容审核数量与条目不一致" }
                document.exercises.forEach(FitnessRules::validateExercise)
                cached = document
            }
        }
    }
}

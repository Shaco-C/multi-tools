package com.pockettoolbox.feature.fitness.domain

import java.time.Clock
import kotlinx.coroutines.flow.Flow

interface FitnessRepository {
    val data: Flow<FitnessData>
    suspend fun initialize()
    suspend fun currentData(): FitnessData
    suspend fun planEvents(planId: String): List<FitnessSetEvent>
    fun observeEvents(filter: FitnessFilter, limit: Int = 200, exerciseId: String? = null, part: FitnessPart? = null,
        unit: FitnessUnit? = null, side: FitnessSide? = null): Flow<List<FitnessSetEvent>>
    suspend fun itemEvents(itemId: String): List<FitnessSetEvent>
    suspend fun recentExercises(today: String): List<String>
    fun observeProgress(date: String, planId: String? = null): Flow<Map<String, Int>>
    fun observeSummary(filter: FitnessFilter): Flow<FitnessSummary>
    fun observeCounts(filter: FitnessFilter, byExercise: Boolean): Flow<List<FitnessCount>>
    fun observeTrainingDates(today: String): Flow<Set<String>>
    suspend fun recovery(parts: List<FitnessPart>, today: String): List<FitnessRecovery>
    suspend fun saveDraft(draft: FitnessDraft?)
    suspend fun savePlan(plan: FitnessPlan, clearDraft: Boolean = false)
    suspend fun archive(planId: String, archived: Boolean)
    suspend fun deletePlan(planId: String)
    suspend fun complete(command: FitnessCompletion, clock: Clock): List<FitnessSetEvent>
    suspend fun revoke(eventId: String, at: Long)
    suspend fun revokeBatch(batchId: String, at: Long)
    suspend fun updateActual(eventId: String, actual: FitnessActual)
    suspend fun stopItem(planId: String, date: String, itemId: String)
    suspend fun saveExercise(exercise: FitnessExercise)
    suspend fun preferences(value: FitnessPreferences)
    suspend fun favorite(exerciseId: String)
    suspend fun saveTemplate(template: FitnessTemplate)
    suspend fun snapshot(): FitnessSnapshot
    suspend fun replace(snapshot: FitnessSnapshot)
}

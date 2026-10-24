package com.pockettoolbox.feature.fitness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessDao {
    @Transaction @Query("SELECT * FROM fitness_plans ORDER BY start DESC, id")
    fun observePlans(): Flow<List<FitnessPlanBundle>>
    @Transaction @Query("SELECT * FROM fitness_plans ORDER BY start DESC, id")
    suspend fun plans(): List<FitnessPlanBundle>
    @Transaction @Query("SELECT * FROM fitness_plans WHERE id = :id")
    suspend fun plan(id: String): FitnessPlanBundle?
    @Query("SELECT * FROM fitness_plans WHERE id = :id") suspend fun planRow(id: String): FitnessPlanRow?
    @Query("SELECT * FROM fitness_days WHERE planId = :planId AND date = :date") suspend fun dayRow(planId: String, date: String): FitnessDayRow?
    @Query("SELECT * FROM fitness_items WHERE id = :id") suspend fun item(id: String): FitnessItemRow?
    @Query("SELECT * FROM fitness_settings WHERE id = 1") fun observeSettings(): Flow<FitnessSettingsRow?>
    @Query("SELECT * FROM fitness_settings WHERE id = 1") suspend fun settings(): FitnessSettingsRow?
    @Query("SELECT (SELECT COUNT(*) FROM fitness_plans) + (SELECT COUNT(*) FROM fitness_personal_exercises) + (SELECT COUNT(*) FROM fitness_templates) + (SELECT COUNT(*) FROM fitness_events)")
    suspend fun businessRowCount(): Long
    @Query("SELECT * FROM fitness_personal_exercises ORDER BY id") fun observeExercises(): Flow<List<FitnessExerciseRow>>
    @Query("SELECT * FROM fitness_personal_exercises ORDER BY id") suspend fun exercises(): List<FitnessExerciseRow>
    @Query("SELECT * FROM fitness_templates ORDER BY id") fun observeTemplates(): Flow<List<FitnessTemplateRow>>
    @Query("SELECT * FROM fitness_templates ORDER BY id") suspend fun templates(): List<FitnessTemplateRow>

    @Query("SELECT * FROM fitness_events WHERE planId = :planId ORDER BY sequence") suspend fun planEvents(planId: String): List<FitnessEventRow>
    @Query("SELECT * FROM fitness_events WHERE itemId = :itemId ORDER BY sequence") suspend fun itemEvents(itemId: String): List<FitnessEventRow>
    @Query("SELECT * FROM fitness_events WHERE itemId IN (:ids) ORDER BY sequence") suspend fun eventsForItems(ids: List<String>): List<FitnessEventRow>
    @Query("SELECT * FROM fitness_events WHERE id = :id") suspend fun event(id: String): FitnessEventRow?
    @Query("SELECT * FROM fitness_events WHERE requestId = :requestId OR batchId = :requestId ORDER BY sequence") suspend fun requested(requestId: String): List<FitnessEventRow>
    @Query("SELECT * FROM fitness_events WHERE batchId = :batchId ORDER BY sequence") suspend fun batch(batchId: String): List<FitnessEventRow>
    @Query("SELECT * FROM fitness_events ORDER BY sequence") suspend fun allEvents(): List<FitnessEventRow>
    @Query("SELECT * FROM fitness_events WHERE sequence > :after ORDER BY sequence LIMIT :limit") suspend fun eventPage(after: Long, limit: Int): List<FitnessEventRow>
    @Query("SELECT COUNT(*) FROM fitness_events") suspend fun eventCount(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM fitness_events WHERE planId = :planId)") suspend fun hasHistory(planId: String): Boolean
    @Query("SELECT COALESCE(MAX(sequence), 0) FROM fitness_events") suspend fun lastSequence(): Long
    @Query("SELECT COUNT(*) FROM fitness_events WHERE itemId = :itemId AND status = 'Valid'") suspend fun completed(itemId: String): Int
    @Query("SELECT id, planId FROM fitness_items WHERE id IN (:ids)") suspend fun itemOwners(ids: List<String>): List<FitnessItemOwner>

    @Query("""SELECT * FROM fitness_events WHERE status = 'Valid' AND trainingDate BETWEEN :start AND :end
        AND (:planId IS NULL OR planId = :planId) AND (:purpose IS NULL OR purpose = :purpose)
        AND (:exerciseId IS NULL OR exerciseId = :exerciseId) AND (:part IS NULL OR primaryPart = :part)
        AND (:unit IS NULL OR unit = :unit) AND (:side IS NULL OR side = :side)
        ORDER BY sequence DESC LIMIT :limit""")
    fun observeEvents(start: String, end: String, planId: String?, purpose: String?, limit: Int, exerciseId: String?, part: String?, unit: String?, side: String?): Flow<List<FitnessEventRow>>

    @Query("""SELECT itemId, COUNT(*) AS completed FROM fitness_events WHERE status = 'Valid' AND trainingDate = :date
        AND (:planId IS NULL OR planId = :planId) GROUP BY itemId""")
    fun observeProgress(date: String, planId: String?): Flow<List<FitnessProgressRow>>

    @Query("""SELECT COUNT(DISTINCT CASE WHEN purpose IN ('Strength','Cardio') THEN trainingDate END) AS trainingDays,
        COALESCE(SUM(CASE WHEN purpose = 'Strength' THEN 1 ELSE 0 END), 0) AS strengthSets,
        COALESCE(SUM(CASE WHEN purpose = 'Cardio' THEN 1 ELSE 0 END), 0) AS cardioSegments,
        COUNT(DISTINCT exerciseId) AS exercises FROM fitness_events WHERE status = 'Valid'
        AND trainingDate BETWEEN :start AND :end AND (:planId IS NULL OR planId = :planId)
        AND (:purpose IS NULL OR purpose = :purpose)""")
    fun observeSummary(start: String, end: String, planId: String?, purpose: String?): Flow<FitnessOverviewRow>

    @Query("""WITH aggregated AS (
        SELECT exerciseId || '|' || unit || '|' || side || '|' || purpose AS `key`, MAX(sequence) AS latestSequence,
        COUNT(DISTINCT trainingDate) AS days, COUNT(*) AS segments, unit, side, purpose,
        COALESCE(SUM(actualTotal),0) AS actualTotal, COUNT(actualTotal) AS totalFilled,
        COALESCE(SUM(actualLeft),0) AS actualLeft, COUNT(actualLeft) AS leftFilled,
        COALESCE(SUM(actualRight),0) AS actualRight, COUNT(actualRight) AS rightFilled
        FROM fitness_events WHERE status = 'Valid' AND trainingDate BETWEEN :start AND :end
        AND (:planId IS NULL OR planId = :planId) AND (:purpose IS NULL OR purpose = :purpose)
        GROUP BY exerciseId, unit, side, purpose)
        SELECT a.`key`, latest.exerciseName AS name, a.days, a.segments, a.unit, a.side, a.purpose,
        a.actualTotal, a.totalFilled, a.actualLeft, a.leftFilled, a.actualRight, a.rightFilled
        FROM aggregated a JOIN fitness_events latest ON latest.sequence = a.latestSequence
        ORDER BY a.segments DESC, a.`key`""")
    fun observeExerciseCounts(start: String, end: String, planId: String?, purpose: String?): Flow<List<FitnessCountRow>>

    @Query("""SELECT CASE WHEN purpose = 'Strength' THEN primaryPart ELSE purpose END AS `key`, '' AS name,
        COUNT(DISTINCT trainingDate) AS days, COUNT(*) AS segments, 'Reps' AS unit, 'Total' AS side,
        MAX(purpose) AS purpose, 0 AS actualTotal, 0 AS totalFilled, 0 AS actualLeft, 0 AS leftFilled,
        0 AS actualRight, 0 AS rightFilled FROM fitness_events WHERE status = 'Valid'
        AND trainingDate BETWEEN :start AND :end AND (:planId IS NULL OR planId = :planId)
        AND (:purpose IS NULL OR purpose = :purpose)
        GROUP BY CASE WHEN purpose = 'Strength' THEN primaryPart ELSE purpose END ORDER BY segments DESC, `key`""")
    fun observePartCounts(start: String, end: String, planId: String?, purpose: String?): Flow<List<FitnessCountRow>>

    @Query("""SELECT DISTINCT trainingDate FROM fitness_events WHERE status = 'Valid' AND trainingDate <= :today
        AND purpose IN ('Strength','Cardio') ORDER BY trainingDate DESC""")
    fun observeTrainingDates(today: String): Flow<List<String>>
    @Query("SELECT exerciseId FROM fitness_events WHERE status = 'Valid' AND trainingDate <= :today GROUP BY exerciseId ORDER BY MAX(sequence) DESC LIMIT 30")
    suspend fun recentExercises(today: String): List<String>

    // 只对少量部位做索引定位，不把全部训练事件加载到内存查最近记录。
    @Query("""SELECT p.id AS part, e.payload AS payload FROM fitness_parts p LEFT JOIN fitness_events e ON e.id = (
        SELECT n.id FROM fitness_events n JOIN fitness_event_parts ep ON ep.eventId = n.id
        WHERE ep.part = p.id AND n.status = 'Valid' AND n.purpose = 'Strength' AND n.trainingDate <= :today
        ORDER BY n.trainingDate DESC, n.sequence DESC LIMIT 1
        ) WHERE p.id IN (:parts)""")
    suspend fun recovery(parts: List<String>, today: String): List<FitnessRecoveryRow>

    @Upsert suspend fun settings(row: FitnessSettingsRow)
    @Upsert suspend fun plan(row: FitnessPlanRow)
    @Upsert suspend fun days(rows: List<FitnessDayRow>)
    @Upsert suspend fun items(rows: List<FitnessItemRow>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEvents(rows: List<FitnessEventRow>)
    @Upsert suspend fun updateEvents(rows: List<FitnessEventRow>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun eventParts(rows: List<FitnessEventPartRow>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun parts(rows: List<FitnessPartRow>)
    @Upsert suspend fun exercise(row: FitnessExerciseRow)
    @Upsert suspend fun template(row: FitnessTemplateRow)
    @Query("DELETE FROM fitness_items WHERE id IN (:ids)") suspend fun deleteItems(ids: List<String>)
    @Query("DELETE FROM fitness_days WHERE planId = :planId AND date IN (:dates)") suspend fun deleteDays(planId: String, dates: List<String>)
    @Query("DELETE FROM fitness_plans WHERE id = :id") suspend fun deletePlan(id: String)
    @Query("DELETE FROM fitness_events") suspend fun clearEvents()
    @Query("DELETE FROM fitness_plans") suspend fun clearPlans()
    @Query("DELETE FROM fitness_personal_exercises") suspend fun clearExercises()
    @Query("DELETE FROM fitness_templates") suspend fun clearTemplates()
}

package com.pockettoolbox.feature.fitness.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation
import com.pockettoolbox.feature.fitness.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Entity(tableName = "fitness_settings", primaryKeys = ["id"])
data class FitnessSettingsRow(val id: Int = 1, val payload: String)

@Entity(tableName = "fitness_plans", primaryKeys = ["id"], indices = [Index("status", "start")])
data class FitnessPlanRow(val id: String, val name: String, val start: String, val end: String, val status: String)

@Entity(tableName = "fitness_days", primaryKeys = ["planId", "date"], indices = [Index("date")],
    foreignKeys = [ForeignKey(entity = FitnessPlanRow::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)])
data class FitnessDayRow(val planId: String, val date: String, val kind: String, val parts: String)

@Entity(tableName = "fitness_items", primaryKeys = ["id"],
    indices = [Index(value = ["planId", "date", "exerciseId"], unique = true), Index(value = ["planId", "date"])],
    foreignKeys = [ForeignKey(entity = FitnessDayRow::class, parentColumns = ["planId", "date"], childColumns = ["planId", "date"], onDelete = ForeignKey.CASCADE)])
data class FitnessItemRow(val id: String, val planId: String, val date: String, val exerciseId: String, val position: Int, val payload: String)

/** 聚合所需字段单独建列；payload保存完整快照，统一由fromEvent生成，禁止局部改列。 */
@Entity(tableName = "fitness_events", primaryKeys = ["id"], indices = [
    Index(value = ["requestId"], unique = true), Index(value = ["sequence"], unique = true),
    Index(value = ["status", "trainingDate"]), Index(value = ["planId", "trainingDate"]),
    Index(value = ["itemId", "status"]), Index("batchId"),
], foreignKeys = [ForeignKey(entity = FitnessItemRow::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)])
data class FitnessEventRow(
    val id: String, val requestId: String, val planId: String, val trainingDate: String, val itemId: String,
    val sequence: Long, val status: String, val exerciseId: String, val exerciseName: String,
    val purpose: String, val primaryPart: String?, val unit: String, val side: String,
    val actualTotal: Int?, val actualLeft: Int?, val actualRight: Int?, val batchId: String?, val payload: String,
)

@Entity(tableName = "fitness_event_parts", primaryKeys = ["eventId", "part"], indices = [Index("part", "eventId")],
    foreignKeys = [ForeignKey(entity = FitnessEventRow::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)])
data class FitnessEventPartRow(val eventId: String, val part: String)

@Entity(tableName = "fitness_parts", primaryKeys = ["id"])
data class FitnessPartRow(val id: String)

@Entity(tableName = "fitness_personal_exercises", primaryKeys = ["id"])
data class FitnessExerciseRow(val id: String, val payload: String)

@Entity(tableName = "fitness_templates", primaryKeys = ["id"])
data class FitnessTemplateRow(val id: String, val payload: String)

data class FitnessPlanBundle(
    @Embedded val plan: FitnessPlanRow,
    @Relation(parentColumn = "id", entityColumn = "planId") val days: List<FitnessDayRow>,
    @Relation(parentColumn = "id", entityColumn = "planId") val items: List<FitnessItemRow>,
) {
    fun toPlan(): FitnessPlan {
        val byDay = items.groupBy { it.date }
        return FitnessPlan(plan.id, plan.name, plan.start, plan.end, days.sortedBy { it.date }.map { day ->
            FitnessPlanDay(day.date, FitnessDayKind.valueOf(day.kind), FitnessJson.decodeFromString(day.parts),
                byDay[day.date].orEmpty().sortedBy { it.position }.map { FitnessJson.decodeFromString<FitnessPlanItem>(it.payload) })
        }, FitnessPlanStatus.valueOf(plan.status))
    }
}

internal fun FitnessPlan.toRow() = FitnessPlanRow(id, name.trim(), start, end, status.name)
internal fun FitnessPlanDay.toRow(planId: String) = FitnessDayRow(planId, date, kind.name, FitnessJson.encodeToString(parts))
internal fun FitnessPlanItem.toRow(planId: String, date: String, position: Int) = FitnessItemRow(id, planId, date, exercise.exerciseId, position, FitnessJson.encodeToString(this))
internal fun FitnessEventRow.toEvent(): FitnessSetEvent = FitnessJson.decodeFromString(payload)
internal fun FitnessSetEvent.toRow() = FitnessEventRow(id, requestId, planId, trainingDate, itemId, sequence, status.name,
    exercise.exerciseId, exercise.name, exercise.purpose.name, exercise.primaryPart?.name, exercise.unit.name, exercise.side.name,
    actual.total, actual.left, actual.right, batchId, FitnessJson.encodeToString(this))
internal fun FitnessSetEvent.partRows() = (listOfNotNull(exercise.primaryPart) + exercise.secondaryParts).distinct().map { FitnessEventPartRow(id, it.name) }

data class FitnessProgressRow(val itemId: String, val completed: Int)
data class FitnessItemOwner(val id: String, val planId: String)
data class FitnessOverviewRow(val trainingDays: Int, val strengthSets: Int, val cardioSegments: Int, val exercises: Int) {
    fun toSummary() = FitnessSummary(trainingDays, strengthSets, cardioSegments, exercises)
}
data class FitnessCountRow(
    val key: String, val name: String, val days: Int, val segments: Int, val unit: String, val side: String, val purpose: String,
    val actualTotal: Long, val totalFilled: Int, val actualLeft: Long, val leftFilled: Int, val actualRight: Long, val rightFilled: Int,
) {
    fun toCount(byExercise: Boolean) = FitnessCount(key,
        if (byExercise) name else FitnessPart.entries.firstOrNull { it.name == key }?.label ?: FitnessPurpose.entries.firstOrNull { it.name == key }?.label.orEmpty(),
        days, segments, FitnessUnit.valueOf(unit), FitnessSide.valueOf(side), FitnessPurpose.valueOf(purpose),
        actualTotal, totalFilled, actualLeft, leftFilled, actualRight, rightFilled)
}
data class FitnessRecoveryRow(val part: String, val payload: String?)

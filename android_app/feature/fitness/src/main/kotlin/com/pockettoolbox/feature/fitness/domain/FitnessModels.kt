package com.pockettoolbox.feature.fitness.domain

import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable enum class FitnessPurpose(val label: String) {
    Strength("力量训练"), Cardio("有氧体能"), Warmup("热身活动"), Stretch("拉伸放松");
    val countsTrainingDay: Boolean get() = this == Strength || this == Cardio
}
@Serializable enum class FitnessUnit(val label: String, val maximum: Int) { Reps("次", 999), Seconds("秒", 3600) }
@Serializable enum class FitnessSide { Total, Each }
@Serializable enum class FitnessPart(val label: String) {
    Chest("胸部"), Back("背部"), Shoulder("肩部"), Biceps("肱二头肌"), Triceps("肱三头肌"),
    Forearm("前臂"), Core("腹部与核心"), Glutes("臀部"), Legs("腿部"), Calves("小腿"), Neck("颈部"), Full("全身")
}
@Serializable enum class FitnessPlanStatus { Active, Archived }
@Serializable enum class FitnessDayKind { Training, Rest, Unplanned }
@Serializable enum class FitnessItemStatus { Active, Stopped }
@Serializable enum class FitnessEventStatus { Valid, Revoked }
@Serializable enum class FitnessTimeSource { Realtime, User, DateOnly }

@Serializable
data class FitnessImage(val path: String, val panel: Int = 0, val panels: Int = 1)

/** 器械内组全部满足、组间任一满足；空器械未知，不等同徒手。 */
@Serializable
data class FitnessExercise(
    val id: String,
    val name: String,
    val english: String = "",
    val aliases: List<String> = emptyList(),
    val purpose: FitnessPurpose,
    val primaryPart: FitnessPart? = null,
    val secondaryParts: List<FitnessPart> = emptyList(),
    val unit: FitnessUnit,
    val side: FitnessSide = FitnessSide.Total,
    val equipment: List<List<String>>,
    val introduction: String = "",
    val steps: List<String> = emptyList(),
    val cues: List<String> = emptyList(),
    val images: List<FitnessImage> = emptyList(),
    val contentRevision: String = "personal-1",
    val references: List<String> = emptyList(),
    val personal: Boolean = false,
    val archived: Boolean = false,
    val reviewed: Boolean = false,
) {
    fun snapshot() = FitnessExerciseSnapshot(id, name, purpose, primaryPart, secondaryParts, unit, side, contentRevision)
    fun matchesEquipment(owned: Set<String>): Boolean {
        val available = if ("dumbbellPair" in owned) owned + "dumbbellSingle" else owned
        return equipment.any { group -> group.isNotEmpty() && group.all { it in available } }
    }
}

/** 内容改名/重分类不重写安排和训练时的语义。 */
@Serializable
data class FitnessExerciseSnapshot(
    val exerciseId: String,
    val name: String,
    val purpose: FitnessPurpose,
    val primaryPart: FitnessPart?,
    val secondaryParts: List<FitnessPart>,
    val unit: FitnessUnit,
    val side: FitnessSide,
    val contentRevision: String,
)

@Serializable
data class FitnessPlanItem(
    val id: String,
    val exercise: FitnessExerciseSnapshot,
    val targetSets: Int? = null,
    val targetValue: Int? = null,
    val status: FitnessItemStatus = FitnessItemStatus.Active,
)

@Serializable
data class FitnessPlanDay(
    val date: String,
    val kind: FitnessDayKind = FitnessDayKind.Unplanned,
    val parts: List<FitnessPart> = emptyList(),
    val items: List<FitnessPlanItem> = emptyList(),
) {
    val activeItems: List<FitnessPlanItem> get() = items.filter { it.status == FitnessItemStatus.Active }
}

@Serializable
data class FitnessPlan(
    val id: String,
    val name: String,
    val start: String,
    val end: String,
    val days: List<FitnessPlanDay>,
    val status: FitnessPlanStatus = FitnessPlanStatus.Active,
)

@Serializable
data class FitnessDraft(
    val plan: FitnessPlan,
    val editing: Boolean,
    val selectedDate: String,
    val step: Int = 0,
    val savedAt: Long,
    val entrySource: String = "new",
    val rawTargets: Map<String, FitnessTargetInput> = emptyMap(),
)

@Serializable data class FitnessTargetInput(val sets: String = "", val value: String = "")

@Serializable
data class FitnessActual(val total: Int? = null, val left: Int? = null, val right: Int? = null)

@Serializable
data class FitnessSetEvent(
    val id: String,
    val requestId: String,
    val planId: String,
    val trainingDate: String,
    val itemId: String,
    val ordinal: Int,
    val exercise: FitnessExerciseSnapshot,
    val targetSets: Int,
    val targetValue: Int,
    val recordedAt: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val timeSource: FitnessTimeSource,
    val actualAt: Long? = null,
    val actual: FitnessActual = FitnessActual(),
    val status: FitnessEventStatus = FitnessEventStatus.Valid,
    val revokedAt: Long? = null,
    val batchId: String? = null,
    val sequence: Long = 0,
)

@Serializable
data class FitnessTemplate(val id: String, val name: String, val parts: List<FitnessPart>, val items: List<FitnessPlanItem>)

@Serializable
data class FitnessPreferences(
    val equipment: Set<String> = setOf("floor", "dumbbellPair", "longBand"),
    val recoveryEnabled: Boolean = true,
    val lastItemId: String? = null,
)

/** UI订阅不加载全部训练事件；事件按日期/统计范围单独读取。 */
@Serializable
data class FitnessData(
    val plans: List<FitnessPlan> = emptyList(),
    val personalExercises: List<FitnessExercise> = emptyList(),
    val preferences: FitnessPreferences = FitnessPreferences(),
    val draft: FitnessDraft? = null,
    val favorites: Set<String> = emptySet(),
    val templates: List<FitnessTemplate> = emptyList(),
    val mediaGeneration: String = "initial",
)

@Serializable
data class FitnessSnapshot(val data: FitnessData = FitnessData(), val events: List<FitnessSetEvent> = emptyList())

data class FitnessPlanIssue(val message: String, val date: String? = null, val itemId: String? = null, val field: String = "")
data class FitnessFilter(val start: String, val end: String, val planId: String? = null, val purpose: FitnessPurpose? = null)
data class FitnessRecordScope(val filter: FitnessFilter, val exerciseId: String? = null, val part: FitnessPart? = null,
    val unit: FitnessUnit? = null, val side: FitnessSide? = null, val limit: Int = 200)
data class FitnessStreak(val days: Int, val anchor: String?)
data class FitnessScheduledItem(val plan: FitnessPlan, val day: FitnessPlanDay, val item: FitnessPlanItem, val completed: Int)

fun fitnessId(): String = UUID.randomUUID().toString()
fun FitnessPlan.day(date: String): FitnessPlanDay? = days.firstOrNull { it.date == date }
fun FitnessSetEvent.isValid(): Boolean = status == FitnessEventStatus.Valid
fun FitnessPlanItem.measurement(): String = "${targetSets ?: "待填"}${if (exercise.purpose == FitnessPurpose.Strength) "组" else "段"} × ${if (exercise.side == FitnessSide.Each) "每侧" else ""}${targetValue ?: "待填"}${exercise.unit.label}"
fun fitnessToday(): String = LocalDate.now().toString()

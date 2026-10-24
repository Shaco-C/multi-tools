package com.pockettoolbox.feature.fitness.domain

import java.time.LocalDate

data class FitnessSummary(
    val trainingDays: Int = 0, val strengthSets: Int = 0, val cardioSegments: Int = 0, val exercises: Int = 0,
)

data class FitnessCount(
    val key: String,
    val name: String,
    val days: Int,
    val segments: Int,
    val unit: FitnessUnit,
    val side: FitnessSide,
    val purpose: FitnessPurpose,
    val actualTotal: Long = 0,
    val totalFilled: Int = 0,
    val actualLeft: Long = 0,
    val leftFilled: Int = 0,
    val actualRight: Long = 0,
    val rightFilled: Int = 0,
)

data class FitnessRecovery(val part: FitnessPart, val event: FitnessSetEvent?, val primary: Boolean)

object FitnessStatistics {
    /** 48小时是近期重复安排的产品筛查窗口，不是个人恢复时长。 */
    fun recoveryMessage(row: FitnessRecovery, now: Long, today: String): String {
        val event = row.event ?: return "暂无已记录训练"
        if (event.trainingDate == today) return "今天已记录此部位；继续安排时留意累计训练负荷"
        val at = when (event.timeSource) {
            FitnessTimeSource.DateOnly -> null
            FitnessTimeSource.User -> event.actualAt
            FitnessTimeSource.Realtime -> event.recordedAt
        }
        if (at == null || at > now) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(FitnessRules.date(event.trainingDate), FitnessRules.date(today))
            return if (days in 0..1) "相邻日有记录，时刻未知；再次安排时可考虑调整部位或强度" else "最近训练日${event.trainingDate}，无法计算精确间隔"
        }
        val hours = (now - at) / 3_600_000
        return if (hours < 48) "距这条训练记录约${hours}小时；今日再次安排时可考虑调整部位或强度" else "最近记录${event.trainingDate}；间隔不代表已完全恢复"
    }

    fun events(events: List<FitnessSetEvent>, filter: FitnessFilter): List<FitnessSetEvent> {
        FitnessRules.date(filter.start); FitnessRules.date(filter.end)
        require(filter.start <= filter.end) { "统计起止顺序无效" }
        return events.filter { it.isValid() && it.trainingDate >= filter.start && it.trainingDate <= filter.end &&
            (filter.planId == null || it.planId == filter.planId) && (filter.purpose == null || it.exercise.purpose == filter.purpose) }
    }

    fun summary(events: List<FitnessSetEvent>): FitnessSummary {
        val active = events.filter { it.isValid() }
        return FitnessSummary(active.filter { it.exercise.purpose.countsTrainingDay }.map { it.trainingDate }.distinct().size,
            active.count { it.exercise.purpose == FitnessPurpose.Strength }, active.count { it.exercise.purpose == FitnessPurpose.Cardio },
            active.map { it.exercise.exerciseId }.distinct().size)
    }

    fun counts(events: List<FitnessSetEvent>, byExercise: Boolean): List<FitnessCount> = events.filter { it.isValid() }.groupBy { event ->
        if (byExercise) "${event.exercise.exerciseId}|${event.exercise.unit}|${event.exercise.side}|${event.exercise.purpose}"
        else if (event.exercise.purpose == FitnessPurpose.Strength) requireNotNull(event.exercise.primaryPart).name else event.exercise.purpose.name
    }.map { (key, rows) ->
        val exercise = rows.maxBy { it.sequence }.exercise
        FitnessCount(key, if (byExercise) exercise.name else if (exercise.purpose == FitnessPurpose.Strength) requireNotNull(exercise.primaryPart).label else exercise.purpose.label,
            rows.map { it.trainingDate }.distinct().size, rows.size, exercise.unit, exercise.side, exercise.purpose,
            rows.sumOf { it.actual.total?.toLong() ?: 0 }, rows.count { it.actual.total != null },
            rows.sumOf { it.actual.left?.toLong() ?: 0 }, rows.count { it.actual.left != null },
            rows.sumOf { it.actual.right?.toLong() ?: 0 }, rows.count { it.actual.right != null })
    }.sortedWith(compareByDescending<FitnessCount> { it.segments }.thenBy { it.key })

    fun streak(dates: Set<String>, today: String): FitnessStreak {
        var cursor = FitnessRules.date(today)
        if (today !in dates) cursor = cursor.minusDays(1)
        val anchor = cursor.toString()
        var count = 0
        while (cursor.toString() in dates) { count++; cursor = cursor.minusDays(1) }
        return FitnessStreak(count, anchor.takeIf { count > 0 })
    }

    fun schedule(data: FitnessData, date: String, events: List<FitnessSetEvent>): List<FitnessScheduledItem> {
        val counts = events.filter { it.isValid() && it.trainingDate == date }.groupingBy { it.itemId }.eachCount()
        return data.plans.filter { it.status == FitnessPlanStatus.Active }.flatMap { plan ->
            plan.day(date)?.takeIf { it.kind == FitnessDayKind.Training }?.let { day ->
                day.activeItems.map { FitnessScheduledItem(plan, day, it, counts[it.id] ?: 0) }
            } ?: emptyList()
        }
    }

    fun recovery(events: List<FitnessSetEvent>, parts: List<FitnessPart>, today: String): List<FitnessRecovery> = parts.map { part ->
        val latest = events.filter { it.isValid() && it.trainingDate <= today && it.exercise.purpose == FitnessPurpose.Strength &&
            (it.exercise.primaryPart == part || part in it.exercise.secondaryParts) }
            .maxWithOrNull(compareBy<FitnessSetEvent> { it.trainingDate }.thenBy { it.sequence })
        FitnessRecovery(part, latest, latest?.exercise?.primaryPart == part)
    }
}

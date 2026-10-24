package com.pockettoolbox.feature.fitness.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class FitnessCompletion(
    val planId: String,
    val date: String,
    val itemId: String,
    val requestId: String,
    val count: Int = 1,
    val timeSource: FitnessTimeSource = FitnessTimeSource.Realtime,
    val actualAt: Long? = null,
    val continuePreviousDay: Boolean = false,
)

object FitnessRules {
    const val MAX_PLAN_DAYS = 366

    fun date(value: String): LocalDate {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { "日期格式须为YYYY-MM-DD" }
        val parsed = runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("日期无效") }
        require(parsed.toString() == value) { "日期无效" }
        return parsed
    }

    fun dates(start: String, end: String): List<String> {
        val first = date(start)
        val last = date(end)
        val count = ChronoUnit.DAYS.between(first, last) + 1
        require(count in 1..MAX_PLAN_DAYS) { "计划起止顺序无效或超过366天" }
        return (0 until count.toInt()).map { first.plusDays(it.toLong()).toString() }
    }

    fun positive(value: String, maximum: Int, label: String): Int {
        val text = value.trim()
        require(Regex("[0-9]+").matches(text)) { "$label 须为正整数" }
        val number = text.toIntOrNull()
        require(number != null && number in 1..maximum) { "$label 须为1—$maximum" }
        return number
    }

    fun name(value: String, label: String = "名称"): String = value.trim().also {
        require(it.length in 1..60) { "$label 须为1—60字符" }
    }

    fun newPlan(start: String, end: String = start): FitnessPlan = FitnessPlan(
        fitnessId(), if (start == end) "${start.substring(5)} 单日训练" else "${start.substring(5)}—${end.substring(5)} 训练计划",
        start, end, dates(start, end).map { FitnessPlanDay(it) },
    )

    fun issues(plan: FitnessPlan, events: List<FitnessSetEvent>): List<FitnessPlanIssue> {
        val result = mutableListOf<FitnessPlanIssue>()
        if (plan.name.trim().length !in 1..60) result += FitnessPlanIssue("填写1—60字符的计划名称", field = "name")
        val range = runCatching { dates(plan.start, plan.end) }.getOrElse {
            return result + FitnessPlanIssue(it.message.orEmpty(), field = "range")
        }.toSet()
        val daySet = plan.days.map { it.date }.toSet()
        require(plan.days.size == daySet.size) { "计划日期重复" }
        require(daySet.containsAll(range)) { "计划日期结构不完整" }
        val validCounts = events.filter { it.isValid() }.groupingBy { it.itemId }.eachCount()
        val historicalDays = events.filter { it.planId == plan.id }.map { it.trainingDate }.toSet()
        val itemIds = mutableSetOf<String>()
        for (day in plan.days) {
            date(day.date)
            require(day.date in range || day.date in historicalDays) { "计划日超出范围" }
            require(day.parts.distinct().size == day.parts.size) { "每日部位重复" }
            require(day.items.map { it.exercise.exerciseId }.distinct().size == day.items.size) { "同一计划日动作不能重复" }
            for (item in day.items) require(item.id.isNotBlank() && itemIds.add(item.id)) { "动作安排身份重复" }
            if (day.date !in range) require(day.activeItems.isEmpty()) { "范围外历史日须结束剩余安排" }
            if (day.kind != FitnessDayKind.Training) {
                require(day.activeItems.isEmpty() && day.parts.isEmpty()) { "休息或未安排日不能有有效动作/部位" }
                continue
            }
            if (day.activeItems.isEmpty()) result += FitnessPlanIssue("${day.date}尚未选择动作", day.date, field = "pick")
            for (part in day.parts) if (day.activeItems.none { it.exercise.purpose == FitnessPurpose.Strength && it.exercise.primaryPart == part }) {
                result += FitnessPlanIssue("${day.date} · ${part.label}尚未选择主动作", day.date, field = "part:${part.name}")
            }
            for (item in day.activeItems) {
                val prefix = "${day.date} · ${item.exercise.name}"
                if (item.targetSets == null || item.targetSets !in 1..99 || item.targetSets < (validCounts[item.id] ?: 0)) {
                    result += FitnessPlanIssue("$prefix：组/段数应为1—99且不少于已完成", day.date, item.id, "sets")
                }
                if (item.targetValue == null || item.targetValue !in 1..item.exercise.unit.maximum) {
                    result += FitnessPlanIssue("$prefix：填写有效的${item.exercise.unit.label}数", day.date, item.id, "value")
                }
                if (item.exercise.purpose == FitnessPurpose.Strength && (item.exercise.primaryPart == null || item.exercise.primaryPart !in day.parts)) {
                    result += FitnessPlanIssue("$prefix：选择对应主部位", day.date, item.id, "part")
                }
            }
        }
        if (plan.status == FitnessPlanStatus.Active && plan.days.none { it.activeItems.isNotEmpty() } && events.none { it.planId == plan.id }) {
            result += FitnessPlanIssue("首次保存至少安排一个完整训练日", field = "pick")
        }
        return result
    }

    fun validatePlan(plan: FitnessPlan, old: FitnessPlan?, events: List<FitnessSetEvent>) {
        require(plan.id.isNotBlank()) { "计划身份无效" }
        val errors = issues(plan, events)
        require(errors.isEmpty()) { errors.joinToString("\n") { it.message } }
        val historicalItems = events.map { it.itemId }.toSet()
        val validCounts = events.filter { it.isValid() }.groupingBy { it.itemId }.eachCount()
        val incoming = plan.days.flatMap { it.items }.associateBy { it.id }
        old?.days?.forEach { day -> day.items.filter { it.id in historicalItems }.forEach { item ->
            val replacement = incoming[item.id]
            require(replacement != null && plan.day(day.date)?.items?.any { it.id == item.id } == true) { "有完成或撤销历史的条目必须保留在原日期" }
            require(replacement.exercise.exerciseId == item.exercise.exerciseId && replacement.exercise.purpose == item.exercise.purpose &&
                replacement.exercise.unit == item.exercise.unit && replacement.exercise.side == item.exercise.side && replacement.exercise.primaryPart == item.exercise.primaryPart) {
                "有历史的条目不能修改动作/用途/计量/主归属"
            }
            val completed = validCounts[item.id] ?: 0
            require(completed == 0 || replacement.targetSets != null && replacement.targetSets in completed..99) { "有有效历史的条目不能清空或减少到已完成组数以下" }
        } }
    }

    fun addExercise(day: FitnessPlanDay, exercise: FitnessExercise): FitnessPlanDay {
        val existing = day.items.firstOrNull { it.exercise.exerciseId == exercise.id }
        require(existing != null || !exercise.archived) { "此动作已停用" }
        val item = existing?.copy(status = FitnessItemStatus.Active) ?: FitnessPlanItem(fitnessId(), exercise.snapshot())
        val parts = if (item.exercise.purpose == FitnessPurpose.Strength) day.parts + requireNotNull(item.exercise.primaryPart) { "力量动作需要主归属" } else day.parts
        return day.copy(kind = FitnessDayKind.Training, parts = parts.distinct(), items = if (existing == null) day.items + item else day.items.map { if (it.id == item.id) item else it })
    }

    fun stop(day: FitnessPlanDay, itemId: String): FitnessPlanDay {
        require(day.items.any { it.id == itemId }) { "动作安排不存在" }
        val items = day.items.map { if (it.id == itemId) it.copy(status = FitnessItemStatus.Stopped) else it }
        val active = items.filter { it.status == FitnessItemStatus.Active }
        return day.copy(kind = if (active.isEmpty()) FitnessDayKind.Unplanned else FitnessDayKind.Training,
            items = items, parts = day.parts.filter { part -> active.any { it.exercise.purpose == FitnessPurpose.Strength && it.exercise.primaryPart == part } })
    }

    fun resize(plan: FitnessPlan, start: String, end: String, events: List<FitnessSetEvent>): FitnessPlan {
        val range = dates(start, end)
        val historical = events.filter { it.planId == plan.id }.map { it.trainingDate }.toSet()
        val removedHistory = plan.days.filter { it.date !in range && it.date in historical }.map { day ->
            day.copy(kind = FitnessDayKind.Unplanned, parts = emptyList(), items = day.items.map { it.copy(status = FitnessItemStatus.Stopped) })
        }
        return plan.copy(start = start, end = end, days = (range.map { plan.day(it) ?: FitnessPlanDay(it) } + removedHistory).sortedBy { it.date })
    }

    fun copyDays(plan: FitnessPlan, source: FitnessPlanDay, targets: Set<String>, events: List<FitnessSetEvent>, overwrite: Boolean): FitnessPlan {
        require(source.items.isNotEmpty() && targets.isNotEmpty()) { "来源动作或目标日期为空" }
        for (target in targets) {
            require(target != source.date || source !in plan.days) { "不能复制到来源日期" }
            val day = requireNotNull(plan.day(target)) { "目标日期不在计划内" }
            require(target in dates(plan.start, plan.end)) { "目标日期不在有效范围" }
            require(events.none { it.planId == plan.id && it.trainingDate == target }) { "$target 有完成或撤销历史，不能覆盖" }
            require(overwrite || day.kind == FitnessDayKind.Unplanned && day.activeItems.isEmpty()) { "$target 已有安排，请明确允许覆盖" }
        }
        val sourceItems = source.activeItems
        require(sourceItems.isNotEmpty()) { "没有可复制的有效动作" }
        return plan.copy(days = plan.days.map { day -> if (day.date !in targets) day else day.copy(kind = FitnessDayKind.Training,
            parts = source.parts, items = sourceItems.map { it.copy(id = fitnessId(), status = FitnessItemStatus.Active) }) })
    }

    fun batchParts(plan: FitnessPlan, targets: Set<String>, kind: FitnessDayKind, parts: List<FitnessPart>, events: List<FitnessSetEvent>, overwrite: Boolean): FitnessPlan {
        require(targets.isNotEmpty()) { "请选择目标日期" }
        for (target in targets) {
            val day = requireNotNull(plan.day(target)) { "目标日期不在计划内" }
            require(target in dates(plan.start, plan.end)) { "目标日期不在有效范围" }
            require(events.none { it.planId == plan.id && it.trainingDate == target }) { "$target 有历史，不能批量覆盖" }
            require(overwrite || day.kind == FitnessDayKind.Unplanned && day.activeItems.isEmpty()) { "$target 已有安排" }
        }
        return plan.copy(days = plan.days.map { day -> if (day.date !in targets) day else day.copy(kind = kind,
            parts = if (kind == FitnessDayKind.Training) parts.distinct() else emptyList(), items = day.items.map { it.copy(status = FitnessItemStatus.Stopped) }) })
    }

    fun complete(plan: FitnessPlan, day: FitnessPlanDay, item: FitnessPlanItem, completed: Int, command: FitnessCompletion,
        clock: Clock, firstSequence: Long): List<FitnessSetEvent> {
        require(plan.id == command.planId && day.date == command.date && item.id == command.itemId) { "训练上下文已变化" }
        require(plan.status == FitnessPlanStatus.Active && day.kind == FitnessDayKind.Training && item.status == FitnessItemStatus.Active) { "此安排不能新增记录" }
        val today = LocalDate.now(clock)
        val trainingDate = date(command.date)
        require(trainingDate <= today) { "未来日期仅可预览" }
        require(command.requestId.isNotBlank() && command.count in 1..99 && firstSequence > 0) { "记录请求无效" }
        require(command.count == 1 || trainingDate < today) { "批量补记仅用于过去日期" }
        val targetSets = requireNotNull(item.targetSets)
        val targetValue = requireNotNull(item.targetValue)
        require(completed >= 0 && completed + command.count <= targetSets) { "目标组数已达到或补记超过剩余目标" }
        require(targetSets in 1..99 && targetValue in 1..item.exercise.unit.maximum) { "动作目标无效" }
        when (command.timeSource) {
            FitnessTimeSource.Realtime -> require(trainingDate == today || command.continuePreviousDay) { "过去日期需明确补记或继续原日" }
            FitnessTimeSource.DateOnly -> require(trainingDate < today && command.actualAt == null) { "仅日期补记须为过去日期且不伪造时刻" }
            FitnessTimeSource.User -> {
                val at = requireNotNull(command.actualAt) { "请填写实际训练时刻" }
                require(at in 0..clock.millis() && Instant.ofEpochMilli(at).atZone(clock.zone).toLocalDate() == trainingDate) { "实际时刻与训练日期不符或晚于当前时刻" }
            }
        }
        if (command.timeSource != FitnessTimeSource.User) require(command.actualAt == null) { "此时间来源不能附带用户填写时刻" }
        return (0 until command.count).map { index -> FitnessSetEvent(
            id = fitnessId(), requestId = if (command.count == 1) command.requestId else "${command.requestId}:$index",
            planId = plan.id, trainingDate = day.date, itemId = item.id, ordinal = completed + index + 1,
            exercise = item.exercise, targetSets = targetSets, targetValue = targetValue, recordedAt = clock.millis(),
            zoneId = clock.zone.id, offsetSeconds = clock.instant().atZone(clock.zone).offset.totalSeconds,
            timeSource = command.timeSource, actualAt = command.actualAt,
            batchId = if (command.count > 1) command.requestId else null, sequence = firstSequence + index,
        ) }
    }

    /** 撤销保留事件；序位按剩余有效操作重排，不把序号当成历史身份。 */
    fun revoke(events: List<FitnessSetEvent>, ids: Set<String>, at: Long): List<FitnessSetEvent> {
        val affected = events.filter { it.id in ids && it.isValid() }.map { it.itemId }.toSet()
        val replaced = events.map { if (it.id in ids && it.isValid()) it.copy(status = FitnessEventStatus.Revoked, revokedAt = at) else it }
        val ranks = replaced.filter { it.isValid() && it.itemId in affected }.groupBy { it.itemId }.values.flatMap { rows ->
            rows.sortedBy { it.sequence }.mapIndexed { index, event -> event.id to index + 1 }
        }.toMap()
        return replaced.map { event -> ranks[event.id]?.let { event.copy(ordinal = it) } ?: event }
    }

    fun validateActual(exercise: FitnessExerciseSnapshot, actual: FitnessActual) {
        val values = if (exercise.side == FitnessSide.Each) listOf(actual.left, actual.right) else listOf(actual.total)
        require(values.all { it == null || it in 1..exercise.unit.maximum }) { "实际值应为正整数或未填写" }
        require(if (exercise.side == FitnessSide.Each) actual.total == null else actual.left == null && actual.right == null) { "实际值的左右口径不符" }
    }

    fun validateExercise(exercise: FitnessExercise) {
        require(exercise.id.isNotBlank()) { "动作身份无效" }
        name(exercise.name, "动作名称")
        require(exercise.purpose != FitnessPurpose.Strength || exercise.primaryPart != null) { "力量动作须选择主部位" }
        require(exercise.equipment.isNotEmpty() && exercise.equipment.all { it.isNotEmpty() && it.none(String::isBlank) }) { "请填写完整器械条件" }
        require(exercise.images.all { Regex("[a-zA-Z0-9_./()+-]+").matches(it.path) && ".." !in it.path && !it.path.startsWith('/') && it.panels in 1..4 && it.panel in 0 until it.panels }) { "图片引用无效" }
    }
}

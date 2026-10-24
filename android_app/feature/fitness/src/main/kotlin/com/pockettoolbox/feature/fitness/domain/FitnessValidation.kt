package com.pockettoolbox.feature.fitness.domain

import java.time.ZoneId
import java.time.Instant

/** 用于恢复边界的完整关联校验；日常记组仅校验当前事务范围。 */
object FitnessValidation {
    fun draft(draft: FitnessDraft) {
        require(draft.step in 0..3 && draft.savedAt >= 0 && draft.plan.id.isNotBlank()) { "草稿状态无效" }
        require(draft.plan.days.isNotEmpty() && draft.plan.days.size <= FitnessRules.MAX_PLAN_DAYS * 2) { "草稿日期结构无效" }
        require(draft.plan.days.any { it.date == draft.selectedDate }) { "草稿选择日期不存在" }
        require(draft.plan.days.map { it.date }.distinct().size == draft.plan.days.size) { "草稿日期重复" }
        val ids = mutableSetOf<String>()
        for (day in draft.plan.days) {
            FitnessRules.date(day.date)
            for (item in day.items) require(item.id.isNotBlank() && ids.add(item.id)) { "草稿条目身份重复" }
        }
        require(draft.rawTargets.keys.all { it in ids } && draft.rawTargets.values.all { it.sets.length <= 12 && it.value.length <= 12 }) { "草稿目标输入无效" }
        // 草稿名称、日期输入和目标可以尚未补齐；不得用生效计划校验拒绝合法未完成草稿。
    }

    fun snapshot(snapshot: FitnessSnapshot) {
        val data = snapshot.data
        require(data.plans.map { it.id }.distinct().size == data.plans.size) { "计划身份重复" }
        require(data.personalExercises.map { it.id }.distinct().size == data.personalExercises.size) { "个人动作身份重复" }
        for (exercise in data.personalExercises) {
            require(exercise.personal && exercise.id.startsWith("USR-")) { "个人动作身份与内置内容冲突" }
            FitnessRules.validateExercise(exercise)
            require(exercise.images.all { it.path.matches(Regex("personal/[a-f0-9]{64}\\.(jpg|png|webp)")) && it.panels == 1 }) { "个人图片引用不合法" }
        }
        val items = mutableMapOf<String, Triple<String, String, FitnessPlanItem>>()
        val byPlan = snapshot.events.groupBy { it.planId }
        for (plan in data.plans) {
            FitnessRules.validatePlan(plan, null, byPlan[plan.id].orEmpty())
            for (day in plan.days) for (item in day.items) {
                require(items.put(item.id, Triple(plan.id, day.date, item)) == null) { "动作条目身份跨计划重复" }
            }
        }
        val eventIds = mutableSetOf<String>()
        val requests = mutableSetOf<String>()
        val sequences = mutableSetOf<Long>()
        for (event in snapshot.events) {
            require(event.id.isNotBlank() && eventIds.add(event.id) && event.requestId.isNotBlank() && requests.add(event.requestId)) { "记录或请求身份重复" }
            require(event.sequence > 0 && sequences.add(event.sequence)) { "记录顺序损坏" }
            val owner = requireNotNull(items[event.itemId]) { "训练记录失去原动作安排" }
            require(owner.first == event.planId && owner.second == event.trainingDate) { "训练记录与日期/计划关联不符" }
            val item = owner.third
            require(event.exercise.exerciseId == item.exercise.exerciseId && event.exercise.purpose == item.exercise.purpose &&
                event.exercise.unit == item.exercise.unit && event.exercise.side == item.exercise.side && event.exercise.primaryPart == item.exercise.primaryPart) { "训练快照语义不符" }
            require(event.ordinal in 1..99 && event.targetSets in 1..99 && event.targetValue in 1..event.exercise.unit.maximum) { "训练快照目标无效" }
            require(event.recordedAt >= 0 && event.offsetSeconds in -64800..64800) { "记录时刻或时区无效" }
            val zone = ZoneId.of(event.zoneId)
            val recordedDate = Instant.ofEpochMilli(event.recordedAt).atZone(zone).toLocalDate().toString()
            require(event.trainingDate <= recordedDate && (event.timeSource != FitnessTimeSource.DateOnly || event.trainingDate < recordedDate)) { "训练日期晚于记录时刻，或补记并非过去日期" }
            require(if (event.timeSource == FitnessTimeSource.User) event.actualAt != null && event.actualAt in 0..event.recordedAt else event.actualAt == null) { "实际时刻与时间来源不符" }
            if (event.timeSource == FitnessTimeSource.User) require(Instant.ofEpochMilli(requireNotNull(event.actualAt)).atZone(zone).toLocalDate().toString() == event.trainingDate) { "手填时刻与训练日期不符" }
            require(if (event.isValid()) event.revokedAt == null else event.revokedAt != null && event.revokedAt >= 0) { "撤销状态不完整" }
            FitnessRules.validateActual(event.exercise, event.actual)
        }
        val activeByItem = snapshot.events.filter { it.isValid() }.groupBy { it.itemId }
        for ((id, events) in activeByItem) {
            val item = items.getValue(id).third
            require(item.targetSets != null && events.size <= item.targetSets) { "当前目标少于已记录组数" }
            require(events.sortedBy { it.sequence }.map { it.ordinal } == (1..events.size).toList()) { "有效组序位不连续" }
        }
        data.draft?.let(::draft)
        require(data.favorites.none { it.isBlank() }) { "收藏身份无效" }
        require(data.templates.map { it.id }.distinct().size == data.templates.size) { "模板身份重复" }
        data.templates.forEach(::template)
        require(data.mediaGeneration.matches(Regex("initial|gen-[a-f0-9-]{36}"))) { "媒体代次无效" }
    }

    fun template(template: FitnessTemplate) {
        require(template.id.isNotBlank() && template.items.isNotEmpty()) { "模板身份或动作为空" }
        FitnessRules.name(template.name, "模板名称")
        require(template.items.map { it.exercise.exerciseId }.distinct().size == template.items.size) { "模板动作重复" }
        for (item in template.items) {
            require(item.status == FitnessItemStatus.Active) { "模板不能包含已结束条目" }
            require(item.targetSets != null && item.targetSets in 1..99 && item.targetValue != null && item.targetValue in 1..item.exercise.unit.maximum) { "先填好模板目标" }
            require(item.exercise.purpose != FitnessPurpose.Strength || item.exercise.primaryPart in template.parts) { "模板主部位缺失" }
        }
    }
}

package com.pockettoolbox.feature.fitness.data

import androidx.room.withTransaction
import com.pockettoolbox.feature.fitness.domain.*
import java.time.Clock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class RoomFitnessRepository(private val database: FitnessDatabase) : FitnessRepository {
    private val dao = database.fitnessDao()
    // 所有健身写入与完整导出/恢复共用该锁；不锁电费、饮食或应用壳。
    internal val operationMutex = Mutex()
    override val data = combine(dao.observePlans().map { it.map(FitnessPlanBundle::toPlan) },
        dao.observeSettings().filterNotNull().map { FitnessJson.decodeFromString<FitnessData>(it.payload) },
        dao.observeExercises().map { it.map { row -> FitnessJson.decodeFromString<FitnessExercise>(row.payload) } },
        dao.observeTemplates().map { it.map { row -> FitnessJson.decodeFromString<FitnessTemplate>(row.payload) } }) { plans, settings, exercises, templates ->
        settings.copy(plans = plans, personalExercises = exercises, templates = templates)
    }.flowOn(Dispatchers.IO)

    override suspend fun initialize() = operationMutex.withLock {
        database.withTransaction {
            if (dao.settings() == null) {
                require(dao.businessRowCount() == 0L) { "健身设置缺失但已有业务资料，请保留数据库并检查，未重新初始化" }
                dao.settings(FitnessSettingsRow(payload = FitnessJson.encodeToString(FitnessData())))
            }
            dao.parts(FitnessPart.entries.map { FitnessPartRow(it.name) })
        }
    }

    internal suspend fun readData(): FitnessData {
        val settings = requireNotNull(dao.settings()) { "健身资料尚未初始化" }
        return FitnessJson.decodeFromString<FitnessData>(settings.payload).copy(plans = dao.plans().map { it.toPlan() },
            personalExercises = dao.exercises().map { FitnessJson.decodeFromString<FitnessExercise>(it.payload) },
            templates = dao.templates().map { FitnessJson.decodeFromString<FitnessTemplate>(it.payload) })
    }

    override suspend fun currentData(): FitnessData = operationMutex.withLock { database.withTransaction { readData() } }
    override suspend fun planEvents(planId: String) = dao.planEvents(planId).map { it.toEvent() }
    override fun observeEvents(filter: FitnessFilter, limit: Int, exerciseId: String?, part: FitnessPart?, unit: FitnessUnit?, side: FitnessSide?) =
        dao.observeEvents(filter.start, filter.end, filter.planId, filter.purpose?.name, limit.coerceIn(1, 5000), exerciseId, part?.name, unit?.name, side?.name).map { it.map(FitnessEventRow::toEvent) }.flowOn(Dispatchers.IO)
    override suspend fun itemEvents(itemId: String): List<FitnessSetEvent> = dao.itemEvents(itemId).map { it.toEvent() }
    override suspend fun recentExercises(today: String): List<String> = dao.recentExercises(today)
    override fun observeProgress(date: String, planId: String?) = dao.observeProgress(date, planId).map { it.associate { row -> row.itemId to row.completed } }
    override fun observeSummary(filter: FitnessFilter) = dao.observeSummary(filter.start, filter.end, filter.planId, filter.purpose?.name).map { it.toSummary() }
    override fun observeCounts(filter: FitnessFilter, byExercise: Boolean) =
        (if (byExercise) dao.observeExerciseCounts(filter.start, filter.end, filter.planId, filter.purpose?.name)
        else dao.observePartCounts(filter.start, filter.end, filter.planId, filter.purpose?.name)).map { it.map { row -> row.toCount(byExercise) } }
    override fun observeTrainingDates(today: String) = dao.observeTrainingDates(today).map { it.toSet() }
    override suspend fun recovery(parts: List<FitnessPart>, today: String): List<FitnessRecovery> {
        if (parts.isEmpty()) return emptyList()
        return dao.recovery(parts.distinct().map { it.name }, today).map { row ->
            val event = row.payload?.let { FitnessJson.decodeFromString<FitnessSetEvent>(it) }
            val part = FitnessPart.valueOf(row.part)
            FitnessRecovery(part, event, event?.exercise?.primaryPart == part)
        }
    }

    private suspend fun settingsChange(transform: (FitnessData) -> FitnessData) = operationMutex.withLock {
        database.withTransaction {
            val row = requireNotNull(dao.settings())
            val current = FitnessJson.decodeFromString<FitnessData>(row.payload)
            dao.settings(row.copy(payload = FitnessJson.encodeToString(transform(current))))
        }
    }

    override suspend fun saveDraft(draft: FitnessDraft?) {
        draft?.let(FitnessValidation::draft)
        settingsChange { it.copy(draft = draft) }
    }

    override suspend fun savePlan(plan: FitnessPlan, clearDraft: Boolean) = operationMutex.withLock {
        database.withTransaction {
            val old = dao.plan(plan.id)?.toPlan()
            val events = dao.planEvents(plan.id).map { it.toEvent() }
            FitnessRules.validatePlan(plan, old, events)
            writePlan(plan, old)
            val settings = requireNotNull(dao.settings())
            val value = FitnessJson.decodeFromString<FitnessData>(settings.payload)
            if (clearDraft) dao.settings(settings.copy(payload = FitnessJson.encodeToString(value.copy(draft = null))))
        }
    }

    private suspend fun writePlan(plan: FitnessPlan, old: FitnessPlan?) {
        val items = plan.days.flatMap { day -> day.items.mapIndexed { position, item -> item.toRow(plan.id, day.date, position) } }
        // 防止Upsert把另一计划的同ID条目搬走；一次查询一批，兼容SQLite绑定数量。
        for (chunk in items.map { it.id }.chunked(800)) require(dao.itemOwners(chunk).all { it.planId == plan.id }) { "动作条目身份已被其他计划占用" }
        dao.plan(plan.toRow())
        dao.days(plan.days.map { it.toRow(plan.id) })
        val newIds = items.map { it.id }.toSet()
        val removed = old?.days.orEmpty().flatMap { it.items }.map { it.id }.filterNot { it in newIds }
        for (chunk in removed.chunked(800)) dao.deleteItems(chunk)
        // 先清理无历史条目再写入，避免同日同动作唯一键与已移除的旧身份冲突。
        dao.items(items)
        val dates = plan.days.map { it.date }.toSet()
        val removedDays = old?.days.orEmpty().map { it.date }.filterNot { it in dates }
        if (removedDays.isNotEmpty()) dao.deleteDays(plan.id, removedDays)
    }

    override suspend fun archive(planId: String, archived: Boolean) = operationMutex.withLock {
        database.withTransaction {
            val plan = requireNotNull(dao.plan(planId)).toPlan().copy(status = if (archived) FitnessPlanStatus.Archived else FitnessPlanStatus.Active)
            if (!archived) FitnessRules.validatePlan(plan, null, dao.planEvents(planId).map { it.toEvent() })
            dao.plan(plan.toRow())
        }
    }

    override suspend fun deletePlan(planId: String) = operationMutex.withLock {
        database.withTransaction {
            dao.deletePlan(planId)
            val settings = requireNotNull(dao.settings())
            val value = FitnessJson.decodeFromString<FitnessData>(settings.payload)
            if (value.draft?.plan?.id == planId) dao.settings(settings.copy(payload = FitnessJson.encodeToString(value.copy(draft = null))))
        }
    }

    override suspend fun complete(command: FitnessCompletion, clock: Clock): List<FitnessSetEvent> = operationMutex.withLock {
        database.withTransaction {
            val existing = dao.requested(command.requestId)
            if (existing.isNotEmpty()) {
                require(existing.all { it.planId == command.planId && it.trainingDate == command.date && it.itemId == command.itemId }) { "请求身份与原训练不符" }
                require(existing.size == command.count && existing.map { it.toEvent() }.all { it.timeSource == command.timeSource && it.actualAt == command.actualAt }) { "同一请求不能变更补记数量或时间来源" }
                return@withTransaction existing.map { it.toEvent() }
            }
            val header = requireNotNull(dao.planRow(command.planId)) { "计划不存在" }
            val storedDay = requireNotNull(dao.dayRow(command.planId, command.date)) { "训练日期不存在" }
            val storedItem = requireNotNull(dao.item(command.itemId)) { "动作安排不存在" }
            require(storedItem.planId == command.planId && storedItem.date == command.date) { "动作不属于当前计划日" }
            val item = FitnessJson.decodeFromString<FitnessPlanItem>(storedItem.payload)
            val day = FitnessPlanDay(storedDay.date, FitnessDayKind.valueOf(storedDay.kind), FitnessJson.decodeFromString(storedDay.parts), listOf(item))
            val plan = FitnessPlan(header.id, header.name, header.start, header.end, listOf(day), FitnessPlanStatus.valueOf(header.status))
            val events = FitnessRules.complete(plan, day, item, dao.completed(item.id), command, clock, dao.lastSequence() + 1)
            dao.insertEvents(events.map { it.toRow() })
            dao.eventParts(events.flatMap { it.partRows() })
            val settings = requireNotNull(dao.settings())
            val value = FitnessJson.decodeFromString<FitnessData>(settings.payload)
            dao.settings(settings.copy(payload = FitnessJson.encodeToString(value.copy(preferences = value.preferences.copy(lastItemId = item.id)))))
            events
        }
    }

    override suspend fun revoke(eventId: String, at: Long) = revokeWhere(at) { dao.event(eventId)?.let(::listOf).orEmpty() }
    override suspend fun revokeBatch(batchId: String, at: Long) = revokeWhere(at) { dao.batch(batchId) }
    private suspend fun revokeWhere(at: Long, selected: suspend () -> List<FitnessEventRow>) = operationMutex.withLock {
        database.withTransaction {
            val rows = selected().filter { it.status == FitnessEventStatus.Valid.name }
            val ids = rows.map { it.id }.toSet()
            val old = rows.map { it.itemId }.distinct().chunked(800).flatMap { dao.eventsForItems(it) }.map { it.toEvent() }
            val changed = FitnessRules.revoke(old, ids, at).filterIndexed { index, event -> event != old[index] }
            dao.updateEvents(changed.map { it.toRow() })
        }
    }

    override suspend fun updateActual(eventId: String, actual: FitnessActual) = operationMutex.withLock {
        database.withTransaction {
            val event = requireNotNull(dao.event(eventId)) { "记录不存在" }.toEvent()
            require(event.isValid()) { "此组已经撤销" }
            FitnessRules.validateActual(event.exercise, actual)
            dao.updateEvents(listOf(event.copy(actual = actual).toRow()))
        }
    }

    override suspend fun stopItem(planId: String, date: String, itemId: String) = operationMutex.withLock {
        database.withTransaction {
            val plan = requireNotNull(dao.plan(planId)).toPlan()
            require(plan.status == FitnessPlanStatus.Active) { "归档计划不能修改安排" }
            val day = requireNotNull(plan.day(date))
            var next = plan.copy(days = plan.days.map { if (it.date == date) FitnessRules.stop(day, itemId) else it })
            if (next.days.none { it.activeItems.isNotEmpty() } && !dao.hasHistory(planId)) next = next.copy(status = FitnessPlanStatus.Archived)
            writePlan(next, plan)
        }
    }

    override suspend fun saveExercise(exercise: FitnessExercise) {
        FitnessRules.validateExercise(exercise)
        require(exercise.personal && exercise.id.startsWith("USR-")) { "不能覆盖内置动作" }
        require(exercise.images.all { it.path.matches(Regex("personal/[a-f0-9]{64}\\.(jpg|png|webp)")) && it.panels == 1 }) { "请先保存图片到个人媒体目录" }
        operationMutex.withLock { dao.exercise(FitnessExerciseRow(exercise.id, FitnessJson.encodeToString(exercise))) }
    }
    override suspend fun preferences(value: FitnessPreferences) = settingsChange { it.copy(preferences = value) }
    override suspend fun favorite(exerciseId: String) {
        require(exerciseId.isNotBlank())
        settingsChange { it.copy(favorites = if (exerciseId in it.favorites) it.favorites - exerciseId else it.favorites + exerciseId) }
    }
    override suspend fun saveTemplate(template: FitnessTemplate) {
        FitnessValidation.template(template)
        operationMutex.withLock { dao.template(FitnessTemplateRow(template.id, FitnessJson.encodeToString(template))) }
    }

    override suspend fun snapshot(): FitnessSnapshot = operationMutex.withLock {
        snapshotLocked()
    }

    internal suspend fun snapshotLocked(): FitnessSnapshot = database.withTransaction { FitnessSnapshot(readData(), dao.allEvents().map { it.toEvent() }) }
    internal suspend fun restoreLocked(snapshot: FitnessSnapshot) = database.withTransaction { replaceLocked(snapshot) }

    override suspend fun replace(snapshot: FitnessSnapshot) {
        FitnessValidation.snapshot(snapshot)
        operationMutex.withLock { database.withTransaction { replaceLocked(snapshot) } }
    }

    internal suspend fun replaceLocked(snapshot: FitnessSnapshot) {
        dao.clearEvents(); dao.clearPlans(); dao.clearExercises(); dao.clearTemplates()
        for (plan in snapshot.data.plans) writePlan(plan, null)
        for (chunk in snapshot.events.chunked(500)) {
            dao.insertEvents(chunk.map { it.toRow() })
            dao.eventParts(chunk.flatMap { it.partRows() })
        }
        for (exercise in snapshot.data.personalExercises) dao.exercise(FitnessExerciseRow(exercise.id, FitnessJson.encodeToString(exercise)))
        for (template in snapshot.data.templates) dao.template(FitnessTemplateRow(template.id, FitnessJson.encodeToString(template)))
        dao.settings(FitnessSettingsRow(payload = FitnessJson.encodeToString(snapshot.data.copy(plans = emptyList(), personalExercises = emptyList(), templates = emptyList()))))
    }
}

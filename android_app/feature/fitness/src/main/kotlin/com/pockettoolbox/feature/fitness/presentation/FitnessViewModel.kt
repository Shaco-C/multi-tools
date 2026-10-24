package com.pockettoolbox.feature.fitness.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies
import com.pockettoolbox.feature.fitness.data.FitnessCatalogDocument
import com.pockettoolbox.feature.fitness.data.InspectedFitnessArchive
import com.pockettoolbox.feature.fitness.domain.*
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FitnessNotice(val text: String, val undo: (() -> Unit)? = null)
data class FitnessEditorRequest(val planId: String? = null, val date: String, val range: Boolean = false,
    val exerciseId: String? = null, val sourceDay: FitnessPlanDay? = null, val step: Int? = null,
    val targetSets: Int? = null, val targetValue: Int? = null, val overwrite: Boolean = false)
data class FitnessDaySelection(val date: String, val planId: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class FitnessViewModel(
    val dependencies: FitnessFeatureDependencies,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val repository = dependencies.repository
    var now by mutableLongStateOf(clock.millis()); private set
    var data by mutableStateOf(FitnessData()); private set
    var catalog by mutableStateOf<FitnessCatalogDocument?>(null); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set
    var today by mutableStateOf(LocalDate.now(clock).toString()); private set
    val zoneId get() = clock.zone
    private val selection = MutableStateFlow(FitnessDaySelection(today))
    val selected: FitnessDaySelection get() = selection.value
    var dayEvents by mutableStateOf<List<FitnessSetEvent>>(emptyList()); private set
    var dayProgress by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var todayProgress by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var itemRecords by mutableStateOf<List<FitnessSetEvent>?>(null); private set
    private val recordScope = MutableStateFlow<FitnessRecordScope?>(null)
    var scopedRecords by mutableStateOf<List<FitnessSetEvent>>(emptyList()); private set
    var recentExerciseIds by mutableStateOf<List<String>>(emptyList()); private set
    var streak by mutableStateOf(FitnessStreak(0, null)); private set
    var recovery by mutableStateOf<List<FitnessRecovery>>(emptyList()); private set
    var recoveryError by mutableStateOf<String?>(null); private set
    var focusItemId by mutableStateOf<String?>(null); private set
    private var session: Pair<String, String>? = null
    private val todayFlow = MutableStateFlow(today)
    private var loadJob: Job? = null
    private var recoveryJob: Job? = null

    var statsFilter by mutableStateOf(FitnessFilter(LocalDate.now(clock).minusDays(6).toString(), today, purpose = FitnessPurpose.Strength)); private set
    private val statsFlow = MutableStateFlow(statsFilter)
    private val statsMode = MutableStateFlow(false)
    var statsByExercise by mutableStateOf(false); private set
    var statsOverview by mutableStateOf(FitnessSummary()); private set
    var statsCounts by mutableStateOf<List<FitnessCount>>(emptyList()); private set
    var statsRecords by mutableStateOf<List<FitnessSetEvent>>(emptyList()); private set

    var draft by mutableStateOf<FitnessDraft?>(null); private set
    var editorVisible by mutableStateOf(false); private set
    var editorHistory by mutableStateOf<List<FitnessSetEvent>>(emptyList()); private set
    var editorError by mutableStateOf<String?>(null); private set
    var draftSaving by mutableStateOf(false); private set
    var draftDirty by mutableStateOf(false); private set
    var pendingEditor by mutableStateOf<FitnessEditorRequest?>(null); private set
    var pendingRange by mutableStateOf<FitnessPlan?>(null); private set
    private var draftJob: Job? = null
    var pendingRestore by mutableStateOf<InspectedFitnessArchive?>(null); private set
    private val noticesChannel = Channel<FitnessNotice>(Channel.BUFFERED)
    val notices = noticesChannel.receiveAsFlow()
    val exercises: List<FitnessExercise> get() = catalog?.exercises.orEmpty() + data.personalExercises

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                loadError = null
                catalog = withContext(ioDispatcher) { dependencies.initialize() }
                launch { selection.flatMapLatest { repository.observeEvents(FitnessFilter(it.date, it.date, it.planId), 200) }.collect { dayEvents = it } }
                launch { selection.flatMapLatest { repository.observeProgress(it.date, it.planId) }.collect { dayProgress = it } }
                launch { todayFlow.flatMapLatest { repository.observeProgress(it) }.collect { todayProgress = it } }
                launch { todayFlow.flatMapLatest { date -> repository.observeTrainingDates(date).map { FitnessStatistics.streak(it, date) } }.collect { streak = it } }
                launch { statsFlow.flatMapLatest { repository.observeSummary(it.copy(purpose = null)) }.collect { statsOverview = it } }
                launch { combine(statsFlow, statsMode) { filter, mode -> filter to mode }.flatMapLatest { (filter, mode) -> repository.observeCounts(filter, mode) }.collect { statsCounts = it } }
                launch { statsFlow.flatMapLatest { repository.observeEvents(it, 200) }.collect { statsRecords = it } }
                launch { recordScope.flatMapLatest { scope -> if (scope == null) flowOf(emptyList()) else repository.observeEvents(scope.filter, scope.limit, scope.exerciseId, scope.part, scope.unit, scope.side) }.collect { scopedRecords = it } }
                repository.data.collect { value ->
                    data = value
                    if (!editorVisible && !draftDirty) draft = value.draft
                    loaded = true
                    refreshRecovery()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { loadError = error.message ?: "健身资料读取失败，原资料未覆盖" }
        }
    }

    fun refreshToday() {
        now = clock.millis()
        val current = LocalDate.now(clock).toString()
        if (today != current) { today = current; todayFlow.value = current; refreshRecovery() }
    }

    fun selectDate(date: String, planId: String? = null) {
        runCatching { FitnessRules.date(date) }.onFailure { notify(it.message.orEmpty()); return }
        val next = FitnessDaySelection(date, planId)
        if (selection.value != next) { selection.value = next; focusItemId = null }
    }

    fun scheduled(): List<FitnessScheduledItem> = data.plans.filter { it.status == FitnessPlanStatus.Active && (selected.planId == null || it.id == selected.planId) }.flatMap { plan ->
        plan.day(selected.date)?.takeIf { it.kind == FitnessDayKind.Training }?.let { day ->
            day.activeItems.map { FitnessScheduledItem(plan, day, it, dayProgress[it.id] ?: 0) }
        }.orEmpty()
    }

    fun focus(item: FitnessScheduledItem) {
        focusItemId = item.item.id
        if (item.day.date == today) session = item.plan.id to item.day.date
    }

    fun complete(item: FitnessScheduledItem, count: Int = 1, backfill: Boolean = false, actualAt: Long? = null, after: () -> Unit = {}) {
        val request = FitnessCompletion(item.plan.id, item.day.date, item.item.id, fitnessId(), count,
            if (backfill) if (actualAt == null) FitnessTimeSource.DateOnly else FitnessTimeSource.User else FitnessTimeSource.Realtime,
            actualAt = actualAt,
            continuePreviousDay = session == (item.plan.id to item.day.date))
        write("已记录${item.item.exercise.name}", action = { repository.complete(request, clock) }, after = { events ->
            if (item.day.date == today) session = item.plan.id to item.day.date
            focusItemId = item.item.id
            refreshRecovery()
            val batchId = events.firstOrNull()?.batchId
            noticesChannel.trySend(FitnessNotice("已记录${events.size}${if (item.item.exercise.purpose == FitnessPurpose.Strength) "组" else "段"}") {
                if (batchId != null) revokeBatch(batchId) else events.firstOrNull()?.let { revoke(it) }
            })
            after()
        }, notice = false)
    }

    fun isContinuing(item: FitnessScheduledItem): Boolean = session == (item.plan.id to item.day.date)
    fun revoke(event: FitnessSetEvent) = write("本组已撤销", {
        repository.revoke(event.id, clock.millis())
        repository.itemEvents(event.itemId)
    }, after = { rows -> if (itemRecords != null) itemRecords = rows; refreshRecovery() })
    fun undoLast(item: FitnessScheduledItem) = write("上一组已撤销", {
        repository.itemEvents(item.item.id).lastOrNull { it.isValid() }?.let { repository.revoke(it.id, clock.millis()) }
    }, after = { refreshRecovery() })
    fun revokeBatch(id: String) {
        val itemId = itemRecords?.firstOrNull()?.itemId
        write("本批补记已撤销", {
            repository.revokeBatch(id, clock.millis())
            itemId?.let { repository.itemEvents(it) }
        }, after = { rows -> if (rows != null && itemRecords != null) itemRecords = rows; refreshRecovery() })
    }
    fun actual(event: FitnessSetEvent, value: FitnessActual, after: () -> Unit) = write("实际值已保存", {
        repository.updateActual(event.id, value); repository.itemEvents(event.itemId)
    }, after = { rows -> if (itemRecords != null) itemRecords = rows; after() })
    fun stop(row: FitnessScheduledItem) = write("剩余安排已结束，历史保留", { repository.stopItem(row.plan.id, row.day.date, row.item.id) })
    fun archive(plan: FitnessPlan) = write("计划状态已更新", { repository.archive(plan.id, plan.status != FitnessPlanStatus.Archived) })
    fun delete(plan: FitnessPlan) = write("计划及其记录已删除", { repository.deletePlan(plan.id) })
    fun favorite(id: String) = write("收藏已更新", { repository.favorite(id) })
    fun records(itemId: String) = write("", { repository.itemEvents(itemId) }, after = { itemRecords = it }, notice = false)
    fun closeItemRecords() { itemRecords = null }
    fun recordScope(scope: FitnessRecordScope?) { recordScope.value = scope }
    fun loadRecent() = write("", { repository.recentExercises(today) }, after = { recentExerciseIds = it }, notice = false)
    fun preferences(value: FitnessPreferences) = write("偏好已更新", { repository.preferences(value) })
    fun saveExercise(value: FitnessExercise, after: () -> Unit) = write("个人动作已保存", { repository.saveExercise(value) }, after = { after() })
    fun image(reader: () -> InputStream, after: (FitnessImage) -> Unit) = write("图片已保存在本机", { dependencies.backup.saveImage(reader()) }, after)

    fun setStats(filter: FitnessFilter, byExercise: Boolean = statsByExercise) {
        try {
            FitnessRules.date(filter.start); FitnessRules.date(filter.end)
            require(filter.start <= filter.end && filter.end <= today) { "统计范围无效或包含未来日期" }
            statsFilter = filter; statsFlow.value = filter; statsByExercise = byExercise; statsMode.value = byExercise
        } catch (error: IllegalArgumentException) { notify(error.message.orEmpty()) }
    }

    private fun refreshRecovery() {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            val parts = data.plans.filter { it.status == FitnessPlanStatus.Active }.flatMap { it.day(today)?.parts.orEmpty() }.distinct()
            try { recovery = withContext(ioDispatcher) { repository.recovery(parts, today) }; recoveryError = null }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { recoveryError = error.message ?: "恢复参考暂不可用" }
        }
    }

    fun requestEditor(request: FitnessEditorRequest, after: () -> Unit) {
        if (busy) return
        val existing = draft ?: data.draft
        if (existing != null && (request.planId != null && existing.editing && existing.plan.id == request.planId ||
            request.planId == null && request.exerciseId == null && request.sourceDay == null && !request.range && !existing.editing && existing.entrySource == "day" && existing.plan.start == request.date && existing.plan.end == request.date)) {
            resumeEditor({ applyEditorIntent(request); after() }, request.date, request.step)
        } else if (existing != null) pendingEditor = request
        else beginEditor(request, after)
    }

    fun cancelEditorRequest() { pendingEditor = null }
    fun replaceEditor(after: () -> Unit) { val request = pendingEditor ?: return; pendingEditor = null; beginEditor(request, after) }
    fun resumeEditor(after: () -> Unit, date: String? = null, step: Int? = null) {
        val existing = draft ?: data.draft ?: return
        pendingEditor = null
        draft = existing.copy(selectedDate = date?.takeIf { selected -> existing.plan.day(selected) != null } ?: existing.selectedDate, step = step ?: existing.step)
        draftDirty = true
        loadHistory(existing.plan.id, after)
    }

    private fun loadHistory(planId: String, after: () -> Unit) = write("", { repository.planEvents(planId) }, after = { history ->
        editorHistory = history; editorVisible = true; editorError = null; after()
    }, notice = false)

    private fun beginEditor(request: FitnessEditorRequest, after: () -> Unit) {
        try {
            val old = request.planId?.let { id -> data.plans.firstOrNull { it.id == id } }
            val plan = old ?: FitnessRules.newPlan(request.date, if (request.range) FitnessRules.date(request.date).plusDays(1).toString() else request.date)
            val date = request.date.takeIf { plan.day(it) != null } ?: plan.start
            draft = FitnessDraft(plan, old != null, date, request.step ?: if (old == null && !request.range) 1 else 0,
                clock.millis(), entrySource = if (request.exerciseId == null && request.sourceDay == null && !request.range && old == null) "day" else "other")
            draftDirty = true
            loadHistory(plan.id) { applyEditorIntent(request); queueDraft(); after() }
        } catch (error: Exception) { notify(error.message ?: "无法创建草稿") }
    }

    private fun applyEditorIntent(request: FitnessEditorRequest) {
        request.exerciseId?.let { id -> exercises.firstOrNull { it.id == id }?.let { exercise ->
            updateDay { day ->
                val existing = day.items.any { it.exercise.exerciseId == id }
                val added = FitnessRules.addExercise(day, exercise)
                if (existing) added else added.copy(items = added.items.map { if (it.exercise.exerciseId == id) it.copy(targetSets = request.targetSets, targetValue = request.targetValue) else it })
            }
        } }
        request.sourceDay?.let { source -> updateDraft { current ->
            current.copy(plan = FitnessRules.copyDays(current.plan, source, setOf(current.selectedDate), editorHistory, request.overwrite))
        } }
    }

    fun updateDraft(transform: (FitnessDraft) -> FitnessDraft): Boolean {
        val current = draft ?: return false
        return try {
            val next = transform(current)
            val itemIds = next.plan.days.flatMap { it.items }.map { it.id }.toSet()
            draft = next.copy(rawTargets = next.rawTargets.filterKeys { it in itemIds }); editorError = null; queueDraft()
            true
        }
        catch (error: IllegalArgumentException) { editorError = error.message; false }
    }
    fun updateDay(transform: (FitnessPlanDay) -> FitnessPlanDay) = updateDraft { current -> current.copy(plan = current.plan.copy(days = current.plan.days.map { if (it.date == current.selectedDate) transform(it) else it })) }
    fun updateTarget(id: String, sets: String? = null, value: String? = null) = updateDraft { current ->
        val item = current.plan.days.flatMap { it.items }.first { it.id == id }
        val previous = current.rawTargets[id] ?: FitnessTargetInput(item.targetSets?.toString().orEmpty(), item.targetValue?.toString().orEmpty())
        val raw = previous.copy(sets = sets?.take(12) ?: previous.sets, value = value?.take(12) ?: previous.value)
        current.copy(rawTargets = current.rawTargets + (id to raw), plan = current.plan.copy(days = current.plan.days.map { day ->
            day.copy(items = day.items.map { row -> if (row.id != id) row else row.copy(targetSets = raw.sets.toIntOrNull(), targetValue = raw.value.toIntOrNull()) })
        }))
    }
    fun toggleExercise(exercise: FitnessExercise) = updateDay { day ->
        val existing = day.activeItems.firstOrNull { it.exercise.exerciseId == exercise.id }
        if (existing == null) FitnessRules.addExercise(day, exercise) else FitnessRules.stop(day, existing.id)
    }

    fun nextStep(step: Int) {
        val current = draft ?: return
        try {
            if (step > 0) {
                FitnessRules.name(current.plan.name, "计划名称")
                val resized = FitnessRules.resize(current.plan, current.plan.start, current.plan.end, editorHistory)
                val removed = current.plan.days.filter { it.date !in FitnessRules.dates(current.plan.start, current.plan.end) && (it.items.isNotEmpty() || it.kind != FitnessDayKind.Unplanned) }
                if (removed.isNotEmpty() && resized != current.plan) { pendingRange = resized; return }
                updateDraft { it.copy(plan = resized, selectedDate = it.selectedDate.takeIf { date -> date in FitnessRules.dates(resized.start, resized.end) } ?: resized.start, step = step.coerceIn(0, 3)) }
            } else updateDraft { it.copy(step = 0) }
        } catch (error: IllegalArgumentException) { editorError = error.message }
    }
    fun applyRange() { val plan = pendingRange ?: return; pendingRange = null; updateDraft { it.copy(plan = plan, selectedDate = plan.start, step = 1) } }
    fun cancelRange() { pendingRange = null }

    private fun queueDraft() {
        draftDirty = true; draftJob?.cancel()
        draftJob = viewModelScope.launch { delay(400); saveDraftNow() }
    }
    private suspend fun saveDraftNow(): Boolean {
        val value = draft?.copy(savedAt = clock.millis()) ?: return true
        draftSaving = true
        return try {
            withContext(ioDispatcher) { repository.saveDraft(value) }
            if (draft?.plan == value.plan && draft?.rawTargets == value.rawTargets && draft?.step == value.step && draft?.selectedDate == value.selectedDate) {
                draft = value; draftDirty = false; editorError = null
            }
            true
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { editorError = "暂存失败，当前填写仍保留：${error.message}"; false }
        finally { draftSaving = false }
    }
    fun retryDraft() { draftJob?.cancel(); draftJob = viewModelScope.launch { saveDraftNow() } }
    fun leaveEditor(after: () -> Unit) {
        if (busy) return
        draftJob?.cancel()
        busy = true
        draftJob = viewModelScope.launch {
            try { if (saveDraftNow()) { editorVisible = false; after() } }
            finally { busy = false }
        }
    }
    fun discardDraft(after: () -> Unit) {
        draftJob?.cancel()
        write("草稿已放弃", { repository.saveDraft(null) }, after = { draft = null; draftDirty = false; editorVisible = false; editorError = null; after() })
    }
    fun savePlan(after: (FitnessPlan) -> Unit) {
        val value = draft ?: return
        draftJob?.cancel()
        write("计划已保存", { repository.savePlan(value.plan, clearDraft = true) }, after = {
            draft = null; draftDirty = false; editorVisible = false; after(value.plan)
        }, onError = { editorError = it })
    }

    fun template(name: String, after: () -> Unit = {}) {
        val day = draft?.let { it.plan.day(it.selectedDate) } ?: return
        write("个人模板已保存", { repository.saveTemplate(FitnessTemplate(fitnessId(), name, day.parts, day.activeItems)) }, after = { after() })
    }

    fun copySaved(source: FitnessPlanDay, targetPlanId: String, dates: Set<String>, overwrite: Boolean, after: () -> Unit) = write("安排已复制，记录从0开始", {
        val current = repository.currentData().plans.first { it.id == targetPlanId }
        require(current.status == FitnessPlanStatus.Active) { "目标计划已归档" }
        val events = repository.planEvents(targetPlanId)
        repository.savePlan(FitnessRules.copyDays(current, source, dates, events, overwrite))
    }, after = { after() })

    fun inspect(reader: () -> InputStream) = write("备份已预检，请确认替换", { dependencies.backup.inspect(reader()) }, after = { pendingRestore?.close(); pendingRestore = it })
    fun dismissRestore() { if (!busy) { pendingRestore?.close(); pendingRestore = null } }
    fun restore(after: () -> Unit) {
        val archive = pendingRestore ?: return
        draftJob?.cancel()
        write("健身资料已恢复", { dependencies.backup.restore(archive) }, after = {
            pendingRestore = null; draft = null; draftDirty = false; editorVisible = false; focusItemId = null; session = null
            selectDate(today); after()
        })
    }
    fun export(writer: () -> OutputStream) = write("健身备份已写出", {
        writer().use { dependencies.backup.export(it, catalog?.version.orEmpty(), clock.millis()) }
    })

    fun notify(text: String) { noticesChannel.trySend(FitnessNotice(text)) }
    private fun <T> write(message: String, action: suspend () -> T, after: (T) -> Unit = {},
        onError: ((String) -> Unit)? = null, notice: Boolean = true) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { action() }
                after(result)
                if (notice && message.isNotBlank()) noticesChannel.send(FitnessNotice(message))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { val text = error.message ?: "保存失败，请重试"; if (onError != null) onError(text) else noticesChannel.send(FitnessNotice(text)) }
            finally { busy = false }
        }
    }
    override fun onCleared() { pendingRestore?.close(); super.onCleared() }
}

class FitnessViewModelFactory(private val dependencies: FitnessFeatureDependencies) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FitnessViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return FitnessViewModel(dependencies) as T
    }
}

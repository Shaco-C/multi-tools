package com.pockettoolbox.feature.diet.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.feature.diet.domain.*
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MealDraft(
    val mealId: String? = null,
    val brandId: String = "",
    val foodId: String = "",
    val eatenOn: String,
    val typeId: String,
    val note: String = "",
    val occurredAt: Long,
    val offsetSeconds: Int,
)

data class DietNotice(val text: String, val undo: (() -> Unit)? = null)

class DietViewModel(
    private val repository: DietRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    var data by mutableStateOf(DietData()); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf<MealDraft?>(null); private set
    var draftVisible by mutableStateOf(false); private set
    var draftError by mutableStateOf<String?>(null); private set
    var pendingRestore by mutableStateOf<DietBackupDocument?>(null); private set
    private val noticeChannel = Channel<DietNotice>(Channel.BUFFERED)
    val notices = noticeChannel.receiveAsFlow()

    init { load() }
    fun load() {
        viewModelScope.launch {
            try {
                loadError = null
                withContext(ioDispatcher) { repository.initialize() }
                repository.data.collect { data = it; loaded = true }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { loadError = error.message ?: "本地数据读取失败" }
        }
    }

    private fun event(mealId: String, typeId: String, at: Long = clock.millis(), offset: Int = clock.instant().atZone(clock.zone).offset.totalSeconds) =
        DiscomfortEvent(UUID.randomUUID().toString(), mealId, typeId, at, offset)

    fun openDraft(brandId: String? = null, foodId: String? = null, edit: DietMeal? = null) {
        val selectedBrand = edit?.brandId ?: brandId ?: data.activeMeal()?.brandId
        val brand = data.brands.firstOrNull { it.id == selectedBrand && !it.archived } ?: data.brands.firstOrNull { !it.archived }
        val selectedFood = edit?.foodId ?: foodId ?: data.activeMeal()?.foodId
        val food = data.foods.firstOrNull { it.brandId == brand?.id && it.id == selectedFood && !it.archived }
            ?: data.foods.firstOrNull { it.brandId == brand?.id && !it.archived }
        draft = MealDraft(edit?.id, edit?.brandId ?: brand?.id.orEmpty(), edit?.foodId ?: food?.id.orEmpty(),
            edit?.eatenOn ?: LocalDate.now(clock).minusDays(1).toString(), data.defaultTypeId, edit?.note.orEmpty(),
            clock.millis(), clock.instant().atZone(clock.zone).offset.totalSeconds)
        draftVisible = true; draftError = null
    }
    fun updateDraft(value: MealDraft) { draft = value; draftError = null }
    fun hideDraftForManage() { draftVisible = false }
    fun resumeDraft() { draftVisible = true; draftError = null }
    fun cancelDraft() { if (!busy) { draft = null; draftVisible = false; draftError = null } }

    fun saveDraft() {
        val value = draft ?: return
        val id = value.mealId ?: UUID.randomUUID().toString()
        val meal = DietMeal(id, value.brandId, value.foodId, value.eatenOn, value.note.trim())
        val event = event(id, value.typeId, value.occurredAt, value.offsetSeconds)
        write(if (value.mealId == null) "已记录${data.typeName(value.typeId)}" else "饮食信息已更新",
            transform = { if (value.mealId == null) DietRules.recordIncident(it, meal, event) else DietRules.editMeal(it, meal) },
            undo = if (value.mealId == null) ({ current -> DietRules.removeEvent(current, event.id) }) else null,
            after = { draft = null; draftVisible = false; draftError = null },
            onError = { draftError = it })
    }

    fun append() {
        val meal = data.activeMeal() ?: return
        val event = event(meal.id, data.defaultTypeId)
        write("已记录${data.typeName(event.typeId)}", { DietRules.append(it, event) }, { DietRules.removeEvent(it, event.id) })
    }
    fun deleteEvent(event: DiscomfortEvent) {
        val meal = data.meals.first { it.id == event.mealId }
        write("已删除这次不适", { DietRules.removeEvent(it, event.id) }, { DietRules.restoreEvent(it, meal, event) })
    }
    fun editEvent(event: DiscomfortEvent, after: () -> Unit, onError: (String) -> Unit) {
        if (event.occurredAt > clock.millis() + 60_000) { onError("请选择已经发生的时间"); return }
        write("不适时间和类型已更新", { DietRules.editEvent(it, event) }, after = after, onError = onError)
    }
    fun finish(meal: DietMeal, finished: Boolean) = write(if (finished) "本次记录已结束" else "已恢复，可继续追加",
        { DietRules.finish(it, meal.id, finished) }, { DietRules.finish(it, meal.id, !finished) })
    fun selectMeal(id: String) = write("已切换关联饮食", { DietRules.selectMeal(it, id) })
    fun setDefault(id: String) = write("默认不适类型已更新", { DietRules.setDefault(it, id) })
    fun toggle(kind: CatalogKind, id: String) = write("选项已更新，历史记录保留", { DietRules.toggleCatalog(it, kind, id) })
    fun saveCatalog(kind: CatalogKind, id: String?, name: String, brandId: String?, after: () -> Unit, onError: (String) -> Unit) =
        write("已保存，以后直接选择", { DietRules.saveCatalog(it, kind, id ?: UUID.randomUUID().toString(), name, brandId) }, after = after, onError = onError)

    private fun write(
        message: String,
        transform: (DietData) -> DietData,
        undo: ((DietData) -> DietData)? = null,
        after: () -> Unit = {},
        onError: ((String) -> Unit)? = null,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                data = withContext(ioDispatcher) { repository.change(transform) }
                after()
                noticeChannel.send(DietNotice(message, undo?.let { change -> { write("已撤销", change) } }))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val text = error.message ?: "保存失败，请重试"
                if (onError != null) onError(text) else noticeChannel.send(DietNotice(text))
            } finally { busy = false }
        }
    }

    fun export(json: Boolean, writer: (String) -> Unit) = fileWork(if (json) "JSON 备份已导出" else "CSV 明细已导出") {
        val snapshot = repository.snapshot()
        writer(if (json) DietBackupCodec.encode(snapshot, clock.millis()) else DietBackupCodec.csv(snapshot))
    }
    fun inspect(reader: () -> String) = fileWork("备份已校验，请确认是否替换") {
        val document = DietBackupCodec.decode(reader())
        withContext(Dispatchers.Main) { pendingRestore = document }
    }
    fun dismissRestore() { if (!busy) pendingRestore = null }
    fun restore() {
        val document = pendingRestore ?: return
        fileWork("饮食不适数据已恢复") {
            repository.replace(document.data)
            withContext(Dispatchers.Main) { data = document.data; pendingRestore = null; draft = null; draftVisible = false }
        }
    }
    private fun fileWork(message: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { withContext(ioDispatcher) { block() }; noticeChannel.send(DietNotice(message)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { noticeChannel.send(DietNotice(error.message ?: "文件操作失败")) }
            finally { busy = false }
        }
    }
}

class DietViewModelFactory(private val repository: DietRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DietViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return DietViewModel(repository) as T
    }
}

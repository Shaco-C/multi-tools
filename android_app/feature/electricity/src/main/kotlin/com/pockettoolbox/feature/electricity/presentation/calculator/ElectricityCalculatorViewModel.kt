package com.pockettoolbox.feature.electricity.presentation.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.domain.ElectricityAllocation
import com.pockettoolbox.feature.electricity.domain.ElectricityAllocationCalculator
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.MeterReadingInput
import com.pockettoolbox.feature.electricity.domain.SaveElectricityBillRequest
import com.pockettoolbox.feature.electricity.domain.UpdateElectricityBillRequest
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class MeterDraft(
    val id: String,
    val label: String,
    val isOwner: Boolean,
    val previousReading: String,
    val currentReading: String,
)

data class ElectricityCalculatorUiState(
    val billingMonth: String = YearMonth.now().toString(),
    val totalAmount: String = "",
    val meters: List<MeterDraft> = initialMeters(),
    val allocation: ElectricityAllocation? = null,
    val validationMessage: String? = null,
    val savedBillId: Long? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val editingBillId: Long? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val isLoadingPreviousReadings: Boolean = false,
    val previousReadingsSourceMonth: YearMonth? = null,
    val previousReadingsNotice: String? = null,
    val previousReadingsError: String? = null,
    val pendingRemovalId: String? = null,
    val isDirty: Boolean = false,
    val showExitConfirmation: Boolean = false,
) {
    companion object {
        fun initialMeters() = listOf(
            MeterDraft(
                id = "owner",
                label = "我的电表",
                isOwner = true,
                previousReading = "",
                currentReading = "",
            ),
            MeterDraft(
                id = "meter-2",
                label = "第 2 户",
                isOwner = false,
                previousReading = "",
                currentReading = "",
            ),
        )
    }
}

class ElectricityCalculatorViewModel(
    private val repository: ElectricityBillRepository,
    private val editingBillId: Long? = null,
    private val initialBillingMonth: YearMonth = YearMonth.now(),
) : ViewModel() {
    private val calculator = ElectricityAllocationCalculator()
    private var nextMeterNumber = 3
    private var loadedNote: String? = null
    private var previousReadingsJob: Job? = null
    private var hasUserEditedMeterData = false
    var uiState by mutableStateOf(
        ElectricityCalculatorUiState(
            billingMonth = initialBillingMonth.toString(),
            editingBillId = editingBillId,
            isLoading = editingBillId != null,
        ),
    )
        private set

    init {
        if (editingBillId == null) {
            recalculate()
            loadPreviousReadings(initialBillingMonth)
        } else {
            loadBill(editingBillId)
        }
    }

    fun updateBillingMonth(value: String) {
        if (uiState.isSaving || uiState.isLoading) return
        if (value.length > MAX_MONTH_LENGTH) return
        previousReadingsJob?.cancel()
        val mayReplaceMeters = !hasUserEditedMeterData && uiState.editingBillId == null
        val meters = if (mayReplaceMeters) ElectricityCalculatorUiState.initialMeters() else uiState.meters
        if (mayReplaceMeters) nextMeterNumber = 3
        uiState = uiState.copy(
            billingMonth = value,
            meters = meters,
            savedBillId = null,
            saveError = null,
            isDirty = true,
            isLoadingPreviousReadings = false,
            previousReadingsSourceMonth = null,
            previousReadingsNotice = if (mayReplaceMeters) null else "已保留当前手动输入，不会自动覆盖读数",
            previousReadingsError = null,
        )
        recalculate()
        if (mayReplaceMeters) {
            runCatching { YearMonth.parse(value) }.getOrNull()?.let(::loadPreviousReadings)
        }
    }

    fun updateTotalAmount(value: String) {
        if (uiState.isSaving || uiState.isLoading) return
        if (value.length > MAX_NUMERIC_INPUT_LENGTH) return
        uiState = uiState.copy(totalAmount = value, savedBillId = null, saveError = null, isDirty = true)
        recalculate()
    }

    fun updateMeter(id: String, transform: (MeterDraft) -> MeterDraft) {
        if (uiState.isSaving || uiState.isLoading) return
        val current = uiState.meters.firstOrNull { it.id == id } ?: return
        val updated = transform(current)
        if (updated.label.length > MAX_LABEL_LENGTH) return
        if (updated.previousReading.length > MAX_NUMERIC_INPUT_LENGTH) return
        if (updated.currentReading.length > MAX_NUMERIC_INPUT_LENGTH) return

        val wasLoadingPreviousReadings = uiState.isLoadingPreviousReadings
        previousReadingsJob?.cancel()
        hasUserEditedMeterData = true
        uiState = uiState.copy(
            meters = uiState.meters.map { meter ->
                if (meter.id == id) updated else meter
            },
            savedBillId = null,
            saveError = null,
            isDirty = true,
            isLoadingPreviousReadings = false,
            previousReadingsNotice = if (wasLoadingPreviousReadings) {
                "已保留当前手动输入，不会自动覆盖读数"
            } else {
                uiState.previousReadingsNotice
            },
            previousReadingsError = null,
        )
        recalculate()
    }

    fun addMeter() {
        if (uiState.isSaving || uiState.isLoading) return
        if (uiState.meters.size >= MAX_HOUSEHOLDS) return
        previousReadingsJob?.cancel()
        hasUserEditedMeterData = true
        val number = nextMeterNumber++
        uiState = uiState.copy(
            meters = uiState.meters + MeterDraft(
                id = UUID.randomUUID().toString(),
                label = "第 $number 户",
                isOwner = false,
                previousReading = "",
                currentReading = "",
            ),
            savedBillId = null,
            saveError = null,
            isDirty = true,
            isLoadingPreviousReadings = false,
            previousReadingsError = null,
        )
        recalculate()
    }

    fun requestRemoveMeter(id: String) {
        if (uiState.isSaving || uiState.isLoading) return
        val target = uiState.meters.firstOrNull { it.id == id } ?: return
        if (target.isOwner || uiState.meters.size == 1) return
        val wasLoadingPreviousReadings = uiState.isLoadingPreviousReadings
        previousReadingsJob?.cancel()
        uiState = uiState.copy(
            pendingRemovalId = id,
            isLoadingPreviousReadings = false,
            previousReadingsNotice = if (wasLoadingPreviousReadings) {
                "已暂停自动带入，当前电表列表保持不变"
            } else {
                uiState.previousReadingsNotice
            },
            previousReadingsError = null,
        )
    }

    fun requestRemoveLastMeter() {
        uiState.meters.lastOrNull { !it.isOwner }?.let { requestRemoveMeter(it.id) }
    }

    fun dismissRemoval() {
        uiState = uiState.copy(pendingRemovalId = null)
    }

    fun confirmRemoval() {
        if (uiState.isSaving || uiState.isLoading) return
        val id = uiState.pendingRemovalId ?: return
        val target = uiState.meters.firstOrNull { it.id == id } ?: run {
            dismissRemoval()
            return
        }
        if (target.isOwner || uiState.meters.size == 1) {
            dismissRemoval()
            return
        }
        previousReadingsJob?.cancel()
        hasUserEditedMeterData = true
        uiState = uiState.copy(
            meters = uiState.meters.filterNot { it.id == id },
            savedBillId = null,
            saveError = null,
            pendingRemovalId = null,
            isDirty = true,
            isLoadingPreviousReadings = false,
            previousReadingsError = null,
        )
        recalculate()
    }

    fun saveBill() {
        val allocation = uiState.allocation ?: return
        if (uiState.isSaving || uiState.savedBillId != null) return
        val billingMonth = runCatching { YearMonth.parse(uiState.billingMonth) }.getOrNull() ?: return

        uiState = uiState.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            try {
                val billId = uiState.editingBillId
                val saved = if (billId == null) {
                    repository.saveNewBill(
                        SaveElectricityBillRequest(
                            billingMonth = billingMonth,
                            allocation = allocation,
                        ),
                    )
                } else {
                    repository.updateBill(
                        UpdateElectricityBillRequest(
                            id = billId,
                            billingMonth = billingMonth,
                            allocation = allocation,
                            note = loadedNote,
                        ),
                    )
                }
                uiState = uiState.copy(
                    savedBillId = saved.id,
                    isSaving = false,
                    saveError = null,
                    isDirty = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isSaving = false,
                    saveError = error.message ?: "账单保存失败，请稍后重试",
                )
            }
        }
    }

    fun requestExitConfirmation() {
        uiState = uiState.copy(showExitConfirmation = true)
    }

    fun dismissExitConfirmation() {
        uiState = uiState.copy(showExitConfirmation = false)
    }

    fun retryLoad() {
        editingBillId?.let(::loadBill)
    }

    fun retryPreviousReadings() {
        if (uiState.editingBillId != null || hasUserEditedMeterData) return
        runCatching { YearMonth.parse(uiState.billingMonth) }.getOrNull()?.let(::loadPreviousReadings)
    }

    private fun loadPreviousReadings(billingMonth: YearMonth) {
        if (editingBillId != null || hasUserEditedMeterData) return
        previousReadingsJob?.cancel()
        uiState = uiState.copy(
            isLoadingPreviousReadings = true,
            previousReadingsNotice = null,
            previousReadingsError = null,
        )
        previousReadingsJob = viewModelScope.launch {
            try {
                val previousBill = repository.getPreviousMonthBill(billingMonth)
                // 查询返回时月份可能已经切换，或用户已经手动输入；旧结果不得覆盖新表单。
                if (uiState.billingMonth != billingMonth.toString() || hasUserEditedMeterData) return@launch

                if (previousBill == null) {
                    uiState = uiState.copy(
                        isLoadingPreviousReadings = false,
                        previousReadingsSourceMonth = null,
                        previousReadingsNotice = "${billingMonth.minusMonths(1).year} 年 ${billingMonth.minusMonths(1).monthValue} 月没有账单，请手动填写上期读数",
                    )
                    return@launch
                }
                check(previousBill.billingMonth == billingMonth.minusMonths(1)) {
                    "读取到的账单不是所选月份的上一个自然月"
                }

                val meters = previousBill.shares.map { share ->
                    MeterDraft(
                        id = share.meterKey,
                        label = share.label,
                        isOwner = share.isOwner,
                        previousReading = share.currentReading.toPlainString(),
                        currentReading = "",
                    )
                }
                updateNextMeterNumber(meters)
                uiState = uiState.copy(
                    meters = meters,
                    isLoadingPreviousReadings = false,
                    previousReadingsSourceMonth = previousBill.billingMonth,
                    previousReadingsNotice = "已带入 ${previousBill.billingMonth.year} 年 ${previousBill.billingMonth.monthValue} 月的本期读数",
                    previousReadingsError = null,
                )
                recalculate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (uiState.billingMonth != billingMonth.toString() || hasUserEditedMeterData) return@launch
                uiState = uiState.copy(
                    isLoadingPreviousReadings = false,
                    previousReadingsError = error.message ?: "上月读数读取失败，可手动填写或重试",
                )
            }
        }
    }

    private fun loadBill(billId: Long) {
        uiState = uiState.copy(isLoading = true, loadError = null)
        viewModelScope.launch {
            try {
                val bill = repository.getBill(billId)
                    ?: throw IllegalStateException("未找到需要编辑的账单")
                loadedNote = bill.note
                val meters = bill.shares.map { share ->
                    MeterDraft(
                        id = share.meterKey,
                        label = share.label,
                        isOwner = share.isOwner,
                        previousReading = share.previousReading.toPlainString(),
                        currentReading = share.currentReading.toPlainString(),
                    )
                }
                updateNextMeterNumber(meters)
                uiState = ElectricityCalculatorUiState(
                    billingMonth = bill.billingMonth.toString(),
                    totalAmount = bill.totalAmount.formatYuan(),
                    meters = meters,
                    editingBillId = bill.id,
                    isLoading = false,
                    isDirty = false,
                )
                recalculate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    loadError = error.message ?: "账单读取失败",
                )
            }
        }
    }

    private fun recalculate() {
        val result = runCatching {
            require(uiState.billingMonth.isNotBlank()) { "请选择账单月份" }
            runCatching { YearMonth.parse(uiState.billingMonth) }
                .getOrElse { throw IllegalArgumentException("账单月份格式应为 YYYY-MM") }
            calculator.calculate(
                totalAmount = Money.parseYuan(uiState.totalAmount),
                meters = uiState.meters.map { draft ->
                    MeterReadingInput(
                        id = draft.id,
                        label = draft.label,
                        isOwner = draft.isOwner,
                        previousReading = draft.previousReading.toReading("请输入${draft.label}的上期读数"),
                        currentReading = draft.currentReading.toReading("请输入${draft.label}的本期读数"),
                    )
                },
            )
        }

        uiState = result.fold(
            onSuccess = { uiState.copy(allocation = it, validationMessage = null) },
            onFailure = { uiState.copy(allocation = null, validationMessage = it.message) },
        )
    }

    private fun updateNextMeterNumber(meters: List<MeterDraft>) {
        // 删除中间住户后编号仍保持递增，避免再次新增时出现同名的“第 N 户”。
        nextMeterNumber = maxOf(
            meters.size + 1,
            meters.mapNotNull { DEFAULT_METER_LABEL.matchEntire(it.label)?.groupValues?.get(1)?.toIntOrNull() }
                .maxOrNull()
                ?.plus(1)
                ?: 2,
        )
    }

    private fun String.toReading(emptyMessage: String): BigDecimal {
        val normalized = trim()
        require(normalized.isNotEmpty()) { emptyMessage }
        require(!normalized.startsWith("-")) { "读数不能为负数" }
        require(READING_PATTERN.matches(normalized)) { "读数格式不正确" }
        return normalized.toBigDecimalOrNull() ?: throw IllegalArgumentException("读数格式不正确")
    }

    private companion object {
        const val MAX_HOUSEHOLDS = 20
        const val MAX_MONTH_LENGTH = 7
        const val MAX_LABEL_LENGTH = 30
        const val MAX_NUMERIC_INPUT_LENGTH = 32
        val READING_PATTERN = Regex("^\\d+(?:\\.\\d+)?$")
        val DEFAULT_METER_LABEL = Regex("^第 (\\d+) 户$")
    }
}

internal class ElectricityCalculatorViewModelFactory(
    private val repository: ElectricityBillRepository,
    private val editingBillId: Long? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ElectricityCalculatorViewModel::class.java)) {
            "不支持的 ViewModel：${modelClass.name}"
        }
        return ElectricityCalculatorViewModel(repository, editingBillId) as T
    }
}

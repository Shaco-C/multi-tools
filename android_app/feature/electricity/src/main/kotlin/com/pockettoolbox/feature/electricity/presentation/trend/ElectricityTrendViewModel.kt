package com.pockettoolbox.feature.electricity.presentation.trend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ElectricityTrendPoint(
    val billId: Long,
    val billingMonth: YearMonth,
    val ownerAmount: Money,
)

data class ElectricityTrendUiState(
    val points: List<ElectricityTrendPoint> = emptyList(),
    val latestAmount: Money? = null,
    val averageAmount: Money? = null,
    val highestAmount: Money? = null,
    val latestChangeCents: Long? = null,
    val latestChangePercent: BigDecimal? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class ElectricityTrendViewModel(
    private val repository: ElectricityBillRepository,
) : ViewModel() {
    var uiState by mutableStateOf(ElectricityTrendUiState())
        private set

    private var observationJob: Job? = null

    init {
        observeTrend()
    }

    fun retry() {
        observeTrend()
    }

    private fun observeTrend() {
        observationJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        observationJob = viewModelScope.launch {
            repository.observeBillSummaries()
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "费用趋势读取失败",
                    )
                }
                .collect { summaries ->
                    val points = summaries
                        .sortedBy { it.billingMonth }
                        .map { summary ->
                            ElectricityTrendPoint(
                                billId = summary.id,
                                billingMonth = summary.billingMonth,
                                ownerAmount = summary.ownerAmount,
                            )
                        }
                    uiState = points.toUiState()
                }
        }
    }

    private fun List<ElectricityTrendPoint>.toUiState(): ElectricityTrendUiState {
        if (isEmpty()) return ElectricityTrendUiState(isLoading = false)

        val latest = last().ownerAmount
        val averageCents = fold(BigDecimal.ZERO) { total, point ->
            total.add(BigDecimal.valueOf(point.ownerAmount.cents))
        }.divide(BigDecimal.valueOf(size.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
        val previous = getOrNull(lastIndex - 1)?.ownerAmount
        val changeCents = previous?.let { Math.subtractExact(latest.cents, it.cents) }
        val changePercent = previous
            ?.takeIf { it.cents != 0L }
            ?.let {
                BigDecimal.valueOf(changeCents!!)
                    .divide(BigDecimal.valueOf(it.cents), 4, RoundingMode.HALF_UP)
                    .movePointRight(2)
            }

        return ElectricityTrendUiState(
            points = this,
            latestAmount = latest,
            averageAmount = Money.ofCents(averageCents),
            highestAmount = maxBy { it.ownerAmount.cents }.ownerAmount,
            latestChangeCents = changeCents,
            latestChangePercent = changePercent,
            isLoading = false,
        )
    }
}

internal class ElectricityTrendViewModelFactory(
    private val repository: ElectricityBillRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ElectricityTrendViewModel::class.java)) {
            "不支持的 ViewModel：${modelClass.name}"
        }
        return ElectricityTrendViewModel(repository) as T
    }
}

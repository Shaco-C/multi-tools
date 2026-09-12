package com.pockettoolbox.feature.electricity.presentation.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityBillSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ElectricityHistoryUiState(
    val bills: List<ElectricityBillSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class ElectricityHistoryViewModel(
    private val repository: ElectricityBillRepository,
) : ViewModel() {
    var uiState by mutableStateOf(ElectricityHistoryUiState())
        private set

    private var observationJob: Job? = null

    init {
        observeHistory()
    }

    fun retry() {
        observeHistory()
    }

    private fun observeHistory() {
        observationJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        observationJob = viewModelScope.launch {
            repository.observeBillSummaries()
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "历史账单读取失败",
                    )
                }
                .collect { bills ->
                    uiState = ElectricityHistoryUiState(
                        bills = bills,
                        isLoading = false,
                    )
                }
        }
    }
}

internal class ElectricityHistoryViewModelFactory(
    private val repository: ElectricityBillRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ElectricityHistoryViewModel::class.java)) {
            "不支持的 ViewModel：${modelClass.name}"
        }
        return ElectricityHistoryViewModel(repository) as T
    }
}

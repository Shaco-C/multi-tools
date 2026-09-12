package com.pockettoolbox.feature.electricity.presentation.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ElectricityBillDetailUiState(
    val bill: ElectricityBillRecord? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val wasDeleted: Boolean = false,
)

class ElectricityBillDetailViewModel(
    private val billId: Long,
    private val repository: ElectricityBillRepository,
) : ViewModel() {
    var uiState by mutableStateOf(ElectricityBillDetailUiState())
        private set

    private var observationJob: Job? = null

    init {
        observeBill()
    }

    fun retry() {
        observeBill()
    }

    fun requestDelete() {
        if (uiState.bill != null && !uiState.isDeleting) {
            uiState = uiState.copy(showDeleteConfirmation = true, deleteError = null)
        }
    }

    fun dismissDelete() {
        if (!uiState.isDeleting) {
            uiState = uiState.copy(showDeleteConfirmation = false)
        }
    }

    fun confirmDelete() {
        if (uiState.isDeleting || uiState.bill == null) return
        uiState = uiState.copy(isDeleting = true, deleteError = null)
        viewModelScope.launch {
            try {
                if (!repository.deleteBill(billId)) {
                    throw IllegalStateException("账单不存在或已经被删除")
                }
                uiState = uiState.copy(
                    isDeleting = false,
                    showDeleteConfirmation = false,
                    wasDeleted = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isDeleting = false,
                    showDeleteConfirmation = false,
                    deleteError = error.message ?: "账单删除失败，请稍后重试",
                )
            }
        }
    }

    private fun observeBill() {
        observationJob?.cancel()
        uiState = uiState.copy(isLoading = true, loadError = null)
        observationJob = viewModelScope.launch {
            repository.observeBill(billId)
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        loadError = error.message ?: "账单读取失败",
                    )
                }
                .collect { bill ->
                    if (bill == null) {
                        uiState = if (uiState.isDeleting || uiState.wasDeleted) {
                            uiState.copy(isLoading = false, wasDeleted = true)
                        } else {
                            uiState.copy(isLoading = false, loadError = "账单不存在或已经被删除")
                        }
                    } else {
                        uiState = uiState.copy(
                            bill = bill,
                            isLoading = false,
                            loadError = null,
                        )
                    }
                }
        }
    }
}

internal class ElectricityBillDetailViewModelFactory(
    private val billId: Long,
    private val repository: ElectricityBillRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ElectricityBillDetailViewModel::class.java)) {
            "不支持的 ViewModel：${modelClass.name}"
        }
        return ElectricityBillDetailViewModel(billId, repository) as T
    }
}

package com.pockettoolbox.feature.electricity.presentation.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.core.backup.BackupInspection
import com.pockettoolbox.feature.electricity.domain.ElectricityCsvExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ElectricityBackupUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val pendingInspection: BackupInspection? = null,
)

class ElectricityBackupViewModel(
    private val contributor: BackupContributor,
    private val csvExporter: ElectricityCsvExporter,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    var uiState by mutableStateOf(ElectricityBackupUiState())
        private set

    private var pendingJson: String? = null

    fun exportBackup(writeFile: suspend (String) -> Unit) {
        if (uiState.isWorking) return
        uiState = uiState.copy(isWorking = true, message = null, errorMessage = null)
        viewModelScope.launch {
            try {
                val json = withContext(workerDispatcher) { contributor.createBackupJson() }
                writeFile(json)
                uiState = uiState.copy(isWorking = false, message = "电费备份已保存")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isWorking = false,
                    errorMessage = error.message ?: "备份保存失败，请重试",
                )
            }
        }
    }

    fun exportCsv(writeFile: suspend (String) -> Unit) {
        if (uiState.isWorking) return
        uiState = uiState.copy(isWorking = true, message = null, errorMessage = null)
        viewModelScope.launch {
            try {
                val csv = withContext(workerDispatcher) { csvExporter.createCsv() }
                writeFile(csv)
                uiState = uiState.copy(isWorking = false, message = "电费 CSV 已保存")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isWorking = false,
                    errorMessage = error.message ?: "CSV 保存失败，请重试",
                )
            }
        }
    }

    fun inspectImport(readFile: suspend () -> String) {
        if (uiState.isWorking) return
        uiState = uiState.copy(isWorking = true, message = null, errorMessage = null)
        viewModelScope.launch {
            try {
                val json = readFile()
                val inspection = withContext(workerDispatcher) { contributor.inspectBackupJson(json) }
                pendingJson = json
                uiState = uiState.copy(
                    isWorking = false,
                    pendingInspection = inspection,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                pendingJson = null
                uiState = uiState.copy(
                    isWorking = false,
                    pendingInspection = null,
                    errorMessage = error.message ?: "备份读取失败，请检查文件",
                )
            }
        }
    }

    fun dismissRestore() {
        if (uiState.isWorking) return
        pendingJson = null
        uiState = uiState.copy(pendingInspection = null)
    }

    fun confirmRestore() {
        val json = pendingJson ?: return
        if (uiState.isWorking || uiState.pendingInspection == null) return
        uiState = uiState.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val restored = withContext(workerDispatcher) { contributor.restoreBackupJson(json) }
                pendingJson = null
                uiState = ElectricityBackupUiState(
                    message = "已恢复 ${restored.recordCount} 个月的电费账单",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isWorking = false,
                    errorMessage = "恢复失败，原有数据未被替换：${error.message ?: "未知错误"}",
                )
            }
        }
    }
}

internal class ElectricityBackupViewModelFactory(
    private val contributor: BackupContributor,
    private val csvExporter: ElectricityCsvExporter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ElectricityBackupViewModel::class.java)) {
            "不支持的 ViewModel：${modelClass.name}"
        }
        return ElectricityBackupViewModel(contributor, csvExporter) as T
    }
}

package com.pockettoolbox.feature.electricity.presentation.backup

import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.core.backup.BackupInspection
import com.pockettoolbox.core.backup.InvalidBackupException
import com.pockettoolbox.feature.electricity.presentation.calculator.MainDispatcherRule
import com.pockettoolbox.feature.electricity.domain.ElectricityCsvExporter
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ElectricityBackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `导出成功后写入系统选择的文件并显示结果`() {
        val contributor = FakeContributor()
        val viewModel = ElectricityBackupViewModel(contributor, FakeCsvExporter(), Dispatchers.Main)
        var written: String? = null

        viewModel.exportBackup { written = it }

        assertEquals("{backup}", written)
        assertFalse(viewModel.uiState.isWorking)
        assertTrue(viewModel.uiState.message?.contains("已保存") == true)
    }

    @Test
    fun `CSV导出成功后写入文件并显示独立结果`() {
        val viewModel = ElectricityBackupViewModel(FakeContributor(), FakeCsvExporter(), Dispatchers.Main)
        var written: String? = null

        viewModel.exportCsv { written = it }

        assertEquals("csv-content", written)
        assertTrue(viewModel.uiState.message?.contains("CSV 已保存") == true)
    }

    @Test
    fun `导入必须先预览并再次确认才执行恢复`() {
        val contributor = FakeContributor()
        val viewModel = ElectricityBackupViewModel(contributor, FakeCsvExporter(), Dispatchers.Main)

        viewModel.inspectImport { "{backup}" }

        assertNotNull(viewModel.uiState.pendingInspection)
        assertEquals(0, contributor.restoreCount)

        viewModel.confirmRestore()

        assertEquals(1, contributor.restoreCount)
        assertNull(viewModel.uiState.pendingInspection)
        assertTrue(viewModel.uiState.message?.contains("2 个月") == true)
    }

    @Test
    fun `无效文件不会进入确认阶段也不会调用恢复`() {
        val contributor = FakeContributor(inspectFailure = InvalidBackupException("文件无效"))
        val viewModel = ElectricityBackupViewModel(contributor, FakeCsvExporter(), Dispatchers.Main)

        viewModel.inspectImport { "bad" }

        assertNull(viewModel.uiState.pendingInspection)
        assertEquals(0, contributor.restoreCount)
        assertTrue(viewModel.uiState.errorMessage?.contains("文件无效") == true)
    }

    private class FakeContributor(
        private val inspectFailure: Exception? = null,
    ) : BackupContributor {
        override val toolId = "electricity"
        override val schemaVersion = 1
        var restoreCount = 0
            private set

        override suspend fun createBackupJson(): String = "{backup}"

        override fun inspectBackupJson(json: String): BackupInspection {
            inspectFailure?.let { throw it }
            return inspection()
        }

        override suspend fun restoreBackupJson(json: String): BackupInspection {
            restoreCount += 1
            return inspection()
        }

        private fun inspection() = BackupInspection(
            toolId = toolId,
            schemaVersion = schemaVersion,
            recordCount = 2,
            exportedAt = 1_757_592_000_000L,
        )
    }

    private class FakeCsvExporter : ElectricityCsvExporter {
        override suspend fun createCsv(): String = "csv-content"
    }
}

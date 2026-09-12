package com.pockettoolbox.feature.electricity.presentation.detail

import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityBillSummary
import com.pockettoolbox.feature.electricity.domain.ElectricityShareRecord
import com.pockettoolbox.feature.electricity.domain.SaveElectricityBillRequest
import com.pockettoolbox.feature.electricity.domain.SavedElectricityBill
import com.pockettoolbox.feature.electricity.domain.UpdateElectricityBillRequest
import com.pockettoolbox.feature.electricity.presentation.calculator.MainDispatcherRule
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ElectricityBillDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `详情页订阅指定账单并响应后续更新`() {
        val repository = DetailRepository(record())
        val viewModel = ElectricityBillDetailViewModel(7L, repository)

        assertFalse(viewModel.uiState.isLoading)
        assertEquals(10_000L, viewModel.uiState.bill?.totalAmount?.cents)

        repository.source.value = record().copy(totalAmount = Money.ofCents(12_000L))
        assertEquals(12_000L, viewModel.uiState.bill?.totalAmount?.cents)
    }

    @Test
    fun `删除需要确认且成功后发出已删除状态`() {
        val repository = DetailRepository(record())
        val viewModel = ElectricityBillDetailViewModel(7L, repository)

        viewModel.requestDelete()
        assertTrue(viewModel.uiState.showDeleteConfirmation)

        viewModel.confirmDelete()

        assertEquals(1, repository.deleteCount)
        assertTrue(viewModel.uiState.wasDeleted)
        assertFalse(viewModel.uiState.showDeleteConfirmation)
    }

    @Test
    fun `删除失败时留在详情页并显示原因`() {
        val repository = DetailRepository(record(), deleteFailure = IllegalStateException("删除失败"))
        val viewModel = ElectricityBillDetailViewModel(7L, repository)
        viewModel.requestDelete()

        viewModel.confirmDelete()

        assertFalse(viewModel.uiState.wasDeleted)
        assertTrue(viewModel.uiState.deleteError?.contains("删除失败") == true)
        assertEquals(7L, viewModel.uiState.bill?.id)
    }

    private class DetailRepository(
        initial: ElectricityBillRecord?,
        private val deleteFailure: Exception? = null,
    ) : ElectricityBillRepository {
        val source = MutableStateFlow(initial)
        var deleteCount = 0
            private set

        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = source
        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = emptyFlow()
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = source.value
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = null
        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill =
            error("测试中不应新增账单")
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill =
            error("测试中不应更新账单")

        override suspend fun deleteBill(billId: Long): Boolean {
            deleteCount += 1
            deleteFailure?.let { throw it }
            source.value = null
            return true
        }
    }

    private fun record() = ElectricityBillRecord(
        id = 7L,
        billingMonth = YearMonth.of(2026, 8),
        totalAmount = Money.ofCents(10_000L),
        totalUsage = BigDecimal("200"),
        shares = listOf(
            ElectricityShareRecord(
                id = 1L,
                meterKey = "owner",
                position = 0,
                label = "我的电表",
                isOwner = true,
                previousReading = BigDecimal("100"),
                currentReading = BigDecimal("200"),
                usage = BigDecimal("100"),
                allocatedAmount = Money.ofCents(5_000L),
            ),
            ElectricityShareRecord(
                id = 2L,
                meterKey = "roommate",
                position = 1,
                label = "第 2 户",
                isOwner = false,
                previousReading = BigDecimal("50"),
                currentReading = BigDecimal("150"),
                usage = BigDecimal("100"),
                allocatedAmount = Money.ofCents(5_000L),
            ),
        ),
        createdAt = 1_754_668_800_000L,
        updatedAt = 1_754_668_800_000L,
        note = null,
    )
}

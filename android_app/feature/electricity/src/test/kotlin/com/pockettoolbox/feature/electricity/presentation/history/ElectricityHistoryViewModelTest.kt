package com.pockettoolbox.feature.electricity.presentation.history

import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillSummary
import com.pockettoolbox.feature.electricity.domain.SaveElectricityBillRequest
import com.pockettoolbox.feature.electricity.domain.SavedElectricityBill
import com.pockettoolbox.feature.electricity.domain.UpdateElectricityBillRequest
import com.pockettoolbox.feature.electricity.presentation.calculator.MainDispatcherRule
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ElectricityHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `订阅后展示数据库中的真实账单`() {
        val source = MutableStateFlow(listOf(summary(id = 2, month = YearMonth.of(2026, 9))))
        val viewModel = ElectricityHistoryViewModel(HistoryRepository(source))

        assertFalse(viewModel.uiState.isLoading)
        assertEquals(listOf(2L), viewModel.uiState.bills.map { it.id })
    }

    @Test
    fun `数据库变化后历史列表实时更新`() {
        val source = MutableStateFlow<List<ElectricityBillSummary>>(emptyList())
        val viewModel = ElectricityHistoryViewModel(HistoryRepository(source))

        source.value = listOf(
            summary(id = 2, month = YearMonth.of(2026, 9)),
            summary(id = 1, month = YearMonth.of(2026, 8)),
        )

        assertEquals(listOf(2L, 1L), viewModel.uiState.bills.map { it.id })
    }

    @Test
    fun `读取失败后显示错误且可以重试`() {
        val repository = RetryRepository()
        val viewModel = ElectricityHistoryViewModel(repository)

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.errorMessage?.contains("读取失败") == true)

        viewModel.retry()

        assertEquals(1, viewModel.uiState.bills.size)
        assertNull(viewModel.uiState.errorMessage)
    }

    private fun summary(id: Long, month: YearMonth) = ElectricityBillSummary(
        id = id,
        billingMonth = month,
        totalAmount = Money.ofCents(10_000L),
        totalUsage = BigDecimal("200"),
        ownerAmount = Money.ofCents(5_000L),
        householdCount = 2,
        updatedAt = 1_757_592_000_000L,
    )

    private class HistoryRepository(
        private val source: Flow<List<ElectricityBillSummary>>,
    ) : ElectricityBillRepository {
        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill {
            error("测试中不应保存账单")
        }

        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = source
        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = null
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = null
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill {
            error("测试中不应更新账单")
        }
        override suspend fun deleteBill(billId: Long): Boolean = false
    }

    private class RetryRepository : ElectricityBillRepository {
        private var attempts = 0

        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill {
            error("测试中不应保存账单")
        }

        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> {
            attempts += 1
            return if (attempts == 1) {
                flow { throw IllegalStateException("读取失败") }
            } else {
                flowOf(
                    listOf(
                        ElectricityBillSummary(
                            id = 1,
                            billingMonth = YearMonth.of(2026, 9),
                            totalAmount = Money.ofCents(10_000L),
                            totalUsage = BigDecimal("200"),
                            ownerAmount = Money.ofCents(5_000L),
                            householdCount = 2,
                            updatedAt = 1_757_592_000_000L,
                        ),
                    ),
                )
            }
        }

        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = null
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = null
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill {
            error("测试中不应更新账单")
        }
        override suspend fun deleteBill(billId: Long): Boolean = false
    }
}

package com.pockettoolbox.feature.electricity.presentation.trend

import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
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

class ElectricityTrendViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `趋势按月份升序并只使用本人费用`() {
        val repository = TrendRepository(
            listOf(summary(3L, "2026-03", 3_000L), summary(1L, "2026-01", 1_000L), summary(2L, "2026-02", 2_000L)),
        )

        val viewModel = ElectricityTrendViewModel(repository)

        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            viewModel.uiState.points.map { it.billingMonth },
        )
        assertEquals(listOf(1_000L, 2_000L, 3_000L), viewModel.uiState.points.map { it.ownerAmount.cents })
        assertEquals(2_000L, viewModel.uiState.averageAmount?.cents)
        assertEquals(3_000L, viewModel.uiState.highestAmount?.cents)
    }

    @Test
    fun `最新费用计算相对上次账单的金额和百分比变化`() {
        val repository = TrendRepository(
            listOf(summary(2L, "2026-02", 7_500L), summary(1L, "2026-01", 5_000L)),
        )

        val state = ElectricityTrendViewModel(repository).uiState

        assertEquals(7_500L, state.latestAmount?.cents)
        assertEquals(2_500L, state.latestChangeCents)
        assertEquals(0, BigDecimal("50").compareTo(state.latestChangePercent!!))
    }

    @Test
    fun `上次费用为零时保留金额变化但不计算无限百分比`() {
        val repository = TrendRepository(
            listOf(summary(1L, "2026-01", 0L), summary(2L, "2026-02", 1_000L)),
        )

        val state = ElectricityTrendViewModel(repository).uiState

        assertEquals(1_000L, state.latestChangeCents)
        assertNull(state.latestChangePercent)
    }

    @Test
    fun `费用下降时保留负数变化而不会构造负金额对象`() {
        val repository = TrendRepository(
            listOf(summary(1L, "2026-01", 10_000L), summary(2L, "2026-02", 7_500L)),
        )

        val state = ElectricityTrendViewModel(repository).uiState

        assertEquals(-2_500L, state.latestChangeCents)
        assertEquals(0, BigDecimal("-25").compareTo(state.latestChangePercent!!))
    }

    @Test
    fun `Room数据变化会实时刷新趋势且空记录有完整状态`() {
        val repository = TrendRepository(emptyList())
        val viewModel = ElectricityTrendViewModel(repository)

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.points.isEmpty())

        repository.source.value = listOf(summary(1L, "2026-01", 1_235L), summary(2L, "2026-02", 1_236L))

        assertEquals(1_236L, viewModel.uiState.latestAmount?.cents)
        assertEquals(1_236L, viewModel.uiState.averageAmount?.cents)
    }

    @Test
    fun `读取失败后展示原因并允许重试`() {
        val repository = RetryTrendRepository()
        val viewModel = ElectricityTrendViewModel(repository)

        assertTrue(viewModel.uiState.errorMessage?.contains("读取失败") == true)

        viewModel.retry()

        assertEquals(2, repository.attempts)
        assertNull(viewModel.uiState.errorMessage)
        assertEquals(2_000L, viewModel.uiState.latestAmount?.cents)
    }

    private class TrendRepository(initial: List<ElectricityBillSummary>) : ElectricityBillRepository {
        val source = MutableStateFlow(initial)

        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = source
        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = null
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = null
        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill = error("测试中不应新增")
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill = error("测试中不应修改")
        override suspend fun deleteBill(billId: Long): Boolean = false
    }

    private class RetryTrendRepository : ElectricityBillRepository {
        var attempts = 0
            private set

        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = flow {
            attempts += 1
            if (attempts == 1) error("读取失败")
            emit(listOf(summary(1L, "2026-01", 2_000L)))
        }

        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = null
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = null
        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill = error("测试中不应新增")
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill = error("测试中不应修改")
        override suspend fun deleteBill(billId: Long): Boolean = false
    }

    private companion object {
        fun summary(id: Long, month: String, ownerCents: Long) = ElectricityBillSummary(
            id = id,
            billingMonth = YearMonth.parse(month),
            totalAmount = Money.ofCents(ownerCents * 2),
            totalUsage = BigDecimal("200"),
            ownerAmount = Money.ofCents(ownerCents),
            householdCount = 2,
            updatedAt = 1_757_592_000_000L,
        )
    }
}

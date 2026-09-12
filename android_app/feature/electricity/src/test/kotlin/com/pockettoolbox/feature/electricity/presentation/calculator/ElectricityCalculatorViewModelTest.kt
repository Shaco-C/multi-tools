package com.pockettoolbox.feature.electricity.presentation.calculator

import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.domain.BillingMonthAlreadyExistsException
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillSummary
import com.pockettoolbox.feature.electricity.domain.ElectricityShareRecord
import com.pockettoolbox.feature.electricity.domain.SaveElectricityBillRequest
import com.pockettoolbox.feature.electricity.domain.SavedElectricityBill
import com.pockettoolbox.feature.electricity.domain.UpdateElectricityBillRequest
import java.time.YearMonth
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ElectricityCalculatorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `初始空表单不显示为已编辑`() {
        val viewModel = ElectricityCalculatorViewModel(FakeRepository())

        assertFalse(viewModel.uiState.isDirty)
        assertNull(viewModel.uiState.allocation)
    }

    @Test
    fun `填写题述数据后实时得到五十元`() {
        val viewModel = validViewModel()

        assertNotNull(viewModel.uiState.allocation)
        assertEquals(5_000L, viewModel.uiState.allocation?.ownerShare?.amount?.cents ?: -1L)
    }

    @Test
    fun `月份修改后立即重新校验`() {
        val viewModel = validViewModel()

        viewModel.updateBillingMonth("2026-9")

        assertNull(viewModel.uiState.allocation)
        assertEquals("账单月份格式应为 YYYY-MM", viewModel.uiState.validationMessage)
    }

    @Test
    fun `删除电表需要确认`() {
        val viewModel = ElectricityCalculatorViewModel(FakeRepository())

        viewModel.requestRemoveMeter("meter-2")
        assertEquals("meter-2", viewModel.uiState.pendingRemovalId)
        assertEquals(2, viewModel.uiState.meters.size)

        viewModel.confirmRemoval()
        assertNull(viewModel.uiState.pendingRemovalId)
        assertEquals(1, viewModel.uiState.meters.size)
    }

    @Test
    fun `删除中间户后新增默认名称不会重复`() {
        val viewModel = ElectricityCalculatorViewModel(FakeRepository())
        viewModel.addMeter()
        viewModel.requestRemoveMeter("meter-2")
        viewModel.confirmRemoval()
        viewModel.addMeter()

        val labels = viewModel.uiState.meters.map { it.label }
        assertTrue("第 3 户" in labels)
        assertTrue("第 4 户" in labels)
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `保存成功后记录账单主键并清除未保存状态`() {
        val repository = FakeRepository()
        val viewModel = validViewModel(repository)

        viewModel.saveBill()

        assertEquals(42L, viewModel.uiState.savedBillId)
        assertFalse(viewModel.uiState.isSaving)
        assertFalse(viewModel.uiState.isDirty)
        assertNull(viewModel.uiState.saveError)
        assertEquals(1, repository.saveCount)
        assertEquals(YearMonth.now(), repository.lastRequest?.billingMonth)
        assertEquals(5_000L, repository.lastRequest?.allocation?.ownerShare?.amount?.cents)
    }

    @Test
    fun `月份已存在时保留表单并展示明确错误`() {
        val repository = FakeRepository(
            failure = BillingMonthAlreadyExistsException(YearMonth.now()),
        )
        val viewModel = validViewModel(repository)

        viewModel.saveBill()

        assertNull(viewModel.uiState.savedBillId)
        assertTrue(viewModel.uiState.isDirty)
        assertTrue(viewModel.uiState.saveError?.contains("已经存在") == true)
    }

    @Test
    fun `保存成功后继续修改会恢复未保存状态`() {
        val viewModel = validViewModel(FakeRepository())
        viewModel.saveBill()

        viewModel.updateTotalAmount("120")

        assertNull(viewModel.uiState.savedBillId)
        assertTrue(viewModel.uiState.isDirty)
    }

    @Test
    fun `保存进行中阻止重复提交和表单修改`() {
        val repository = DeferredRepository()
        val viewModel = validViewModel(repository)
        val originalAmount = viewModel.uiState.totalAmount

        viewModel.saveBill()
        viewModel.saveBill()
        viewModel.updateTotalAmount("120")

        assertTrue(viewModel.uiState.isSaving)
        assertEquals(1, repository.saveCount)
        assertEquals(originalAmount, viewModel.uiState.totalAmount)

        repository.complete()
        assertFalse(viewModel.uiState.isSaving)
        assertEquals(42L, viewModel.uiState.savedBillId)
    }

    @Test
    fun `编辑模式读取原账单并恢复全部电表快照`() {
        val repository = FakeRepository(existingBill = existingBill())

        val viewModel = ElectricityCalculatorViewModel(repository, editingBillId = 7L)

        assertFalse(viewModel.uiState.isLoading)
        assertEquals("2026-08", viewModel.uiState.billingMonth)
        assertEquals("100.00", viewModel.uiState.totalAmount)
        assertEquals(listOf("owner", "roommate"), viewModel.uiState.meters.map { it.id })
        assertEquals("200", viewModel.uiState.meters.first().currentReading)
        assertNotNull(viewModel.uiState.allocation)
        assertFalse(viewModel.uiState.isDirty)
    }

    @Test
    fun `编辑账单保存时调用更新而不是新增`() {
        val repository = FakeRepository(existingBill = existingBill())
        val viewModel = ElectricityCalculatorViewModel(repository, editingBillId = 7L)
        viewModel.updateTotalAmount("120")

        viewModel.saveBill()

        assertEquals(0, repository.saveCount)
        assertEquals(1, repository.updateCount)
        assertEquals(7L, repository.lastUpdateRequest?.id)
        assertEquals("原备注", repository.lastUpdateRequest?.note)
        assertEquals(7L, viewModel.uiState.savedBillId)
    }

    @Test
    fun `新账单自动把上月本期读数带入为本月上期读数`() {
        val repository = FakeRepository(previousBill = existingBill())

        val viewModel = ElectricityCalculatorViewModel(repository, initialBillingMonth = YearMonth.of(2026, 9))

        assertEquals(1, repository.previousMonthQueryCount)
        assertEquals(YearMonth.of(2026, 9), repository.lastPreviousMonthQuery)
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.previousReadingsSourceMonth)
        assertEquals(listOf("owner", "roommate"), viewModel.uiState.meters.map { it.id })
        assertEquals(listOf("200", "150"), viewModel.uiState.meters.map { it.previousReading })
        assertTrue(viewModel.uiState.meters.all { it.currentReading.isEmpty() })
        assertFalse(viewModel.uiState.isDirty)
    }

    @Test
    fun `编辑历史账单时不会查询或套用上月读数`() {
        val repository = FakeRepository(existingBill = existingBill(), previousBill = existingBill())

        val viewModel = ElectricityCalculatorViewModel(repository, editingBillId = 7L)

        assertEquals(0, repository.previousMonthQueryCount)
        assertEquals("100", viewModel.uiState.meters.first().previousReading)
        assertEquals("200", viewModel.uiState.meters.first().currentReading)
    }

    @Test
    fun `自动读取期间开始手动输入不会被迟到结果覆盖`() {
        val repository = DeferredPreviousRepository()
        val viewModel = ElectricityCalculatorViewModel(repository, initialBillingMonth = YearMonth.of(2026, 9))

        viewModel.updateMeter("owner") { it.copy(previousReading = "999") }
        repository.completePrevious(existingBill())

        assertEquals("999", viewModel.uiState.meters.first { it.id == "owner" }.previousReading)
        assertNull(viewModel.uiState.previousReadingsSourceMonth)
        assertTrue(viewModel.uiState.previousReadingsNotice?.contains("不会自动覆盖") == true)
    }

    @Test
    fun `手动修改读数后切换月份仍保留输入且不再次自动查询`() {
        val repository = FakeRepository(previousBill = existingBill())
        val viewModel = ElectricityCalculatorViewModel(repository, initialBillingMonth = YearMonth.of(2026, 9))
        viewModel.updateMeter("owner") { it.copy(previousReading = "999") }

        viewModel.updateBillingMonth("2026-10")

        assertEquals(1, repository.previousMonthQueryCount)
        assertEquals("999", viewModel.uiState.meters.first { it.id == "owner" }.previousReading)
        assertTrue(viewModel.uiState.previousReadingsNotice?.contains("不会自动覆盖") == true)
    }

    @Test
    fun `仓库返回非目标上月账单时拒绝带入`() {
        val repository = FakeRepository(previousBill = existingBill())

        val viewModel = ElectricityCalculatorViewModel(repository, initialBillingMonth = YearMonth.of(2026, 10))

        assertNull(viewModel.uiState.previousReadingsSourceMonth)
        assertTrue(viewModel.uiState.previousReadingsError?.contains("不是所选月份") == true)
        assertTrue(viewModel.uiState.meters.all { it.previousReading.isEmpty() })
    }

    private fun validViewModel(
        repository: ElectricityBillRepository = FakeRepository(),
    ): ElectricityCalculatorViewModel {
        return ElectricityCalculatorViewModel(repository).apply {
            updateTotalAmount("100")
            updateMeter("owner") { it.copy(previousReading = "100", currentReading = "200") }
            updateMeter("meter-2") { it.copy(previousReading = "50", currentReading = "150") }
        }
    }

    private class FakeRepository(
        private val failure: Exception? = null,
        private val existingBill: ElectricityBillRecord? = null,
        private val previousBill: ElectricityBillRecord? = null,
    ) : ElectricityBillRepository {
        var saveCount: Int = 0
            private set
        var lastRequest: SaveElectricityBillRequest? = null
            private set
        var updateCount: Int = 0
            private set
        var lastUpdateRequest: UpdateElectricityBillRequest? = null
            private set
        var previousMonthQueryCount: Int = 0
            private set
        var lastPreviousMonthQuery: YearMonth? = null
            private set

        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill {
            saveCount += 1
            lastRequest = request
            failure?.let { throw it }
            return SavedElectricityBill(id = 42L, billingMonth = request.billingMonth)
        }

        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = emptyFlow()
        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = existingBill
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? {
            previousMonthQueryCount += 1
            lastPreviousMonthQuery = billingMonth
            return previousBill
        }
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill {
            updateCount += 1
            lastUpdateRequest = request
            failure?.let { throw it }
            return SavedElectricityBill(id = request.id, billingMonth = request.billingMonth)
        }
        override suspend fun deleteBill(billId: Long): Boolean = false
    }

    private class DeferredRepository : ElectricityBillRepository {
        private val result = CompletableDeferred<SavedElectricityBill>()
        var saveCount: Int = 0
            private set

        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill {
            saveCount += 1
            return result.await()
        }

        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = emptyFlow()
        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = null
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = null
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill {
            error("测试中不应更新账单")
        }
        override suspend fun deleteBill(billId: Long): Boolean = false

        fun complete() {
            result.complete(SavedElectricityBill(id = 42L, billingMonth = YearMonth.now()))
        }
    }

    private class DeferredPreviousRepository : ElectricityBillRepository {
        private val previous = CompletableDeferred<ElectricityBillRecord?>()

        override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill = error("测试中不应新增")
        override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> = emptyFlow()
        override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> = flowOf(null)
        override suspend fun getBill(billId: Long): ElectricityBillRecord? = null
        override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? = previous.await()
        override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill = error("测试中不应更新")
        override suspend fun deleteBill(billId: Long): Boolean = false

        fun completePrevious(record: ElectricityBillRecord?) {
            previous.complete(record)
        }
    }

    private fun existingBill() = ElectricityBillRecord(
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
        note = "原备注",
    )
}

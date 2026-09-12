package com.pockettoolbox.feature.electricity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.core.backup.InvalidBackupException
import com.pockettoolbox.feature.electricity.data.RoomElectricityBillRepository
import com.pockettoolbox.feature.electricity.data.backup.ElectricityBackupContributor
import com.pockettoolbox.feature.electricity.data.export.RoomElectricityCsvExporter
import com.pockettoolbox.feature.electricity.domain.BillingMonthAlreadyExistsException
import com.pockettoolbox.feature.electricity.domain.ElectricityAllocationCalculator
import com.pockettoolbox.feature.electricity.domain.MeterReadingInput
import com.pockettoolbox.feature.electricity.domain.SaveElectricityBillRequest
import com.pockettoolbox.feature.electricity.domain.UpdateElectricityBillRequest
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ElectricityDatabaseTest {
    private lateinit var database: ElectricityDatabase
    private lateinit var dao: ElectricityBillDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ElectricityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.electricityBillDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun billAndSharesAreSavedAndReadAsOneGraph() = runBlocking {
        val billId = dao.insertBillWithShares(sampleBill(), sampleShares())

        val saved = dao.getBillById(billId)

        assertNotNull(saved)
        assertEquals("2026-09", saved?.bill?.billingMonth)
        assertEquals(10_000L, saved?.bill?.totalAmountCents)
        assertEquals(listOf("owner", "roommate"), saved?.orderedShares?.map { it.meterKey })
        assertEquals(1, saved?.orderedShares?.count { it.isOwner })
    }

    @Test
    fun deletingBillCascadesToItsShares() = runBlocking {
        val billId = dao.insertBillWithShares(sampleBill(), sampleShares())
        assertEquals(1, dao.countBills())
        assertEquals(2, dao.countShares())

        assertEquals(1, dao.deleteBillById(billId))

        assertEquals(0, dao.countBills())
        assertEquals(0, dao.countShares())
        assertNull(dao.getBillById(billId))
    }

    @Test
    fun inconsistentSnapshotIsRejectedBeforeWriting() = runBlocking {
        val invalidShares = sampleShares().mapIndexed { index, share ->
            if (index == 0) share.copy(usage = "99") else share
        }

        val failure = runCatching { dao.insertBillWithShares(sampleBill(), invalidShares) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, dao.countBills())
        assertEquals(0, dao.countShares())
    }

    @Test
    fun repositoryPersistsCalculatedBillWithoutLosingPrecision() = runBlocking {
        val repository = RoomElectricityBillRepository(
            dao = dao,
            currentTimeMillis = { 1_757_592_000_000L },
        )
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_001L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100.1"), BigDecimal("200.2")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50.2"), BigDecimal("150.3")),
            ),
        )

        val saved = repository.saveNewBill(
            SaveElectricityBillRequest(YearMonth.of(2026, 9), allocation),
        )
        val stored = dao.getBillById(saved.id)

        assertEquals("200.2", stored?.bill?.totalUsage)
        assertEquals(10_001L, stored?.orderedShares?.sumOf { it.allocatedAmountCents })
        assertEquals("100.1", stored?.orderedShares?.first()?.usage)
        assertEquals(1_757_592_000_000L, stored?.bill?.createdAt)
    }

    @Test
    fun repositoryRejectsDuplicateMonthWithoutOverwritingOriginal() = runBlocking {
        val repository = RoomElectricityBillRepository(
            dao = dao,
            currentTimeMillis = { 1_757_592_000_000L },
        )
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        val request = SaveElectricityBillRequest(YearMonth.of(2026, 9), allocation)
        repository.saveNewBill(request)

        val failure = runCatching { repository.saveNewBill(request) }.exceptionOrNull()

        assertTrue(failure is BillingMonthAlreadyExistsException)
        assertEquals(1, dao.countBills())
        assertEquals(2, dao.countShares())
    }

    @Test
    fun historySummariesAreObservedNewestFirstAndUseOwnerShare() = runBlocking {
        val repository = RoomElectricityBillRepository(
            dao = dao,
            currentTimeMillis = { 1_757_592_000_000L },
        )
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 8), allocation))
        repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 9), allocation))

        val summaries = repository.observeBillSummaries().first()

        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), summaries.map { it.billingMonth })
        assertEquals(listOf(5_000L, 5_000L), summaries.map { it.ownerAmount.cents })
        assertEquals(listOf(2, 2), summaries.map { it.householdCount })
    }

    @Test
    fun previousMonthLookupRequiresTheExactCalendarMonth() = runBlocking {
        val repository = RoomElectricityBillRepository(dao = dao)
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 7), allocation))

        assertNull(repository.getPreviousMonthBill(YearMonth.of(2026, 9)))

        repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 8), allocation))
        val previous = repository.getPreviousMonthBill(YearMonth.of(2026, 9))

        assertEquals(YearMonth.of(2026, 8), previous?.billingMonth)
        assertEquals(listOf("200", "150"), previous?.shares?.map { it.currentReading.toPlainString() })
    }

    @Test
    fun repositoryUpdatesBillAndReplacesSharesInOneTransaction() = runBlocking {
        var now = 1_757_592_000_000L
        val repository = RoomElectricityBillRepository(dao = dao, currentTimeMillis = { now })
        val original = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        val saved = repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 8), original))
        now += 60_000L
        val updatedAllocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(12_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("220")),
                MeterReadingInput("new-roommate", "新室友", false, BigDecimal("50"), BigDecimal("130")),
            ),
        )

        repository.updateBill(
            UpdateElectricityBillRequest(saved.id, YearMonth.of(2026, 8), updatedAllocation, "修改后"),
        )
        val stored = repository.getBill(saved.id)

        assertEquals(12_000L, stored?.totalAmount?.cents)
        assertEquals(listOf("owner", "new-roommate"), stored?.shares?.map { it.meterKey })
        assertEquals("修改后", stored?.note)
        assertEquals(1_757_592_000_000L, stored?.createdAt)
        assertEquals(now, stored?.updatedAt)
        assertEquals(2, dao.countShares())
    }

    @Test
    fun changingBillToAnExistingMonthIsRejectedWithoutDataLoss() = runBlocking {
        val repository = RoomElectricityBillRepository(dao = dao)
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        val august = repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 8), allocation))
        repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 9), allocation))

        val failure = runCatching {
            repository.updateBill(UpdateElectricityBillRequest(august.id, YearMonth.of(2026, 9), allocation))
        }.exceptionOrNull()

        assertTrue(failure is BillingMonthAlreadyExistsException)
        assertEquals(YearMonth.of(2026, 8), repository.getBill(august.id)?.billingMonth)
        assertEquals(2, dao.countBills())
        assertEquals(4, dao.countShares())
    }

    @Test
    fun repositoryDeleteRemovesBillAndShares() = runBlocking {
        val repository = RoomElectricityBillRepository(dao = dao)
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        val saved = repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 8), allocation))

        assertTrue(repository.deleteBill(saved.id))

        assertEquals(0, dao.countBills())
        assertEquals(0, dao.countShares())
    }

    @Test
    fun updateTimestampDoesNotMoveBackwardsWhenDeviceClockMovesBack() = runBlocking {
        var now = 1_757_592_000_000L
        val repository = RoomElectricityBillRepository(dao = dao, currentTimeMillis = { now })
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        val saved = repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 8), allocation))
        now += 60_000L
        repository.updateBill(UpdateElectricityBillRequest(saved.id, YearMonth.of(2026, 8), allocation))
        val firstUpdatedAt = repository.getBill(saved.id)?.updatedAt

        now -= 120_000L
        repository.updateBill(UpdateElectricityBillRequest(saved.id, YearMonth.of(2026, 8), allocation))

        assertEquals(firstUpdatedAt, repository.getBill(saved.id)?.updatedAt)
    }

    @Test
    fun jsonBackupRoundTripRestoresCompleteBillSnapshots() = runBlocking {
        val repository = RoomElectricityBillRepository(dao = dao)
        val contributor = ElectricityBackupContributor(dao = dao, currentTimeMillis = { 1_757_592_000_000L })
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_001L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100.1"), BigDecimal("200.2")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50.2"), BigDecimal("150.3")),
            ),
        )
        repository.saveNewBill(
            SaveElectricityBillRequest(YearMonth.of(2026, 9), allocation, note = "九月账单"),
        )
        val json = contributor.createBackupJson()
        dao.deleteBillById(dao.getAllBills().single().bill.id)

        val restored = contributor.restoreBackupJson(json)
        val bill = dao.getAllBills().single()

        assertEquals(1, restored.recordCount)
        assertEquals("2026-09", bill.bill.billingMonth)
        assertEquals(10_001L, bill.bill.totalAmountCents)
        assertEquals("九月账单", bill.bill.note)
        assertEquals(listOf("100.1", "100.1"), bill.orderedShares.map { it.usage })
        assertEquals(10_001L, bill.orderedShares.sumOf { it.allocatedAmountCents })
    }

    @Test
    fun invalidBackupIsRejectedBeforeExistingDataIsDeleted() = runBlocking {
        val repository = RoomElectricityBillRepository(dao = dao)
        val contributor = ElectricityBackupContributor(dao = dao)
        val allocation = ElectricityAllocationCalculator().calculate(
            totalAmount = Money.ofCents(10_000L),
            meters = listOf(
                MeterReadingInput("owner", "我的电表", true, BigDecimal("100"), BigDecimal("200")),
                MeterReadingInput("roommate", "第 2 户", false, BigDecimal("50"), BigDecimal("150")),
            ),
        )
        repository.saveNewBill(SaveElectricityBillRequest(YearMonth.of(2026, 9), allocation))
        val validJson = contributor.createBackupJson()
        val invalidJson = validJson.replace("\"totalAmountCents\": 10000", "\"totalAmountCents\": 9999")

        val failure = runCatching { contributor.restoreBackupJson(invalidJson) }.exceptionOrNull()

        assertTrue(failure is InvalidBackupException)
        assertEquals(1, dao.countBills())
        assertEquals(2, dao.countShares())
        assertEquals(10_000L, dao.getAllBills().single().bill.totalAmountCents)
    }

    @Test
    fun confirmedEmptyBackupClearsOnlyElectricityBills() = runBlocking {
        val contributor = ElectricityBackupContributor(dao = dao)
        val emptyJson = contributor.createBackupJson()
        dao.insertBillWithShares(sampleBill(), sampleShares())

        val restored = contributor.restoreBackupJson(emptyJson)

        assertEquals(0, restored.recordCount)
        assertEquals(0, dao.countBills())
        assertEquals(0, dao.countShares())
    }

    @Test
    fun jsonWithoutRequiredBackupHeaderIsRejected() {
        val contributor = ElectricityBackupContributor(dao = dao)

        val failure = runCatching {
            contributor.inspectBackupJson("""{"exportedAt":1757592000000,"bills":[]}""")
        }.exceptionOrNull()

        assertTrue(failure is InvalidBackupException)
    }

    @Test
    fun csvExporterReadsAllRoomBillsAndHouseholds() = runBlocking {
        dao.insertBillWithShares(sampleBill(), sampleShares())

        val csv = RoomElectricityCsvExporter(dao).createCsv()

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"2026-09\""))
        assertTrue(csv.contains("\"我的电表\""))
        assertTrue(csv.contains("\"第 2 户\""))
        assertEquals(3, "\r\n".toRegex().findAll(csv).count())
    }

    private fun sampleBill() = ElectricityBillEntity(
        billingMonth = "2026-09",
        totalAmountCents = 10_000L,
        totalUsage = "200",
        createdAt = 1_757_592_000_000L,
        updatedAt = 1_757_592_000_000L,
    )

    private fun sampleShares() = listOf(
        ElectricityShareEntity(
            meterKey = "owner",
            position = 0,
            label = "我的电表",
            isOwner = true,
            previousReading = "100",
            currentReading = "200",
            usage = "100",
            allocatedAmountCents = 5_000L,
        ),
        ElectricityShareEntity(
            meterKey = "roommate",
            position = 1,
            label = "第 2 户",
            isOwner = false,
            previousReading = "50",
            currentReading = "150",
            usage = "100",
            allocatedAmountCents = 5_000L,
        ),
    )
}

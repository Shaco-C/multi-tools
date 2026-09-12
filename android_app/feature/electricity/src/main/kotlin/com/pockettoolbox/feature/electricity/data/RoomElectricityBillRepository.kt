package com.pockettoolbox.feature.electricity.data

import android.database.sqlite.SQLiteConstraintException
import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillDao
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillEntity
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillWithShares
import com.pockettoolbox.feature.electricity.data.local.ElectricityShareEntity
import com.pockettoolbox.feature.electricity.domain.BillingMonthAlreadyExistsException
import com.pockettoolbox.feature.electricity.domain.ElectricityAllocation
import com.pockettoolbox.feature.electricity.domain.ElectricityBillNotFoundException
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityBillSummary
import com.pockettoolbox.feature.electricity.domain.ElectricityShareRecord
import com.pockettoolbox.feature.electricity.domain.SaveElectricityBillRequest
import com.pockettoolbox.feature.electricity.domain.SavedElectricityBill
import com.pockettoolbox.feature.electricity.domain.UpdateElectricityBillRequest
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomElectricityBillRepository(
    private val dao: ElectricityBillDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ElectricityBillRepository {
    override fun observeBillSummaries(): Flow<List<ElectricityBillSummary>> =
        dao.observeAllBills().map { bills ->
            bills.map { stored ->
                val record = stored.toRecord()
                ElectricityBillSummary(
                    id = record.id,
                    billingMonth = record.billingMonth,
                    totalAmount = record.totalAmount,
                    totalUsage = record.totalUsage,
                    ownerAmount = record.ownerShare.allocatedAmount,
                    householdCount = record.shares.size,
                    updatedAt = record.updatedAt,
                )
            }
        }

    override fun observeBill(billId: Long): Flow<ElectricityBillRecord?> =
        dao.observeBillById(billId).map { it?.toRecord() }

    override suspend fun getBill(billId: Long): ElectricityBillRecord? =
        dao.getBillById(billId)?.toRecord()

    override suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord? =
        dao.getBillByMonth(billingMonth.minusMonths(1).toString())?.toRecord()

    override suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill {
        val month = request.billingMonth.toString()
        // 先检查可给用户更友好的错误；数据库唯一索引仍负责兜住并发写入竞态。
        if (dao.billExists(month)) {
            throw BillingMonthAlreadyExistsException(request.billingMonth)
        }

        val now = currentTimeMillis()
        val bill = ElectricityBillEntity(
            billingMonth = month,
            totalAmountCents = request.allocation.totalAmount.cents,
            totalUsage = request.allocation.totalUsage.toStorageDecimal(),
            createdAt = now,
            updatedAt = now,
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val shares = request.allocation.toShareEntities()

        val id = try {
            dao.insertBillWithShares(bill, shares)
        } catch (error: SQLiteConstraintException) {
            // 如果在预检查之后被另一写入抢先占用月份，将底层约束错误转换为业务提示。
            if (dao.billExists(month)) {
                throw BillingMonthAlreadyExistsException(request.billingMonth)
            }
            throw error
        }
        return SavedElectricityBill(id = id, billingMonth = request.billingMonth)
    }

    override suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill {
        val existing = dao.getBillById(request.id)?.bill
            ?: throw ElectricityBillNotFoundException(request.id)
        val month = request.billingMonth.toString()
        if (dao.billExistsExceptId(month, request.id)) {
            throw BillingMonthAlreadyExistsException(request.billingMonth)
        }
        val updated = ElectricityBillEntity(
            id = request.id,
            billingMonth = month,
            totalAmountCents = request.allocation.totalAmount.cents,
            totalUsage = request.allocation.totalUsage.toStorageDecimal(),
            createdAt = existing.createdAt,
            updatedAt = maxOf(currentTimeMillis(), existing.updatedAt),
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )

        try {
            dao.updateBillWithShares(updated, request.allocation.toShareEntities())
        } catch (error: SQLiteConstraintException) {
            if (dao.billExistsExceptId(month, request.id)) {
                throw BillingMonthAlreadyExistsException(request.billingMonth)
            }
            throw error
        }
        return SavedElectricityBill(id = request.id, billingMonth = request.billingMonth)
    }

    override suspend fun deleteBill(billId: Long): Boolean = dao.deleteBillById(billId) == 1

    private fun ElectricityBillWithShares.toRecord(): ElectricityBillRecord = ElectricityBillRecord(
        id = bill.id,
        billingMonth = YearMonth.parse(bill.billingMonth),
        totalAmount = Money.ofCents(bill.totalAmountCents),
        totalUsage = bill.totalUsage.toBigDecimal(),
        shares = orderedShares.map { share ->
            ElectricityShareRecord(
                id = share.id,
                meterKey = share.meterKey,
                position = share.position,
                label = share.label,
                isOwner = share.isOwner,
                previousReading = share.previousReading.toBigDecimal(),
                currentReading = share.currentReading.toBigDecimal(),
                usage = share.usage.toBigDecimal(),
                allocatedAmount = Money.ofCents(share.allocatedAmountCents),
            )
        },
        createdAt = bill.createdAt,
        updatedAt = bill.updatedAt,
        note = bill.note,
    )

    private fun ElectricityAllocation.toShareEntities() =
        shares.mapIndexed { position, share ->
            ElectricityShareEntity(
                meterKey = share.id,
                position = position,
                label = share.label,
                isOwner = share.isOwner,
                previousReading = share.previousReading.toStorageDecimal(),
                currentReading = share.currentReading.toStorageDecimal(),
                usage = share.usage.toStorageDecimal(),
                allocatedAmountCents = share.amount.cents,
            )
        }

    // Room 以十进制字符串保存电表读数，避免 SQLite 浮点列损失精度。
    private fun BigDecimal.toStorageDecimal(): String = stripTrailingZeros().toPlainString()
}

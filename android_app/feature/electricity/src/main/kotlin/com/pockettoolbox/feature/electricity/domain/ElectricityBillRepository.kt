package com.pockettoolbox.feature.electricity.domain

import com.pockettoolbox.core.common.money.Money
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

data class SaveElectricityBillRequest(
    val billingMonth: YearMonth,
    val allocation: ElectricityAllocation,
    val note: String? = null,
)

data class SavedElectricityBill(
    val id: Long,
    val billingMonth: YearMonth,
)

data class ElectricityBillSummary(
    val id: Long,
    val billingMonth: YearMonth,
    val totalAmount: Money,
    val totalUsage: BigDecimal,
    val ownerAmount: Money,
    val householdCount: Int,
    val updatedAt: Long,
)

data class ElectricityShareRecord(
    val id: Long,
    val meterKey: String,
    val position: Int,
    val label: String,
    val isOwner: Boolean,
    val previousReading: BigDecimal,
    val currentReading: BigDecimal,
    val usage: BigDecimal,
    val allocatedAmount: Money,
)

data class ElectricityBillRecord(
    val id: Long,
    val billingMonth: YearMonth,
    val totalAmount: Money,
    val totalUsage: BigDecimal,
    val shares: List<ElectricityShareRecord>,
    val createdAt: Long,
    val updatedAt: Long,
    val note: String?,
) {
    val ownerShare: ElectricityShareRecord
        get() = shares.single { it.isOwner }
}

data class UpdateElectricityBillRequest(
    val id: Long,
    val billingMonth: YearMonth,
    val allocation: ElectricityAllocation,
    val note: String? = null,
)

interface ElectricityBillRepository {
    suspend fun saveNewBill(request: SaveElectricityBillRequest): SavedElectricityBill

    fun observeBillSummaries(): Flow<List<ElectricityBillSummary>>

    fun observeBill(billId: Long): Flow<ElectricityBillRecord?>

    suspend fun getBill(billId: Long): ElectricityBillRecord?

    suspend fun getPreviousMonthBill(billingMonth: YearMonth): ElectricityBillRecord?

    suspend fun updateBill(request: UpdateElectricityBillRequest): SavedElectricityBill

    suspend fun deleteBill(billId: Long): Boolean
}

class BillingMonthAlreadyExistsException(
    billingMonth: YearMonth,
) : IllegalStateException("${billingMonth} 的账单已经存在，当前不会覆盖原记录")

class ElectricityBillNotFoundException(
    billId: Long,
) : IllegalStateException("未找到账单（ID：$billId），它可能已经被删除")

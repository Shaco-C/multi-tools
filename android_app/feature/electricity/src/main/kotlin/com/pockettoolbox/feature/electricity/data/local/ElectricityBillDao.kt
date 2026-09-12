package com.pockettoolbox.feature.electricity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ElectricityBillDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertBillEntity(bill: ElectricityBillEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertShareEntities(shares: List<ElectricityShareEntity>)

    @Update
    protected abstract suspend fun updateBillEntity(bill: ElectricityBillEntity): Int

    @Query("DELETE FROM electricity_share WHERE bill_id = :billId")
    protected abstract suspend fun deleteSharesByBillId(billId: Long): Int

    @Query("DELETE FROM electricity_bill")
    protected abstract suspend fun deleteAllBills(): Int

    @Transaction
    open suspend fun insertBillWithShares(
        bill: ElectricityBillEntity,
        shares: List<ElectricityShareEntity>,
    ): Long {
        // 主账单与全部分摊明细必须同成同败，不能留下只有主表的残缺账单。
        ElectricityBillPersistenceValidator.validate(bill, shares)
        val billId = insertBillEntity(bill)
        insertShareEntities(shares.map { it.copy(billId = billId) })
        return billId
    }

    @Transaction
    open suspend fun updateBillWithShares(
        bill: ElectricityBillEntity,
        shares: List<ElectricityShareEntity>,
    ) {
        // 修改账单时整体替换明细，确保读数、用量和金额仍属于同一份计算快照。
        ElectricityBillPersistenceValidator.validateExisting(bill, shares)
        check(updateBillEntity(bill) == 1) { "要更新的账单不存在" }
        deleteSharesByBillId(bill.id)
        insertShareEntities(shares.map { it.copy(billId = bill.id) })
    }

    @Transaction
    open suspend fun replaceAllBills(snapshots: List<ElectricityBillWriteSnapshot>) {
        // JSON 恢复采用单一事务；任何一条记录写入失败，原数据库都会完整回滚。
        require(snapshots.map { it.bill.billingMonth }.distinct().size == snapshots.size) {
            "备份中存在重复账单月份"
        }
        snapshots.forEach { snapshot ->
            ElectricityBillPersistenceValidator.validate(snapshot.bill, snapshot.shares)
        }

        deleteAllBills()
        snapshots.forEach { snapshot ->
            val billId = insertBillEntity(snapshot.bill)
            insertShareEntities(snapshot.shares.map { it.copy(billId = billId) })
        }
    }

    @Transaction
    @Query("SELECT * FROM electricity_bill WHERE id = :billId LIMIT 1")
    abstract suspend fun getBillById(billId: Long): ElectricityBillWithShares?

    @Transaction
    @Query("SELECT * FROM electricity_bill WHERE id = :billId LIMIT 1")
    abstract fun observeBillById(billId: Long): Flow<ElectricityBillWithShares?>

    @Transaction
    @Query("SELECT * FROM electricity_bill WHERE billing_month = :billingMonth LIMIT 1")
    abstract suspend fun getBillByMonth(billingMonth: String): ElectricityBillWithShares?

    @Query("SELECT EXISTS(SELECT 1 FROM electricity_bill WHERE billing_month = :billingMonth)")
    abstract suspend fun billExists(billingMonth: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM electricity_bill
            WHERE billing_month = :billingMonth AND id != :excludedBillId
        )
        """,
    )
    abstract suspend fun billExistsExceptId(billingMonth: String, excludedBillId: Long): Boolean

    @Transaction
    @Query("SELECT * FROM electricity_bill ORDER BY billing_month DESC, id DESC")
    abstract fun observeAllBills(): Flow<List<ElectricityBillWithShares>>

    @Transaction
    @Query("SELECT * FROM electricity_bill ORDER BY billing_month ASC, id ASC")
    abstract suspend fun getAllBills(): List<ElectricityBillWithShares>

    @Query("DELETE FROM electricity_bill WHERE id = :billId")
    abstract suspend fun deleteBillById(billId: Long): Int

    @Query("SELECT COUNT(*) FROM electricity_bill")
    abstract suspend fun countBills(): Int

    @Query("SELECT COUNT(*) FROM electricity_share")
    abstract suspend fun countShares(): Int
}

package com.pockettoolbox.feature.electricity.data.local

import org.junit.Assert.assertThrows
import org.junit.Test

class ElectricityBillPersistenceValidatorTest {
    @Test
    fun `题述两户账单快照可以通过校验`() {
        ElectricityBillPersistenceValidator.validate(sampleBill(), sampleShares())
    }

    @Test
    fun `更新账单必须携带有效主键`() {
        ElectricityBillPersistenceValidator.validateExisting(
            sampleBill().copy(id = 7L),
            sampleShares(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validateExisting(sampleBill(), sampleShares())
        }
    }

    @Test
    fun `等值但小数位数不同的用量可以通过校验`() {
        val shares = sampleShares().map { share ->
            if (share.isOwner) {
                share.copy(previousReading = "100.0", currentReading = "200.0", usage = "100.00")
            } else {
                share
            }
        }

        ElectricityBillPersistenceValidator.validate(sampleBill(), shares)
    }

    @Test
    fun `无效月份不能写入数据库`() {
        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validate(
                sampleBill().copy(billingMonth = "2026-13"),
                sampleShares(),
            )
        }
    }

    @Test
    fun `账单必须且只能有一条本人明细`() {
        val shares = sampleShares().map { it.copy(isOwner = false) }

        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validate(sampleBill(), shares)
        }
    }

    @Test
    fun `本人电表不在第一列时拒绝写入`() {
        val shares = sampleShares().mapIndexed { index, share ->
            share.copy(isOwner = index == 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validate(sampleBill(), shares)
        }
    }

    @Test
    fun `分摊金额合计必须等于账单金额`() {
        val shares = sampleShares().mapIndexed { index, share ->
            if (index == 0) share.copy(allocatedAmountCents = 4_999L) else share
        }

        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validate(sampleBill(), shares)
        }
    }

    @Test
    fun `明细位置必须连续且不能重复`() {
        val shares = sampleShares().map { it.copy(position = 0) }

        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validate(sampleBill(), shares)
        }
    }

    @Test
    fun `明细数组顺序必须与位置编号一致`() {
        val shares = sampleShares().mapIndexed { index, share ->
            share.copy(position = 1 - index)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ElectricityBillPersistenceValidator.validate(sampleBill(), shares)
        }
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

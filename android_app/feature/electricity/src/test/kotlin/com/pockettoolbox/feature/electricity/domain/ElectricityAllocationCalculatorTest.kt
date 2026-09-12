package com.pockettoolbox.feature.electricity.domain

import com.pockettoolbox.core.common.money.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElectricityAllocationCalculatorTest {
    private val calculator = ElectricityAllocationCalculator()

    @Test
    fun `题述示例两户应各分摊五十元`() {
        val result = calculator.calculate(
            totalAmount = Money.parseYuan("100"),
            meters = listOf(
                meter("mine", "我的电表", true, "100", "200"),
                meter("other", "第 2 户", false, "50", "150"),
            ),
        )

        assertEquals(BigDecimal("200"), result.totalUsage)
        assertEquals(5_000, result.shares[0].amount.cents)
        assertEquals(5_000, result.shares[1].amount.cents)
        assertEquals(10_000, result.shares.sumOf { it.amount.cents })
    }

    @Test
    fun `无法整除时按最大余数法分配且总额不变`() {
        val result = calculator.calculate(
            totalAmount = Money.ofCents(100),
            meters = listOf(
                meter("mine", "我的电表", true, "0", "1"),
                meter("b", "第 2 户", false, "0", "1"),
                meter("c", "第 3 户", false, "0", "1"),
            ),
        )

        assertEquals(listOf(34L, 33L, 33L), result.shares.map { it.amount.cents })
        assertEquals(100, result.shares.sumOf { it.amount.cents })
    }

    @Test
    fun `零用量户金额为零`() {
        val result = calculator.calculate(
            totalAmount = Money.parseYuan("12.34"),
            meters = listOf(
                meter("mine", "我的电表", true, "10", "10"),
                meter("other", "第 2 户", false, "20", "30"),
            ),
        )

        assertEquals(0, result.ownerShare.amount.cents)
        assertEquals(1_234, result.shares[1].amount.cents)
    }

    @Test
    fun `本期读数小于上期时拒绝计算`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                totalAmount = Money.parseYuan("100"),
                meters = listOf(meter("mine", "我的电表", true, "200", "100")),
            )
        }

        assertEquals("我的电表的本期读数不能小于上期读数", error.message)
    }

    @Test
    fun `总用量为零时拒绝计算`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                totalAmount = Money.parseYuan("100"),
                meters = listOf(meter("mine", "我的电表", true, "100", "100")),
            )
        }

        assertEquals("总用量为 0，无法按比例分摊", error.message)
    }

    @Test
    fun `总金额为零且存在用量时允许计算`() {
        val result = calculator.calculate(
            totalAmount = Money.Zero,
            meters = listOf(
                meter("mine", "我的电表", true, "0.5", "1.25"),
                meter("other", "第 2 户", false, "10", "10.25"),
            ),
        )

        assertEquals(BigDecimal("1.00"), result.totalUsage)
        assertEquals(listOf(0L, 0L), result.shares.map { it.amount.cents })
    }

    @Test
    fun `重复电表标识时拒绝计算`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                totalAmount = Money.parseYuan("10"),
                meters = listOf(
                    meter("same", "我的电表", true, "0", "1"),
                    meter("same", "第 2 户", false, "0", "1"),
                ),
            )
        }

        assertEquals("电表标识不能重复", error.message)
    }

    private fun meter(
        id: String,
        label: String,
        isOwner: Boolean,
        previous: String,
        current: String,
    ) = MeterReadingInput(
        id = id,
        label = label,
        isOwner = isOwner,
        previousReading = BigDecimal(previous),
        currentReading = BigDecimal(current),
    )
}

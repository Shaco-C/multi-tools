package com.pockettoolbox.feature.electricity.domain
import com.pockettoolbox.core.common.money.Money
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AllocationFormulaTest {
    @Test fun formulasUseActualAllocatedCentsIncludingRemainderAndZeroUsage() {
        val bill = ElectricityAllocationCalculator().calculate(Money.ofCents(1), listOf("1", "1", "1", "0").mapIndexed { index, usage ->
            MeterReadingInput("$index", "户$index", index == 0, BigDecimal("10"), BigDecimal("10") + BigDecimal(usage))
        })
        val text = bill.shares.map { allocationFormula(bill.totalAmount, bill.totalUsage, it.previousReading, it.currentReading, it.usage, it.amount) }
        assertTrue(text[0].contains("0.003333 元"))
        assertTrue(text[0].contains("最终应付：0.01 元（按分币尾差分配）"))
        assertTrue(text[1].contains("最终应付：0.00 元（按分币尾差分配）"))
        assertTrue(text[3].contains("10 − 10 = 0 度"))
        assertFalse(text[3].contains("尾差"))
        assertEquals(1L, bill.shares.sumOf { it.amount.cents })
    }
}

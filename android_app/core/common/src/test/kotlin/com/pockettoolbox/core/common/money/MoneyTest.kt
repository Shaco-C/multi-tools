package com.pockettoolbox.core.common.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun `金额按分解析和格式化`() {
        val money = Money.parseYuan("100.25")

        assertEquals(10_025, money.cents)
        assertEquals("100.25", money.formatYuan())
    }

    @Test
    fun `整数金额补齐两位小数`() {
        assertEquals("100.00", Money.parseYuan("100").formatYuan())
    }

    @Test
    fun `拒绝三位小数`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Money.parseYuan("1.001")
        }

        assertEquals("金额最多保留两位小数", error.message)
    }

    @Test
    fun `拒绝超出Long范围的金额`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Money.parseYuan("999999999999999999999999999999.99")
        }

        assertEquals("金额过大", error.message)
    }

    @Test
    fun `拒绝科学计数法避免异常规模输入`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Money.parseYuan("1e1000000")
        }

        assertEquals("金额格式不正确", error.message)
    }
}

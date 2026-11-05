package com.pockettoolbox.feature.electricity.domain

import com.pockettoolbox.core.common.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

fun allocationFormula(total: Money, totalUsage: BigDecimal, previous: BigDecimal, current: BigDecimal, usage: BigDecimal, payable: Money): String {
    require(totalUsage.signum() > 0)
    fun BigDecimal.number() = stripTrailingZeros().toPlainString()
    val numerator = BigDecimal.valueOf(total.cents, 2).multiply(usage)
    val proportional = numerator.divide(totalUsage, 6, RoundingMode.HALF_UP).number()
    val exact = BigDecimal.valueOf(payable.cents, 2).multiply(totalUsage).compareTo(numerator) == 0
    return "本户用电：${current.number()} − ${previous.number()} = ${usage.number()} 度\n" +
        "全部用电：${totalUsage.number()} 度\n" +
        "按比例：${total.formatYuan()} × ${usage.number()} ÷ ${totalUsage.number()} ≈ $proportional 元\n" +
        "最终应付：${payable.formatYuan()} 元" + if (exact) "" else "（按分币尾差分配）"
}

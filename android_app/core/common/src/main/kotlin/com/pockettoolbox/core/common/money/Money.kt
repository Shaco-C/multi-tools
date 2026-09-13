package com.pockettoolbox.core.common.money

import java.math.BigDecimal
import java.math.RoundingMode

/** 金额始终以“分”保存，避免使用 Double 造成账单精度误差。 */
@JvmInline
value class Money private constructor(val cents: Long) {
    init {
        require(cents >= 0) { "金额不能为负数" }
    }

    fun formatYuan(): String = BigDecimal(cents)
        .movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString()

    companion object {
        val Zero = Money(0)

        fun ofCents(cents: Long): Money = Money(cents)

        fun parseYuan(text: String): Money {
            val normalized = text.trim()
            require(normalized.isNotEmpty()) { "请输入金额" }
            require(!normalized.startsWith("-")) { "金额不能为负数" }
            require(DECIMAL_PATTERN.matches(normalized)) { "金额格式不正确" }
            val yuan = normalized.toBigDecimalOrNull()
                ?: throw IllegalArgumentException("金额格式不正确")
            require(yuan.signum() >= 0) { "金额不能为负数" }
            require(yuan.scale().coerceAtLeast(0) <= 2) { "金额最多保留两位小数" }
            val cents = runCatching { yuan.movePointRight(2).longValueExact() }
                .getOrElse { throw IllegalArgumentException("金额过大") }
            return Money(cents)
        }

        private val DECIMAL_PATTERN = Regex("^\\d+(?:\\.\\d+)?$")
    }
}

package com.pockettoolbox.feature.electricity.data.local

import java.math.BigDecimal
import java.time.YearMonth

internal object ElectricityBillPersistenceValidator {
    fun validate(
        bill: ElectricityBillEntity,
        shares: List<ElectricityShareEntity>,
    ) {
        require(bill.id == 0L) { "新增账单不能预设主键" }
        validateContents(bill, shares)
    }

    fun validateExisting(
        bill: ElectricityBillEntity,
        shares: List<ElectricityShareEntity>,
    ) {
        require(bill.id > 0L) { "更新账单需要有效主键" }
        validateContents(bill, shares)
    }

    private fun validateContents(
        bill: ElectricityBillEntity,
        shares: List<ElectricityShareEntity>,
    ) {
        // 此处是数据库写入的最后一道边界，正常保存与备份恢复都会经过同一套校验。
        runCatching { YearMonth.parse(bill.billingMonth) }
            .getOrElse { throw IllegalArgumentException("账单月份格式应为 YYYY-MM") }
        require(bill.billingMonth.length == 7) { "账单月份格式应为 YYYY-MM" }
        require(bill.totalAmountCents >= 0L) { "总金额不能为负数" }
        require(bill.totalUsage.length <= MAX_NUMERIC_LENGTH) { "总用量格式不正确" }
        require(bill.createdAt > 0L) { "创建时间无效" }
        require(bill.updatedAt >= bill.createdAt) { "更新时间不能早于创建时间" }
        require((bill.note?.length ?: 0) <= MAX_NOTE_LENGTH) { "备注内容过长" }
        require(shares.isNotEmpty()) { "账单至少需要一条分摊明细" }
        require(shares.size <= MAX_HOUSEHOLDS) { "账单最多支持 $MAX_HOUSEHOLDS 户" }
        require(shares.count { it.isOwner } == 1) {
            "账单必须且只能有一条本人明细"
        }
        // 产品约定第一列永远代表本人；恢复外部 JSON 时也不能破坏该约定。
        require(shares.first().isOwner) { "第一条分摊明细必须是本人电表" }
        require(shares.all { it.id == 0L && it.billId == 0L }) {
            "写入的明细不能预设主键或账单主键"
        }
        require(shares.all { it.meterKey.isNotBlank() }) {
            "电表标识不能为空"
        }
        require(shares.all { it.meterKey.length <= MAX_METER_KEY_LENGTH }) { "电表标识过长" }
        require(shares.map { it.meterKey }.distinct().size == shares.size) {
            "同一账单内的电表标识不能重复"
        }
        require(shares.map { it.position } == shares.indices.toList()) {
            "明细顺序必须按列表从 0 开始且连续"
        }

        val totalUsage = bill.totalUsage.toDecimal("总用量格式不正确")
        require(totalUsage.signum() > 0) { "总用量必须大于 0" }

        var usageSum = BigDecimal.ZERO
        var amountSum = 0L
        shares.forEach { share ->
            require(share.label.isNotBlank()) { "电表名称不能为空" }
            require(share.label.length <= MAX_LABEL_LENGTH) { "电表名称过长" }
            require(
                share.previousReading.length <= MAX_NUMERIC_LENGTH &&
                    share.currentReading.length <= MAX_NUMERIC_LENGTH &&
                    share.usage.length <= MAX_NUMERIC_LENGTH
            ) { "${share.label}的读数内容过长" }
            require(share.allocatedAmountCents >= 0L) { "分摊金额不能为负数" }
            val previous = share.previousReading.toDecimal("${share.label}的上期读数格式不正确")
            val current = share.currentReading.toDecimal("${share.label}的本期读数格式不正确")
            val usage = share.usage.toDecimal("${share.label}的用量格式不正确")
            require(previous.signum() >= 0 && current.signum() >= 0) { "电表读数不能为负数" }
            require(current >= previous) { "${share.label}的本期读数不能小于上期读数" }
            require(usage.compareTo(current.subtract(previous)) == 0) {
                "${share.label}的用量快照与读数不一致"
            }
            usageSum = usageSum.add(usage)
            amountSum = try {
                Math.addExact(amountSum, share.allocatedAmountCents)
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("分摊金额合计超出允许范围")
            }
        }

        require(usageSum.compareTo(totalUsage) == 0) { "明细用量合计与账单总用量不一致" }
        require(amountSum == bill.totalAmountCents) { "明细金额合计与账单总金额不一致" }
    }

    private fun String.toDecimal(message: String): BigDecimal {
        val value = trim()
        require(DECIMAL_PATTERN.matches(value)) { message }
        return value.toBigDecimalOrNull() ?: throw IllegalArgumentException(message)
    }

    private const val MAX_HOUSEHOLDS = 20
    private const val MAX_LABEL_LENGTH = 30
    private const val MAX_METER_KEY_LENGTH = 100
    private const val MAX_NOTE_LENGTH = 1_000
    private const val MAX_NUMERIC_LENGTH = 32
    private val DECIMAL_PATTERN = Regex("^\\d+(?:\\.\\d+)?$")
}

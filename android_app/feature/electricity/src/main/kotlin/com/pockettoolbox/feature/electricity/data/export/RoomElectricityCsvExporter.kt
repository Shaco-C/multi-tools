package com.pockettoolbox.feature.electricity.data.export

import com.pockettoolbox.feature.electricity.data.local.ElectricityBillDao
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillWithShares
import com.pockettoolbox.feature.electricity.domain.ElectricityCsvExporter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class RoomElectricityCsvExporter(
    private val dao: ElectricityBillDao,
) : ElectricityCsvExporter {
    override suspend fun createCsv(): String = ElectricityCsvEncoder.encode(dao.getAllBills())
}

internal object ElectricityCsvEncoder {
    private val headers = listOf(
        "账单月份",
        "总电费(元)",
        "总用量(度)",
        "户序号",
        "电表标识",
        "电表名称",
        "是否本人",
        "上期读数",
        "本期读数",
        "本期用量(度)",
        "分摊金额(元)",
        "备注",
        "创建时间(UTC)",
        "更新时间(UTC)",
    )

    fun encode(bills: List<ElectricityBillWithShares>): String = buildString {
        // 写入 UTF-8 BOM，便于 Windows Excel 直接识别中文编码。
        append('\uFEFF')
        appendRow(headers.map { CsvCell(it, protectAsText = true) })
        bills.sortedBy { it.bill.billingMonth }.forEach { stored ->
            stored.orderedShares.forEach { share ->
                appendRow(
                    listOf(
                        CsvCell(stored.bill.billingMonth),
                        CsvCell(stored.bill.totalAmountCents.toYuan()),
                        CsvCell(stored.bill.totalUsage),
                        CsvCell((share.position + 1).toString()),
                        CsvCell(share.meterKey, protectAsText = true),
                        CsvCell(share.label, protectAsText = true),
                        CsvCell(if (share.isOwner) "是" else "否", protectAsText = true),
                        CsvCell(share.previousReading),
                        CsvCell(share.currentReading),
                        CsvCell(share.usage),
                        CsvCell(share.allocatedAmountCents.toYuan()),
                        CsvCell(stored.bill.note.orEmpty(), protectAsText = true),
                        CsvCell(Instant.ofEpochMilli(stored.bill.createdAt).toString()),
                        CsvCell(Instant.ofEpochMilli(stored.bill.updatedAt).toString()),
                    ),
                )
            }
        }
    }

    private fun StringBuilder.appendRow(cells: List<CsvCell>) {
        append(cells.joinToString(",") { it.encoded() })
        append("\r\n")
    }

    private fun CsvCell.encoded(): String {
        // 对可能被表格软件解释为公式的文本增加前缀；只影响导出文件，不修改数据库原值。
        val startsWithControlPrefix = value.firstOrNull()?.let { it in CONTROL_FORMULA_PREFIXES } == true
        val firstNonSpace = value.dropWhile { it == ' ' }.firstOrNull()
        val startsWithFormula = firstNonSpace?.let { it in VISIBLE_FORMULA_PREFIXES } == true
        val safeValue = if (protectAsText && (startsWithControlPrefix || startsWithFormula)) {
            "'$value"
        } else {
            value
        }
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }

    private data class CsvCell(
        val value: String,
        val protectAsText: Boolean = false,
    )

    private fun Long.toYuan(): String = BigDecimal.valueOf(this)
        .movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString()

    private val VISIBLE_FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    private val CONTROL_FORMULA_PREFIXES = setOf('\t', '\r', '\n')
}

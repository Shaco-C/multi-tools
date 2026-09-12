package com.pockettoolbox.feature.electricity.data.export

import com.pockettoolbox.feature.electricity.data.local.ElectricityBillEntity
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillWithShares
import com.pockettoolbox.feature.electricity.data.local.ElectricityShareEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectricityCsvEncoderTest {
    @Test
    fun `空数据仍导出带BOM和CRLF的表头`() {
        val csv = ElectricityCsvEncoder.encode(emptyList())

        assertTrue(csv.startsWith("\uFEFF\"账单月份\",\"总电费(元)\""))
        assertTrue(csv.endsWith("\r\n"))
        assertEquals(1, "\r\n".toRegex().findAll(csv).count())
    }

    @Test
    fun `每户各占一行且账单按月份升序`() {
        val september = bill("2026-09", 10_001L)
        val august = bill("2026-08", 8_000L)

        val csv = ElectricityCsvEncoder.encode(listOf(september, august))

        assertTrue(csv.indexOf("\"2026-08\"") < csv.indexOf("\"2026-09\""))
        assertTrue(csv.contains("\"100.01\""))
        assertEquals(5, "\r\n".toRegex().findAll(csv).count())
    }

    @Test
    fun `文本正确转义并阻止Excel公式注入`() {
        val dangerous = bill(
            month = "2026-09",
            totalCents = 10_000L,
            ownerLabel = "=SUM(A1:A2)",
            note = "@恶意,内容\n含\"引号\"",
        )

        val csv = ElectricityCsvEncoder.encode(listOf(dangerous))

        assertTrue(csv.contains("\"'=SUM(A1:A2)\""))
        assertTrue(csv.contains("\"'@恶意,内容\n含\"\"引号\"\"\""))

        val controlPrefixCsv = ElectricityCsvEncoder.encode(
            listOf(bill("2026-09", 10_000L, ownerLabel = "\t普通文本")),
        )
        assertTrue(controlPrefixCsv.contains("\"'\t普通文本\""))
    }

    private fun bill(
        month: String,
        totalCents: Long,
        ownerLabel: String = "我的电表",
        note: String? = null,
    ) = ElectricityBillWithShares(
        bill = ElectricityBillEntity(
            id = if (month == "2026-08") 1L else 2L,
            billingMonth = month,
            totalAmountCents = totalCents,
            totalUsage = "200",
            createdAt = 1_757_592_000_000L,
            updatedAt = 1_757_592_000_000L,
            note = note,
        ),
        shares = listOf(
            ElectricityShareEntity(
                id = 1L,
                billId = 2L,
                meterKey = "owner",
                position = 0,
                label = ownerLabel,
                isOwner = true,
                previousReading = "100",
                currentReading = "200",
                usage = "100",
                allocatedAmountCents = totalCents / 2,
            ),
            ElectricityShareEntity(
                id = 2L,
                billId = 2L,
                meterKey = "meter-2",
                position = 1,
                label = "第 2 户",
                isOwner = false,
                previousReading = "50",
                currentReading = "150",
                usage = "100",
                allocatedAmountCents = totalCents - totalCents / 2,
            ),
        ),
    )
}

package com.pockettoolbox.feature.electricity.presentation.calculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettoolbox.feature.electricity.domain.allocationFormula
import com.pockettoolbox.core.common.money.Money

@Composable
internal fun ElectricityBillConfirmation(preview: BillPreview, saving: Boolean, error: String?, confirm: () -> Unit, cancel: () -> Unit) {
    val bill = preview.allocation
    AlertDialog(onDismissRequest = { if (!saving) cancel() }, title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确认月度电费 · ${preview.month}")
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本期总电费 ${bill.totalAmount.formatYuan()} 元", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("户别 · 上期读数 → 本期读数", style = MaterialTheme.typography.labelMedium)
            bill.shares.forEach { Text("${it.label}：${it.previousReading.stripTrailingZeros().toPlainString()} → ${it.currentReading.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Text("全部用电：${bill.shares.joinToString(" + ") { it.usage.stripTrailingZeros().toPlainString() }} = ${bill.totalUsage.stripTrailingZeros().toPlainString()} 度", style = MaterialTheme.typography.bodySmall)
            bill.shares.forEach { share ->
                Text(share.label, fontWeight = FontWeight.Bold)
                Text(allocationFormula(bill.totalAmount, bill.totalUsage, share.previousReading, share.currentReading, share.usage, share.amount), style = MaterialTheme.typography.bodySmall)
            }
            Text("应付合计：${bill.shares.joinToString(" + ") { it.amount.formatYuan() }} = ${Money.ofCents(bill.shares.sumOf { it.amount.cents }).formatYuan()} 元", fontWeight = FontWeight.Bold)
        } }, confirmButton = { TextButton(onClick = confirm, enabled = !saving) { Text(if (saving) "正在保存…" else "确认保存") } },
        dismissButton = { TextButton(onClick = cancel, enabled = !saving) { Text("取消") } })
}

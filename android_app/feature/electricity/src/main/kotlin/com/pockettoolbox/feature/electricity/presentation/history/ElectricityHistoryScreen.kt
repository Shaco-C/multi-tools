package com.pockettoolbox.feature.electricity.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityBillSummary
import com.pockettoolbox.feature.electricity.presentation.ElectricityHeader
import com.pockettoolbox.feature.electricity.presentation.ElectricitySection
import com.pockettoolbox.feature.electricity.presentation.ElectricitySectionBar

@Composable
internal fun ElectricityHistoryRoute(
    repository: ElectricityBillRepository,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    onOpenBill: (Long) -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: ElectricityHistoryViewModel = viewModel(
        factory = ElectricityHistoryViewModelFactory(repository),
    ),
) {
    ElectricityHistoryScreen(
        state = viewModel.uiState,
        onBack = onBack,
        onNavigate = onNavigate,
        onOpenBill = onOpenBill,
        onOpenBackup = onOpenBackup,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun ElectricityHistoryScreen(
    state: ElectricityHistoryUiState,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    onOpenBill: (Long) -> Unit,
    onOpenBackup: () -> Unit,
    onRetry: () -> Unit,
) {
    val errorMessage = state.errorMessage
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) {
            item(key = "header") {
                ElectricityHeader(
                    onBack = onBack,
                    backDescription = "返回计算页",
                )
                ElectricitySectionBar(
                    active = ElectricitySection.History,
                    onSelect = onNavigate,
                )
                Spacer(Modifier.height(22.dp))
            }

            item(key = "backup-entry") {
                BackupEntryCard(
                    onClick = onOpenBackup,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    LoadingHistory()
                }

                errorMessage != null -> item(key = "error") {
                    HistoryError(message = errorMessage, onRetry = onRetry)
                }

                state.bills.isEmpty() -> item(key = "empty") {
                    EmptyHistory(onStartCalculating = { onNavigate(ElectricitySection.Calculator) })
                }

                else -> {
                    item(key = "summary") {
                        HistorySummaryCard(
                            count = state.bills.size,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                        Text(
                            text = "历史账单",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "按账单月份从近到远排列",
                            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(
                        items = state.bills,
                        key = ElectricityBillSummary::id,
                    ) { bill ->
                        HistoryBillCard(
                            bill = bill,
                            onClick = { onOpenBill(bill.id) },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    item(key = "bottom-space") {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ImportExport, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text("数据导出与恢复", fontWeight = FontWeight.Bold)
                Text("导出 JSON / CSV，或恢复电费备份", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun HistorySummaryCard(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(ToolboxColors.NavyDeep, Color(0xFF0B6864)),
                ),
                RoundedCornerShape(24.dp),
            )
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = ToolboxColors.TealBright)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text("已保存 $count 个月", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("全部数据仅保存在当前设备", color = Color(0xFFB7CED4), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryBillCard(
    bill: ElectricityBillSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "${bill.billingMonth.year} 年 ${bill.billingMonth.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${bill.householdCount} 户 · 总用量 ${bill.totalUsage.stripTrailingZeros().toPlainString()} 度",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "我的费用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "¥ ${bill.ownerAmount.formatYuan()}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 13.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("本期总电费", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ ${bill.totalAmount.formatYuan()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("查看详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LoadingHistory() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = ToolboxColors.Teal)
    }
}

@Composable
private fun EmptyHistory(onStartCalculating: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "还没有历史账单",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "完成一次电费计算并保存后，账单会按月份出现在这里。",
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onStartCalculating,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text("去计算电费")
        }
    }
}

@Composable
private fun HistoryError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("历史账单读取失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
            Text("重新读取")
        }
    }
}

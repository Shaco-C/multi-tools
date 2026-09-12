package com.pockettoolbox.feature.electricity.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRecord
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityShareRecord
import com.pockettoolbox.feature.electricity.presentation.ElectricityHeader
import com.pockettoolbox.feature.electricity.presentation.ElectricitySection
import com.pockettoolbox.feature.electricity.presentation.ElectricitySectionBar
import java.math.RoundingMode

@Composable
internal fun ElectricityBillDetailRoute(
    billId: Long,
    repository: ElectricityBillRepository,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ElectricityBillDetailViewModel = viewModel(
        factory = ElectricityBillDetailViewModelFactory(billId, repository),
    ),
) {
    LaunchedEffect(viewModel.uiState.wasDeleted) {
        if (viewModel.uiState.wasDeleted) onDeleted()
    }
    ElectricityBillDetailScreen(
        state = viewModel.uiState,
        onBack = onBack,
        onNavigate = onNavigate,
        onEdit = { onEdit(billId) },
        onRetry = viewModel::retry,
        onRequestDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
    )
}

@Composable
private fun ElectricityBillDetailScreen(
    state: ElectricityBillDetailUiState,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val bill = state.bill
    val loadError = state.loadError
    BackHandler(enabled = state.isDeleting) {
        // 删除事务完成前保持当前页面，避免重复操作或错误返回。
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("删除这份账单？") },
            text = { Text("账单和全部分摊明细都会从本机永久删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, enabled = !state.isDeleting) {
                    Text(if (state.isDeleting) "正在删除…" else "确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete, enabled = !state.isDeleting) { Text("取消") }
            },
        )
    }

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
                    onBack = { if (!state.isDeleting) onBack() },
                    backDescription = "返回历史记录",
                )
                ElectricitySectionBar(
                    active = ElectricitySection.History,
                    onSelect = { section ->
                        if (!state.isDeleting) onNavigate(section)
                    },
                )
                Spacer(Modifier.height(22.dp))
            }

            when {
                state.isLoading -> item(key = "loading") {
                    DetailLoading()
                }

                loadError != null -> item(key = "error") {
                    DetailError(loadError, onRetry)
                }

                bill != null -> {
                    item(key = "summary") {
                        BillSummary(bill)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onEdit,
                                enabled = !state.isDeleting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text("修改账单", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = onRequestDelete,
                                enabled = !state.isDeleting,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                Text("删除", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        state.deleteError?.let { message ->
                            Text(
                                message,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text("分摊明细", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "保存时的电表名称、读数和金额快照",
                            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(items = bill.shares, key = ElectricityShareRecord::id) { share ->
                        ShareDetailCard(
                            share = share,
                            totalUsage = bill.totalUsage,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BillSummary(bill: ElectricityBillRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolboxColors.NavyDeep, RoundedCornerShape(24.dp))
            .padding(21.dp),
    ) {
        Text(
            "${bill.billingMonth.year} 年 ${bill.billingMonth.monthValue} 月账单",
            color = Color(0xFFB7CED4),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            "我的费用  ¥ ${bill.ownerShare.allocatedAmount.formatYuan()}",
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color.White.copy(alpha = 0.12f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("总电费 ¥ ${bill.totalAmount.formatYuan()}", color = Color(0xFFB7CED4), style = MaterialTheme.typography.bodySmall)
            Text(
                "总用量 ${bill.totalUsage.stripTrailingZeros().toPlainString()} 度",
                color = Color(0xFFB7CED4),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ShareDetailCard(
    share: ElectricityShareRecord,
    totalUsage: java.math.BigDecimal,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(share.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (share.isOwner) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 7.dp)
                                .size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text("¥ ${share.allocatedAmount.formatYuan()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("上期 ${share.previousReading.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodySmall)
                Text("本期 ${share.currentReading.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "用量 ${share.usage.stripTrailingZeros().toPlainString()} 度",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "占比 ${share.usage.divide(totalUsage, 4, RoundingMode.HALF_UP).movePointRight(2).stripTrailingZeros().toPlainString()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DetailLoading() {
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ToolboxColors.Teal)
    }
}

@Composable
private fun DetailError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("无法打开账单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 18.dp)) { Text("重新读取") }
    }
}

package com.pockettoolbox.feature.electricity.presentation.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.electricity.domain.MeterAllocation
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.presentation.ElectricityHeader
import com.pockettoolbox.feature.electricity.presentation.ElectricitySection
import com.pockettoolbox.feature.electricity.presentation.ElectricitySectionBar
import java.math.RoundingMode

@Composable
internal fun ElectricityCalculatorRoute(
    repository: ElectricityBillRepository,
    editingBillId: Long? = null,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    viewModel: ElectricityCalculatorViewModel = viewModel(
        factory = ElectricityCalculatorViewModelFactory(repository, editingBillId),
    ),
) {
    val requestExit = {
        when {
            viewModel.uiState.isSaving -> Unit
            viewModel.uiState.isDirty -> viewModel.requestExitConfirmation()
            else -> onBack()
        }
    }
    BackHandler(onBack = requestExit)

    if (viewModel.uiState.isLoading || viewModel.uiState.loadError != null) {
        ElectricityEditorLoadState(
            errorMessage = viewModel.uiState.loadError,
            onBack = onBack,
            onRetry = viewModel::retryLoad,
        )
        return
    }

    ElectricityCalculatorScreen(
        state = viewModel.uiState,
        onBack = requestExit,
        onNavigate = { section ->
            if (!viewModel.uiState.isSaving) onNavigate(section)
        },
        onMonthChange = viewModel::updateBillingMonth,
        onTotalAmountChange = viewModel::updateTotalAmount,
        onAddMeter = viewModel::addMeter,
        onRemoveLastMeter = viewModel::requestRemoveLastMeter,
        onRemoveMeter = viewModel::requestRemoveMeter,
        onMeterChange = viewModel::updateMeter,
        onConfirm = viewModel::saveBill,
        onConfirmRemoval = viewModel::confirmRemoval,
        onDismissRemoval = viewModel::dismissRemoval,
        onDiscardAndExit = {
            viewModel.dismissExitConfirmation()
            onBack()
        },
        onDismissExit = viewModel::dismissExitConfirmation,
        onRetryPreviousReadings = viewModel::retryPreviousReadings,
    )
}

@Composable
private fun ElectricityCalculatorScreen(
    state: ElectricityCalculatorUiState,
    onBack: () -> Unit,
    onNavigate: (ElectricitySection) -> Unit,
    onMonthChange: (String) -> Unit,
    onTotalAmountChange: (String) -> Unit,
    onAddMeter: () -> Unit,
    onRemoveLastMeter: () -> Unit,
    onRemoveMeter: (String) -> Unit,
    onMeterChange: (String, (MeterDraft) -> MeterDraft) -> Unit,
    onConfirm: () -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onDismissExit: () -> Unit,
    onRetryPreviousReadings: () -> Unit,
) {
    val pendingRemoval = state.meters.firstOrNull { it.id == state.pendingRemovalId }
    if (pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = onDismissRemoval,
            title = { Text("删除${pendingRemoval.label}？") },
            text = {
                Text(
                    if (pendingRemoval.previousReading.isNotBlank() || pendingRemoval.currentReading.isNotBlank()) {
                        "该电表已有读数，删除后当前输入将丢失。"
                    } else {
                        "将从本期计算中移除该电表。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRemoval) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemoval) { Text("取消") }
            },
        )
    }

    if (state.showExitConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissExit,
            title = { Text("放弃本次输入？") },
            text = { Text("当前账单还没有保存，离开后本次输入将丢失。") },
            confirmButton = {
                TextButton(onClick = onDiscardAndExit) {
                    Text("放弃并返回", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExit) { Text("继续填写") }
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
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        ) {
            item(key = "header") {
                ElectricityHeader(onBack = onBack)
                if (state.editingBillId == null) {
                    ElectricitySectionBar(
                        active = ElectricitySection.Calculator,
                        onSelect = onNavigate,
                    )
                }
                Spacer(Modifier.height(22.dp))
                BillOverviewCard(
                    totalAmount = state.totalAmount,
                    month = state.billingMonth,
                    householdCount = state.meters.size,
                    onTotalAmountChange = onTotalAmountChange,
                    onMonthChange = onMonthChange,
                    onDecrease = onRemoveLastMeter,
                    onIncrease = onAddMeter,
                    enabled = !state.isSaving,
                    isEditing = state.editingBillId != null,
                )
            }

            item(key = "meter-heading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("电表读数", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "系统会按实际用量自动计算",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${state.allocation?.totalUsage?.stripTrailingZeros()?.toPlainString() ?: "--"} 度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item(key = "owner-meter-guide") {
                PreviousReadingsStatus(
                    state = state,
                    onRetry = onRetryPreviousReadings,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OwnerMeterGuide(modifier = Modifier.padding(bottom = 12.dp))
            }

            itemsIndexed(
                items = state.meters,
                key = { _, meter -> meter.id },
            ) { index, meter ->
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    MeterCard(
                        index = index,
                        meter = meter,
                        allocation = state.allocation?.shares?.getOrNull(index),
                        onRemove = { onRemoveMeter(meter.id) },
                        onChange = { transform -> onMeterChange(meter.id, transform) },
                        enabled = !state.isSaving,
                    )
                }
            }

            item(key = "add-meter") {
                OutlinedButton(
                    onClick = onAddMeter,
                    enabled = !state.isSaving && state.meters.size < 20,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加一户", modifier = Modifier.padding(start = 6.dp))
                }
            }

            state.validationMessage?.takeIf { state.isDirty }?.let { message ->
                item(key = "validation") {
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item(key = "result") {
                ResultPanel(
                    state = state,
                    onConfirm = onConfirm,
                    modifier = Modifier.padding(top = 20.dp, bottom = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviousReadingsStatus(
    state: ElectricityCalculatorUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (
        state.editingBillId != null ||
        (!state.isLoadingPreviousReadings && state.previousReadingsNotice == null && state.previousReadingsError == null)
    ) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isLoadingPreviousReadings) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (state.previousReadingsError == null) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = when {
                    state.isLoadingPreviousReadings -> "正在读取上个月的电表数据…"
                    state.previousReadingsError != null -> state.previousReadingsError
                    else -> state.previousReadingsNotice.orEmpty()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp),
                color = if (state.previousReadingsError == null) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.previousReadingsError != null) {
                TextButton(onClick = onRetry, enabled = !state.isSaving) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun OwnerMeterGuide(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ToolboxColors.TealSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF08716B),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = "第一户固定代表你",
                color = Color(0xFF075F5A),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "请在第 1 户填写你的电表读数；“我的费用”和后续趋势都会以这一户为准。",
                modifier = Modifier.padding(top = 3.dp),
                color = Color(0xFF466A6C),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BillOverviewCard(
    totalAmount: String,
    month: String,
    householdCount: Int,
    onTotalAmountChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    enabled: Boolean,
    isEditing: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolboxColors.NavyDeep, RoundedCornerShape(25.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (isEditing) "修改已保存账单 · 本期总电费" else "本期总电费",
            color = Color(0xFF9CB5BF),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¥", color = ToolboxColors.TealBright, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = totalAmount,
                onValueChange = onTotalAmountChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = darkFieldColors(),
                enabled = enabled,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("账单月份", color = Color(0xFF9CB5BF), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = month,
                    onValueChange = onMonthChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    singleLine = true,
                    colors = darkFieldColors(),
                    enabled = enabled,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("分摊户数", color = Color(0xFF9CB5BF), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(56.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDecrease, enabled = enabled && householdCount > 1) {
                        Icon(Icons.Default.Remove, contentDescription = "减少一户", tint = ToolboxColors.TealBright)
                    }
                    Text("$householdCount", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onIncrease, enabled = enabled && householdCount < 20) {
                        Icon(Icons.Default.Add, contentDescription = "增加一户", tint = ToolboxColors.TealBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterCard(
    index: Int,
    meter: MeterDraft,
    allocation: MeterAllocation?,
    onRemove: () -> Unit,
    onChange: ((MeterDraft) -> MeterDraft) -> Unit,
    enabled: Boolean,
) {
    val borderColor = if (meter.isOwner) ToolboxColors.Teal else MaterialTheme.colorScheme.outline
    val borderWidth = if (meter.isOwner) 2.dp else 1.dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(21.dp)),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (meter.isOwner) {
                ToolboxColors.TealSoft.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(ToolboxColors.TealSoft, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = Color(0xFF08756F), fontWeight = FontWeight.ExtraBold)
                }
                OutlinedTextField(
                    value = meter.label,
                    onValueChange = { value -> onChange { it.copy(label = value) } },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    enabled = enabled,
                )
                if (meter.isOwner) {
                    Row(
                        modifier = Modifier
                            .background(ToolboxColors.TealSoft, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFF08716B),
                        )
                        Text(
                            text = "本人 · 固定",
                            modifier = Modifier.padding(start = 4.dp),
                            color = Color(0xFF08716B),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除${meter.label}")
                    }
                }
            }

            if (meter.isOwner) {
                Text(
                    text = "这张卡用于计算你的费用，不可删除。名称可以按需要修改。",
                    modifier = Modifier.padding(top = 7.dp, start = 38.dp),
                    color = Color(0xFF527170),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReadingField(
                    label = "上期读数",
                    value = meter.previousReading,
                    onValueChange = { value -> onChange { it.copy(previousReading = value) } },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                ReadingField(
                    label = "本期读数",
                    value = meter.currentReading,
                    onValueChange = { value -> onChange { it.copy(currentReading = value) } },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
            }

            if (allocation != null) {
                HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "用量 ${allocation.usage.stripTrailingZeros().toPlainString()} 度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${allocation.ratio.movePointRight(2).setScale(1, RoundingMode.HALF_UP)}%",
                        modifier = Modifier
                            .background(ToolboxColors.TealSoft, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF08756F),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "¥ ${allocation.amount.formatYuan()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = enabled,
    )
}

@Composable
private fun ResultPanel(
    state: ElectricityCalculatorUiState,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = state.allocation?.ownerShare
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ToolboxColors.NavyDeep, RoundedCornerShape(25.dp))
            .padding(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("我的本期费用", color = Color(0xFFA4BEC7))
            Text(
                "¥ ${owner?.amount?.formatYuan() ?: "--"}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = Color.White.copy(alpha = 0.10f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "总用量 ${state.allocation?.totalUsage?.stripTrailingZeros()?.toPlainString() ?: "--"} 度",
                color = Color(0xFFA8C0C8),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "占比 ${owner?.ratio?.movePointRight(2)?.setScale(1, RoundingMode.HALF_UP) ?: "--"}%",
                color = Color(0xFFA8C0C8),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onConfirm,
            enabled = state.allocation != null && !state.isSaving && state.savedBillId == null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.savedBillId != null) Color(0xFF14766F) else ToolboxColors.TealBright,
                contentColor = if (state.savedBillId != null) Color(0xFFD9FFF9) else ToolboxColors.NavyDeep,
                disabledContainerColor = when {
                    state.savedBillId != null -> Color(0xFF14766F)
                    state.isSaving -> ToolboxColors.TealBright.copy(alpha = 0.72f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
                disabledContentColor = when {
                    state.savedBillId != null -> Color(0xFFD9FFF9)
                    state.isSaving -> ToolboxColors.NavyDeep
                    else -> Color.White.copy(alpha = 0.42f)
                },
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (state.savedBillId != null) Icons.Default.Check else Icons.Default.ReceiptLong,
                    contentDescription = null,
                )
            }
            Text(
                when {
                    state.isSaving -> "正在保存到本机…"
                    state.savedBillId != null -> "本月账单已保存"
                    state.editingBillId != null -> "保存账单修改"
                    else -> "保存本月账单"
                },
                modifier = Modifier.padding(start = 7.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        state.saveError?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ElectricityEditorLoadState(
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElectricityHeader(onBack = onBack, backDescription = "返回账单详情")
            Spacer(Modifier.height(72.dp))
            if (errorMessage == null) {
                CircularProgressIndicator(color = ToolboxColors.Teal)
                Text(
                    "正在读取账单…",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("账单读取失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    errorMessage,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 18.dp)) {
                    Text("重新读取")
                }
            }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = ToolboxColors.TealBright,
    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
    cursorColor = ToolboxColors.TealBright,
)

package com.pockettoolbox.feature.electricity.presentation.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.core.backup.InvalidBackupException
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.feature.electricity.presentation.ElectricityHeader
import com.pockettoolbox.feature.electricity.domain.ElectricityCsvExporter
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ElectricityBackupRoute(
    contributor: BackupContributor,
    csvExporter: ElectricityCsvExporter,
    onBack: () -> Unit,
    viewModel: ElectricityBackupViewModel = viewModel(
        factory = ElectricityBackupViewModelFactory(contributor, csvExporter),
    ),
) {
    val context = LocalContext.current
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            viewModel.exportBackup { json -> context.contentResolver.writeUtf8(it, json) }
        }
    }
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.inspectImport { context.contentResolver.readUtf8Limited(it) }
        }
    }
    val createCsvDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let {
            viewModel.exportCsv { csv -> context.contentResolver.writeUtf8(it, csv) }
        }
    }

    ElectricityBackupScreen(
        state = viewModel.uiState,
        onBack = onBack,
        onExport = {
            createDocument.launch("随身工具箱-电费备份-${LocalDate.now()}.json")
        },
        onImport = { openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onExportCsv = {
            createCsvDocument.launch("随身工具箱-电费明细-${LocalDate.now()}.csv")
        },
        onDismissRestore = viewModel::dismissRestore,
        onConfirmRestore = viewModel::confirmRestore,
    )
}

@Composable
private fun ElectricityBackupScreen(
    state: ElectricityBackupUiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExportCsv: () -> Unit,
    onDismissRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
) {
    BackHandler(enabled = state.isWorking) {
        // 本地文件或数据库操作结束前保持页面存活，避免用户误以为任务已取消。
    }
    state.pendingInspection?.let { inspection ->
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text("恢复 ${inspection.recordCount} 个月账单？") },
            text = { Text("恢复会先清空当前电费工具的全部账单，再写入所选备份。其他工具的数据不受影响。此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = onConfirmRestore, enabled = !state.isWorking) {
                    Text(if (state.isWorking) "正在恢复…" else "确认替换", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestore, enabled = !state.isWorking) { Text("取消") }
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
        Column(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            ElectricityHeader(onBack = { if (!state.isWorking) onBack() }, backDescription = "返回历史记录")
            Text("数据导出与恢复", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "电费工具独立管理自己的数据文件",
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BackupActionCard(
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                title = "导出 JSON 备份",
                description = "包含全部账单、每户读数、分摊金额和时间信息。你可以保存到手机文件夹或发送到其他设备。",
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Button(onClick = onExport, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
                    Text("选择保存位置")
                }
            }
            BackupActionCard(
                icon = { Icon(Icons.Default.Upload, contentDescription = null) },
                title = "从 JSON 恢复",
                description = "选择文件后会先校验并显示账单数量，只有再次确认才会替换当前电费记录。",
                modifier = Modifier.padding(top = 14.dp),
            ) {
                OutlinedButton(onClick = onImport, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
                    Text("选择备份文件")
                }
            }
            BackupActionCard(
                icon = { Icon(Icons.Default.TableView, contentDescription = null) },
                title = "导出 CSV 明细",
                description = "每个月、每一户各占一行，适合用 Excel 查看、筛选或制作自己的统计表。CSV 仅用于查看，不用于恢复。",
                modifier = Modifier.padding(top = 14.dp),
            ) {
                OutlinedButton(onClick = onExportCsv, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
                    Text("选择 CSV 保存位置")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), RoundedCornerShape(17.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    "文件由 Android 系统选择器读写，本应用不申请存储目录权限，也不会上传到网络。JSON 和 CSV 均为未加密明文，请自行妥善保管；恢复只影响电费工具。",
                    modifier = Modifier.padding(start = 10.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.isWorking && state.pendingInspection == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ToolboxColors.Teal)
                    Text("正在处理本地文件…", Modifier.padding(start = 10.dp))
                }
            }
            state.message?.let {
                Text(
                    it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.errorMessage?.let {
                Text(
                    it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BackupActionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(ToolboxColors.TealSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Text(title, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                description,
                modifier = Modifier.padding(top = 12.dp, bottom = 15.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            action()
        }
    }
}

private suspend fun ContentResolver.writeUtf8(uri: Uri, content: String) = withContext(Dispatchers.IO) {
    val stream = openOutputStream(uri, "rwt") ?: error("无法打开所选保存位置")
    stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
}

private suspend fun ContentResolver.readUtf8Limited(uri: Uri): String = withContext(Dispatchers.IO) {
    val input = openInputStream(uri) ?: error("无法读取所选文件")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BACKUP_BYTES) {
                throw InvalidBackupException("备份文件超过 5 MB，已拒绝读取")
            }
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

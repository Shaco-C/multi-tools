package com.pockettoolbox.feature.diet.presentation

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.pockettoolbox.feature.diet.domain.DietBackupCodec
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Composable
internal fun DietBackupActions(vm: DietViewModel) {
    val context = LocalContext.current
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(true) { text -> context.contentResolver.writeText(uri, text) } }
    }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.export(false) { text -> context.contentResolver.writeText(uri, text) } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.inspect { context.contentResolver.readLimited(uri) } }
    }
    DietCard {
        Text("数据导出与恢复", fontWeight = FontWeight.Bold)
        Text("包含品牌、食品、类型、饮食与每次不适时间。数据留在本机。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { jsonExport.launch("随身工具箱-饮食不适-${LocalDate.now()}.json") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("导出 JSON 备份") }
        OutlinedButton(onClick = { csvExport.launch("随身工具箱-饮食不适明细-${LocalDate.now()}.csv") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV 明细") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("选择 JSON 备份恢复") }
        Text("恢复只替换此工具的数据，不影响电费账单。导出文件是明文，请妥善保管。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    vm.pendingRestore?.let { document ->
        AlertDialog(onDismissRequest = vm::dismissRestore, title = { Text("恢复 ${document.data.events.size} 条不适记录？") },
            text = { Text("包含 ${document.data.meals.size} 顿饮食和 ${document.data.brands.size} 个品牌。确认后会替换当前饮食不适工具的全部数据，请先导出当前备份。") },
            confirmButton = { TextButton(onClick = vm::restore, enabled = !vm.busy) { Text("确认替换", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::dismissRestore, enabled = !vm.busy) { Text("取消") } })
    }
}

private fun ContentResolver.writeText(uri: Uri, text: String) {
    val stream = requireNotNull(openOutputStream(uri, "wt")) { "无法打开目标文件" }
    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
}

private fun ContentResolver.readLimited(uri: Uri): String {
    val stream = requireNotNull(openInputStream(uri)) { "无法读取备份文件" }
    stream.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            require(output.size() + count <= DietBackupCodec.MAX_BYTES) { "备份文件不能超过 5 MB" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

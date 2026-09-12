package com.pockettoolbox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockettoolbox.core.designsystem.ToolboxColors
import com.pockettoolbox.core.navigation.ToolEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ToolboxHomeScreen(
    tools: List<ToolEntry>,
    onOpenTool: (String) -> Unit,
) {
    val featured = tools.firstOrNull()
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
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            HomeHeader()
            featured?.let { tool ->
                FeaturedToolCard(
                    tool = tool,
                    onClick = { onOpenTool(tool.route) },
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("全部工具", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${tools.size} 个可用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tools.forEach { tool ->
                    ToolTile(tool = tool, onClick = { onOpenTool(tool.route) })
                }
                ComingSoonTile()
            }
            TipCard(modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日 · EEEE", Locale.CHINA)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "你好",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "今天想用哪个小工具？",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(48.dp))
    }
}

@Composable
private fun FeaturedToolCard(
    tool: ToolEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(286.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(ToolboxColors.NavyDeep, ToolboxColors.NavySoft, Color(0xFF0C766F)),
                ),
            )
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.09f),
                contentColor = ToolboxColors.TealBright,
            ) {
                Box(contentAlignment = Alignment.Center) { tool.icon() }
            }
            tool.statusLabel?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFFB8FFF8),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column {
            Text("常用工具", color = Color(0xFF76DCD4), style = MaterialTheme.typography.labelMedium)
            Text(
                tool.title,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(tool.description, color = Color(0xFFBDD3DA), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("本地计算", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("数据仅保存在当前设备", color = Color(0xFF98B7C1), style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(ToolboxColors.TealBright, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "打开${tool.title}", tint = ToolboxColors.NavyDeep)
            }
        }
    }
}

@Composable
private fun ToolTile(tool: ToolEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = ToolboxColors.TealSoft,
                contentColor = Color(0xFF087871),
            ) {
                Box(contentAlignment = Alignment.Center) { tool.icon() }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(tool.title, fontWeight = FontWeight.Bold)
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ComingSoonTile() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text("下一个工具", fontWeight = FontWeight.Bold)
                Text("等待你的灵感", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}

@Composable
private fun TipCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE6FAF7), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color.White, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF096E68))
        }
        Column(Modifier.padding(start = 13.dp)) {
            Text("小提示", color = Color(0xFF086E68), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                "账单只保存在本机；记录和趋势由电费工具独立管理。",
                color = Color(0xFF466A6C),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

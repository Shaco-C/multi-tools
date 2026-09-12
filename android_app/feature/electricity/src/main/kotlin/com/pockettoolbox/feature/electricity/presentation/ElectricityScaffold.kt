package com.pockettoolbox.feature.electricity.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class ElectricitySection(val label: String) {
    Calculator("计算"),
    History("记录"),
    Trend("趋势"),
}

@Composable
internal fun ElectricityHeader(
    onBack: () -> Unit,
    backDescription: String = "返回工具箱",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "随身工具箱",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "电费分摊",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.size(48.dp))
    }
}

@Composable
internal fun ElectricitySectionBar(
    active: ElectricitySection,
    onSelect: (ElectricitySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ElectricitySection.entries.forEach { section ->
            val selected = section == active
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(section) },
                        role = Role.Tab,
                    )
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (section) {
                        ElectricitySection.Calculator -> Icons.Default.ReceiptLong
                        ElectricitySection.History -> Icons.Default.History
                        ElectricitySection.Trend -> Icons.Default.BarChart
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

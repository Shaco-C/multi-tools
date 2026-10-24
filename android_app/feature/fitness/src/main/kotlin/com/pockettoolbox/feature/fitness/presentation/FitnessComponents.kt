package com.pockettoolbox.feature.fitness.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies
import com.pockettoolbox.feature.fitness.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val FitnessEquipmentLabels = linkedMapOf(
    "floor" to "地面空间", "dumbbellPair" to "双哑铃", "dumbbellSingle" to "单哑铃", "longBand" to "长弹力带",
    "loopBand" to "短弹力圈", "bench" to "训练凳", "anchorHigh" to "高位固定点", "anchorChest" to "胸高固定点",
    "anchorLow" to "低位固定点", "wall" to "稳定墙面", "step" to "固定踏步台", "pullupBar" to "单杠",
    "barbell" to "杠铃及保护装置", "cable" to "绳索训练机", "machine" to "对应专用训练机", "kettlebell" to "壶铃",
    "ball" to "健身球", "medicineBall" to "药球", "foamRoll" to "泡沫轴", "stretchStrap" to "无弹性拉伸带",
)

@Composable
internal fun FitnessCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun FitnessHint(text: String, warning: Boolean = false) {
    Text(text, modifier = Modifier.fillMaxWidth().background(
        if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f),
        RoundedCornerShape(12.dp)).padding(13.dp), style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
}

@Composable
internal fun FitnessEmpty(text: String, action: (@Composable () -> Unit)? = null) {
    FitnessCard { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant); action?.invoke() }
}

@Composable
internal fun FitnessChoice(label: String, value: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(choices.firstOrNull { it.first == value }?.second ?: "请选择", modifier = Modifier.weight(1f)); Text("▾")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                choices.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(id) }) }
            }
        }
    }
}

@Composable
internal fun FitnessExerciseRow(vm: FitnessViewModel, exercise: FitnessExercise, onClick: () -> Unit, selected: Boolean? = null, onToggle: (() -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FitnessMuscle(vm.dependencies, exercise.primaryPart, exercise.id, Modifier.size(43.dp, 66.dp))
            FitnessMedia(vm.dependencies, exercise.images.lastOrNull(), vm.data.mediaGeneration, "${exercise.name}姿势图", Modifier.size(64.dp), width = 64.dp, height = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(exercise.name, fontWeight = FontWeight.Bold)
                Text("${exercise.purpose.label} · ${if (exercise.side == FitnessSide.Each) "每侧" else ""}${exercise.unit.label}", style = MaterialTheme.typography.labelSmall)
                Text(exercise.equipment.firstOrNull().orEmpty().joinToString("＋") { FitnessEquipmentLabels[it] ?: it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected != null && onToggle != null) Checkbox(selected, onCheckedChange = { onToggle() }, modifier = Modifier.size(48.dp))
        }
    }
}

private object FitnessBitmapCache {
    val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    val locks = List(16) { Mutex() }
}

private suspend fun fitnessBitmap(context: Context, dependencies: FitnessFeatureDependencies, image: FitnessImage, generation: String, width: Int, height: Int): Bitmap = withContext(Dispatchers.IO) {
    val key = "$generation/${image.path}/${image.panel}/${image.panels}/$width/$height"
    FitnessBitmapCache.locks[(key.hashCode() and Int.MAX_VALUE) % FitnessBitmapCache.locks.size].withLock {
        FitnessBitmapCache.cache.get(key) ?: run {
            val bitmap = if (image.path.endsWith(".svg")) {
                val svg = context.assets.open("fitness/${image.path}").use(SVG::getFromInputStream)
                val picture = svg.renderToPicture(width, height)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawPicture(picture) }
            } else {
                fun source() = if (image.path.startsWith("personal/")) dependencies.media.resolve(generation, image.path).inputStream() else context.assets.open("fitness/${image.path}")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                source().use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth >= image.panels && bounds.outHeight > 0) { "图片无法读取" }
                var sample = 1
                while (bounds.outWidth / image.panels / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
                val decoded = source().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?: throw IllegalArgumentException("图片解码失败")
                if (image.panels == 1) decoded else {
                    val left = decoded.width * image.panel / image.panels
                    Bitmap.createBitmap(decoded, left, 0, decoded.width * (image.panel + 1) / image.panels - left, decoded.height).also { if (it !== decoded) decoded.recycle() }
                }
            }
            FitnessBitmapCache.cache.put(key, bitmap)
            bitmap
        }
    }
}

private data class FitnessImageState(val bitmap: Bitmap? = null, val failed: Boolean = false)

@Composable
internal fun FitnessMedia(dependencies: FitnessFeatureDependencies, image: FitnessImage?, generation: String, description: String?, modifier: Modifier = Modifier,
    width: Dp = 320.dp, height: Dp = 320.dp, retryEnabled: Boolean = false, transparent: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pixels = with(density) { width.roundToPx().coerceAtLeast(1) to height.roundToPx().coerceAtLeast(1) }
    var retry by remember(image, generation) { mutableIntStateOf(0) }
    val state by produceState(FitnessImageState(), image, generation, pixels, retry) {
        value = if (image == null) FitnessImageState(failed = true) else try { FitnessImageState(fitnessBitmap(context, dependencies, image, generation, pixels.first, pixels.second)) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { FitnessImageState(failed = true) }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).then(if (transparent) Modifier else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)), contentAlignment = Alignment.Center) {
        val bitmap = state.bitmap
        if (bitmap != null) Image(bitmap.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else if (!state.failed) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (image == null) "暂无图片" else "图片暂不可用", style = MaterialTheme.typography.labelSmall)
            if (retryEnabled && image != null) TextButton(onClick = { retry++ }) { Text("重试") }
        }
    }
}

@Composable
internal fun FitnessMuscle(dependencies: FitnessFeatureDependencies, part: FitnessPart?, exerciseId: String? = null, modifier: Modifier = Modifier) {
    val (back, ids) = when (part) {
        FitnessPart.Chest -> false to listOf(4)
        FitnessPart.Back -> true to listOf(12, 9)
        FitnessPart.Shoulder -> false to if (exerciseId == "EX-SHO-005") emptyList() else listOf(2)
        FitnessPart.Biceps -> false to listOf(1)
        FitnessPart.Triceps -> true to listOf(5)
        FitnessPart.Core -> false to listOf(6, 14)
        FitnessPart.Glutes -> true to listOf(8)
        FitnessPart.Legs -> if (exerciseId == "EX-LEG-015") true to listOf(11) else false to listOf(10)
        FitnessPart.Calves -> true to listOf(7, 15)
        else -> false to emptyList()
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        FitnessMedia(dependencies, FitnessImage("muscles/${if (back) "back" else "front"}.svg"), "builtin", null, Modifier.fillMaxSize(), width = 100.dp, height = 145.dp)
        ids.forEach { id -> FitnessMedia(dependencies, FitnessImage("muscles/main-$id.svg"), "builtin", null, Modifier.fillMaxSize(), width = 100.dp, height = 145.dp, transparent = true) }
        if (ids.isEmpty()) Text("区域待补", style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .85f)))
    }
}

package com.pockettoolbox.feature.diet.domain

import com.pockettoolbox.core.backup.BackupInspection
import com.pockettoolbox.core.backup.InvalidBackupException
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DietBackupDocument(
    val format: String = "pocket-toolbox-backup",
    val toolId: String = "diet",
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val data: DietData,
)

object DietBackupCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(data: DietData, now: Long): String {
        DietRules.validate(data)
        val source = json.encodeToString(DietBackupDocument(exportedAt = now, data = data))
        requireBackup(source.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, "JSON 备份超过 5 MB，请先导出 CSV 明细")
        return source
    }

    fun decode(source: String): DietBackupDocument {
        requireBackup(source.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, "备份文件不能超过 5 MB")
        var depth = 0; var quoted = false; var escaped = false
        source.forEach { c ->
            if (quoted) {
                when { escaped -> escaped = false; c == '\\' -> escaped = true; c == '"' -> quoted = false }
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; requireBackup(depth <= 30, "备份文件嵌套过深") }
                '}', ']' -> depth--
            }
        }
        val document = try { json.decodeFromString<DietBackupDocument>(source) }
        catch (error: IllegalArgumentException) { throw InvalidBackupException("不是有效的饮食不适备份文件", error) }
        requireBackup(document.format == "pocket-toolbox-backup" && document.toolId == "diet" && document.schemaVersion == 1, "请选择此版本的饮食不适工具备份")
        requireBackup(document.exportedAt > 0, "备份导出时间无效")
        try { DietRules.validate(document.data) }
        catch (error: IllegalArgumentException) { throw InvalidBackupException(error.message ?: "备份数据无效", error) }
        return document
    }

    fun inspection(document: DietBackupDocument) = BackupInspection("diet", 1, document.data.events.size, document.exportedAt)

    fun csv(data: DietData): String {
        fun cell(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val meals = data.meals.associateBy { it.id }
        val brands = data.brands.associateBy { it.id }
        val foods = data.foods.associateBy { it.id }
        val types = data.types.associateBy { it.id }
        val header = listOf("品牌", "食品", "进食日期", "不适类型", "发生时间（含时区）", "备注", "本次记录状态")
        val rows = data.events.sortedByDescending { it.occurredAt }.map { e ->
            val meal = meals.getValue(e.mealId)
            listOf(brands.getValue(meal.brandId).name, foods.getValue(meal.foodId).name, meal.eatenOn, types.getValue(e.typeId).name,
                e.localTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), meal.note, if (meal.finished) "已结束" else "可追加")
        }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } } + "\r\n"
    }

    private fun requireBackup(condition: Boolean, message: String) { if (!condition) throw InvalidBackupException(message) }
}

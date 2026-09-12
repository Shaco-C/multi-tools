package com.pockettoolbox.feature.electricity.data.backup

import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.core.backup.BackupInspection
import com.pockettoolbox.core.backup.InvalidBackupException
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillDao
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillEntity
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillPersistenceValidator
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillWithShares
import com.pockettoolbox.feature.electricity.data.local.ElectricityBillWriteSnapshot
import com.pockettoolbox.feature.electricity.data.local.ElectricityShareEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ElectricityBackupContributor(
    private val dao: ElectricityBillDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : BackupContributor {
    override val toolId: String = TOOL_ID
    override val schemaVersion: Int = SCHEMA_VERSION

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    override suspend fun createBackupJson(): String {
        val document = ElectricityBackupDocument(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            toolId = toolId,
            schemaVersion = schemaVersion,
            exportedAt = currentTimeMillis(),
            bills = dao.getAllBills().map { it.toBackupBill() },
        )
        return json.encodeToString(document)
    }

    override fun inspectBackupJson(json: String): BackupInspection = decodeAndValidate(json).inspection

    override suspend fun restoreBackupJson(json: String): BackupInspection {
        val prepared = decodeAndValidate(json)
        dao.replaceAllBills(prepared.snapshots)
        return prepared.inspection
    }

    private fun decodeAndValidate(source: String): PreparedBackup {
        // 在反序列化前限制文件体积和嵌套深度，避免异常文件占用过多内存或解析时间。
        if (source.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            throw InvalidBackupException("备份文件超过 ${MAX_BACKUP_BYTES / 1024 / 1024} MB，已拒绝读取")
        }
        validateNestingDepth(source)
        val document = try {
            json.decodeFromString<ElectricityBackupDocument>(source)
        } catch (error: SerializationException) {
            throw InvalidBackupException("不是有效的随身工具箱电费备份文件", error)
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException("备份文件内容无法解析", error)
        }

        if (document.format != FORMAT || document.formatVersion != FORMAT_VERSION) {
            throw InvalidBackupException("不支持的备份文件格式")
        }
        if (document.toolId != toolId) {
            throw InvalidBackupException("该文件不是电费工具的备份")
        }
        if (document.schemaVersion != schemaVersion) {
            throw InvalidBackupException("暂不支持此备份版本：${document.schemaVersion}")
        }
        if (document.exportedAt <= 0L) {
            throw InvalidBackupException("备份导出时间无效")
        }
        if (document.bills.size > MAX_BILLS) {
            throw InvalidBackupException("备份账单数量超过上限 $MAX_BILLS")
        }
        if (document.bills.map { it.billingMonth }.distinct().size != document.bills.size) {
            throw InvalidBackupException("备份中存在重复账单月份")
        }

        // 复用数据库写入校验，保证导入数据与应用自身创建的数据遵守完全相同的规则。
        val snapshots = try {
            document.bills.map { it.toWriteSnapshot() }.also { items ->
                items.forEach { ElectricityBillPersistenceValidator.validate(it.bill, it.shares) }
            }
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException(error.message ?: "备份中的账单数据不完整", error)
        }
        return PreparedBackup(
            inspection = BackupInspection(
                toolId = toolId,
                schemaVersion = schemaVersion,
                recordCount = snapshots.size,
                exportedAt = document.exportedAt,
            ),
            snapshots = snapshots,
        )
    }

    private data class PreparedBackup(
        val inspection: BackupInspection,
        val snapshots: List<ElectricityBillWriteSnapshot>,
    )

    private companion object {
        const val FORMAT = "pocket-toolbox-backup"
        const val FORMAT_VERSION = 1
        const val TOOL_ID = "electricity"
        const val SCHEMA_VERSION = 1
        const val MAX_BILLS = 1_200
        const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
    }
}

private fun validateNestingDepth(source: String) {
    var depth = 0
    var inString = false
    var escaped = false
    source.forEach { character ->
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
        } else {
            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth += 1
                    if (depth > MAX_JSON_NESTING) throw InvalidBackupException("备份 JSON 嵌套层级过深")
                }
                '}', ']' -> depth -= 1
            }
        }
    }
}

private const val MAX_JSON_NESTING = 32

@Serializable
private data class ElectricityBackupDocument(
    val format: String,
    val formatVersion: Int,
    val toolId: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val bills: List<ElectricityBackupBill>,
)

@Serializable
private data class ElectricityBackupBill(
    val billingMonth: String,
    val totalAmountCents: Long,
    val totalUsage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val note: String? = null,
    val shares: List<ElectricityBackupShare>,
)

@Serializable
private data class ElectricityBackupShare(
    val meterKey: String,
    val position: Int,
    val label: String,
    val isOwner: Boolean,
    val previousReading: String,
    val currentReading: String,
    val usage: String,
    val allocatedAmountCents: Long,
)

private fun ElectricityBillWithShares.toBackupBill() = ElectricityBackupBill(
    billingMonth = bill.billingMonth,
    totalAmountCents = bill.totalAmountCents,
    totalUsage = bill.totalUsage,
    createdAt = bill.createdAt,
    updatedAt = bill.updatedAt,
    note = bill.note,
    shares = orderedShares.map { share ->
        ElectricityBackupShare(
            meterKey = share.meterKey,
            position = share.position,
            label = share.label,
            isOwner = share.isOwner,
            previousReading = share.previousReading,
            currentReading = share.currentReading,
            usage = share.usage,
            allocatedAmountCents = share.allocatedAmountCents,
        )
    },
)

private fun ElectricityBackupBill.toWriteSnapshot() = ElectricityBillWriteSnapshot(
    bill = ElectricityBillEntity(
        billingMonth = billingMonth,
        totalAmountCents = totalAmountCents,
        totalUsage = totalUsage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        note = note,
    ),
    shares = shares.map { share ->
        ElectricityShareEntity(
            meterKey = share.meterKey,
            position = share.position,
            label = share.label,
            isOwner = share.isOwner,
            previousReading = share.previousReading,
            currentReading = share.currentReading,
            usage = share.usage,
            allocatedAmountCents = share.allocatedAmountCents,
        )
    },
)

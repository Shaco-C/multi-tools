package com.pockettoolbox.core.backup

data class BackupInspection(
    val toolId: String,
    val schemaVersion: Int,
    val recordCount: Int,
    val exportedAt: Long,
)

/**
 * 每个具有本地数据的工具自行实现此契约。
 * 应用壳只负责文件选择和读写，不解析任何工具的业务字段。
 */
interface BackupContributor {
    val toolId: String
    val schemaVersion: Int

    suspend fun createBackupJson(): String

    fun inspectBackupJson(json: String): BackupInspection

    suspend fun restoreBackupJson(json: String): BackupInspection
}

class InvalidBackupException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

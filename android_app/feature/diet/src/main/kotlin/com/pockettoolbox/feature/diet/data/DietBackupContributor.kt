package com.pockettoolbox.feature.diet.data

import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.feature.diet.domain.DietBackupCodec
import com.pockettoolbox.feature.diet.domain.DietRepository

class DietBackupContributor(private val repository: DietRepository) : BackupContributor {
    override val toolId = "diet"
    override val schemaVersion = 1
    override suspend fun createBackupJson() = DietBackupCodec.encode(repository.snapshot(), System.currentTimeMillis())
    override fun inspectBackupJson(json: String) = DietBackupCodec.inspection(DietBackupCodec.decode(json))
    override suspend fun restoreBackupJson(json: String) = DietBackupCodec.decode(json).let {
        repository.replace(it.data)
        DietBackupCodec.inspection(it)
    }
}

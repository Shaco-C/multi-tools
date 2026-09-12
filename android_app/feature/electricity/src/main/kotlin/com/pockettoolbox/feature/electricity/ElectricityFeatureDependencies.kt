package com.pockettoolbox.feature.electricity

import android.content.Context
import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.feature.electricity.data.RoomElectricityBillRepository
import com.pockettoolbox.feature.electricity.data.backup.ElectricityBackupContributor
import com.pockettoolbox.feature.electricity.data.export.RoomElectricityCsvExporter
import com.pockettoolbox.feature.electricity.data.local.ElectricityDatabase
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityCsvExporter

class ElectricityFeatureDependencies private constructor(
    private val database: ElectricityDatabase,
    val repository: ElectricityBillRepository,
    val backupContributor: BackupContributor,
    val csvExporter: ElectricityCsvExporter,
) {
    fun close() {
        database.close()
    }

    companion object {
        fun create(context: Context): ElectricityFeatureDependencies {
            val database = ElectricityDatabase.open(context)
            return ElectricityFeatureDependencies(
                database = database,
                repository = RoomElectricityBillRepository(database.electricityBillDao()),
                backupContributor = ElectricityBackupContributor(database.electricityBillDao()),
                csvExporter = RoomElectricityCsvExporter(database.electricityBillDao()),
            )
        }
    }
}

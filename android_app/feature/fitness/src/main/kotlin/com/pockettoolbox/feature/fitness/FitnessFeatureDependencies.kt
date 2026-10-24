package com.pockettoolbox.feature.fitness

import android.content.Context
import com.pockettoolbox.feature.fitness.data.*
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FitnessFeatureDependencies private constructor(
    val repository: RoomFitnessRepository,
    val catalog: FitnessCatalog,
    val media: FitnessMediaStore,
    val backup: FitnessBackupService,
) {
    val available: Boolean get() = isEnabled
    private val initializeMutex = Mutex()
    private var initialized = false

    suspend fun initialize(): FitnessCatalogDocument = initializeMutex.withLock {
        if (!initialized) {
            repository.initialize()
            repository.operationMutex.withLock { media.cleanupExcept(repository.readData().mediaGeneration) }
            initialized = true
        }
        catalog.load()
    }

    companion object {
        val isEnabled: Boolean get() = BuildConfig.DEBUG || BuildConfig.FITNESS_RELEASE_CONTENT
        fun create(context: Context): FitnessFeatureDependencies {
            val app = context.applicationContext
            val repository = RoomFitnessRepository(FitnessDatabase.open(app))
            val media = FitnessMediaStore(File(app.filesDir, "fitness-media"))
            return FitnessFeatureDependencies(repository, FitnessCatalog(app), media,
                FitnessBackupService(repository, media, File(app.cacheDir, "fitness-backup")))
        }
    }
}

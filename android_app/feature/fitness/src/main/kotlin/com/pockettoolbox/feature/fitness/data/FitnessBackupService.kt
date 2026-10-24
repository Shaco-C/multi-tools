package com.pockettoolbox.feature.fitness.data

import com.pockettoolbox.feature.fitness.domain.FitnessImage
import com.pockettoolbox.feature.fitness.domain.FitnessValidation
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class FitnessBackupService(
    private val repository: RoomFitnessRepository,
    private val media: FitnessMediaStore,
    private val work: File,
) {
    suspend fun export(output: OutputStream, contentVersion: String, at: Long) = repository.operationMutex.withLock {
        val snapshot = repository.snapshotLocked()
        val files = FitnessArchive.referencedImages(snapshot.data).associateWith { media.resolve(snapshot.data.mediaGeneration, it) }
        // 持有工具操作锁保证快照与不可变媒体一致，ZIP文件IO不在Room事务内。
        FitnessArchive.write(snapshot, files, contentVersion, at, output, work) { media.validateImage(it) }
    }

    fun inspect(input: InputStream): InspectedFitnessArchive = FitnessArchive.inspect(input, work) { media.validateImage(it) }

    suspend fun restore(archive: InspectedFitnessArchive) = repository.operationMutex.withLock {
        FitnessValidation.snapshot(archive.snapshot)
        val generation = media.prepare(archive.images)
        try {
            repository.restoreLocked(archive.snapshot.copy(data = archive.snapshot.data.copy(mediaGeneration = generation)))
        } catch (error: Exception) {
            // 取消可能发生在提交边界；不能仅因协程抛错就删除已被数据库引用的新目录。
            withContext(NonCancellable) {
                val active = runCatching { repository.readData().mediaGeneration }.getOrNull()
                if (active != null && active != generation) media.discard(generation)
            }
            throw error
        }
        // 业务事务提交后才能清理旧代次；中断时启动清理只依据已提交指针。
        media.cleanupExcept(generation)
        archive.close()
    }

    suspend fun saveImage(input: InputStream): FitnessImage = repository.operationMutex.withLock {
        media.save(repository.readData().mediaGeneration, input)
    }
}

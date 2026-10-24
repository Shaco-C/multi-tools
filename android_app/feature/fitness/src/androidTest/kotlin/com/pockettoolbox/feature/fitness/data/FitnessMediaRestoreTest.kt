package com.pockettoolbox.feature.fitness.data

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pockettoolbox.feature.fitness.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真正使用Android图片解码与SQLite事务；需设备执行。 */
@RunWith(AndroidJUnit4::class)
class FitnessMediaRestoreTest {
    private lateinit var database: FitnessDatabase
    private lateinit var repository: RoomFitnessRepository
    private lateinit var directory: File
    private lateinit var media: FitnessMediaStore
    private lateinit var backup: FitnessBackupService
    private lateinit var picture: FitnessImage

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "fitness-media-test-").toFile()
        database = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        repository = RoomFitnessRepository(database); repository.initialize()
        media = FitnessMediaStore(File(directory, "media"))
        backup = FitnessBackupService(repository, media, File(directory, "work"))
        val bytes = ByteArrayOutputStream()
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.compress(Bitmap.CompressFormat.PNG, 100, bytes); it.recycle() }
        picture = backup.saveImage(ByteArrayInputStream(bytes.toByteArray()))
        repository.saveExercise(FitnessExercise("USR-media-test", "图片测试", purpose = FitnessPurpose.Strength, primaryPart = FitnessPart.Chest,
            unit = FitnessUnit.Reps, equipment = listOf(listOf("floor")), images = listOf(picture), personal = true))
    }
    @After fun close() { database.close(); directory.deleteRecursively() }
    private suspend fun exported(): ByteArray = ByteArrayOutputStream().also { backup.export(it, "test", 1) }.toByteArray()

    @Test fun fullArchiveRestoresPersonalImageAndSwitchesGeneration() = runBlocking {
        val old = media.resolve("initial", picture.path)
        val hash = FitnessArchive.sha256(old)
        val archive = backup.inspect(ByteArrayInputStream(exported()))
        backup.restore(archive)
        val restored = repository.currentData()
        assertTrue(restored.mediaGeneration.startsWith("gen-"))
        assertEquals(hash, FitnessArchive.sha256(media.resolve(restored.mediaGeneration, picture.path)))
        assertFalse(old.exists()); assertFalse(archive.directory.exists())
    }

    @Test fun failedDatabaseCommitKeepsOriginalPointerAndImage() = runBlocking {
        val before = repository.snapshot()
        val archive = backup.inspect(ByteArrayInputStream(exported()))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_fitness_restore BEFORE UPDATE ON fitness_settings BEGIN SELECT RAISE(ABORT, 'injected commit failure'); END")
        assertTrue(runCatching { backup.restore(archive) }.isFailure)
        assertEquals(before, repository.snapshot())
        assertTrue(media.resolve("initial", picture.path).exists())
        assertEquals(listOf("initial"), File(directory, "media").listFiles().orEmpty().filter { it.isDirectory }.map { it.name })
        archive.close()
    }
}

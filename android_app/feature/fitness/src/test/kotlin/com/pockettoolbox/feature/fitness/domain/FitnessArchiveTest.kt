package com.pockettoolbox.feature.fitness.domain

import com.pockettoolbox.feature.fitness.data.FitnessArchive
import com.pockettoolbox.feature.fitness.data.FitnessArchiveManifest
import com.pockettoolbox.feature.fitness.data.FitnessJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FitnessArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun snapshot(): FitnessSnapshot {
        val plan = testPlan()
        val events = completed(plan)
        return FitnessSnapshot(FitnessData(plans = listOf(plan), draft = FitnessDraft(plan, true, today, savedAt = 0,
            rawTargets = mapOf(plan.days.single().items.single().id to FitnessTargetInput("not-number", "")))), events)
    }
    private fun archive(snapshot: FitnessSnapshot = snapshot(), images: Map<String, File> = emptyMap()): ByteArray = ByteArrayOutputStream().also {
        FitnessArchive.write(snapshot, images, "test-content", fitnessClock.millis(), it, temporary.newFolder()) {}
    }.toByteArray()
    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().also { values ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; values[entry.name] = zip.readBytes(); zip.closeEntry() } }
    }
    private fun zip(values: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { target ->
        ZipOutputStream(target).use { zip -> values.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun inspect(bytes: ByteArray) = FitnessArchive.inspect(ByteArrayInputStream(bytes), temporary.newFolder()) {}

    @Test fun archiveRoundTripPreservesSnapshotsAndIncompleteDraftInput() {
        val plan = testPlan(); val event = completed(plan).single()
        val value = snapshot().let { it.copy(data = it.data.copy(plans = listOf(plan), draft = FitnessDraft(plan, true, today, savedAt = 0)), events = listOf(event.copy(status = FitnessEventStatus.Revoked, revokedAt = fitnessClock.millis()))) }
        inspect(archive(value)).use { imported -> assertEquals(value, imported.snapshot); assertEquals(1, imported.manifest.events); assertTrue(imported.directory.exists()) }
    }
    @Test fun rawDraftInputRemainsEditableAfterBackup() {
        val value = snapshot()
        inspect(archive(value)).use { assertEquals("not-number", it.snapshot.data.draft?.rawTargets?.values?.single()?.sets) }
    }
    @Test fun personalMediaIsASeparateVerifiedEntryAndStagingIsRemovedOnClose() {
        val picture = temporary.newFile().apply { writeText("platform decoder is injected") }
        val path = "personal/${FitnessArchive.sha256(picture)}.png"
        val personal = testExercise("USR-test").copy(personal = true, images = listOf(FitnessImage(path)))
        val value = FitnessSnapshot(FitnessData(personalExercises = listOf(personal)))
        val bytes = archive(value, mapOf(path to picture))
        val imported = inspect(bytes); val directory = imported.directory
        assertArrayEquals(picture.readBytes(), imported.images.getValue(path).readBytes())
        assertEquals(3, entries(bytes).size); imported.close(); assertFalse(directory.exists())
    }
    @Test fun rejectsBusinessMetadataHashMismatch() {
        val values = entries(archive()); values["snapshot.json"] = "{}".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { inspect(zip(values)) }
    }
    @Test fun rejectsMissingRequiredFile() {
        val values = entries(archive()); values.remove("snapshot.json")
        assertThrows(IllegalArgumentException::class.java) { inspect(zip(values)) }
    }
    @Test fun rejectsUnsupportedToolVersionBeforeRestore() {
        val values = entries(archive())
        val manifest = FitnessJson.decodeFromString<FitnessArchiveManifest>(values.getValue("manifest.json").decodeToString())
        values["manifest.json"] = FitnessJson.encodeToString(manifest.copy(version = 99)).toByteArray()
        assertThrows(IllegalArgumentException::class.java) { inspect(zip(values)) }
    }
    @Test fun rejectsManifestCountMismatch() {
        val values = entries(archive())
        val manifest = FitnessJson.decodeFromString<FitnessArchiveManifest>(values.getValue("manifest.json").decodeToString())
        values["manifest.json"] = FitnessJson.encodeToString(manifest.copy(events = 999)).toByteArray()
        assertThrows(IllegalArgumentException::class.java) { inspect(zip(values)) }
    }
    @Test fun rejectsPathTraversalAndCleansFailedStaging() {
        val work = temporary.newFolder()
        assertThrows(IllegalArgumentException::class.java) { FitnessArchive.inspect(ByteArrayInputStream(zip(mapOf("../escape" to byteArrayOf(1)))), work) {} }
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }
    @Test fun rejectsUnreferencedMediaEvenWithValidBusinessData() {
        val values = entries(archive()); values["media/${"a".repeat(64)}.png"] = byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { inspect(zip(values)) }
    }
    @Test fun rejectsExpandedOversizedImageBeforeDecoding() {
        val values = entries(archive()); values["media/${"a".repeat(64)}.png"] = ByteArray(FitnessArchive.MAX_IMAGE_BYTES.toInt() + 1)
        assertThrows(IllegalArgumentException::class.java) { inspect(zip(values)) }
    }
    @Test fun codecRejectsOrphanEventsAndOtherToolFormat() {
        assertThrows(IllegalArgumentException::class.java) { FitnessBackupCodec.write(FitnessSnapshot(events = completed()), ByteArrayOutputStream()) }
        assertThrows(IllegalArgumentException::class.java) { FitnessBackupCodec.read(ByteArrayInputStream("{\"format\":\"other\",\"schemaVersion\":1,\"snapshot\":{}}".toByteArray())) }
    }
    @Test fun boundedStreamsRejectOverflowWithoutBufferingEntireInput() {
        assertThrows(IllegalArgumentException::class.java) { FitnessLimitedInput(ByteArrayInputStream(ByteArray(5)), 4).readBytes() }
        assertThrows(IllegalArgumentException::class.java) { FitnessLimitedOutput(ByteArrayOutputStream(), 4).write(ByteArray(5)) }
    }
}

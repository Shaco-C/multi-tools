package com.pockettoolbox.feature.fitness.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.caverock.androidsvg.SVG
import com.pockettoolbox.feature.fitness.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Checks generated Android assets, not only repository source paths. */
@RunWith(RobolectricTestRunner::class)
class FitnessContentTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun packagedCatalogRetainsTheDevelopmentApprovalBoundary() = runBlocking {
        val catalog = FitnessCatalog(context).load()
        assertEquals(876, catalog.sourceRecords.size)
        assertEquals(16, catalog.exercises.size)
        assertEquals(0, catalog.reviewedCount)
        assertTrue(catalog.exercises.none { it.reviewed })
        context.assets.open("fitness/attributions/LICENSE.source.md").use { assertTrue(it.readBytes().isNotEmpty()) }
    }

    @Test fun everyCatalogImageIsAvailableOfflineAndDecodes() = runBlocking {
        val catalog = FitnessCatalog(context).load()
        val sources = catalog.sourceRecords.flatMap { it.images }.map { it.path }.distinct()
        assertEquals(1746, sources.size)
        val samples = catalog.exercises.flatMap { it.images }.map { it.path }.distinct()
        assertEquals(16, samples.size)
        for (path in sources + samples) {
            val bitmap = context.assets.open("fitness/$path").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
            }
            assertNotNull("Missing/undecodable asset: $path", bitmap)
            bitmap!!.recycle()
        }
    }

    @Test fun all876SourceRecordsAreAvailableForClassificationAndPlanning() = runBlocking {
        val catalog = FitnessCatalog(context).load()
        assertEquals(892, catalog.selectableExercises.size)
        assertEquals(892, catalog.selectableExercises.map { it.id }.distinct().size)
        for (source in catalog.sourceRecords) {
            val exercise = catalog.selectableExercises.single { it.id == "SRC-${source.id}" }
            FitnessRules.validateExercise(exercise)
            assertEquals(source.name, exercise.english)
            assertEquals(source.instructions, exercise.steps)
            assertEquals(source.images, exercise.images)
            assertFalse(exercise.reviewed)
            assertNotNull(exercise.primaryPart)
            val day = FitnessRules.addExercise(FitnessPlanDay("2026-10-25"), exercise)
            assertEquals(exercise.id, day.activeItems.single().exercise.exerciseId)
            assertEquals(exercise.snapshot(), day.activeItems.single().exercise)
        }
        assertTrue(FitnessPart.entries.filter { it != FitnessPart.Full }.all { part -> catalog.selectableExercises.any { it.purpose == FitnessPurpose.Strength && it.primaryPart == part } })
    }

    @Test fun unknownSourceEquipmentIsNotClassifiedAsBodyweight() = runBlocking {
        val catalog = FitnessCatalog(context).load()
        catalog.sourceRecords.filter { it.equipment == null || it.equipment == "other" }.forEach { source ->
            val exercise = catalog.selectableExercises.single { it.id == "SRC-${source.id}" }
            assertFalse(exercise.matchesEquipment(setOf("floor", "dumbbellPair", "longBand")))
        }
        val stretching = catalog.sourceRecords.first { it.category == "stretching" }
        val exercise = catalog.selectableExercises.single { it.id == "SRC-${stretching.id}" }
        assertEquals(FitnessPurpose.Stretch, exercise.purpose)
        assertEquals(FitnessUnit.Seconds, exercise.unit)
    }

    @Test fun sourceActionKeepsItsIdentityAndChosenMeasurementAcrossBackupRestore() = runBlocking {
        val firstDb = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val secondDb = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val origin = RoomFitnessRepository(firstDb)
            val target = RoomFitnessRepository(secondDb)
            origin.initialize()
            target.initialize()
            val catalog = FitnessCatalog(context).load()
            val source = catalog.selectableExercises.first { it.id.startsWith("SRC-") && it.purpose == FitnessPurpose.Strength }
            val base = FitnessRules.newPlan("2026-10-25")
            val day = FitnessRules.addExercise(base.days.single(), source)
            val item = day.items.single().let { it.copy(exercise = it.exercise.copy(unit = FitnessUnit.Seconds, side = FitnessSide.Each), targetSets = 3, targetValue = 30) }
            val plan = base.copy(days = listOf(day.copy(items = listOf(item))))
            origin.savePlan(plan)
            origin.favorite(source.id)
            val clock = Clock.fixed(Instant.parse("2026-10-25T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
            val first = origin.complete(FitnessCompletion(plan.id, plan.start, item.id, fitnessId()), clock).single()
            origin.revoke(first.id, clock.millis())
            val second = origin.complete(FitnessCompletion(plan.id, plan.start, item.id, fitnessId()), clock).single()
            origin.updateActual(second.id, FitnessActual(left = 25, right = 28))
            val folder = File(context.cacheDir, "source-restore-${fitnessId()}")
            val export = FitnessBackupService(origin, FitnessMediaStore(File(folder, "original-media")), File(folder, "export"))
            val restore = FitnessBackupService(target, FitnessMediaStore(File(folder, "restored-media")), File(folder, "restore"))
            val output = ByteArrayOutputStream()
            export.export(output, catalog.version, clock.millis())
            restore.restore(restore.inspect(ByteArrayInputStream(output.toByteArray())))
            val restored = target.snapshot()
            assertEquals(plan, restored.data.plans.single())
            assertEquals(setOf(source.id), restored.data.favorites)
            assertEquals(origin.snapshot().events, restored.events)
            assertEquals(FitnessActual(left = 25, right = 28), restored.events.single { it.isValid() }.actual)
            assertTrue(restored.data.personalExercises.isEmpty())
            FitnessValidation.snapshot(restored)
        } finally { firstDb.close(); secondDb.close() }
    }

    @Test fun everyMuscleLayerCanBeParsedAndRendered() {
        val paths = context.assets.list("fitness/muscles")!!.filter { it.endsWith(".svg") }
        assertEquals(17, paths.size)
        for (path in paths) {
            val svg = context.assets.open("fitness/muscles/$path").use(SVG::getFromInputStream)
            val picture = svg.renderToPicture(100, 145)
            assertEquals(path, 100, picture.width); assertEquals(path, 145, picture.height)
        }
    }
}

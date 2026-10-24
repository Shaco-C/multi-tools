package com.pockettoolbox.feature.fitness.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pockettoolbox.feature.fitness.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 需在另一台电脑通过connectedDebugAndroidTest执行；本机未冒充运行Room/KSP。 */
@RunWith(AndroidJUnit4::class)
class FitnessRepositoryTest {
    private lateinit var database: FitnessDatabase
    private lateinit var repository: RoomFitnessRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val exercise = FitnessExercise("test", "测试俯卧撑", purpose = FitnessPurpose.Strength, primaryPart = FitnessPart.Chest,
        secondaryParts = listOf(FitnessPart.Shoulder), unit = FitnessUnit.Reps, equipment = listOf(listOf("floor")))

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnessDatabase::class.java).build()
        repository = RoomFitnessRepository(database)
        repository.initialize()
    }
    @After fun close() { database.close() }
    private fun plan(date: String = "2026-10-24"): FitnessPlan {
        val value = FitnessRules.newPlan(date)
        val day = FitnessRules.addExercise(value.days.single(), exercise)
        return value.copy(days = listOf(day.copy(items = day.items.map { it.copy(targetSets = 4, targetValue = 12) })))
    }
    private fun command(plan: FitnessPlan, id: String = fitnessId(), count: Int = 1) = FitnessCompletion(plan.id, plan.start, plan.days.single().items.single().id, id, count,
        if (plan.start < "2026-10-24") FitnessTimeSource.DateOnly else FitnessTimeSource.Realtime)

    @Test fun duplicateConcurrentRequestIsPersistedOnce() = runBlocking {
        val plan = plan(); repository.savePlan(plan); val request = command(plan, "same-request")
        val results = coroutineScope { (1..8).map { async(Dispatchers.IO) { repository.complete(request, clock) } }.awaitAll() }
        assertEquals(1, results.map { it.single().id }.distinct().size)
        assertEquals(1, repository.planEvents(plan.id).size)
    }
    @Test fun concurrentUniqueRequestsCannotExceedTarget() = runBlocking {
        val plan = plan(); repository.savePlan(plan)
        val results = coroutineScope { (1..12).map { async(Dispatchers.IO) { runCatching { repository.complete(command(plan), clock) }.isSuccess } }.awaitAll() }
        assertEquals(4, results.count { it }); assertEquals(4, repository.planEvents(plan.id).size)
    }
    @Test fun batchUndoKeepsLaterIndependentRecordAndRenumbersValidRows() = runBlocking {
        val plan = plan("2026-10-23"); repository.savePlan(plan)
        repository.complete(command(plan, "batch", 2), clock)
        val later = repository.complete(command(plan), clock).single()
        repository.revokeBatch("batch", clock.millis())
        val rows = repository.planEvents(plan.id)
        assertEquals(3, rows.size); assertEquals(1, rows.count { it.isValid() })
        assertEquals(later.id, rows.single { it.isValid() }.id); assertEquals(1, rows.single { it.isValid() }.ordinal)
    }
    @Test fun invalidRestorePreservesOriginalSnapshot() = runBlocking {
        val plan = plan(); repository.savePlan(plan); repository.complete(command(plan), clock)
        val before = repository.snapshot()
        assertTrue(runCatching { repository.replace(before.copy(data = before.data.copy(plans = emptyList()))) }.isFailure)
        assertEquals(before, repository.snapshot())
    }
    @Test fun historyPreventsRemovingOrMovingAnItem() = runBlocking {
        val plan = plan(); repository.savePlan(plan); repository.complete(command(plan), clock)
        val changed = plan.copy(days = listOf(FitnessPlanDay(plan.start, FitnessDayKind.Rest)))
        assertTrue(runCatching { repository.savePlan(changed) }.isFailure)
        assertEquals(1, repository.planEvents(plan.id).size)
    }
    @Test fun aggregateQueriesExcludeRevokedAndKeepActualSeparateFromTarget() = runBlocking {
        val plan = plan(); repository.savePlan(plan)
        val first = repository.complete(command(plan), clock).single(); repository.complete(command(plan), clock)
        repository.updateActual(first.id, FitnessActual(total = 9))
        val filter = FitnessFilter(plan.start, plan.end)
        val count = repository.observeCounts(filter, true).first().single()
        assertEquals(9L, count.actualTotal); assertEquals(1, count.totalFilled); assertEquals(2, count.segments)
        repository.revoke(first.id, clock.millis())
        assertEquals(1, repository.observeSummary(filter).first().strengthSets)
        assertEquals(0L, repository.observeCounts(filter, true).first().single().actualTotal)
    }
    @Test fun deleteCascadesEventsAndRecoveryLinks() = runBlocking {
        val plan = plan(); repository.savePlan(plan); repository.complete(command(plan), clock)
        assertNotNull(repository.recovery(listOf(FitnessPart.Shoulder), plan.start).single().event)
        repository.deletePlan(plan.id)
        assertTrue(repository.snapshot().events.isEmpty())
        assertNull(repository.recovery(listOf(FitnessPart.Shoulder), plan.start).single().event)
    }
    @Test fun finalSaveClearsReplacedDraftEvenBeforeNewAutosave() = runBlocking {
        val old = plan("2026-10-23")
        repository.saveDraft(FitnessDraft(old, false, old.start, savedAt = clock.millis()))
        val replacement = plan()
        repository.savePlan(replacement, clearDraft = true)
        assertNull(repository.currentData().draft)
    }
}

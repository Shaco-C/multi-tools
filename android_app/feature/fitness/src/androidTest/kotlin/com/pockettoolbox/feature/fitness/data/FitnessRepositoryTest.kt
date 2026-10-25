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

/** Shared Room contracts: host Robolectric with native SQLite, or connectedDebugAndroidTest. */
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

    @Test fun dailyCountsIncludeArchivedHistoryAndExcludeRevokedRecords() = runBlocking {
        val first = plan(); val second = plan(); val yesterday = plan("2026-10-23")
        listOf(first, second, yesterday).forEach { repository.savePlan(it) }
        val revoked = repository.complete(command(first), clock).single()
        repository.complete(command(second), clock); repository.complete(command(yesterday), clock)
        repository.archive(second.id, true)
        val filter = FitnessFilter("2026-10-23", "2026-10-24")
        assertEquals(mapOf("2026-10-23" to 1, "2026-10-24" to 2), repository.observeDailyCounts(filter).first())
        repository.revoke(revoked.id, clock.millis())
        assertEquals(1, repository.observeDailyCounts(filter).first()["2026-10-24"])
        assertEquals(mapOf("2026-10-24" to 1), repository.observeDailyCounts(filter.copy(planId = second.id)).first())
        assertTrue(repository.observeDailyCounts(filter.copy(purpose = FitnessPurpose.Cardio)).first().isEmpty())
    }

    @Test fun reusedRequestWithAnotherPlanIsRejectedWithoutChangingEitherPlan() = runBlocking {
        val first = plan(); val second = plan(); repository.savePlan(first); repository.savePlan(second)
        repository.complete(command(first, "request"), clock)
        assertTrue(runCatching { repository.complete(command(second, "request"), clock) }.isFailure)
        assertEquals(1, repository.planEvents(first.id).size); assertTrue(repository.planEvents(second.id).isEmpty())
    }

    @Test fun stoppedAndRevokedItemCanBeResumedWithoutChangingItsIdentity() = runBlocking {
        val plan = plan(); repository.savePlan(plan)
        val event = repository.complete(command(plan), clock).single()
        repository.revoke(event.id, clock.millis())
        repository.stopItem(plan.id, plan.start, event.itemId)
        val stopped = repository.currentData().plans.single()
        val resumed = stopped.copy(days = listOf(FitnessRules.addExercise(stopped.days.single(), exercise)))
        repository.savePlan(resumed)
        val next = repository.complete(command(resumed), clock).single()
        assertEquals(event.itemId, next.itemId); assertEquals(1, next.ordinal)
        assertEquals(2, repository.planEvents(plan.id).size)
        assertFalse(repository.planEvents(plan.id).first().isValid())
    }

    @Test fun midnightContinuationKeepsTrainingDateAndNewOperationTime() = runBlocking {
        val plan = plan(); repository.savePlan(plan)
        val afterMidnight = Clock.fixed(Instant.parse("2026-10-24T16:01:00Z"), clock.zone)
        val event = repository.complete(command(plan).copy(continuePreviousDay = true), afterMidnight).single()
        assertEquals("2026-10-24", event.trainingDate); assertEquals(afterMidnight.millis(), event.recordedAt)
        FitnessValidation.snapshot(repository.snapshot())
        assertEquals(mapOf("2026-10-24" to 1), repository.observeDailyCounts(FitnessFilter("2026-10-24", "2026-10-25")).first())
    }

    @Test fun personalEditsDoNotRewriteExistingPlanOrEventSnapshots() = runBlocking {
        val personal = exercise.copy(id = "USR-test", personal = true)
        repository.saveExercise(personal)
        val value = FitnessRules.newPlan("2026-10-24")
        val day = FitnessRules.addExercise(value.days.single(), personal)
        val plan = value.copy(days = listOf(day.copy(items = day.items.map { it.copy(targetSets = 4, targetValue = 12) })))
        repository.savePlan(plan); val event = repository.complete(command(plan), clock).single()
        repository.saveExercise(personal.copy(name = "新的名称", primaryPart = FitnessPart.Legs, archived = true))
        assertEquals(event.exercise, repository.planEvents(plan.id).single().exercise)
        assertEquals(FitnessPart.Chest, repository.currentData().plans.single().days.single().items.single().exercise.primaryPart)
    }

    @Test fun foreignItemIdentityCannotStealAnotherPlansRecords() = runBlocking {
        val first = plan(); repository.savePlan(first); repository.complete(command(first), clock)
        val other = first.copy(id = fitnessId(), name = "另一份计划")
        assertTrue(runCatching { repository.savePlan(other) }.isFailure)
        assertEquals(listOf(first.id), repository.currentData().plans.map { it.id })
        assertEquals(1, repository.planEvents(first.id).size)
    }

    @Test fun staleDraftCannotSilentlyUnarchiveItsPlan() = runBlocking {
        val plan = plan(); repository.savePlan(plan)
        val draft = FitnessDraft(plan, true, plan.start, savedAt = clock.millis())
        repository.saveDraft(draft); repository.archive(plan.id, true)
        assertTrue(runCatching { repository.savePlan(draft.plan, clearDraft = true) }.isFailure)
        assertEquals(FitnessPlanStatus.Archived, repository.currentData().plans.single().status)
        assertEquals(draft, repository.currentData().draft)
        repository.archive(plan.id, false); repository.savePlan(draft.plan, clearDraft = true)
        assertNull(repository.currentData().draft)
    }
}

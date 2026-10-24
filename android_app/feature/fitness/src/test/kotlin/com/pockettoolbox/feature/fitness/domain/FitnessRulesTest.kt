package com.pockettoolbox.feature.fitness.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

internal val fitnessClock: Clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("Asia/Shanghai"))
internal const val today = "2026-10-24"
internal fun testExercise(id: String = "EX-CHE-001", part: FitnessPart = FitnessPart.Chest) = FitnessExercise(
    id, "测试动作", purpose = FitnessPurpose.Strength, primaryPart = part, secondaryParts = listOf(FitnessPart.Shoulder),
    unit = FitnessUnit.Reps, equipment = listOf(listOf("floor")),
)
internal fun testPlan(date: String = today, exercise: FitnessExercise = testExercise()): FitnessPlan {
    val plan = FitnessRules.newPlan(date)
    val day = FitnessRules.addExercise(plan.days.single(), exercise)
    return plan.copy(name = "测试计划", days = listOf(day.copy(items = day.items.map { it.copy(targetSets = 3, targetValue = 12) })))
}
internal fun completed(plan: FitnessPlan = testPlan(), count: Int = 1, source: FitnessTimeSource = FitnessTimeSource.Realtime): List<FitnessSetEvent> {
    val day = plan.days.single()
    return FitnessRules.complete(plan, day, day.items.single(), 0,
        FitnessCompletion(plan.id, day.date, day.items.single().id, "request", count, source), fitnessClock, 1)
}

class FitnessRulesTest {
    @Test fun projectUsesCanonicalCalendarDays() {
        assertEquals(listOf("2028-02-28", "2028-02-29", "2028-03-01"), FitnessRules.dates("2028-02-28", "2028-03-01"))
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.date("2026-02-29") }
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.date("2026-2-01") }
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.dates("2026-10-24", "2026-10-23") }
    }

    @Test fun planRangeIncludesBothEndsAndRejectsOversize() {
        assertEquals(366, FitnessRules.dates("2024-01-01", "2024-12-31").size)
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.dates("2024-01-01", "2025-01-01") }
        assertEquals(1, FitnessRules.newPlan(today).days.size)
    }

    @Test fun inputsDoNotTurnBlankZeroDecimalOrExponentIntoAValidTarget() {
        for (value in listOf("", " ", "0", "-1", "1.2", "1e2", "999999999999999999")) {
            assertThrows(IllegalArgumentException::class.java) { FitnessRules.positive(value, 99, "组数") }
        }
        assertEquals(4, FitnessRules.positive(" 4 ", 99, "组数"))
    }

    @Test fun dumbbellPairSatisfiesSingleButNotMissingBench() {
        val exercise = testExercise().copy(equipment = listOf(listOf("dumbbellSingle", "bench")))
        assertTrue(exercise.matchesEquipment(setOf("dumbbellPair", "bench")))
        assertFalse(exercise.matchesEquipment(setOf("dumbbellPair")))
        assertFalse(exercise.copy(equipment = listOf(listOf("dumbbellPair"))).matchesEquipment(setOf("dumbbellSingle")))
        assertFalse(exercise.copy(equipment = emptyList()).matchesEquipment(emptySet()))
    }

    @Test fun planPartsNeedTheirOwnPrimaryActions() {
        val plan = testPlan()
        val day = plan.days.single().copy(parts = listOf(FitnessPart.Chest, FitnessPart.Shoulder))
        val issues = FitnessRules.issues(plan.copy(days = listOf(day)), emptyList())
        assertTrue(issues.any { it.field == "part:Shoulder" && it.date == today })
    }

    @Test fun incompleteTargetsCanBeInspectedButNotActivated() {
        val plan = testPlan()
        val day = plan.days.single().let { it.copy(items = it.items.map { item -> item.copy(targetValue = null) }) }
        val incomplete = plan.copy(days = listOf(day))
        assertTrue(FitnessRules.issues(incomplete, emptyList()).any { it.field == "value" })
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validatePlan(incomplete, null, emptyList()) }
        FitnessValidation.draft(FitnessDraft(incomplete.copy(name = "", start = ""), false, today, savedAt = fitnessClock.millis()))
    }

    @Test fun confirmationDoesNotFabricateActualRepetitions() {
        val plan = testPlan()
        val event = completed(plan).single()
        assertEquals(FitnessActual(), event.actual)
        assertNull(event.actualAt)
        assertEquals(12, event.targetValue)
        assertEquals("Asia/Shanghai", event.zoneId)
        assertEquals(28800, event.offsetSeconds)
        assertEquals(0, FitnessRules.issues(plan, emptyList()).size)
    }

    @Test fun futureArchivedStoppedAndOverTargetAreRejected() {
        val plan = testPlan()
        val day = plan.days.single()
        val item = day.items.single()
        fun call(p: FitnessPlan = plan, d: FitnessPlanDay = day, i: FitnessPlanItem = item, count: Int = 0) = FitnessRules.complete(
            p, d, i, count, FitnessCompletion(p.id, d.date, i.id, "request"), fitnessClock, 1)
        assertThrows(IllegalArgumentException::class.java) { call(p = plan.copy(status = FitnessPlanStatus.Archived)) }
        assertThrows(IllegalArgumentException::class.java) { call(i = item.copy(status = FitnessItemStatus.Stopped)) }
        assertThrows(IllegalArgumentException::class.java) { call(count = 3) }
        val future = testPlan("2026-10-25")
        assertThrows(IllegalArgumentException::class.java) { completed(future) }
    }

    @Test fun pastDateRequiresExplicitSourceAndDoesNotInventTime() {
        val plan = testPlan("2026-10-23")
        assertThrows(IllegalArgumentException::class.java) { completed(plan) }
        val event = completed(plan, source = FitnessTimeSource.DateOnly).single()
        assertNull(event.actualAt)
        assertEquals("2026-10-23", event.trainingDate)
        assertEquals(fitnessClock.millis(), event.recordedAt)
    }

    @Test fun continuingOriginalDayAcrossMidnightNeedsExplicitContext() {
        val plan = testPlan("2026-10-23")
        val day = plan.days.single()
        val result = FitnessRules.complete(plan, day, day.items.single(), 0,
            FitnessCompletion(plan.id, day.date, day.items.single().id, "request", continuePreviousDay = true), fitnessClock, 1)
        assertEquals("2026-10-23", result.single().trainingDate)
    }

    @Test fun batchBackfillHasOneBatchIdentityAndIndependentRequests() {
        val plan = testPlan("2026-10-23")
        val events = completed(plan, 3, FitnessTimeSource.DateOnly)
        assertEquals(3, events.map { it.requestId }.distinct().size)
        assertEquals(listOf("request"), events.map { it.batchId }.distinct())
        assertEquals(listOf(1, 2, 3), events.map { it.ordinal })
        assertTrue(events.all { it.actual == FitnessActual() && it.actualAt == null })
        assertThrows(IllegalArgumentException::class.java) { completed(testPlan(), 2) }
    }

    @Test fun backfillMustStayWithinRemainingTarget() {
        val plan = testPlan("2026-10-23")
        val day = plan.days.single()
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.complete(plan, day, day.items.single(), 2,
            FitnessCompletion(plan.id, day.date, day.items.single().id, "request", 2, FitnessTimeSource.DateOnly), fitnessClock, 1) }
    }

    @Test fun revocationIsIdempotentAndLeavesAuditHistory() {
        val plan = testPlan("2026-10-23")
        val events = completed(plan, 2, FitnessTimeSource.DateOnly)
        val revoked = FitnessRules.revoke(events, setOf(events.last().id), fitnessClock.millis())
        assertEquals(1, revoked.count { it.isValid() })
        assertEquals(2, revoked.size)
        assertEquals(revoked, FitnessRules.revoke(revoked, setOf(events.last().id), fitnessClock.millis() + 100))
        assertEquals(2, events.count { it.isValid() })
    }

    @Test fun revokingAnEarlierBatchDoesNotRevokeLaterUnrelatedWork() {
        val plan = testPlan("2026-10-23")
        val first = completed(plan, 2, FitnessTimeSource.DateOnly)
        val day = plan.days.single()
        val later = FitnessRules.complete(plan, day, day.items.single(), 2,
            FitnessCompletion(plan.id, day.date, day.items.single().id, "later", timeSource = FitnessTimeSource.DateOnly), fitnessClock, 3)
        val result = FitnessRules.revoke(first + later, first.map { it.id }.toSet(), fitnessClock.millis())
        assertEquals(later.single().id, result.single { it.isValid() }.id)
        assertEquals(1, result.single { it.isValid() }.ordinal)
    }

    @Test fun actualValuesRespectSidesUnitsAndUnknowns() {
        val exercise = testExercise().copy(unit = FitnessUnit.Seconds, side = FitnessSide.Each).snapshot()
        FitnessRules.validateActual(exercise, FitnessActual(left = 25))
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validateActual(exercise, FitnessActual(total = 50)) }
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validateActual(exercise, FitnessActual(right = 0)) }
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validateActual(exercise, FitnessActual(right = 3601)) }
    }

    @Test fun removeAndReaddKeepsIdentityAndTargets() {
        val plan = testPlan()
        val day = plan.days.single()
        val readded = FitnessRules.addExercise(FitnessRules.stop(day, day.items.single().id), testExercise())
        assertEquals(day.items.single(), readded.items.single())
        assertEquals(FitnessDayKind.Training, readded.kind)
    }

    @Test fun revokedHistoryStillFreezesMeasurement() {
        val old = testPlan()
        val events = completed(old).map { it.copy(status = FitnessEventStatus.Revoked, revokedAt = fitnessClock.millis()) }
        val changed = old.copy(days = old.days.map { day -> day.copy(items = day.items.map { it.copy(exercise = it.exercise.copy(unit = FitnessUnit.Seconds)) }) })
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validatePlan(changed, old, events) }
    }

    @Test fun allRemainingWorkCanEndWithoutLosingHistory() {
        val old = testPlan()
        val ended = old.copy(days = old.days.map { FitnessRules.stop(it, it.items.single().id) })
        FitnessRules.validatePlan(ended, old, completed(old))
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validatePlan(ended, old, emptyList()) }
    }

    @Test fun sameDayDuplicateExerciseAndCrossDayDuplicateItemAreRejected() {
        val plan = testPlan()
        val day = plan.days.single()
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.issues(plan.copy(days = listOf(day.copy(items = day.items + day.items.single().copy(id = "other")))), emptyList()) }
        val range = plan.copy(end = "2026-10-25", days = listOf(day, day.copy(date = "2026-10-25")))
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.issues(range, emptyList()) }
    }

    @Test fun resizingPreservesOutOfRangeHistoryAsStopped() {
        val old = testPlan("2026-10-23")
        val events = completed(old, source = FitnessTimeSource.DateOnly)
        val resized = FitnessRules.resize(old, today, today, events)
        assertEquals(2, resized.days.size)
        assertTrue(resized.day("2026-10-23")!!.activeItems.isEmpty())
        assertEquals(old.days.single().items.single().id, resized.day("2026-10-23")!!.items.single().id)
    }

    @Test fun copyCreatesNewItemIdsAndNoEvents() {
        val source = testPlan()
        val plan = source.copy(end = "2026-10-26", days = source.days + listOf(FitnessPlanDay("2026-10-25"), FitnessPlanDay("2026-10-26")))
        val result = FitnessRules.copyDays(plan, source.days.single(), setOf("2026-10-25", "2026-10-26"), emptyList(), false)
        val ids = result.days.flatMap { it.items }.map { it.id }
        assertEquals(3, ids.distinct().size)
        assertEquals(3, result.day("2026-10-25")!!.items.single().targetSets)
    }

    @Test fun copyIntoAnyHistoricalTargetRejectsTheWholeOperation() {
        val source = testPlan("2026-10-23")
        val events = completed(source, source = FitnessTimeSource.DateOnly).map { it.copy(status = FitnessEventStatus.Revoked, revokedAt = fitnessClock.millis()) }
        val plan = source.copy(end = "2026-10-25", days = source.days + listOf(FitnessPlanDay(today), FitnessPlanDay("2026-10-25")))
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.copyDays(plan, source.days.single().copy(date = "external"), setOf(today, "2026-10-23"), events, true) }
        assertEquals(FitnessDayKind.Unplanned, plan.day(today)!!.kind)
    }

    @Test fun batchPartsRequiresExplicitOverwriteAndProtectsRevokedHistory() {
        val plan = testPlan()
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.batchParts(plan, setOf(today), FitnessDayKind.Rest, emptyList(), emptyList(), false) }
        val revoked = completed(plan).map { it.copy(status = FitnessEventStatus.Revoked, revokedAt = fitnessClock.millis()) }
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.batchParts(plan, setOf(today), FitnessDayKind.Rest, emptyList(), revoked, true) }
    }

    @Test fun snapshotRejectsOrphansDuplicateRequestsAndChangedSemantics() {
        val plan = testPlan()
        val events = completed(plan)
        val snapshot = FitnessSnapshot(FitnessData(plans = listOf(plan)), events)
        FitnessValidation.snapshot(snapshot)
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.snapshot(snapshot.copy(events = events + events.single().copy(id = "other", sequence = 2))) }
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.snapshot(snapshot.copy(events = events.map { it.copy(itemId = "missing") })) }
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.snapshot(snapshot.copy(events = events.map { it.copy(exercise = it.exercise.copy(side = FitnessSide.Each)) })) }
    }

    @Test fun personalExerciseCannotImpersonateBuiltinOrReferenceArbitraryFiles() {
        val exercise = testExercise().copy(personal = true)
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.snapshot(FitnessSnapshot(FitnessData(personalExercises = listOf(exercise)))) }
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validateExercise(exercise.copy(images = listOf(FitnessImage("../secret.png")))) }
    }

    @Test fun templateRequiresCompleteTargetsWithoutCompletionHistory() {
        val plan = testPlan()
        val day = plan.days.single()
        FitnessValidation.template(FitnessTemplate("template", "胸部日", day.parts, day.activeItems))
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.template(FitnessTemplate("bad", "空", emptyList(), emptyList())) }
    }

    @Test fun restoreRejectsUserTimeOnAnotherTrainingDate() {
        val plan = testPlan()
        val event = completed(plan).single().copy(timeSource = FitnessTimeSource.User, actualAt = fitnessClock.millis() - 86_400_000)
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.snapshot(FitnessSnapshot(FitnessData(plans = listOf(plan)), listOf(event))) }
    }

    @Test fun stoppedItemsWithValidHistoryCannotLoseTheirTargets() {
        val old = testPlan()
        val events = completed(old)
        val stopped = old.copy(days = listOf(FitnessRules.stop(old.days.single(), old.days.single().items.single().id)))
        FitnessRules.validatePlan(stopped, old, events)
        val broken = stopped.copy(days = stopped.days.map { it.copy(items = it.items.map { row -> row.copy(targetSets = null) }) })
        assertThrows(IllegalArgumentException::class.java) { FitnessRules.validatePlan(broken, old, events) }
    }
    @Test fun restoreRejectsTrainingBeforeItCouldHaveBeenRecorded() {
        val plan = testPlan()
        val event = completed(plan).single().copy(recordedAt = fitnessClock.millis() - 86_400_000)
        assertThrows(IllegalArgumentException::class.java) { FitnessValidation.snapshot(FitnessSnapshot(FitnessData(plans = listOf(plan)), listOf(event))) }
    }
}

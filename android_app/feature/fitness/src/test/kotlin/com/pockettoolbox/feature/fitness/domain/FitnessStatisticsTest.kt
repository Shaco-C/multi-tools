package com.pockettoolbox.feature.fitness.domain

import org.junit.Assert.*
import org.junit.Test

class FitnessStatisticsTest {
    @Test fun sameDayAcrossPlansCountsOneDayAndAllSets() {
        val a = testPlan()
        val b = testPlan()
        val events = completed(a) + completed(b).map { it.copy(requestId = "other", sequence = 2) }
        assertEquals(FitnessSummary(1, 2, 0, 1), FitnessStatistics.summary(events))
        assertEquals(1, FitnessStatistics.counts(events, true).single().days)
    }

    @Test fun targetValuesAreNeverAddedToActualTotals() {
        val event = completed().single()
        val row = FitnessStatistics.counts(listOf(event), true).single()
        assertEquals(0L, row.actualTotal)
        assertEquals(0, row.totalFilled)
    }

    @Test fun sidesHaveIndependentCoverageAndAreNotDoubled() {
        val plan = testPlan(exercise = testExercise().copy(side = FitnessSide.Each, unit = FitnessUnit.Seconds))
        val first = completed(plan).single().copy(actual = FitnessActual(left = 20))
        val second = first.copy(id = "second", requestId = "second", sequence = 2, ordinal = 2, actual = FitnessActual(left = 25, right = 22))
        val row = FitnessStatistics.counts(listOf(first, second), true).single()
        assertEquals(45L, row.actualLeft)
        assertEquals(22L, row.actualRight)
        assertEquals(2, row.leftFilled)
        assertEquals(1, row.rightFilled)
    }

    @Test fun sameStableExerciseDifferentUnitsIsNotSummedTogether() {
        val event = completed().single()
        val second = event.copy(id = "second", sequence = 2, exercise = event.exercise.copy(unit = FitnessUnit.Seconds))
        assertEquals(2, FitnessStatistics.counts(listOf(event, second), true).size)
    }

    @Test fun warmupDoesNotCountTrainingDayOrMergeIntoStrengthPart() {
        val event = completed().single()
        val warmup = event.copy(exercise = event.exercise.copy(purpose = FitnessPurpose.Warmup))
        assertEquals(0, FitnessStatistics.summary(listOf(warmup)).trainingDays)
        assertEquals("Warmup", FitnessStatistics.counts(listOf(warmup), false).single().key)
        assertEquals(1, FitnessStatistics.summary(listOf(warmup.copy(exercise = warmup.exercise.copy(purpose = FitnessPurpose.Cardio)))).trainingDays)
    }

    @Test fun todayWithoutTrainingExtendsOnlyThroughYesterday() {
        assertEquals(FitnessStreak(2, "2026-10-23"), FitnessStatistics.streak(setOf("2026-10-22", "2026-10-23"), today))
        assertEquals(0, FitnessStatistics.streak(setOf("2026-10-22"), today).days)
    }

    @Test fun filteringUsesTrainingDateRatherThanRecordOperationTime() {
        val plan = testPlan("2026-10-23")
        val events = completed(plan, source = FitnessTimeSource.DateOnly)
        assertEquals(1, FitnessStatistics.events(events, FitnessFilter("2026-10-23", "2026-10-23")).size)
        assertTrue(FitnessStatistics.events(events, FitnessFilter(today, today)).isEmpty())
    }

    @Test fun archivedRecordsStillCountWhileTasksDisappear() {
        val plan = testPlan()
        val events = completed(plan)
        val data = FitnessData(plans = listOf(plan.copy(status = FitnessPlanStatus.Archived)))
        assertTrue(FitnessStatistics.schedule(data, today, events).isEmpty())
        assertEquals(1, FitnessStatistics.summary(events).strengthSets)
    }

    @Test fun recoveryReportsUnknownTimeAndSecondaryParticipationWithoutCountdown() {
        val plan = testPlan("2026-10-23")
        val events = completed(plan, source = FitnessTimeSource.DateOnly)
        val rows = FitnessStatistics.recovery(events, listOf(FitnessPart.Chest, FitnessPart.Shoulder, FitnessPart.Legs), today)
        assertTrue(rows[0].primary)
        assertFalse(rows[1].primary)
        assertNull(rows[1].event!!.actualAt)
        assertNull(rows[2].event)
    }

    @Test fun recoveryDateOnlyDoesNotInventHours() {
        val event = completed(testPlan("2026-10-23"), source = FitnessTimeSource.DateOnly).single()
        val message = FitnessStatistics.recoveryMessage(FitnessRecovery(FitnessPart.Chest, event, true), fitnessClock.millis(), today)
        assertTrue(message.contains("时刻未知")); assertFalse(message.contains("小时"))
    }
    @Test fun recoveryUsesUserTimeRatherThanBackfillOperationTime() {
        val event = completed(testPlan("2026-10-23"), source = FitnessTimeSource.DateOnly).single().copy(timeSource = FitnessTimeSource.User, actualAt = fitnessClock.millis() - 25 * 3_600_000)
        val message = FitnessStatistics.recoveryMessage(FitnessRecovery(FitnessPart.Chest, event, true), fitnessClock.millis(), today)
        assertTrue(message.contains("25小时"))
    }
    @Test fun recoveryClockAnomalyDoesNotDisplayNegativeHours() {
        val event = completed(testPlan("2026-10-23"), source = FitnessTimeSource.DateOnly).single().copy(timeSource = FitnessTimeSource.Realtime, recordedAt = fitnessClock.millis() + 1000)
        assertFalse(FitnessStatistics.recoveryMessage(FitnessRecovery(FitnessPart.Chest, event, true), fitnessClock.millis(), today).contains("小时"))
    }
    @Test fun recoveryLongIntervalDoesNotGuaranteeReadiness() {
        val event = completed(testPlan("2026-10-21"), source = FitnessTimeSource.DateOnly).single().copy(timeSource = FitnessTimeSource.Realtime, recordedAt = fitnessClock.millis() - 72 * 3_600_000)
        assertTrue(FitnessStatistics.recoveryMessage(FitnessRecovery(FitnessPart.Chest, event, true), fitnessClock.millis(), today).contains("不代表已完全恢复"))
    }
}

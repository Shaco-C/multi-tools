package com.pockettoolbox.feature.fitness.domain
import org.junit.Assert.*
import org.junit.Test
class FitnessPlanCopyTest {
    @Test fun entireWeekCopiesIndependentIdsAndPreservesRestAndTargets() {
        val base = FitnessRules.newPlan("2026-11-01", "2026-11-07")
        val active = testPlan().days.single().items.single()
        val source = base.copy(status = FitnessPlanStatus.Archived, days = base.days.mapIndexed { i, day ->
            when(i) { 0 -> day.copy(kind = FitnessDayKind.Training, parts = listOf(FitnessPart.Chest), items = listOf(active, active.copy(id = "stopped", status = FitnessItemStatus.Stopped)))
                1 -> day.copy(kind = FitnessDayKind.Rest)
                else -> day }
        })
        val copy = FitnessPlanCopy.copy(source, "2026-11-08")
        assertEquals("2026-11-14", copy.end)
        assertNotEquals(source.id, copy.id)
        assertEquals(FitnessPlanStatus.Active, copy.status)
        assertEquals(FitnessDayKind.Rest, copy.days[1].kind)
        assertEquals(FitnessDayKind.Unplanned, copy.days[2].kind)
        val item = copy.days.first().items.single()
        assertNotEquals(active.id, item.id)
        assertEquals(active.copy(id = item.id), item)
        assertEquals(2, source.days.first().items.size)
        assertEquals(FitnessPlanStatus.Archived, source.status)
    }
    @Test fun handlesLeapDayAndYearBoundaryWithoutCopyingOutOfRangeHistory() {
        val source = FitnessRules.newPlan("2026-12-30", "2027-01-01")
        assertEquals("2028-03-01", FitnessPlanCopy.copy(source, "2028-02-28").end)
        assertEquals("2027-01-02", FitnessPlanCopy.copy(source, "2026-12-31").end)
        val extended = source.copy(days = source.days + FitnessPlanDay("2025-01-01"))
        assertEquals(3, FitnessPlanCopy.copy(extended, "2028-02-28").days.size)
    }
}

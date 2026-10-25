package com.pockettoolbox.feature.fitness.presentation

import com.pockettoolbox.feature.fitness.domain.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class FitnessEventTimeTest {
    @Test fun dateOnlyBackfillShowsOperationTimeWithoutInventingTrainingTime() {
        val event = completed(testPlan("2026-10-23"), source = FitnessTimeSource.DateOnly).single()
        val text = fitnessEventTime(event)
        assertTrue(text.startsWith("2026-10-23 · 仅日期补记"))
        assertTrue(text.contains("补记操作于2026-10-24 20:00"))
    }

    @Test fun userSuppliedTrainingTimeAndLaterOperationTimeAreBothVisible() {
        val event = completed(testPlan("2026-10-23"), source = FitnessTimeSource.DateOnly).single()
            .copy(timeSource = FitnessTimeSource.User, actualAt = Instant.parse("2026-10-23T02:30:00Z").toEpochMilli())
        val text = fitnessEventTime(event)
        assertTrue(text.contains("2026-10-23 10:30 · 手填训练时刻"))
        assertTrue(text.contains("补记操作于2026-10-24 20:00"))
    }
}

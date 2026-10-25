package com.pockettoolbox.feature.fitness.presentation

import org.junit.Assert.*
import org.junit.Test

class FitnessStatisticsChartTest {
    @Test fun numericScaleUsesIntegerTicksForEmptyAndSmallCounts() {
        assertEquals(4L, fitnessChartMaximum(0))
        assertEquals(4L, fitnessChartMaximum(1))
        assertEquals(8L, fitnessChartMaximum(5))
        assertEquals(20L, fitnessChartMaximum(20))
        assertEquals(40L, fitnessChartMaximum(21))
    }
    @Test fun scaleDoesNotOverflowForLargeHistoricalCounts() {
        val maximum = fitnessChartMaximum(Int.MAX_VALUE)
        assertTrue(maximum >= Int.MAX_VALUE.toLong())
        assertEquals(0L, maximum % 4)
    }
}

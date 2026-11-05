package com.pockettoolbox.feature.fitness.domain

import java.time.temporal.ChronoUnit

object FitnessPlanCopy {
    fun copy(source: FitnessPlan, start: String): FitnessPlan {
        val dates = FitnessRules.dates(source.start, source.end)
        val first = FitnessRules.date(start)
        val end = first.plusDays(dates.size - 1L).toString()
        FitnessRules.dates(start, end)
        return FitnessPlan(fitnessId(), (source.name.take(55) + " · 副本"), start, end,
            dates.map { original ->
                val day = source.day(original) ?: FitnessPlanDay(original)
                day.copy(date = first.plusDays(ChronoUnit.DAYS.between(FitnessRules.date(source.start), FitnessRules.date(original))).toString(),
                    items = day.activeItems.map { it.copy(id = fitnessId()) })
            })
    }
}

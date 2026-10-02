package com.pockettoolbox.feature.diet.domain

import com.pockettoolbox.core.backup.InvalidBackupException
import org.junit.Assert.*
import org.junit.Test

class DietBackupCodecTest {
    @Test fun roundTripPreservesLinksTimesArchiveAndFinishedState() {
        val data = DietRules.finish(DietRules.toggleCatalog(recorded(), CatalogKind.Food, "wings"), "meal", true)
        val restored = DietBackupCodec.decode(DietBackupCodec.encode(data, 12345))
        assertEquals(data, restored.data)
        assertEquals(1, DietBackupCodec.inspection(restored).recordCount)
    }

    @Test fun malformedDatesOffsetsAndLinksAreReportedAsInvalidBackup() {
        val source = DietBackupCodec.encode(recorded(), 12345)
        val cases = listOf(
            source.replace("2026-10-01", "not-a-date"),
            source.replace("\"offsetSeconds\": 28800", "\"offsetSeconds\": 999999"),
            source.replace("\"foodId\": \"wings\"", "\"foodId\": \"missing\""),
            source.replace("\"mealId\": \"meal\"", "\"mealId\": \"missing\""),
            source.replace("\"toolId\": \"diet\"", "\"toolId\": \"electricity\""),
            source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"),
        )
        cases.forEach { invalid -> assertThrows(InvalidBackupException::class.java) { DietBackupCodec.decode(invalid) } }
    }

    @Test fun orphanMealDuplicateIdsAndWrongBrandAreRejectedBeforeRestore() {
        val data = recorded()
        assertThrows(IllegalArgumentException::class.java) { DietRules.validate(data.copy(events = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { DietRules.validate(data.copy(events = data.events + data.events)) }
        assertThrows(IllegalArgumentException::class.java) { DietRules.validate(data.copy(meals = listOf(data.meals.single().copy(brandId = "kfc")))) }
        assertThrows(IllegalArgumentException::class.java) { DietRules.validate(data.copy(activeMealId = "missing")) }
    }

    @Test fun oversizedAndDeeplyNestedFilesAreRejected() {
        assertThrows(InvalidBackupException::class.java) { DietBackupCodec.decode(" ".repeat(DietBackupCodec.MAX_BYTES + 1)) }
        assertThrows(InvalidBackupException::class.java) { DietBackupCodec.decode("[".repeat(31) + "]".repeat(31)) }
    }

    @Test fun csvIncludesEveryEventTimezoneAndEscapesFormulaQuotesAndNewlines() {
        val data = recorded().let { it.copy(meals = listOf(it.meals.single().copy(note = "=SUM(1,2)\n\"备注\""))) }
        val csv = DietBackupCodec.csv(data)
        assertTrue(csv.startsWith("\uFEFF\"品牌\""))
        assertTrue(csv.contains("2026-10-02T08:00:00+08:00"))
        assertTrue(csv.contains("\"'=SUM(1,2)\n\"\"备注\"\"\""))
        assertTrue(csv.contains("\"麦当劳\",\"辣翅\",\"2026-10-01\",\"腹泻\""))
    }
}

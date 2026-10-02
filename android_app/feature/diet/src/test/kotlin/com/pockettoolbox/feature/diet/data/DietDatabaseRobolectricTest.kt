package com.pockettoolbox.feature.diet.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.pockettoolbox.feature.diet.domain.*
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DietDatabaseRobolectricTest {
    private lateinit var database: DietDatabase
    private lateinit var repo: RoomDietRepository
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DietDatabase::class.java).build()
        repo = RoomDietRepository(database)
    }
    @After fun close() { database.close() }

    private fun event(id: String): DiscomfortEvent {
        val time = OffsetDateTime.parse("2026-10-02T08:30:00+08:00")
        return DiscomfortEvent(id, "meal", "diarrhea", time.toInstant().toEpochMilli(), 28800)
    }
    private suspend fun seed() {
        repo.initialize()
        repo.change { DietRules.saveCatalog(it, CatalogKind.Brand, "brand", "麦当劳") }
        repo.change { DietRules.saveCatalog(it, CatalogKind.Food, "food", "辣翅", "brand") }
        repo.change { DietRules.recordIncident(it, DietMeal("meal", "brand", "food", "2026-10-01"), event("first")) }
    }

    @Test fun initializationAndRelationsSurviveRepositoryRecreation() = runBlocking {
        seed()
        val reopened = RoomDietRepository(database)
        reopened.initialize()
        assertEquals(1, reopened.snapshot().events.size)
        assertEquals("麦当劳", reopened.snapshot().brandName("brand"))
        assertEquals("meal", reopened.snapshot().activeMealId)
        DietRules.validate(reopened.snapshot())
    }

    @Test fun concurrentAppendTransactionsDoNotLoseEvents() = runBlocking {
        seed()
        (1..12).map { id -> async { repo.change { DietRules.append(it, event("event$id")) } } }.awaitAll()
        assertEquals(13, repo.snapshot().events.size)
        assertEquals(1, repo.snapshot().meals.size)
    }

    @Test fun failureRollsBackAndDeleteLastEventCanBeRestored() = runBlocking {
        seed()
        val original = repo.snapshot()
        try {
            repo.change { it.copy(events = it.events + event("bad").copy(typeId = "missing")) }
            fail("Foreign key violation should roll back")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(original, repo.snapshot())
        repo.change { DietRules.removeEvent(it, "first") }
        assertTrue(repo.snapshot().meals.isEmpty())
        repo.change { DietRules.restoreEvent(it, original.meals.single(), original.events.single()) }
        assertEquals(1, repo.snapshot().events.size)
    }

    @Test fun invalidImportLeavesExistingRowsAndValidReplaceIsComplete() = runBlocking {
        seed()
        val original = repo.snapshot()
        try { repo.replace(original.copy(events = emptyList())); fail("Invalid import should fail") }
        catch (_: IllegalArgumentException) { }
        assertEquals(original, repo.snapshot())
        repo.replace(DietData())
        assertTrue(repo.snapshot().brands.isEmpty())
        assertTrue(repo.snapshot().events.isEmpty())
        assertEquals(3, repo.snapshot().types.size)
    }
}

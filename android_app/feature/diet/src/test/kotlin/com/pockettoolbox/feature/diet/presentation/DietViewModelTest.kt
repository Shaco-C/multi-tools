package com.pockettoolbox.feature.diet.presentation

import androidx.lifecycle.ViewModelStore
import com.pockettoolbox.feature.diet.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-02T00:35:00Z"), ZoneId.of("Asia/Shanghai"))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun vm(repo: MemoryRepository): DietViewModel = DietViewModel(repo, clock, dispatcher).also { store.put("diet", it) }

    @Test fun typingAndCancellingNeverSaveAndConfirmUsesButtonTime() = runTest(dispatcher) {
        val repo = MemoryRepository(catalogs())
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openDraft("mcd", "wings")
        val draft = requireNotNull(vm.draft)
        vm.updateDraft(draft.copy(note = "十翅一桶"))
        assertEquals(0, repo.writes)
        vm.cancelDraft()
        assertEquals(0, repo.writes)
        vm.openDraft("mcd", "wings")
        vm.saveDraft()
        vm.saveDraft() // Disabled during save; a fast second tap cannot duplicate the row.
        advanceUntilIdle()
        assertEquals(1, repo.writes)
        assertEquals("2026-10-01", repo.data.value.meals.single().eatenOn)
        assertEquals(clock.millis(), repo.data.value.events.single().occurredAt)
        assertEquals(28800, repo.data.value.events.single().offsetSeconds)
        assertNull(vm.draft)
    }

    @Test fun failedSaveKeepsDraftAndDoesNotPartiallyWrite() = runTest(dispatcher) {
        val repo = MemoryRepository(catalogs())
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openDraft("mcd", "wings")
        vm.updateDraft(requireNotNull(vm.draft).copy(eatenOn = "2026-10-03"))
        vm.saveDraft()
        advanceUntilIdle()
        assertTrue(vm.draftVisible)
        assertNotNull(vm.draftError)
        assertTrue(repo.data.value.events.isEmpty())
        assertTrue(repo.data.value.meals.isEmpty())
        assertFalse(vm.busy)
    }

    @Test fun appendUndoDoesNotRevertLaterCatalogRename() = runTest(dispatcher) {
        val repo = MemoryRepository(recorded())
        val vm = vm(repo)
        advanceUntilIdle()
        vm.append()
        advanceUntilIdle()
        val undo = requireNotNull(vm.notices.first().undo)
        vm.saveCatalog(CatalogKind.Brand, "mcd", "新名称", null, {}, { fail(it) })
        advanceUntilIdle()
        undo()
        advanceUntilIdle()
        assertEquals(1, repo.data.value.events.size)
        assertEquals("新名称", repo.data.value.brandName("mcd"))
    }

    @Test fun backupInspectionDoesNotReplaceUntilConfirmedAndInvalidBackupIsSafe() = runTest(dispatcher) {
        val repo = MemoryRepository(catalogs())
        val vm = vm(repo)
        advanceUntilIdle()
        vm.inspect { DietBackupCodec.encode(recorded(), clock.millis()) }
        advanceUntilIdle()
        assertNotNull(vm.pendingRestore)
        assertTrue(repo.data.value.events.isEmpty())
        vm.restore()
        advanceUntilIdle()
        assertEquals(recorded(), repo.data.value)
        assertNull(vm.pendingRestore)
        vm.inspect { "invalid JSON" }
        advanceUntilIdle()
        assertNull(vm.pendingRestore)
        assertEquals(recorded(), repo.data.value)
    }

    private class MemoryRepository(initial: DietData) : DietRepository {
        override val data = MutableStateFlow(initial)
        var writes = 0
        override suspend fun initialize() = Unit
        override suspend fun snapshot() = data.value
        override suspend fun change(transform: (DietData) -> DietData): DietData {
            val next = transform(data.value)
            data.value = next
            writes++
            return next
        }
        override suspend fun replace(data: DietData) { DietRules.validate(data); this.data.value = data }
    }
}

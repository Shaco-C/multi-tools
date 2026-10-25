package com.pockettoolbox.feature.fitness.presentation

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies
import com.pockettoolbox.feature.fitness.data.*
import com.pockettoolbox.feature.fitness.domain.*
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FitnessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val exercise = FitnessExercise("test", "俯卧撑", purpose = FitnessPurpose.Strength,
        primaryPart = FitnessPart.Chest, unit = FitnessUnit.Reps, equipment = listOf(listOf("floor")))
    private lateinit var database: FitnessDatabase
    private lateinit var dependencies: FitnessFeatureDependencies
    private lateinit var repository: TestRepository
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val real = RoomFitnessRepository(database)
        val media = FitnessMediaStore(File(context.cacheDir, "viewmodel-media"))
        dependencies = FitnessFeatureDependencies(real, FitnessCatalog(context), media,
            FitnessBackupService(real, media, File(context.cacheDir, "viewmodel-backup")))
        repository = TestRepository(real)
    }
    @After fun close() { stores.forEach { it.clear() }; database.close(); Dispatchers.resetMain() }
    private fun model(): FitnessViewModel = FitnessViewModel(dependencies, clock, dispatcher, repository, elapsedMillis = { dispatcher.scheduler.currentTime }) {
        FitnessCatalogDocument("test", 0, 0, "test", listOf(exercise), emptyList())
    }.also { vm -> stores += ViewModelStore().apply { put("fitness", vm) } }
    private fun plan(): FitnessPlan {
        val value = FitnessRules.newPlan("2026-10-24", "2026-10-25")
        return value.copy(days = value.days.map { day -> FitnessRules.addExercise(day, exercise).let { added ->
            added.copy(items = added.items.map { it.copy(targetSets = 4, targetValue = 12) })
        } })
    }
    private fun staged(): FitnessDraft = FitnessDraft(plan(), false, "2026-10-24", step = 2, savedAt = clock.millis())

    @Test fun observationFailureIsDisplayedAndRetryRestartsCollectors() = runTest(dispatcher) {
        repository.failObservations = true
        val vm = model(); runCurrent()
        assertEquals("injected read failure", vm.loadError)
        repository.failObservations = false
        vm.load(); runCurrent()
        assertNull(vm.loadError); assertTrue(vm.loaded)
    }

    @Test fun selectedDateIsObservableEvenWhenBothDatesHaveNoEvents() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        val dates = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { snapshotFlow { vm.selected.date }.collect { dates += it } }
        vm.selectDate("2026-10-25"); Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals(listOf("2026-10-24", "2026-10-25"), dates)
    }

    @Test fun dateChangeDoesNotTemporarilyReusePreviousProgress() = runTest(dispatcher) {
        repository.progress = mapOf("old-item" to 3)
        val vm = model(); runCurrent(); assertEquals(3, vm.dayProgress["old-item"])
        repository.progressDelay = 100
        vm.selectDate("2026-10-25")
        assertTrue(vm.dayProgress.isEmpty()); assertTrue(vm.dayEvents.isEmpty())
    }

    @Test fun incompleteTargetsSurviveAutosaveAndViewModelRecreation() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        val id = vm.draft!!.plan.days.first().items.single().id
        vm.updateTarget(id, sets = "", value = "-")
        advanceTimeBy(450); runCurrent()
        assertFalse(vm.draftDirty)
        stores.last().clear()
        val restored = model(); runCurrent()
        assertEquals(FitnessTargetInput("", "-"), restored.draft!!.rawTargets[id])
        assertNull(restored.draft!!.plan.days.first().items.single().targetSets)
    }

    @Test fun signedInputCannotSilentlyBecomeAValidTarget() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        val id = vm.draft!!.plan.days.first().items.single().id
        vm.updateTarget(id, sets = "+2")
        assertNull(vm.draft!!.plan.days.first().items.single().targetSets)
        assertTrue(FitnessRules.issues(vm.draft!!.plan, emptyList()).any { it.itemId == id && it.field == "sets" })
    }

    @Test fun changingMeasurementRequiresANewTargetAndPreservesOtherItems() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        val original = vm.draft!!.plan.days.first().items.single()
        assertTrue(vm.updateMeasurement(original.id, FitnessUnit.Seconds, FitnessSide.Each))
        val changed = vm.draft!!.plan.days.first().items.single()
        assertEquals(FitnessUnit.Seconds, changed.exercise.unit)
        assertEquals(FitnessSide.Each, changed.exercise.side)
        assertEquals(original.targetSets, changed.targetSets)
        assertNull(changed.targetValue)
        assertEquals("", vm.draft!!.rawTargets[original.id]!!.value)
        assertEquals(original.exercise, vm.draft!!.plan.days.last().items.single().exercise)
        advanceTimeBy(450); runCurrent()
        assertEquals(FitnessUnit.Seconds, repository.state.value.draft!!.plan.days.first().items.single().exercise.unit)
    }

    @Test fun revokedHistoryStillLocksTheMeasurement() = runTest(dispatcher) {
        val plan = plan()
        val day = plan.days.first()
        val item = day.items.single()
        val event = FitnessRules.complete(plan, day, item, 0, FitnessCompletion(plan.id, day.date, item.id, fitnessId()), clock, 1).single()
        repository.completed += event.copy(status = FitnessEventStatus.Revoked, revokedAt = clock.millis())
        repository.state.value = FitnessData(plans = listOf(plan), draft = FitnessDraft(plan, true, day.date, step = 2, savedAt = clock.millis()))
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        assertFalse(vm.updateMeasurement(item.id, FitnessUnit.Seconds, FitnessSide.Each))
        assertEquals(item, vm.draft!!.plan.days.first().items.single())
        assertEquals("已有完成或撤销历史，计量口径不能修改", vm.editorError)
    }

    @Test fun resumedDateAndStepArePersistedWithoutAnotherEdit() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent()
        vm.resumeEditor({}, date = "2026-10-25", step = 3)
        advanceTimeBy(450); runCurrent()
        assertEquals("2026-10-25", repository.state.value.draft!!.selectedDate)
        assertEquals(3, repository.state.value.draft!!.step)
        assertFalse(vm.draftDirty)
    }

    @Test fun failedDraftSaveKeepsEditorAndInputUntilRetrySucceeds() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        val id = vm.draft!!.plan.days.first().items.single().id
        vm.updateTarget(id, sets = "8"); repository.failDraft = true
        var left = false
        vm.leaveEditor { left = true }; runCurrent()
        assertFalse(left); assertTrue(vm.editorVisible); assertTrue(vm.draftDirty)
        assertTrue(vm.editorError!!.contains("暂存失败")); assertEquals("8", vm.draft!!.rawTargets[id]!!.sets)
        repository.failDraft = false; vm.leaveEditor { left = true }; runCurrent()
        assertTrue(left); assertFalse(vm.draftDirty)
    }

    @Test fun newEntryProtectsAnExistingDraft() = runTest(dispatcher) {
        val original = staged(); repository.state.value = FitnessData(draft = original)
        val vm = model(); runCurrent()
        var opened = false
        val request = FitnessEditorRequest(date = "2026-10-26")
        vm.requestEditor(request) { opened = true }; runCurrent()
        assertFalse(opened); assertEquals(request, vm.pendingEditor); assertEquals(original, vm.draft)
        vm.cancelEditorRequest(); assertNull(vm.pendingEditor); assertEquals(original, repository.state.value.draft)
    }

    @Test fun finalSaveCancelsPendingAutosaveAndClearsDraft() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        val id = vm.draft!!.plan.days.first().items.single().id
        vm.updateTarget(id, sets = "5")
        var saved = false; vm.savePlan { saved = true }
        advanceTimeBy(1000); runCurrent()
        assertTrue(saved); assertNull(vm.draft); assertNull(repository.state.value.draft)
        assertEquals(5, repository.state.value.plans.single().days.first().items.single().targetSets)
    }

    @Test fun failedPlanSaveRetainsDraftForCorrection() = runTest(dispatcher) {
        repository.state.value = FitnessData(draft = staged())
        val vm = model(); runCurrent(); vm.resumeEditor({}); runCurrent()
        vm.updateTarget(vm.draft!!.plan.days.first().items.single().id, sets = "0")
        var saved = false; vm.savePlan { saved = true }; runCurrent()
        assertFalse(saved); assertTrue(vm.editorVisible); assertNotNull(vm.editorError); assertNotNull(vm.draft)
        assertTrue(repository.state.value.plans.isEmpty())
    }

    @Test fun rapidTapsAreSuppressedEvenAfterTheFirstWriteFinishes() = runTest(dispatcher) {
        val plan = plan(); repository.state.value = FitnessData(plans = listOf(plan))
        val vm = model(); runCurrent()
        val day = plan.days.first(); val row = FitnessScheduledItem(plan, day, day.items.single(), 0)
        vm.complete(row); vm.complete(row); runCurrent()
        vm.complete(row); runCurrent(); assertEquals(1, repository.completed.size)
        advanceTimeBy(650); vm.complete(row); runCurrent(); assertEquals(2, repository.completed.size)
    }

    @Test fun failedCompletionAllowsImmediateRetryWithoutIncreasingProgress() = runTest(dispatcher) {
        val plan = plan(); repository.state.value = FitnessData(plans = listOf(plan))
        val vm = model(); runCurrent()
        val day = plan.days.first(); val row = FitnessScheduledItem(plan, day, day.items.single(), 0)
        repository.failCompletion = true; vm.complete(row); runCurrent()
        assertTrue(repository.completed.isEmpty())
        repository.failCompletion = false; vm.complete(row); runCurrent()
        assertEquals(1, repository.completed.size)
    }

    @Test fun systemTimeZoneChangeRefreshesTodayWithoutMovingSavedPlans() = runTest(dispatcher) {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val plan = plan(); repository.state.value = FitnessData(plans = listOf(plan))
            val vm = FitnessViewModel(dependencies, ioDispatcher = dispatcher, repository = repository,
                initialize = { FitnessCatalogDocument("test", 0, 0, "test", listOf(exercise), emptyList()) })
            stores += ViewModelStore().apply { put("fitness", vm) }; runCurrent()
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            vm.refreshToday(); runCurrent()
            assertEquals(ZoneId.of("Pacific/Kiritimati"), vm.zoneId)
            assertEquals(java.time.LocalDate.now(vm.zoneId).toString(), vm.today)
            assertEquals(plan, vm.data.plans.single())
        } finally { TimeZone.setDefault(originalZone) }
    }

    private class TestRepository(real: FitnessRepository) : FitnessRepository by real {
        val state = MutableStateFlow(FitnessData())
        override val data: Flow<FitnessData> = state
        var failObservations = false
        var failDraft = false
        var progress = emptyMap<String, Int>()
        var progressDelay = 0L
        val completed = mutableListOf<FitnessSetEvent>()
        var failCompletion = false
        override suspend fun complete(command: FitnessCompletion, clock: Clock): List<FitnessSetEvent> {
            if (failCompletion) throw IOException("injected write failure")
            val plan = state.value.plans.first { it.id == command.planId }
            val day = plan.day(command.date)!!; val item = day.items.first { it.id == command.itemId }
            return FitnessRules.complete(plan, day, item, completed.count { it.itemId == item.id }, command, clock, completed.size + 1L).also { completed += it }
        }
        override fun observeEvents(filter: FitnessFilter, limit: Int, exerciseId: String?, part: FitnessPart?, unit: FitnessUnit?, side: FitnessSide?) = flow<List<FitnessSetEvent>> {
            if (failObservations) throw IOException("injected read failure")
            emit(emptyList())
        }
        override fun observeProgress(date: String, planId: String?) = flow { delay(progressDelay); emit(progress) }
        override fun observeDailyCounts(filter: FitnessFilter) = flowOf(emptyMap<String, Int>())
        override fun observeTrainingDates(today: String) = flowOf(emptySet<String>())
        override fun observeSummary(filter: FitnessFilter) = flowOf(FitnessSummary())
        override fun observeCounts(filter: FitnessFilter, byExercise: Boolean) = flowOf(emptyList<FitnessCount>())
        override suspend fun recovery(parts: List<FitnessPart>, today: String) = emptyList<FitnessRecovery>()
        override suspend fun planEvents(planId: String) = completed.filter { it.planId == planId }
        override suspend fun saveDraft(draft: FitnessDraft?) {
            if (failDraft) throw IOException("injected disk failure")
            FitnessValidation.draft(draft ?: run { state.value = state.value.copy(draft = null); return })
            state.value = state.value.copy(draft = draft)
        }
        override suspend fun savePlan(plan: FitnessPlan, clearDraft: Boolean) {
            FitnessRules.validatePlan(plan, null, emptyList())
            state.value = state.value.copy(plans = state.value.plans.filterNot { it.id == plan.id } + plan,
                draft = if (clearDraft) null else state.value.draft)
        }
    }
}

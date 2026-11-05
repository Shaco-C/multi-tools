package com.pockettoolbox.feature.fitness.presentation

import android.content.Context
import android.graphics.Bitmap
import org.robolectric.shadows.ShadowLooper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pockettoolbox.core.designsystem.PocketToolboxTheme
import com.pockettoolbox.core.designsystem.ToolboxPalette
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies
import com.pockettoolbox.feature.fitness.data.*
import com.pockettoolbox.feature.fitness.domain.*
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Main-page interaction checks; input/IME and touch behavior still require an Android device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
class FitnessPagesTest {
    @get:Rule val compose = createComposeRule()
    private val restoration = StateRestorationTester(compose)
    private val store = ViewModelStore()
    private lateinit var database: FitnessDatabase
    private lateinit var backDispatcher: androidx.activity.OnBackPressedDispatcher
    private lateinit var vm: FitnessViewModel
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("Asia/Shanghai"))
    @After fun close() { store.clear(); if (::database.isInitialized) database.close() }

    private fun open(palette: ToolboxPalette, recorded: Boolean, itemCount: Int = 1) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        val repository = RoomFitnessRepository(database)
        val media = FitnessMediaStore(File(context.cacheDir, "pages-media"))
        val dependencies = FitnessFeatureDependencies(repository, FitnessCatalog(context), media,
            FitnessBackupService(repository, media, File(context.cacheDir, "pages-backup")))
        runBlocking {
            val catalog = dependencies.initialize()
            if (recorded) {
                val exercises = catalog.exercises.filter { it.purpose == FitnessPurpose.Strength }.take(itemCount)
                val value = FitnessRules.newPlan("2026-10-24")
                val day = exercises.fold(value.days.single()) { day, exercise -> FitnessRules.addExercise(day, exercise) }
                val plan = value.copy(days = listOf(day.copy(items = day.items.map { it.copy(targetSets = 4, targetValue = 12) })))
                repository.savePlan(plan)
                repository.complete(FitnessCompletion(plan.id, plan.start, plan.days.single().items.first().id, fitnessId()), clock)
            }
        }
        compose.runOnUiThread { vm = FitnessViewModel(dependencies, clock); store.put("fitness", vm) }
        restoration.setContent {
            backDispatcher = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            PocketToolboxTheme(palette) { FitnessRoute(dependencies, {}, vm) }
        }
        awaitData { vm.loaded && (!recorded || vm.statsCounts.isNotEmpty()) }
        compose.onNodeWithText("今天的训练").assertExists()
    }
    private fun screenshot(name: String) {
        val directory = File(requireNotNull(System.getProperty("fitness.screenshot.dir"))); directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    private fun awaitData(condition: () -> Boolean) {
        // Room uses real IO threads; drain Android main callbacks while waiting in paused-looper mode.
        compose.waitUntil(15_000) { ShadowLooper.idleMainLooper(); condition() }
    }
    private fun openDialogAndDraw(action: () -> Unit) {
        // Robolectric pauses native window draw callbacks; render the dialog before Compose's idle check.
        compose.mainClock.autoAdvance = false
        try {
            action()
            compose.mainClock.advanceTimeBy(2000)
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
            compose.runOnUiThread {
                val decor = requireNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog().window).decorView
                val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                try { decor.draw(android.graphics.Canvas(bitmap)) } finally { bitmap.recycle() }
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun copyingPlanProtectsDraftAndSavesIndependentPlanWithoutHistory() {
        open(ToolboxPalette.Fresh, true)
        val original = vm.data.plans.single()
        val originalEvents = runBlocking { vm.dependencies.repository.snapshot() }.events
        compose.onNodeWithText("创建多日计划").performClick()
        awaitData { vm.draft != null && !vm.busy }
        val previousDraft = vm.draft!!.plan.id
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("从历史计划中复制"))
        openDialogAndDraw { compose.onNodeWithText("从历史计划中复制").performClick() }
        compose.onNodeWithText("请选择").performClick()
        compose.onNodeWithText("${original.name} · ${original.start}—${original.end}").performClick()
        openDialogAndDraw { compose.onNodeWithText("复制并调整").performClick() }
        compose.onNodeWithText("先处理未完成草稿").assertExists()
        assertEquals(previousDraft, vm.draft!!.plan.id)
        compose.onNodeWithText("取消").performClick()
        assertEquals(previousDraft, vm.draft!!.plan.id)
        // Re-request and explicitly replace; the new plan remains only a draft.
        openDialogAndDraw { compose.runOnIdle { vm.requestEditor(FitnessEditorRequest(date = "2026-11-08", range = true, sourcePlanId = original.id)) {} } }
        compose.onNodeWithText("替换草稿").performClick()
        awaitData { !vm.busy && vm.draft!!.plan.id != previousDraft }
        val copy = vm.draft!!.plan
        assertEquals("2026-11-08", copy.start)
        assertTrue(vm.editorHistory.isEmpty())
        assertFalse(vm.draft!!.editing)
        assertEquals(1, runBlocking { vm.dependencies.repository.snapshot() }.data.plans.size)
        compose.runOnIdle { vm.updateDraft { it.copy(plan = it.plan.copy(name = "复制后微调")) }; vm.savePlan {} }
        awaitData { !vm.busy && vm.draft == null && vm.data.plans.size == 2 }
        val snapshot = runBlocking { vm.dependencies.repository.snapshot() }
        assertEquals(original, snapshot.data.plans.first { it.id == original.id })
        assertEquals(originalEvents, snapshot.events)
        assertEquals("复制后微调", snapshot.data.plans.first { it.id == copy.id }.name)
        assertNotEquals(original.days.single().items.first().id, copy.days.single().items.first().id)
    }

    @Test fun pickerReturnsToSameExerciseAndOffsetAfterDetailsAndAdding() {
        open(ToolboxPalette.Fresh, false)
        compose.onNodeWithText("创建多日计划").performClick()
        awaitData { vm.draft != null && !vm.busy }
        compose.runOnIdle { vm.updateDraft { it.copy(step = 2) }; vm.updateDay { it.copy(kind = FitnessDayKind.Training, parts = listOf(FitnessPart.Chest)) } }
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("胸部 · 已选0个"))
        compose.onNodeWithText("胸部 · 已选0个").performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("加载更多", substring = true))
        compose.onNodeWithText("加载更多", substring = true).performClick()
        val target = vm.exercises.filter { it.purpose == FitnessPurpose.Strength && it.primaryPart == FitnessPart.Chest }[25]
        val matcher = hasTestTag("fitness-exercise-${target.id}")
        compose.onNodeWithTag("fitness-page").performScrollToNode(matcher)
        val before = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top
        repeat(2) { index ->
            compose.onNode(matcher).performClick()
            if (index == 0) compose.onNodeWithContentDescription("返回", useUnmergedTree = true).performClick()
            else compose.runOnUiThread { backDispatcher.onBackPressed() }
            compose.onNode(matcher).assertIsDisplayed()
            assertEquals(before, compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        compose.onNode(matcher).performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("返回选择（未选则加入）"))
        compose.onNodeWithText("返回选择（未选则加入）").performClick()
        compose.onNode(matcher).assertIsDisplayed()
        assertEquals(before, compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(1, vm.draft!!.plan.day(vm.draft!!.selectedDate)!!.activeItems.count { it.exercise.exerciseId == target.id })
        screenshot("picker-return-position")
    }

    @Test fun libraryAndEditorPositionsSurviveDetailsAndStateRestoration() {
        open(ToolboxPalette.Fresh, true, itemCount = 8)
        compose.onNodeWithText("动作库", useUnmergedTree = true).performClick()
        compose.onNodeWithText("胸部").performClick()
        val exercise = vm.exercises.filter { it.purpose == FitnessPurpose.Strength && it.primaryPart == FitnessPart.Chest }[15]
        val matcher = hasTestTag("fitness-exercise-${exercise.id}")
        compose.onNodeWithTag("fitness-page").performScrollToNode(matcher)
        val before = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top
        compose.onNode(matcher).performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("加入我的计划"))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("加入我的计划").assertIsDisplayed()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        assertEquals(before, compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top, 1f)
        val original = vm.data.plans.single()
        compose.onNodeWithText("创建多日计划").assertDoesNotExist()
        compose.onNodeWithText("今日", useUnmergedTree = true).performClick()
        compose.onNodeWithText("创建多日计划").performClick()
        awaitData { vm.draft != null && !vm.busy }
        compose.runOnIdle { vm.updateDraft { it.copy(plan = original, selectedDate = original.start, step = 2) } }
        val editorMatcher = hasTestTag("fitness-exercise-${original.days.single().items.last().exercise.exerciseId}")
        compose.onNodeWithTag("fitness-page").performScrollToNode(editorMatcher)
        val editorBefore = compose.onNode(editorMatcher).fetchSemanticsNode().boundsInRoot.top
        compose.onNode(editorMatcher).performClick()
        compose.onNodeWithContentDescription("返回", useUnmergedTree = true).performClick()
        assertEquals(editorBefore, compose.onNode(editorMatcher).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun freshThemeNavigationConnectsStatisticsToOriginalDay() {
        open(ToolboxPalette.Fresh, true)
        screenshot("fresh-today")
        compose.onNodeWithText("动作库", useUnmergedTree = true).performClick()
        compose.onNodeWithText("先认识练哪里，再了解怎么做。").assertExists()
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("查看组成记录"))
        compose.onNodeWithText("1个训练日 · 1组").assertExists()
        screenshot("fresh-statistics")
        compose.onNodeWithText("查看组成记录").performClick()
        awaitData { vm.scopedRecords.size == 1 }
        compose.runOnIdle { vm.recordScope(null) }
        awaitData { vm.scopedRecords.isEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        awaitData { vm.scopedRecords.size == 1 }
        compose.onNodeWithText("当日").performClick()
        compose.onNodeWithText("编辑这一天安排").assertExists()
        compose.onNodeWithContentDescription("返回", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasTestTag("fitness-statistics-chart"))
        compose.onNodeWithTag("fitness-statistics-chart").assertIsDisplayed()
        assertEquals("2026-10-18", vm.statsFilter.start)
    }

    @Test fun warmThemeShowsUnknownActualValueAndMetricSwitch() {
        open(ToolboxPalette.Warm, true)
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithText("按动作").performClick()
        awaitData { vm.statsCounts.firstOrNull()?.key?.contains('|') == true }
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("实际值尚未填写 · 0/1组/段已填"))
        compose.onNodeWithText("实际值尚未填写 · 0/1组/段已填").assertExists()
        screenshot("warm-statistics")
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("组 / 段数"))
        compose.onNodeWithText("组 / 段数").performClick()
        compose.runOnIdle { assertFalse(vm.statsByDays) }
    }

    @Test fun interleavedExercisesRemainDirectlyRecordableWithoutSwitchingFocus() {
        open(ToolboxPalette.Fresh, true, itemCount = 2)
        val rows = vm.scheduled()
        assertEquals(2, rows.size)
        compose.runOnIdle { vm.focus(rows.first()) }
        fun record(index: Int, expected: Int) {
            val id = rows[index].item.id
            compose.onNodeWithTag("fitness-page").performScrollToNode(hasTestTag("fitness-complete-$id"))
            compose.onNodeWithTag("fitness-complete-$id").assertIsEnabled().performClick()
            awaitData { vm.dayProgress[id] == expected && !vm.busy }
            compose.onAllNodesWithText("切到此动作").assertCountEquals(0)
        }
        record(1, 1)
        record(0, 2)
        record(1, 2)
        val events = runBlocking { vm.dependencies.repository.snapshot() }.events.filter { it.isValid() }
        assertEquals(listOf(rows[0].item.id, rows[1].item.id, rows[0].item.id, rows[1].item.id), events.sortedBy { it.sequence }.map { it.itemId })
        screenshot("interleaved-workouts")
    }

    private fun seedStatistics(parts: List<FitnessPart>) {
        runBlocking {
            val all = vm.catalog!!.selectableExercises
            val base = FitnessRules.newPlan(vm.today)
            val day = parts.fold(base.days.single()) { day, part -> FitnessRules.addExercise(day,
                all.first { it.purpose == FitnessPurpose.Strength && it.primaryPart == part }) }
            val plan = base.copy(days = listOf(day.copy(items = day.items.map { it.copy(targetSets = 4, targetValue = 12) })))
            vm.dependencies.repository.savePlan(plan)
            plan.days.single().items.forEachIndexed { index, item -> repeat(index % 4 + 1) {
                vm.dependencies.repository.complete(FitnessCompletion(plan.id, plan.start, item.id, fitnessId()), clock)
            } }
        }
        awaitData { vm.statsCounts.size == parts.size }
    }

    @Test fun statisticsUseOneCoordinateChartWithCompactFiltersAndSelectableBars() {
        open(ToolboxPalette.Fresh, false)
        seedStatistics(listOf(FitnessPart.Chest, FitnessPart.Back, FitnessPart.Shoulder, FitnessPart.Biceps,
            FitnessPart.Triceps, FitnessPart.Core, FitnessPart.Legs, FitnessPart.Glutes))
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("fitness-statistics-chart").assertIsDisplayed()
        compose.onAllNodesWithText("开始日期").assertCountEquals(0)
        screenshot("fresh-coordinate-statistics")
        val next = vm.statsCounts.sortedWith(compareByDescending<FitnessCount> { it.days }.thenBy { it.key })[1]
        compose.onNodeWithTag("fitness-statistics-chart").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * .6f, 12.dp.toPx() + (height - 48.dp.toPx()) / 8 * 1.5f))
        }
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("当前项目（也可点图选择）"))
        compose.onNodeWithText(next.name).assertExists()
        compose.onNodeWithText("查看组成记录").performClick()
        awaitData { vm.scopedRecords.isNotEmpty() }
        assertTrue(vm.scopedRecords.all { it.exercise.primaryPart?.name == next.key })
    }

    @Test fun sourceActionCanBeFoundAndAddedWithoutCreatingACustomCopy() {
        open(ToolboxPalette.Fresh, false)
        val source = vm.exercises.first { it.id.startsWith("SRC-") && it.primaryPart == FitnessPart.Chest }
        compose.onNodeWithText("动作库", useUnmergedTree = true).performClick()
        compose.onNodeWithText("892个内置可选动作 · 中文样稿16项＋来源动作876项").assertExists()
        screenshot("full-library")
        compose.onNodeWithText("全库搜索").performClick()
        compose.onNodeWithText("搜索名称、别名或英文名").performTextInput(source.english)
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasTestTag("fitness-exercise-${source.id}"))
        compose.onNodeWithTag("fitness-exercise-${source.id}").performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("加入我的计划"))
        openDialogAndDraw { compose.onNodeWithText("加入我的计划").performClick() }
        compose.onNodeWithText("进入计划编辑").performClick()
        awaitData { vm.draft?.plan?.days?.flatMap { it.items }?.any { it.exercise.exerciseId == source.id } == true }
        assertTrue(vm.data.personalExercises.isEmpty())
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("计量口径"))
        compose.onNodeWithText("计量口径").assertExists()
    }

    @Test fun manyExerciseBarsStayPagedWithinOneChartAndKeepTheSameScale() {
        open(ToolboxPalette.Fresh, false)
        runBlocking {
            val base = FitnessRules.newPlan(vm.today)
            val exercises = vm.exercises.filter { it.id.startsWith("SRC-") && it.purpose == FitnessPurpose.Strength }.take(13)
            val day = exercises.fold(base.days.single()) { day, exercise -> FitnessRules.addExercise(day, exercise) }
            val plan = base.copy(days = listOf(day.copy(items = day.items.map { it.copy(targetSets = 9, targetValue = 12) })))
            vm.dependencies.repository.savePlan(plan)
            plan.days.single().items.forEachIndexed { index, item -> repeat(if (index == 0) 9 else 1) {
                vm.dependencies.repository.complete(FitnessCompletion(plan.id, plan.start, item.id, fitnessId()), clock)
            } }
        }
        compose.runOnIdle { vm.statsView(false, true); vm.statsMetric(false) }
        awaitData { vm.statsCounts.size == 13 }
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        fun description() = compose.onNodeWithTag("fitness-statistics-chart").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        val firstPage = description()
        assertTrue(firstPage.contains("量程0到20"))
        assertEquals(12, firstPage.count { it == '；' })
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("下一页"))
        compose.onNodeWithText("下一页").performClick()
        compose.onNodeWithText("2 / 2 · 共13项").assertExists()
        assertTrue(description().contains("量程0到20"))
        assertEquals(1, description().count { it == '；' })
        compose.onAllNodesWithTag("fitness-statistics-chart").assertCountEquals(1)
    }

    @Test fun whiteThemeEmptyStateKeepsAllFourTabs() {
        open(ToolboxPalette.White, false)
        compose.onNodeWithText("安排今天").assertExists()
        listOf("今日", "计划", "动作库", "统计").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        screenshot("white-empty")
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("fitness-page").performScrollToNode(hasText("该范围尚无有效记录。过去日期可从计划详情明确补记。"))
        compose.onNodeWithText("该范围尚无有效记录。过去日期可从计划详情明确补记。").assertExists()
    }

    @Test fun restoredDraftIsVisibleImmediatelyWithoutRestartingTheTool() {
        open(ToolboxPalette.Fresh, false)
        val draft = FitnessDraft(FitnessRules.newPlan("2026-10-24").copy(name = "换机前的草稿"), false,
            "2026-10-24", savedAt = clock.millis())
        val bytes = runBlocking {
            vm.dependencies.repository.saveDraft(draft)
            val output = ByteArrayOutputStream()
            vm.dependencies.backup.export(output, "test", clock.millis())
            vm.dependencies.repository.saveDraft(null)
            output.toByteArray()
        }
        awaitData { vm.data.draft == null }
        compose.runOnIdle { vm.inspect { ByteArrayInputStream(bytes) } }
        awaitData { vm.pendingRestore != null && !vm.busy }
        var restored = false
        compose.runOnIdle { vm.restore { restored = true } }
        awaitData { restored && !vm.busy }
        assertEquals(draft, vm.draft)
        compose.onNodeWithText("计划", useUnmergedTree = true).performClick()
        compose.onNodeWithText("未提交草稿：换机前的草稿").assertExists()
    }
}

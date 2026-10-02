package com.pockettoolbox.feature.diet.presentation

import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pockettoolbox.core.designsystem.PocketToolboxTheme
import com.pockettoolbox.feature.diet.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.IdlingPolicies
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DietScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @Before fun timeout() { IdlingPolicies.setMasterPolicyTimeout(60, TimeUnit.SECONDS) }
    @After fun resetTimeout() { store.clear(); IdlingPolicies.setMasterPolicyTimeout(60, TimeUnit.SECONDS) }

    private fun open(repo: UiRepository) {
        lateinit var model: DietViewModel
        compose.runOnUiThread { model = DietViewModel(repo, ioDispatcher = Dispatchers.Main.immediate); store.put("diet", model) }
        compose.setContent { PocketToolboxTheme { DietRoute(repo, {}, model) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("只记不舒服的那一餐").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) { compose.onNodeWithText(text).performClick() }
    private fun scrollClick(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    @Test fun firstUseMaintainsOptionsThenRecordsAndAppendsWithoutTypingNames() {
        val repo = UiRepository(DietData())
        open(repo)
        assertTrue(repo.data.value.meals.isEmpty())
        click("管理")
        click("＋ 新增品牌")
        compose.onNodeWithText("品牌名称").performTextInput("麦当劳")
        click("保存")
        compose.waitUntil(10_000) { repo.data.value.brands.size == 1 }
        scrollClick("＋ 新增食品")
        compose.onNodeWithText("食品名称").performTextInput("辣翅")
        click("保存")
        compose.waitUntil(10_000) { repo.data.value.foods.size == 1 }
        click("记录")
        click("＋ 记录一次不适")
        compose.onNodeWithText("记下这次不适").assertExists()
        click("保存这次不适")
        compose.waitUntil(10_000) { repo.data.value.events.size == 1 }
        scrollClick("＋ 再记一次腹泻")
        compose.waitUntil(10_000) { repo.data.value.events.size == 2 }
        assertEquals(1, repo.data.value.meals.size)
        scrollClick("结束本次记录")
        compose.waitUntil(10_000) { repo.data.value.activeMealId == null }
        compose.onNodeWithText("只记不舒服的那一餐").assertExists()
        click("统计")
        compose.onNodeWithText("品牌不适对比").assertExists()
        click("关联饮食次数")
        compose.onNodeWithText("同一顿饮食出现多次不适，只计一顿").assertExists()
        scrollClick("麦当劳")
        compose.onNodeWithText("辣翅的不适明细").assertExists()
    }

    @Test fun emptyCatalogCanBeManagedWithoutLosingMealDraft() {
        val repo = UiRepository(DietData())
        open(repo)
        click("＋ 记录一次不适")
        compose.onNodeWithText("保存这次不适").assertIsNotEnabled()
        click("没找到？去管理常用选项 →")
        compose.onNodeWithText("刚才的记录草稿已保留").assertExists()
        click("继续记录")
        compose.onNodeWithText("记下这次不适").assertExists()
        click("取消")
        assertTrue(repo.data.value.events.isEmpty())
        assertTrue(repo.data.value.meals.isEmpty())
    }

    private class UiRepository(initial: DietData) : DietRepository {
        override val data = MutableStateFlow(initial)
        override suspend fun initialize() = Unit
        override suspend fun snapshot() = data.value
        override suspend fun change(transform: (DietData) -> DietData): DietData = transform(data.value).also { data.value = it }
        override suspend fun replace(data: DietData) { this.data.value = data }
    }
}

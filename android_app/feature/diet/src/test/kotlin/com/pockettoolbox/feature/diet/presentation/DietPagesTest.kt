package com.pockettoolbox.feature.diet.presentation

import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pockettoolbox.core.designsystem.PocketToolboxTheme
import com.pockettoolbox.feature.diet.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Input dialogs are exercised by the device suite; the local runner tests the main pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DietPagesTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun clear() { store.clear() }
    private fun open(repo: PageRepository, title: String) {
        lateinit var model: DietViewModel
        compose.runOnUiThread {
            model = DietViewModel(repo, ioDispatcher = Dispatchers.Main.immediate)
            store.put("diet", model)
        }
        compose.setContent { PocketToolboxTheme { DietRoute(repo, {}, model) } }
        compose.onNodeWithText(title).assertExists()
    }

    @Test fun emptyAppHasNoMealsAndManagementKeepsDefaultType() {
        val repo = PageRepository(DietData())
        open(repo, "只记不舒服的那一餐")
        assertTrue(repo.data.value.events.isEmpty())
        compose.onNodeWithText("管理").performClick()
        compose.onNodeWithText("品牌与食品").assertExists()
        compose.onNodeWithText("腹泻 · 默认").assertExists()
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("当前时间和类型下没有记录").assertExists()
    }

    @Test fun appendFinishStatisticsAndBrandDetailAreConnected() {
        val repo = PageRepository(recorded())
        open(repo, "继续记录这顿饮食")
        compose.onNodeWithText("＋ 再记一次腹泻").performScrollTo().performClick()
        assertEquals(2, repo.data.value.events.size)
        assertEquals(1, repo.data.value.meals.size)
        compose.onNodeWithText("结束本次记录").performScrollTo().performClick()
        assertNull(repo.data.value.activeMealId)
        compose.onNodeWithText("只记不舒服的那一餐").assertExists()
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("品牌不适对比").assertExists()
        compose.onNodeWithText("关联饮食次数").performClick()
        compose.onNodeWithText("同一顿饮食出现多次不适，只计一顿").assertExists()
        compose.onNodeWithText("麦当劳").performScrollTo().performClick()
        compose.onNodeWithText("辣翅的不适明细").assertExists()
    }

    private class PageRepository(initial: DietData) : DietRepository {
        override val data = MutableStateFlow(initial)
        override suspend fun initialize() = Unit
        override suspend fun snapshot() = data.value
        override suspend fun change(transform: (DietData) -> DietData) = transform(data.value).also { data.value = it }
        override suspend fun replace(data: DietData) { this.data.value = data }
    }
}

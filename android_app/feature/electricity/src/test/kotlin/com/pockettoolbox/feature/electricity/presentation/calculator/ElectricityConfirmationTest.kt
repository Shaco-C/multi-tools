package com.pockettoolbox.feature.electricity.presentation.calculator

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pockettoolbox.core.common.money.Money
import com.pockettoolbox.core.designsystem.PocketToolboxTheme
import com.pockettoolbox.feature.electricity.domain.*
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
class ElectricityConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private var confirmed = 0
    private var cancelled = 0
    private fun open(count: Int = 2, fontScale: Float = 1f, error: String? = null, saving: Boolean = false) {
        val allocation = ElectricityAllocationCalculator().calculate(Money.ofCents(15528),
            (0 until count).map { MeterReadingInput("$it", "第 ${it + 1} 户", it == 0, BigDecimal("100"), if (it == 0) BigDecimal("184") else BigDecimal("283.5")) })
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PocketToolboxTheme {
                    ElectricityBillConfirmation(BillPreview("2026-11", allocation), saving, error, { confirmed++ }, { cancelled++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        draw("confirmation-${count}-${fontScale}")
        compose.mainClock.autoAdvance = true
    }
    private fun draw(name: String) {
        compose.runOnUiThread {
            val decor = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            try {
                decor.draw(android.graphics.Canvas(bitmap))
                val directory = File(requireNotNull(System.getProperty("electricity.screenshot.dir"))).apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
    @Test fun twoHouseholdBillShowsCompleteSummaryAndWaitsForConfirmation() {
        open()
        compose.onNodeWithText("本期总电费 155.28 元").assertIsDisplayed()
        compose.onNodeWithText("应付合计：48.76 + 106.52 = 155.28 元").assertIsDisplayed()
        compose.onNodeWithText("确认保存").assertIsDisplayed()
        assertEquals(0, confirmed)
        compose.onNodeWithText("确认保存").performClick()
        assertEquals(1, confirmed)
    }
    @Test fun failedSaveMessageIsVisibleWithoutScrollingManyHouseholds() {
        open(20, error = "账单保存失败，请重试")
        compose.onNodeWithText("账单保存失败，请重试").assertIsDisplayed()
        compose.onNodeWithText("确认保存").assertIsEnabled()
        compose.onNodeWithText("取消").assertIsEnabled()
    }
    @Test fun savingDisablesBothFooterActions() {
        open(saving = true)
        compose.onNodeWithText("正在保存…").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
    }

    @Test fun twentyHouseholdsAtLargeFontCanScrollWhileFooterStaysVisible() {
        open(20, 1.5f)
        compose.onNodeWithText("确认保存").assertIsDisplayed()
        compose.onNodeWithText("应付合计：", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("确认保存").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
        draw("confirmation-large-font-bottom")
    }
}

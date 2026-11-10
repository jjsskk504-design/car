package ai.prism.visualization

import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.ViewportTransform
import ai.prism.visualization.ui.RadarConfig
import ai.prism.visualization.ui.RadarScreen
import ai.prism.visualization.ui.RadarTestTags
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RadarScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private fun canvasDescription(): String =
        rule.onNodeWithTag(RadarTestTags.CANVAS).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()

    /** 캔버스에서 차량 A 중심의 픽셀 색(ARGB) */
    private fun pixelAtVehicleA(): Int {
        val node = rule.onNodeWithTag(RadarTestTags.CANVAS)
        val size = node.fetchSemanticsNode().size
        val img = node.captureToImage().asAndroidBitmap()
        val t = ViewportTransform(size.width.toFloat(), size.height.toFloat(), RoadConfig())
        val p = t.toScreen(3.5f, 15f - 0.6f) // 몸체 중심보다 약간 뒤(진행 방향 삼각형 회피)
        return img.getPixel(p.x.toInt(), p.y.toInt())
    }

    private fun assertColorClose(expected: Long, actual: Int) {
        val e = expected.toInt()
        val d = listOf(16, 8, 0).maxOf { s -> abs(((e shr s) and 0xFF) - ((actual shr s) and 0xFF)) }
        assertTrue("expected #%08X got #%08X".format(e, actual), d <= 6)
    }

    @Test
    fun riskColorTransitions_NORMAL_WATCH_WARNING_UNKNOWN() {
        var level by mutableStateOf(RiskLevel.NORMAL)
        rule.setContent {
            RadarScreen(TestData.vehicleFrame(), Modifier.size(900.dp, 600.dp),
                predictionFrame = TestData.predictionFrame(), riskFrame = TestData.riskFrame(a = level))
        }
        rule.onNodeWithText("강조할 위험 없음").assertIsDisplayed()
        assertColorClose(RiskStyle.NORMAL.fill, pixelAtVehicleA())

        level = RiskLevel.WATCH
        rule.waitForIdle()
        rule.onNodeWithText("주의 1대").assertIsDisplayed()
        assertTrue(canvasDescription().contains("주의 1대(A)"))
        assertColorClose(RiskStyle.WATCH.fill, pixelAtVehicleA())

        level = RiskLevel.WARNING
        rule.waitForIdle()
        rule.onNodeWithText("경고 1대").assertIsDisplayed()
        assertColorClose(RiskStyle.WARNING.fill, pixelAtVehicleA())

        level = RiskLevel.UNKNOWN
        rule.waitForIdle()
        rule.onNodeWithText("판단 보류 1대").assertIsDisplayed()
        // UNKNOWN 은 반투명 회색 + 점선 → 빨강/노랑이 아니어야 한다
        val px = pixelAtVehicleA()
        val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
        assertTrue("UNKNOWN should be neutral, got #%08X".format(px), abs(r - g) < 25 && abs(g - b) < 25)
    }

    @Test
    fun missingVehicleData_showsNotice() {
        rule.setContent { RadarScreen(null, Modifier.size(800.dp, 500.dp)) }
        rule.onNodeWithText("ⓘ 차량 데이터 없음").assertIsDisplayed()
        rule.onNodeWithText("강조할 위험 없음").assertIsDisplayed()
    }

    @Test
    fun staleData_suppressesWarning() {
        rule.setContent {
            RadarScreen(TestData.vehicleFrame(ts = 1000), Modifier.size(800.dp, 500.dp),
                riskFrame = TestData.riskFrame(1000, RiskLevel.WARNING), nowMs = 3000)
        }
        rule.onNodeWithText("ⓘ 데이터 지연 · 위험 표시 보류").assertIsDisplayed()
        rule.onNodeWithText("판단 보류 1대").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("never").fetchSemanticsNodes().size)
        assertTrue(!canvasDescription().contains("경고"))
    }

    @Test
    fun riskFrameMissing_allNeutral() {
        rule.setContent { RadarScreen(TestData.vehicleFrame(), Modifier.size(800.dp, 500.dp)) }
        rule.onNodeWithText("ⓘ 위험 정보 없음").assertIsDisplayed()
        assertColorClose(RiskStyle.NORMAL.fill, pixelAtVehicleA())
    }

    @Test
    fun vehicleClick_onlyInDetailMode() {
        var clicked: String? = null
        var mode by mutableStateOf(DisplayMode.DRIVING)
        rule.setContent {
            RadarScreen(TestData.vehicleFrame(), Modifier.size(800.dp, 600.dp),
                riskFrame = TestData.riskFrame(a = RiskLevel.WATCH), mode = mode,
                config = RadarConfig(), onVehicleClick = { clicked = it })
        }
        fun tapA() {
            val node = rule.onNodeWithTag(RadarTestTags.CANVAS)
            val size = node.fetchSemanticsNode().size
            val p = ViewportTransform(size.width.toFloat(), size.height.toFloat(), RoadConfig()).toScreen(3.5f, 15f)
            node.performTouchInput { click(androidx.compose.ui.geometry.Offset(p.x, p.y)) }
            rule.waitForIdle()
        }
        tapA()
        assertEquals(null, clicked)
        mode = DisplayMode.DETAIL
        rule.waitForIdle()
        tapA()
        assertEquals("A", clicked)
    }
}

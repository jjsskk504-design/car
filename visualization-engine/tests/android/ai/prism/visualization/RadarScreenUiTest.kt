package ai.prism.visualization

import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.ViewportTransform
import ai.prism.visualization.rendering.VehicleGeometry
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

    /**
     * 차량 A(중심 x=3.5, y=15, 전방 주행) 탑뷰 그래픽 위 한 점의 픽셀 색.
     * a = 길이 방향(앞 +0.5), b = 폭 방향(왼쪽 +0.5) 정규화 좌표 — VehicleShapes 와 같은 규약.
     */
    private fun pixelOnVehicleA(a: Float, b: Float): Int {
        val node = rule.onNodeWithTag(RadarTestTags.CANVAS)
        val size = node.fetchSemanticsNode().size
        val img = node.captureToImage().asAndroidBitmap()
        val t = ViewportTransform(size.width.toFloat(), size.height.toFloat(), RoadConfig())
        val w = VehicleGeometry.displayWidthM(4.6f, 1.9f, t)
        val p = t.toScreen(3.5f - b * w, 15f + a * 4.6f)
        return img.getPixel(p.x.toInt(), p.y.toInt())
    }

    private fun hood() = pixelOnVehicleA(0.40f, 0f)          // 보닛 = 차체 색
    private fun rightEdge() = pixelOnVehicleA(0f, -0.5f)     // 차체 오른쪽 외곽선

    /** 회색·노랑·빨강·파랑 기준색 중 가장 가까운 색(반투명 영역이 겹쳐도 판별되도록) */
    private fun nearest(px: Int): Long {
        val refs = listOf(RiskStyle.NORMAL.fill, RiskStyle.WATCH.fill, RiskStyle.WARNING.fill, RiskStyle.EGO.fill)
        return refs.minBy { r ->
            val e = r.toInt()
            listOf(16, 8, 0).sumOf { s -> val d = ((e shr s) and 0xFF) - ((px shr s) and 0xFF); d * d }
        }
    }

    private fun assertNearest(expected: Long, actual: Int, what: String) =
        assertEquals("$what: #%08X".format(actual), expected, nearest(actual))

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
        assertColorClose(RiskStyle.NORMAL.fill, hood())           // 일반 차량 = 회색 차체

        level = RiskLevel.WATCH
        rule.waitForIdle()
        rule.onNodeWithText("주의 1대").assertIsDisplayed()
        assertTrue(canvasDescription().contains("주의 1대(A)"))
        assertNearest(RiskStyle.NORMAL.fill, hood(), "WATCH 차체는 회색 유지")
        assertNearest(RiskStyle.WATCH.fill, rightEdge(), "WATCH 외곽선은 노란색")

        level = RiskLevel.WARNING
        rule.waitForIdle()
        rule.onNodeWithText("경고 1대").assertIsDisplayed()
        assertNearest(RiskStyle.NORMAL.fill, hood(), "WARNING 차체는 회색 유지")
        assertNearest(RiskStyle.WARNING.fill, rightEdge(), "WARNING 외곽선은 빨간색")

        level = RiskLevel.UNKNOWN
        rule.waitForIdle()
        rule.onNodeWithText("판단 보류 1대").assertIsDisplayed()
        // UNKNOWN 은 반투명 회색 + 점선 → 빨강/노랑이 아니어야 한다
        val px = hood()
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
        rule.onNodeWithText("ⓘ 데이터 지연 2.0초 · 위험 표시 보류").assertIsDisplayed()  // 경과 시간 표시(별도 처리)
        rule.onNodeWithText("판단 보류 1대").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("never").fetchSemanticsNodes().size)
        assertTrue(!canvasDescription().contains("경고"))
    }

    @Test
    fun riskFrameMissing_allNeutral() {
        rule.setContent { RadarScreen(TestData.vehicleFrame(), Modifier.size(800.dp, 500.dp)) }
        rule.onNodeWithText("ⓘ 위험 정보 없음").assertIsDisplayed()
        assertColorClose(RiskStyle.NORMAL.fill, hood())
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

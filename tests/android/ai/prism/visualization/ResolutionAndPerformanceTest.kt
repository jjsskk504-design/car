package ai.prism.visualization

import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.overlays.drawRadarScene
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.rendering.ViewportTransform
import ai.prism.visualization.replay.ReplayScreen
import ai.prism.visualization.replay.ReplayTestTags
import ai.prism.visualization.replay.rememberReplayState
import ai.prism.visualization.ui.RadarScreen
import ai.prism.visualization.ui.RadarTestTags
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class ResolutionAndPerformanceTest {
    @get:Rule val rule = createComposeRule()

    /**
     * 가로형 차량 디스플레이 비율별 레이아웃 확인(밀도 1로 고정해 px = dp).
     * 테스트 창보다 큰 해상도는 같은 비율로 축소해 검증한다.
     */
    @Test
    fun landscapeResolutions_layoutAndRoadFit() {
        val sizes = listOf(
            "1920x720" to (1440 to 540),   // 8:3 (1920x720 의 0.75배)
            "1280x720" to (1280 to 720),   // 16:9
            "2560x1080" to (1280 to 540),  // 21:9 (0.5배)
            "1024x600" to (1024 to 600),
            "800x480" to (800 to 480),
        )
        val session = TestData.session()
        var current by mutableStateOf(sizes.first())
        rule.setContent {
            val (name, wh) = current
            val (w, h) = wh
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(w.dp, h.dp)) {
                    val st = rememberReplayState("res-$name")
                    st.positionMs = 2_500
                    ReplayScreen(session, Modifier.requiredSize(w.dp, h.dp), state = st)
                }
            }
        }
        sizes.forEach { entry ->
            val name = entry.first
            current = entry
            rule.waitForIdle()
            rule.onNodeWithTag(ReplayTestTags.PLAY).assertIsDisplayed()
            rule.onNodeWithTag(ReplayTestTags.SLIDER).assertIsDisplayed()
            val canvas = rule.onNodeWithTag(RadarTestTags.CANVAS).fetchSemanticsNode().size
            assertTrue("$name canvas too small: $canvas", canvas.width >= 200 && canvas.height >= 150)
            val t = ViewportTransform(canvas.width.toFloat(), canvas.height.toFloat(), RoadConfig())
            assertTrue("$name road overflow", t.roadLeftPx >= 0 && t.roadRightPx <= canvas.width)
            TestData.save(rule.onRoot().captureToImage().asAndroidBitmap(), "replay_$name.png")
        }
    }

    @Test
    fun drivingRadar_screenshots() {
        var level by mutableStateOf(RiskLevel.NORMAL)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f)) {
                RadarScreen(TestData.vehicleFrame(), Modifier.requiredSize(560.dp, 640.dp),
                    predictionFrame = TestData.predictionFrame(), riskFrame = TestData.riskFrame(a = level, sourceB = true))
            }
        }
        listOf(RiskLevel.NORMAL, RiskLevel.WATCH, RiskLevel.WARNING, RiskLevel.UNKNOWN).forEach { lv ->
            level = lv
            rule.waitForIdle()
            TestData.save(rule.onRoot().captureToImage().asAndroidBitmap(), "radar_${lv.name.lowercase()}.png")
        }
    }

    /**
     * 차량 수 증가에 따른 프레임당 렌더링 비용. Scene 생성 + 실제 그리기(오프스크린 비트맵, CPU 래스터)를 함께 잰다.
     * 화면 합성·GPU 시간은 포함하지 않으므로 실제 FPS 는 데모 앱 stress 모드(logcat PRISM_FPS)로 별도 측정한다.
     */
    @Test
    fun renderCost_scalesWithVehicleCount() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(1.5f)
        val tm = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr)
        val w = 960; val h = 720
        val bmp = ImageBitmap(w, h)
        val canvas = Canvas(bmp)
        val drawScope = CanvasDrawScope()
        val transform = ViewportTransform(w.toFloat(), h.toFloat(), RoadConfig())
        val lines = ArrayList<String>()
        lines += "vehicles,avg_ms,p95_ms,max_fps_equiv"
        for (n in listOf(5, 20, 50, 100, 200)) {
            val frames = (0 until 120).map { synthetic(n, it * 16L) }
            fun once(i: Int) {
                val (vf, rf) = frames[i % frames.size]
                val scene = SceneBuilder.build(vf, null, rf, DisplayMode.DETAIL)
                drawScope.draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                    drawRadarScene(scene, transform, tm)
                }
            }
            repeat(20) { once(it) } // 워밍업
            val times = (0 until 100).map { i ->
                val t0 = System.nanoTime(); once(i); (System.nanoTime() - t0) / 1e6
            }.sorted()
            val avg = times.average()
            val p95 = times[(times.size * 0.95).toInt()]
            lines += "%d,%.2f,%.2f,%.0f".format(n, avg, p95, 1000 / avg)
        }
        lines.forEach { Log.i("PRISM_BENCH", it) }
        java.io.File(TestData.outputDir(), "render_benchmark.csv").writeText(lines.joinToString("\n"))
        TestData.save(bmp.asAndroidBitmap(), "benchmark_200_vehicles.png")
        // 계측 테스트 APK 는 debuggable(ART 최적화 꺼짐)이라 절대 성능 기준으로 쓰지 않는다.
        // 회귀 감지용 느슨한 기준만 둔다. 실제 화면 FPS 는 release 데모 stress 모드로 측정(README 참고).
        val avg50 = lines.first { it.startsWith("50,") }.split(",")[1].toDouble()
        assertTrue("50 vehicles avg $avg50 ms > 50ms (debuggable regression bound)", avg50 <= 50.0)
    }

    private fun synthetic(n: Int, tMs: Long): Pair<VehicleFrame, RiskFrame> {
        val t = tMs / 1000.0
        val vs = ArrayList<VehicleState>()
        vs += VehicleState("ego", 0f, 0f, 0f, 27f, (PI / 2).toFloat(), 4.6f, 1.9f, "L2")
        val risks = ArrayList<VehicleRisk>()
        for (i in 0 until n) {
            val lane = i % 3
            val y = -28f + (i / 3) * (110f / ((n + 2) / 3)) + (3 * sin(t + i)).toFloat()
            vs += VehicleState("V$i", (lane - 1) * 3.5f, y, 0f, 27f, (PI / 2 + 0.05 * sin(t + i)).toFloat(), 4.6f, 1.9f, "L${lane + 1}")
            val lv = when (i % 7) { 0 -> RiskLevel.WATCH; 3 -> RiskLevel.WARNING; 5 -> RiskLevel.UNKNOWN; else -> RiskLevel.NORMAL }
            risks += VehicleRisk("V$i", lv, listOf("FRONT_CLOSING"))
        }
        return VehicleFrame(tMs, "ego", vs) to RiskFrame(tMs, risks)
    }
}

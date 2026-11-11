package ai.prism.visualization

import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.rendering.FrameInterpolator
import ai.prism.visualization.rendering.VehicleGeometry
import ai.prism.visualization.rendering.ViewportTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GeometryTest {
    private val eps = 1e-3f
    private val forward = (PI / 2).toFloat()

    @Test
    fun egoIsAnchoredAndAxesPointRightAndUp() {
        val t = ViewportTransform(1000f, 1100f, RoadConfig(), )
        val ego = t.toScreen(0f, 0f)
        assertEquals(500f, ego.x, eps)                       // 3차로 가운데 차선 → 화면 중앙
        val cfg = t.config
        assertEquals(1100f * cfg.aheadM / (cfg.aheadM + cfg.behindM), ego.y, eps)  // ahead/(ahead+behind)
        assertTrue(t.toScreen(3.5f, 0f).x > ego.x)             // x+ = 오른쪽
        assertTrue(t.toScreen(0f, 10f).y < ego.y)              // y+ = 전방 = 화면 위
        val back = t.toWorld(t.toScreen(1.2f, 33f).x, t.toScreen(1.2f, 33f).y)
        assertEquals(1.2f, back.x, eps); assertEquals(33f, back.y, eps)
    }

    @Test
    fun roadFitsAcrossLandscapeResolutions() {
        // 차량용 가로 해상도에서 레이더 패널 폭(화면의 약 절반)과 전체 폭 모두 확인
        val sizes = listOf(1920f to 720f, 1280f to 720f, 2560f to 1080f, 1024f to 600f, 800f to 480f, 3840f to 1080f)
        for ((w, h) in sizes) for (panel in listOf(w, w * 0.5f)) for (lanes in listOf(3, 4, 5)) {
            val road = RoadConfig(laneCount = lanes, egoLaneIndex = lanes / 2)
            val t = ViewportTransform(panel, h, road)
            assertTrue("$w x $h panel=$panel lanes=$lanes", t.roadLeftPx >= 0f && t.roadRightPx <= panel)
            assertTrue("lane too thin at $panel", (t.roadRightPx - t.roadLeftPx) / lanes > 20f)
        }
    }

    @Test
    fun egoInOuterLaneStillCentersRoad() {
        val t = ViewportTransform(800f, 600f, RoadConfig(laneCount = 3, egoLaneIndex = 0))
        assertEquals(400f, (t.roadLeftPx + t.roadRightPx) / 2, eps)
        assertTrue(t.toScreen(0f, 0f).x < 400f)
    }

    @Test
    fun cornersFollowHeading() {
        val c = VehicleGeometry.corners(0f, 0f, forward, 4f, 2f)
        // 전방 주행: 앞-오른 (1, 2)
        assertEquals(1f, c[0].x, eps); assertEquals(2f, c[0].y, eps)
        assertEquals(-1f, c[3].x, eps); assertEquals(2f, c[3].y, eps)
        // 오른쪽(+x)을 향하면 길이 방향이 x 축
        val r = VehicleGeometry.corners(0f, 0f, 0f, 4f, 2f)
        assertEquals(2f, r.maxOf { it.x }, eps); assertEquals(1f, r.maxOf { it.y }, eps)
        // 코(진행 방향 삼각형)는 앞쪽에 있다
        val nose = VehicleGeometry.nose(0f, 0f, forward, 4.6f, 1.9f)
        assertTrue(nose[0].y > 1.5f)
    }

    @Test
    fun interpolatesById_andDoesNotInventVehicles() {
        fun v(id: String, y: Float, h: Float = forward) = VehicleState(id, 0f, y, 0f, 20f, h, 4.6f, 1.9f, "L1")
        val a = VehicleFrame(0, "ego", listOf(v("ego", 0f), v("A", 10f), v("gone", 30f)))
        val b = VehicleFrame(100, "ego", listOf(v("ego", 0f), v("A", 12f), v("new", -20f)))
        val m = FrameInterpolator.lerp(a, b, 0.25f)
        assertEquals(10.5f, m.vehicles.first { it.id == "A" }.y, eps)
        assertEquals(setOf("ego", "A", "gone"), m.vehicles.map { it.id }.toSet())
        assertEquals(setOf("ego", "A", "new"), FrameInterpolator.lerp(a, b, 0.75f).vehicles.map { it.id }.toSet())
        assertEquals(25L, m.timestampMs)
    }

    @Test
    fun headingInterpolationTakesShortestArc() {
        val h = FrameInterpolator.mixAngle((PI - 0.1).toFloat(), (-PI + 0.1).toFloat(), 0.5f)
        assertEquals(PI.toFloat(), kotlin.math.abs(h), 1e-3f)
    }

    @Suppress("unused")
    private fun p(x: Float, y: Float) = Point2(x, y)
}

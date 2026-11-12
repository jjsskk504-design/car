package ai.prism.visualization

import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RiskZone
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.rendering.ConvexOverlap
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.NoticeKind
import ai.prism.visualization.rendering.RegionKind
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** UI/UX 개선(feature/visualization-polish)에서 추가한 표시 규칙. */
class PolishRulesTest {
    private val fwd = (PI / 2).toFloat()
    private fun v(id: String, x: Float, y: Float) = VehicleState(id, x, y, 0f, 25f, fwd, 4.6f, 1.9f, "L2")
    private fun box(cx: Float, cy: Float, hw: Float = 1f, hl: Float = 2.5f) =
        listOf(Point2(cx - hw, cy - hl), Point2(cx + hw, cy - hl), Point2(cx + hw, cy + hl), Point2(cx - hw, cy + hl))

    // 자차·차량 모두 vy = 25 → 상대 운동 표시에서 영역은 dt × 25m 만큼 뒤로 이동
    private val vf = VehicleFrame(1000, "ego", listOf(v("ego", 0f, 0f), v("A", 3.5f, 20f), v("T", 3.5f, 45f), v("Z", -3.5f, 60f)))

    @Test
    fun convexOverlap_basic() {
        assertTrue(ConvexOverlap.overlaps(box(0f, 0f), box(1.5f, 1f)))
        assertFalse(ConvexOverlap.overlaps(box(0f, 0f), box(3f, 0f)))
        assertFalse(ConvexOverlap.overlaps(box(0f, 0f), box(0f, 6f)))
        // 회전된 사각형(마름모)과 모서리 근처
        val diamond = listOf(Point2(0f, 2f), Point2(2f, 0f), Point2(0f, -2f), Point2(-2f, 0f))
        assertTrue(ConvexOverlap.overlaps(diamond, box(1.5f, 1.5f, 0.6f, 0.6f)))
        assertFalse(ConvexOverlap.overlaps(diamond, box(1.9f, 1.9f, 0.3f, 0.3f)))
    }

    @Test
    fun regions_splitIntoOccupancyAndConflictWithEgo() {
        // dt=1000 → 표시 위치 = (x, y - 25). 자차(원점)와 겹치는 영역은 CONFLICT, 먼 영역은 OCCUPANCY
        val zones = listOf(
            RiskZone(1000, box(0.5f, 25f + 1f)),   // 표시 (0.5, 1) → 자차와 겹침
            RiskZone(2000, box(3.5f, 50f + 30f)),  // 표시 (3.5, 30) → 겹치지 않음
        )
        val rf = RiskFrame(1000, listOf(VehicleRisk("A", RiskLevel.WARNING, listOf("PATH_OVERLAP"), riskZones = zones)))
        val s = SceneBuilder.build(vf, null, rf, DisplayMode.DRIVING, 1000)
        assertEquals(listOf(RegionKind.CONFLICT, RegionKind.OCCUPANCY), s.regions.map { it.kind })
        assertEquals(listOf(1000L, 2000L), s.regions.map { it.dtMs })
    }

    @Test
    fun sourceHighlight_onlyWhenLinkedToEgoRisk() {
        // T 는 A(경고)의 원인 → 강조. Z 는 NORMAL 차량의 원인으로만 적혀 있음 → 강조 안 함
        val rf = RiskFrame(1000, listOf(
            VehicleRisk("A", RiskLevel.WARNING, listOf("CHAIN_DECEL"), sourceVehicleIds = listOf("T")),
            VehicleRisk("T", RiskLevel.NORMAL, emptyList(), sourceVehicleIds = listOf("Z")),
        ))
        val s = SceneBuilder.build(vf, null, rf, DisplayMode.DRIVING, 1000)
        assertEquals(listOf("T" to "A"), s.relations.map { it.fromId to it.toId })
        assertTrue(s.vehicles.first { it.id == "T" }.isSource)
        assertFalse(s.vehicles.first { it.id == "Z" }.isSource)
    }

    @Test
    fun relations_onlyFromConfirmedData() {
        // source_vehicle_ids 가 없으면(팀 실제 RiskFrame) 관계를 추정해서 그리지 않는다
        val rf = RiskFrame(1000, listOf(VehicleRisk("A", RiskLevel.WARNING, listOf("HARD_BRAKING"))))
        val s = SceneBuilder.build(vf, null, rf, DisplayMode.DETAIL, 1000)
        assertTrue(s.relations.isEmpty())
        assertTrue(s.vehicles.none { it.isSource })
    }

    @Test
    fun staleData_markedSeparatelyFromUnknown() {
        val rf = RiskFrame(1000, listOf(VehicleRisk("A", RiskLevel.WARNING, listOf("FRONT_CLOSING"), riskZones = listOf(RiskZone(1000, box(0f, 25f))))))
        val s = SceneBuilder.build(vf, null, rf, DisplayMode.DRIVING, nowMs = 2200)
        assertTrue(s.stale)
        assertEquals(1200L, s.dataAgeMs)
        assertEquals("데이터 지연 1.2초 · 위험 표시 보류", s.notices.first { it.kind == NoticeKind.DATA_STALE }.text)
        val a = s.vehicles.first { it.id == "A" }
        assertEquals(DisplayLevel.UNKNOWN, a.level)   // 경고 억제
        assertTrue(a.stale)                            // 판단 보류(센서)와 구분되는 표시
        assertTrue(s.regions.isEmpty())                // 믿을 수 없는 위험 영역은 그리지 않음
        // 신선한 데이터의 UNKNOWN(센서 누락)은 stale 이 아니다
        val fresh = SceneBuilder.build(vf, null, RiskFrame(1000, listOf(VehicleRisk("A", RiskLevel.UNKNOWN, listOf("SENSOR_DROPOUT")))), nowMs = 1100)
        assertFalse(fresh.stale)
        assertFalse(fresh.vehicles.first { it.id == "A" }.stale)
    }

    @Test
    fun unknownStyle_isBlueDashedWithQuestionMark() {
        val st = RiskStyle.UNKNOWN
        assertTrue(st.dashed)
        assertEquals("?", st.glyph)
        val r = (st.stroke shr 16) and 0xFF; val b = st.stroke and 0xFF
        assertTrue("파란 계열이어야 함: #%08X".format(st.stroke), b > r + 60)
        assertTrue(st.stroke != RiskStyle.EGO.fill)
    }
}

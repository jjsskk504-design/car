package ai.prism.visualization

import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.PredictionFrame
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RiskZone
import ai.prism.visualization.model.Trajectory
import ai.prism.visualization.model.TrajectoryPoint
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehiclePrediction
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.NoticeKind
import ai.prism.visualization.rendering.RiskIcon
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SceneBuilderTest {
    private val fwd = (PI / 2).toFloat()
    private fun v(id: String, x: Float, y: Float) = VehicleState(id, x, y, 0f, 25f, fwd, 4.6f, 1.9f, "L2")
    private val vf = VehicleFrame(1000, "ego", listOf(v("ego", 0f, 0f), v("A", 3.5f, 15f), v("B", 0f, 30f), v("C", -3.5f, -10f)))
    private fun risk(id: String, lv: RiskLevel, src: List<String> = emptyList(), mode: String? = null) =
        VehicleRisk(id, lv, listOf("FRONT_CLOSING"), null,
            listOf(RiskZone(1000, listOf(Point2(0f, 0f), Point2(1f, 0f), Point2(1f, 1f)))),
            sourceVehicleIds = src, trajectoryMode = mode)

    @Test
    fun styleMapping_isNotColorOnly() {
        val styles = listOf(RiskLevel.NORMAL, RiskLevel.WATCH, RiskLevel.WARNING, RiskLevel.UNKNOWN).map { RiskStyle.of(it) }
        // 색이 모두 다르고
        assertEquals(4, styles.map { it.fill }.toSet().size)
        // 색 외 단서(아이콘·글자·선 모양)도 등급마다 다르다
        assertEquals(4, styles.map { Triple(it.icon, it.glyph, it.dashed) }.toSet().size)
        assertEquals(RiskIcon.NONE, RiskStyle.NORMAL.icon)
        assertTrue(RiskStyle.UNKNOWN.dashed)
        assertEquals(0xFF8A919CL, RiskStyle.NORMAL.fill)   // 회색
        assertEquals(0xFFF2C230L, RiskStyle.WATCH.fill)    // 노란색
        assertEquals(0xFFE5484DL, RiskStyle.WARNING.fill)  // 빨간색
        assertNotEquals(RiskStyle.WARNING.fill, RiskStyle.UNKNOWN.fill)
    }

    @Test
    fun colorTransition_followsRiskFrames() {
        val seq = listOf(RiskLevel.NORMAL, RiskLevel.WATCH, RiskLevel.WARNING, RiskLevel.WATCH, RiskLevel.NORMAL)
        val shown = seq.map { lv ->
            SceneBuilder.build(vf, null, RiskFrame(1000, listOf(risk("A", lv)))).vehicles.first { it.id == "A" }.level
        }
        assertEquals(listOf(DisplayLevel.NORMAL, DisplayLevel.WATCH, DisplayLevel.WARNING, DisplayLevel.WATCH, DisplayLevel.NORMAL), shown)
    }

    @Test
    fun noRiskFrame_allNeutralWithNotice() {
        val s = SceneBuilder.build(vf, null, null)
        assertTrue(s.vehicles.filter { !it.isEgo }.all { it.level == DisplayLevel.NORMAL })
        assertTrue(s.notices.any { it.kind == NoticeKind.NO_RISK_DATA })
        assertEquals("강조할 위험 없음", s.summary())
    }

    @Test
    fun noVehicleFrame_showsEgoOnly() {
        val s = SceneBuilder.build(null, null, RiskFrame(0, listOf(risk("A", RiskLevel.WARNING))))
        assertEquals(1, s.vehicles.size); assertTrue(s.vehicles[0].isEgo)
        assertTrue(s.notices.any { it.kind == NoticeKind.NO_VEHICLE_DATA })
        assertTrue(s.ghosts.isEmpty())
    }

    @Test
    fun staleData_suppressesWarningsToUnknown() {
        val rf = RiskFrame(1000, listOf(risk("A", RiskLevel.WARNING), risk("B", RiskLevel.WATCH, listOf("A"))))
        val s = SceneBuilder.build(vf, null, rf, nowMs = 1000 + 900)
        assertTrue(s.notices.any { it.kind == NoticeKind.DATA_STALE })
        assertEquals(DisplayLevel.UNKNOWN, s.vehicles.first { it.id == "A" }.level)
        assertEquals(DisplayLevel.UNKNOWN, s.vehicles.first { it.id == "B" }.level)
        assertTrue(s.relations.isEmpty())
        assertFalse(s.vehicles.any { it.level == DisplayLevel.WARNING })
        // 신선한 데이터면 그대로
        val fresh = SceneBuilder.build(vf, null, rf, nowMs = 1000 + 100)
        assertEquals(DisplayLevel.WARNING, fresh.vehicles.first { it.id == "A" }.level)
    }

    @Test
    fun riskOutOfSync_suppressesWarnings() {
        val s = SceneBuilder.build(vf, null, RiskFrame(1000 - 800, listOf(risk("A", RiskLevel.WARNING))))
        assertTrue(s.notices.any { it.kind == NoticeKind.RISK_OUT_OF_SYNC })
        assertEquals(DisplayLevel.UNKNOWN, s.vehicles.first { it.id == "A" }.level)
    }

    @Test
    fun riskForMissingVehicle_isGhostUnknown_neverWarning() {
        val s = SceneBuilder.build(vf, null, RiskFrame(1000, listOf(risk("LOST", RiskLevel.WARNING))))
        assertEquals(1, s.ghosts.size)
        assertEquals("LOST", s.ghosts[0].vehicleId)
        assertEquals(DisplayLevel.UNKNOWN, s.highestLevel)
        assertTrue(s.summary().contains("판단 보류"))
    }

    @Test
    fun invalidVehicles_droppedWithNotice() {
        val bad = vf.copy(vehicles = vf.vehicles + v("X", Float.NaN, 1f) + v("A", 9f, 9f) /* 중복 ID */)
        val s = SceneBuilder.build(bad, null, null)
        assertEquals(4, s.vehicles.size)
        assertTrue(s.notices.any { it.kind == NoticeKind.INVALID_VEHICLE_DATA })
        assertEquals(15f, s.vehicles.first { it.id == "A" }.center.y)
    }

    @Test
    fun drivingMode_onlyHighlightsRiskVehiclesAndChosenCandidate() {
        fun traj(m: String) = Trajectory(m, null, listOf(TrajectoryPoint(1000, 0f, 20f, null)))
        val pf = PredictionFrame(1000, listOf(
            VehiclePrediction("A", listOf(traj("lane_keep"), traj("lane_change_left"))),
            VehiclePrediction("B", listOf(traj("lane_keep"))),
        ))
        val rf = RiskFrame(1000, listOf(risk("A", RiskLevel.WATCH, mode = "lane_change_left"), VehicleRisk("B", RiskLevel.NORMAL, emptyList())))
        val drive = SceneBuilder.build(vf, pf, rf, DisplayMode.DRIVING)
        assertEquals(listOf("A" to "lane_change_left"), drive.trajectories.map { it.vehicleId to it.mode })
        assertTrue(drive.trajectories.single().emphasized)
        assertEquals(listOf("A"), drive.regions.map { it.vehicleId })

        val detail = SceneBuilder.build(vf, pf, rf, DisplayMode.DETAIL)
        assertEquals(setOf("A" to "lane_keep", "A" to "lane_change_left", "B" to "lane_keep"), detail.trajectories.map { it.vehicleId to it.mode }.toSet())
        assertEquals(setOf("A" to "lane_change_left"), detail.trajectories.filter { it.emphasized }.map { it.vehicleId to it.mode }.toSet())
    }

    @Test
    fun danglingCandidateReference_stillShowsTrajectories() {
        val pf = PredictionFrame(1000, listOf(VehiclePrediction("A", listOf(
            Trajectory("lane_keep", null, listOf(TrajectoryPoint(1000, 3.5f, 40f, null)))))))
        val rf = RiskFrame(1000, listOf(risk("A", RiskLevel.WARNING, mode = "not_in_prediction")))
        val s = SceneBuilder.build(vf, pf, rf, DisplayMode.DRIVING)
        assertEquals(listOf("lane_keep"), s.trajectories.map { it.mode })
    }

    @Test
    fun drivingMode_capsNumberOfEmphasizedVehicles() {
        val many = VehicleFrame(1000, "ego", listOf(v("ego", 0f, 0f)) + (0 until 8).map { v("V$it", 3.5f, it * 10f) })
        val rf = RiskFrame(1000, (0 until 8).map { risk("V$it", if (it < 2) RiskLevel.WARNING else RiskLevel.WATCH) })
        val s = SceneBuilder.build(many, null, rf, DisplayMode.DRIVING)
        assertEquals(3, s.regions.size)
        assertTrue(s.regions.map { it.vehicleId }.containsAll(listOf("V0", "V1")))
        assertEquals(8, SceneBuilder.build(many, null, rf, DisplayMode.DETAIL).regions.size)
    }

    @Test
    fun chainRelation_andSourceMarking() {
        val rf = RiskFrame(1000, listOf(risk("A", RiskLevel.WARNING, listOf("B")), VehicleRisk("B", RiskLevel.NORMAL, emptyList())))
        val s = SceneBuilder.build(vf, null, rf)
        assertEquals(1, s.relations.size)
        assertEquals("B", s.relations[0].fromId); assertEquals("A", s.relations[0].toId)
        assertTrue(s.vehicles.first { it.id == "B" }.isSource)
        assertEquals(DisplayLevel.NORMAL, s.vehicles.first { it.id == "B" }.level)
    }

    @Test
    fun missingEgoInFrame_addsDefaultEgo() {
        val noEgo = vf.copy(vehicles = vf.vehicles.filter { it.id != "ego" })
        val s = SceneBuilder.build(noEgo, null, null)
        assertEquals(1, s.vehicles.count { it.isEgo })
    }
}

package ai.prism.visualization

import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.rendering.DataHealth
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RiskAnalyzer
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.rendering.SceneRelation
import ai.prism.visualization.rendering.severity
import ai.prism.visualization.replay.ReplayJson
import ai.prism.visualization.replay.ReplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI

/** 위험 분석 패널 로직: 화면 표시와의 일관성, 연쇄 경로, 센서 상태, 재현성. */
class RiskAnalyzerTest {

    private val ids = listOf("normal", "front_brake", "lane_change", "chain", "sensor_dropout")
    private fun load(id: String): ReplaySession =
        ReplayJson.parse(File("demo/src/main/assets/scenarios/$id.json").readText()).session

    private fun analyses(s: ReplaySession, mode: DisplayMode = DisplayMode.DRIVING) = s.frames.map { f ->
        val scene = SceneBuilder.build(f.vehicle, f.prediction, f.risk, mode, f.vehicle.timestampMs)
        scene to RiskAnalyzer.analyze(scene, f.risk, s.road)
    }

    @Test
    fun countsAndItemsMatchTheRadarForEveryFrame() {
        for (id in ids) for ((scene, a) in analyses(load(id))) {
            val tag = "$id t=${scene.timestampMs}"
            assertEquals(tag, scene.count(DisplayLevel.WARNING), a.warningCount)
            assertEquals(tag, scene.count(DisplayLevel.WATCH), a.watchCount)
            assertEquals(tag, scene.count(DisplayLevel.UNKNOWN), a.unknownCount)
            assertEquals(tag, a.warningCount + a.watchCount + a.unknownCount, a.items.size)
            // 패널의 각 차량 등급 = 레이더에 그려진 등급
            a.items.filter { !it.unobserved }.forEach { i ->
                assertEquals(tag, scene.vehicles.first { it.id == i.vehicleId }.level, i.level)
            }
            // 주요 위험 = 가장 높은 등급
            a.primary?.let { assertEquals(tag, a.items.maxOf { it.level.severity }, it.level.severity) }
            assertEquals(tag, scene.highestLevel, a.overall)
        }
    }

    @Test
    fun normalScenario_noRisk_sensorOk() {
        for ((_, a) in analyses(load("normal"))) {
            assertTrue(a.items.isEmpty())
            assertTrue(a.chains.isEmpty())
            assertEquals(DataHealth.OK, a.sensor.health)
        }
    }

    @Test
    fun chainScenario_showsTruckAsFirstCauseAndRearPropagation() {
        val all = analyses(load("chain"), DisplayMode.DETAIL).map { it.second }
        assertTrue("T1 → R1 경로", all.any { a -> a.chains.any { it.take(2) == listOf("T1", "R1") } })
        assertTrue("R1 → B1 경로(후방 전파)", all.any { a -> a.chains.any { c -> c.windowed(2).any { it == listOf("R1", "B1") } } })
        // 최초 원인은 항상 경로 맨 앞이며, 경로 끝 차량은 위험 차량이다
        all.forEach { a -> a.chains.forEach { c -> assertTrue(a.items.any { it.vehicleId == c.last() }) } }
    }

    @Test
    fun sensorDropout_missingVehicleIsNeverNormal_andSensorStatusSaysSo() {
        val s = load("sensor_dropout")
        var checked = 0
        analyses(s).forEachIndexed { i, (_, a) ->
            val missing = s.frames[i].vehicle.vehicles.none { it.id == "R1" }
            if (missing) {
                checked++
                assertEquals(DataHealth.PARTIAL, a.sensor.health)
                assertTrue(a.sensor.missingVehicleIds.contains("R1"))
                val item = a.items.first { it.vehicleId == "R1" }
                assertEquals(DisplayLevel.UNKNOWN, item.level)
                assertTrue(item.unobserved)
                assertTrue(a.sensor.summary.contains("판단 보류"))
            }
        }
        assertTrue(checked >= 10)
    }

    @Test
    fun staleData_reportsLimitation() {
        val fwd = (PI / 2).toFloat()
        val vf = VehicleFrame(1000, "ego", listOf(VehicleState("ego", 0f, 0f, 0f, 25f, fwd, 4.6f, 1.9f, "L2"), VehicleState("A", 3.5f, 20f, 0f, 25f, fwd, 4.6f, 1.9f, "L3")))
        val rf = RiskFrame(1000, listOf(VehicleRisk("A", RiskLevel.WARNING, listOf("FRONT_CLOSING"))))
        val scene = SceneBuilder.build(vf, null, rf, DisplayMode.DRIVING, nowMs = 2500)
        val a = RiskAnalyzer.analyze(scene, rf)
        assertEquals(DataHealth.STALE, a.sensor.health)
        assertEquals("데이터 지연 1.5초 · 위험 표시 보류", a.sensor.summary)
        assertEquals(0, a.warningCount) // 경고로 세지 않음(판단 보류)
        assertEquals(1, a.unknownCount)
    }

    @Test
    fun replayIsDeterministic() {
        for (id in ids) {
            val a1 = analyses(load(id)).map { it.second }
            val a2 = analyses(load(id)).map { it.second }
            assertEquals(id, a1, a2)
        }
    }

    @Test
    fun relativePositionAndTtcOnlyFromData() {
        assertEquals("같은 차로 · 전방 15m", RiskAnalyzer.relativePosition(0.2f, 15.4f))
        assertEquals("오른쪽 1차로 · 후방 8m", RiskAnalyzer.relativePosition(3.4f, -8f))
        assertEquals("왼쪽 1차로 · 나란히", RiskAnalyzer.relativePosition(-3.5f, 1f))
        // RiskFrame 에 ttc_s 가 없으면 TTC 를 만들지 않는다
        val chain = analyses(load("lane_change")).map { it.second }.flatMap { it.items }
        val s = load("lane_change")
        val provided = s.frames.flatMap { f -> f.risk?.risks.orEmpty() }.any { it.ttcS != null }
        if (!provided) assertTrue(chain.all { it.ttcS == null })
    }

    @Test
    fun chainsHandleBranchesAndCycles() {
        fun r(a: String, b: String) = SceneRelation(a, b, DisplayLevel.WATCH)
        assertEquals(listOf(listOf("T", "R", "B"), listOf("T", "R", "C")),
            RiskAnalyzer.chains(listOf(r("T", "R"), r("R", "B"), r("R", "C"))))
        val cyc = RiskAnalyzer.chains(listOf(r("A", "B"), r("B", "A")))
        assertTrue(cyc.isNotEmpty()); assertTrue(cyc.all { it.toSet().size == it.size })
        assertFalse(RiskAnalyzer.chains(emptyList()).isNotEmpty())
    }
}

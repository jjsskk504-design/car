package ai.prism.visualization

import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.Scene
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.replay.ReplayJson
import ai.prism.visualization.replay.ReplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * demo 의 샘플 JSON(5개 시연 시나리오)을 실제로 파싱해 화면 규칙을 검증한다.
 * 위험 등급 자체는 Mock 이므로, 여기서 확인하는 것은 "주어진 RiskFrame 을 시각화 엔진이 올바르게 표시하는가"이다.
 */
class SampleScenarioTest {

    private fun load(id: String): ReplaySession {
        val f = File("demo/src/main/assets/scenarios/$id.json")
        val parsed = ReplayJson.parse(f.readText())
        assertTrue("${f.path} warnings ${parsed.warnings.take(3)}", parsed.warnings.isEmpty())
        return parsed.session
    }

    private fun scenes(s: ReplaySession, mode: DisplayMode = DisplayMode.DRIVING): List<Scene> =
        s.frames.map { SceneBuilder.build(it.vehicle, it.prediction, it.risk, mode, it.vehicle.timestampMs) }

    private fun Scene.levelOf(id: String) = vehicles.firstOrNull { it.id == id }?.level

    @Test
    fun allScenariosParse_andCoordinatesAreEgoRelative() {
        listOf("normal", "front_brake", "lane_change", "chain", "sensor_dropout").forEach { id ->
            val s = load(id)
            assertTrue(s.frames.size > 50)
            s.frames.forEach { f ->
                val ego = f.vehicle.vehicles.first { it.id == f.vehicle.egoId }
                assertEquals(0f, ego.x, 1e-3f); assertEquals(0f, ego.y, 1e-3f)
                assertEquals(100L, s.frames[1].vehicle.timestampMs - s.frames[0].vehicle.timestampMs)
            }
        }
    }

    @Test
    fun normal_everyVehicleNeutral() {
        val sc = scenes(load("normal"))
        sc.forEach { s ->
            assertEquals("t=${s.timestampMs}", DisplayLevel.NORMAL, s.highestLevel)
            assertTrue(s.trajectories.isEmpty() && s.regions.isEmpty() && s.relations.isEmpty())
        }
    }

    @Test
    fun frontBrake_leadThenFollowerChange() {
        val sc = scenes(load("front_brake"))
        val firstF1Watch = sc.indexOfFirst { it.levelOf("F1") == DisplayLevel.WATCH }
        val firstF1Warn = sc.indexOfFirst { it.levelOf("F1") == DisplayLevel.WARNING }
        val firstB1 = sc.indexOfFirst { (it.levelOf("B1")?.let { l -> l == DisplayLevel.WATCH || l == DisplayLevel.WARNING }) == true }
        assertTrue(firstF1Watch in 0 until firstF1Warn)
        assertTrue("follower after lead", firstB1 > firstF1Watch)
        // 측면 차량은 끝까지 중립
        assertTrue(sc.all { it.levelOf("L1") == DisplayLevel.NORMAL && it.levelOf("R1") == DisplayLevel.NORMAL })
    }

    @Test
    fun laneChange_onlyIntrudingVehicleHighlighted_withLaneChangePath() {
        val sc = scenes(load("lane_change"))
        val highlighted = sc.flatMap { s -> s.vehicles.filter { !it.isEgo && it.level != DisplayLevel.NORMAL }.map { it.id } }.toSet()
        assertEquals(setOf("R1"), highlighted)
        val firstWatch = sc.first { it.levelOf("R1") == DisplayLevel.WATCH }
        assertTrue(firstWatch.trajectories.any { it.vehicleId == "R1" && it.mode == "lane_change_left" && it.emphasized })
        assertTrue(sc.any { it.levelOf("R1") == DisplayLevel.WARNING })
    }

    @Test
    fun chain_truckIsSource_andRelationsFormChain() {
        val sc = scenes(load("chain"))
        val withChain = sc.filter { s -> s.relations.any { it.fromId == "T1" && it.toId == "R1" } }
        assertTrue(withChain.isNotEmpty())
        assertTrue(withChain.all { s -> s.vehicles.first { it.id == "T1" }.isSource })
        // 화물차 감속 → R1 주의 → R1 경고 → 후방 B1 순서
        val tR1 = sc.indexOfFirst { it.levelOf("R1") == DisplayLevel.WATCH }
        val tR1w = sc.indexOfFirst { it.levelOf("R1") == DisplayLevel.WARNING }
        val tB1 = sc.indexOfFirst { it.levelOf("B1") != DisplayLevel.NORMAL && it.levelOf("B1") != null }
        assertTrue("$tR1 < $tR1w < $tB1", tR1 in 0 until tR1w && tR1w < tB1)
        assertTrue(sc.any { s -> s.relations.any { it.fromId == "R1" && it.toId == "B1" } })
    }

    @Test
    fun sensorDropout_unknownAndWarningSuppressed() {
        val s = load("sensor_dropout")
        val sc = scenes(s)
        val missing = sc.indices.filter { i -> s.frames[i].vehicle.vehicles.none { it.id == "R1" } }
        assertTrue(missing.size >= 10)
        missing.forEach { i ->
            val scene = sc[i]
            assertTrue(scene.ghosts.any { it.vehicleId == "R1" })
            assertFalse(scene.vehicles.any { it.id == "R1" })
            assertTrue(scene.highestLevel == DisplayLevel.UNKNOWN || scene.highestLevel == DisplayLevel.NORMAL)
        }
        // Mock 위험 엔진이 누락 직후에도 WATCH 를 보낸 프레임이 있고, 화면은 이를 UNKNOWN 으로 낮췄다
        val reportedWatchWhileMissing = missing.filter { i -> s.frames[i].risk!!.risks.first { it.vehicleId == "R1" }.level == RiskLevel.WATCH }
        assertTrue(reportedWatchWhileMissing.isNotEmpty())
        reportedWatchWhileMissing.forEach { i -> assertEquals(DisplayLevel.UNKNOWN, sc[i].highestLevel) }
    }

    @Test
    fun replayEvents_explainCauses() {
        val ev = load("chain").events()
        val r1 = ev.first { it.vehicleId == "R1" && it.to == RiskLevel.WATCH }
        assertEquals(listOf("T1"), r1.sourceVehicleIds)
        assertTrue(r1.cause.contains("연쇄 감속"))
        val t1 = ev.first { it.vehicleId == "T1" }
        assertTrue(t1.tMs < r1.tMs)
    }

    @Test
    fun replayIndexAndInterpolation() {
        val s = load("lane_change")
        assertEquals(0, s.indexAt(0)); assertEquals(0, s.indexAt(99)); assertEquals(1, s.indexAt(100))
        assertEquals(s.frames.lastIndex, s.indexAt(s.durationMs + 5000))
        val mid = s.frameAt(150)
        val a = s.frames[1].vehicle.vehicles.first { it.id == "R1" }.y
        val b = s.frames[2].vehicle.vehicles.first { it.id == "R1" }.y
        val m = mid.vehicle.vehicles.first { it.id == "R1" }.y
        assertEquals((a + b) / 2, m, 1e-3f)
    }
}

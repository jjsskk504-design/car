package ai.prism.visualization

import ai.prism.visualization.model.ContractJson
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.replay.ReplayJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * PRISM 팀 저장소 안에서 실행될 때, 다른 엔진이 실제로 만든 출력 파일을 **읽기만** 해서
 * 시각화 엔진이 경고 없이 해석하고 화면 장면을 만들 수 있는지 확인한다.
 * 다른 팀원 파일은 수정하지 않는다. 팀 저장소 밖(독립 저장소)에서는 건너뛴다.
 */
class TeamContractCompatibilityTest {

    private val repoRoot = File("..").canonicalFile
    private fun sibling(path: String) = File(repoRoot, path)

    @Test
    fun vehicleIntegrationScenarios_parseWithoutWarnings_andRender() {
        val dir = sibling("vehicle-integration/app/src/main/assets/scenarios")
        assumeTrue("팀 저장소 밖에서는 건너뜀: ${dir.path}", dir.isDirectory)
        val scenarios = dir.listFiles { f -> File(f, "vehicle_frames.jsonl").exists() }.orEmpty().sortedBy { it.name }
        assertTrue("시나리오 없음", scenarios.isNotEmpty())
        for (s in scenarios) {
            val parsed = ReplayJson.fromJsonl(
                id = s.name, title = s.name,
                vehicleJsonl = File(s, "vehicle_frames.jsonl").readText(),
                predictionJsonl = File(s, "prediction_frames.jsonl").takeIf { it.exists() }?.readText(),
                riskJsonl = File(s, "risk_frames.jsonl").takeIf { it.exists() }?.readText(),
            )
            assertTrue("${s.name}: ${parsed.warnings.take(3)}", parsed.warnings.isEmpty())
            val session = parsed.session
            assertTrue("${s.name}: 프레임 없음", session.frames.size > 10)
            // 모든 프레임에서 장면을 만들 수 있고, 위험 차량의 등급이 그대로 표시된다(동기화된 데이터 기준)
            var flagged = 0
            session.frames.forEach { f ->
                val scene = SceneBuilder.build(f.vehicle, f.prediction, f.risk, DisplayMode.DETAIL, f.vehicle.timestampMs)
                f.risk?.risks?.filter { it.level == RiskLevel.WARNING || it.level == RiskLevel.WATCH }?.forEach { r ->
                    val v = scene.vehicles.firstOrNull { it.id == r.vehicleId }
                    if (v != null) { assertEquals("${s.name} t=${f.vehicle.timestampMs} ${r.vehicleId}", r.level.name, v.level.name); flagged++ }
                }
                if (f.prediction != null) assertTrue(scene.trajectories.isNotEmpty())
            }
            println("${s.name}: frames=${session.frames.size}, events=${session.events().size}, flagged=$flagged")
        }
    }

    @Test
    fun dropoutScenario_unknownIsNeverShownAsWarning() {
        val dir = sibling("vehicle-integration/app/src/main/assets/scenarios/dropout")
        assumeTrue(dir.isDirectory)
        val session = ReplayJson.fromJsonl("dropout", "dropout",
            File(dir, "vehicle_frames.jsonl").readText(), null, File(dir, "risk_frames.jsonl").readText()).session
        var unknownFrames = 0
        session.frames.forEach { f ->
            val scene = SceneBuilder.build(f.vehicle, null, f.risk, DisplayMode.DRIVING, f.vehicle.timestampMs)
            f.risk?.risks?.filter { it.level == RiskLevel.UNKNOWN }?.forEach { r ->
                unknownFrames++
                val shown = scene.vehicles.firstOrNull { it.id == r.vehicleId }?.level ?: DisplayLevel.UNKNOWN
                assertEquals(DisplayLevel.UNKNOWN, shown)
            }
        }
        assertTrue(unknownFrames > 0)
    }

    @Test
    fun predictionEngineExample_parses() {
        val f = sibling("prediction-engine/examples/prediction_frame.json")
        assumeTrue(f.exists())
        val r = ContractJson.parsePredictionFrame(JSONObject(f.readText()))
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        assertTrue(r.value.predictions.isNotEmpty())
        assertTrue(r.value.predictions.all { p -> p.trajectories.all { t -> t.points.all { it.dtMs in 1..5000 } } })
    }

    @Test
    fun simulationEngineSamples_parse() {
        val dir = sibling("simulation-engine/samples")
        assumeTrue(dir.isDirectory)
        val files = dir.listFiles { f -> f.name.endsWith(".jsonl") && !f.name.contains("ego_pose") }.orEmpty()
        assumeTrue(files.isNotEmpty())
        for (f in files) {
            val lines = f.readLines().filter { it.isNotBlank() }
            // VehicleFrame 형식 파일만 검사(첫 줄에 vehicles 필드가 있는 파일)
            if (!JSONObject(lines.first()).has("vehicles")) continue
            lines.forEachIndexed { i, line ->
                val r = ContractJson.parseVehicleFrame(line)
                assertTrue("${f.name}:${i + 1} ${r.warnings}", r.warnings.isEmpty())
            }
        }
    }
}

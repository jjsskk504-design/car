package ai.prism.visualization

import ai.prism.visualization.model.ContractJson
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.replay.ReplayJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * README 의 "예제 입력·출력" 파일을 만든다(build/examples).
 * 입력: 연쇄 위험 시나리오 4.5초 시점의 VehicleFrame / PredictionFrame / RiskFrame
 * 출력: 시각화 엔진이 만든 Scene 요약(주행 모드·상세 모드)
 */
class ExampleIoTest {
    @Test
    fun writeExamples() {
        val s = ReplayJson.parse(File("demo/src/main/assets/scenarios/chain.json").readText()).session
        val f = s.frames[s.indexAt(4_500)]
        val out = File("build/examples").apply { mkdirs() }
        File(out, "input_vehicle_frame.json").writeText(ContractJson.toJson(f.vehicle).toString(2))
        File(out, "input_prediction_frame.json").writeText(ContractJson.toJson(f.prediction!!).toString(2))
        File(out, "input_risk_frame.json").writeText(ContractJson.toJson(f.risk!!).toString(2))

        val result = JSONObject()
        for (mode in DisplayMode.entries) {
            val scene = SceneBuilder.build(f.vehicle, f.prediction, f.risk, mode, f.vehicle.timestampMs)
            result.put(mode.name, JSONObject().apply {
                put("summary", scene.summary())
                put("highest_level", scene.highestLevel.name)
                put("vehicles", JSONArray().apply {
                    scene.vehicles.forEach { v ->
                        put(JSONObject().apply {
                            put("id", v.id); put("display_level", v.level.name)
                            put("reported_level", v.reportedLevel?.name ?: JSONObject.NULL)
                            put("is_source", v.isSource); put("cause_text", v.causeText ?: JSONObject.NULL)
                        })
                    }
                })
                put("trajectories", JSONArray().apply {
                    scene.trajectories.forEach { t -> put("${t.vehicleId}:${t.mode}${if (t.emphasized) " (강조)" else ""}") }
                })
                put("regions", JSONArray(scene.regions.map { it.vehicleId }))
                put("relations", JSONArray(scene.relations.map { "${it.fromId} -> ${it.toId}" }))
                put("notices", JSONArray(scene.notices.map { it.text }))
            })
        }
        File(out, "output_scene_summary.json").writeText(result.toString(2))
        assertTrue(result.getJSONObject("DRIVING").getString("summary").contains("R1"))
    }
}

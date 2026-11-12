package com.prism.vehicle.adapters

import com.prism.vehicle.replay.ContractParser
import com.prism.vehicle.replay.EventAnnotation
import com.prism.vehicle.replay.ScenarioManifest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 03 simulation-engine/samples → app/src/main/assets/scenarios/sim_<이름>/
 *   vehicle_frames.jsonl, ego_pose.jsonl 은 그대로 복사, manifest 는 05 형식으로 변환, index.json 갱신.
 * 실행: ./gradlew :adapters:importSim03
 */
fun main(args: Array<String>) {
    val samples = File(args[0])
    val scenariosDir = File(args[1], "scenarios")
    val json = ContractParser.json
    val root = json.parseToJsonElement(File(samples, "scenario_manifest.json").readText()).jsonObject
    fun JsonObject.str(k: String) = get(k)?.jsonPrimitive?.content

    val ids = root.getValue("scenarios").jsonArray.map { it.jsonObject }.map { s ->
        val id = "sim_${s.str("scenario")}"
        val dir = File(scenariosDir, id).apply { mkdirs() }
        File(samples, s.str("file")!!).copyTo(File(dir, "vehicle_frames.jsonl"), overwrite = true)
        File(samples, s.str("ego_pose_file")!!).copyTo(File(dir, "ego_pose.jsonl"), overwrite = true)
        val forced = s["event_annotation"]?.jsonArray.orEmpty().map { it.jsonObject }.map { e ->
            EventAnnotation(e.getValue("t_ms").jsonPrimitive.long, e.str("note") ?: "${e.str("type")} ${e.str("vehicle_id")}")
        }
        // 03 시뮬레이터의 차선 변경 판단(안전 기준 결과). VehicleFrame v1 에 방향지시등 필드가 없어 주석으로 보여 준다.
        val decided = s["lane_change_decisions"]?.jsonArray.orEmpty().map { it.jsonObject }.map { d ->
            val what = when (d.str("result")) {
                "accepted" -> "방향지시등 켬 · 차선 변경 시작"
                "rejected" -> "차선 변경 취소(시작 전)"
                "aborted" -> "차선 변경 중단 · 원래 차선 복귀"
                "completed" -> "차선 변경 완료"
                else -> d.str("result")
            }
            val reason = d.str("reason")?.takeIf { d.str("result") in setOf("rejected", "aborted") }?.let { " — $it" }.orEmpty()
            EventAnnotation(d.getValue("t_ms").jsonPrimitive.long, "${d.str("vehicle_id")} ${d.str("from_lane")}→${d.str("to_lane")} $what$reason")
        }
        val events = if (decided.isEmpty()) forced else (forced + decided).sortedBy { it.tMs }
        val engine = s["provenance"]?.jsonObject?.str("engine") ?: "simulation-engine"
        val manifest = ScenarioManifest(
            scenarioId = id,
            title = "[03] ${s.str("description") ?: s.str("scenario")}",
            durationMs = s.getValue("duration_ms").jsonPrimitive.long,
            stepMs = s.getValue("step_ms").jsonPrimitive.long,
            seed = s["seed"]?.jsonPrimitive?.longOrNull,
            egoId = s.str("ego_id")!!,
            eventAnnotation = events,
            provenance = "$engine / simulation-engine/samples/${s.str("file")}",
        )
        File(dir, "scenario_manifest.json").writeText(json.encodeToString(ScenarioManifest.serializer(), manifest))
        println("$id <- ${s.str("file")}")
        id
    }

    val indexFile = File(scenariosDir, "index.json")
    val list = ListSerializer(String.serializer())
    val kept = json.decodeFromString(list, indexFile.readText()).filterNot { it.startsWith("sim_") }
    indexFile.writeText(json.encodeToString(list, kept + ids))
    println("index.json: ${kept.size} 기존 + ${ids.size} sim")
}

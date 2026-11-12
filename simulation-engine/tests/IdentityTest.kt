package com.prism.simulation

import com.prism.simulation.generators.load
import com.prism.simulation.generators.run
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityTest {
    private fun JsonObject.ids() = getValue("vehicle_ids").jsonArray.map { it.jsonPrimitive.content }
    private fun JsonObject.events() = getValue("event_annotation").jsonArray.map { it.jsonObject }

    @Test fun idsUniqueAndStable() = RESULTS.forEach { (name, r) ->
        val declared = r.manifest.ids().toSet()
        for (f in r.frames) {
            val ids = f.vehicles().map { it.s("id") }
            assertEquals(ids.size, ids.toSet().size, name)
            assertTrue(declared.containsAll(ids) && f.s("ego_id") in ids, name)
        }
    }

    /** 관측 누락은 매니페스트의 의도적 sensor_dropout 구간에서만 허용(그 외는 결함). */
    @Test fun missingOnlyInsideAnnotatedDropout() = RESULTS.forEach { (name, r) ->
        val windows = r.manifest.events().filter { it.s("type") == "sensor_dropout" }
        assertTrue(windows.all { it.getValue("intentional").jsonPrimitive.boolean })
        for (f in r.frames) {
            val t = f.i("timestamp_ms")
            for (vid in r.manifest.ids() - f.vehicles().map { it.s("id") }.toSet()) {
                assertTrue(windows.any { it.s("vehicle_id") == vid && it.i("t_ms") <= t && t < it.i("t_end_ms") }, "$name $vid $t")
            }
        }
    }

    @Test fun dropoutScenarioActuallyDrops() {
        val frames = run(load(ROOT.resolve("scenarios/sensor_dropout.yaml"))).frames
        val seen = frames.flatMap { f -> f.vehicles().map { it.s("id") } }.groupingBy { it }.eachCount()
        assertEquals(frames.size - 20, seen["car_01"]) // 3~5s, 100ms 간격 -> 20프레임 누락
        assertEquals(frames.size - 5, seen["car_03"])
    }

    @Test fun laneIdsInMap() = RESULTS.forEach { (name, r) ->
        val lanes = r.manifest.getValue("lane_id_map").jsonObject.keys
        assertTrue(r.frames.all { f -> f.vehicles().all { it.s("lane_id") in lanes } }, name)
    }

    /** 프롬프트 5항 메타데이터: duration, timestep, seed, 차량 수, 이벤트 시점, 자차 ID, 데이터 출처. */
    @Test fun manifestMetadataComplete() = RESULTS.forEach { (name, r) ->
        val m = r.manifest
        for (k in listOf("duration_ms", "step_ms", "sim_substep_ms", "seed", "vehicle_count", "event_annotation", "ego_id", "provenance")) {
            assertTrue(k in m, "$name $k")
        }
        assertEquals(m.ids().size, m.i("vehicle_count"))
        assertTrue("synthetic" in m.getValue("provenance").jsonObject.s("data_source"))
        assertTrue(m.events().all { it.s("origin") == "forced" && "t_ms" in it })
    }

    @Test fun expectedOutputFiles() {
        val names = Files.list(ROOT.resolve("samples")).use { s -> s.map { it.fileName.toString() }.toList() }
            .filter { it.endsWith(".jsonl") && !it.endsWith(".ego_pose.jsonl") }.toSet()
        assertEquals(
            listOf("normal", "sudden_braking", "rear_approach", "lane_change", "merge", "chain_reaction", "sensor_dropout",
                "lane_change_safe", "lane_change_cancel", "stop_and_go").map { "$it.jsonl" }.toSet(),
            names,
        )
    }
}

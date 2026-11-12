package com.prism.simulation.generators

import com.prism.simulation.adapters.SIM_EVENTS
import com.prism.simulation.adapters.SUBSTEPS
import com.prism.simulation.adapters.simulate
import com.prism.simulation.adapters.toEgo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random

const val ENGINE_VERSION = "0.2.0"
const val HIGHWAY_ENV_VERSION = "1.12.1"

data class RunResult(val frames: List<JsonObject>, val poses: List<JsonObject>, val manifest: JsonObject)

fun laneId(i: Int) = "L${i + 1}" // L1 = 가장 왼쪽 차선 (HighwayEnv lane 0)

/** 파이썬 round(x, n) 과 같은 반올림(이진 값 그대로 half-even). */
private fun round(x: Double, n: Int) = BigDecimal(x).setScale(n, RoundingMode.HALF_EVEN).toDouble()

private fun EventSpec.activeAt(tMs: Int) = tS * 1000 <= tMs && tMs < (tEndS ?: Double.POSITIVE_INFINITY) * 1000

/** 시나리오를 실행해 VehicleFrame v1 목록, 자차 월드 자세, 매니페스트 항목을 만든다. */
fun run(sc: Scenario): RunResult {
    val (worldFrames, collisions, decisions) = simulate(sc)
    val outEvents = sc.events.filter { it.type in OUTPUT_EVENTS }
    val rng = Random(sc.seed)
    val frames = mutableListOf<JsonObject>()
    val poses = mutableListOf<JsonObject>()
    for ((tMs, states) in worldFrames) {
        val ego = states.first { it.id == sc.egoId }
        frames += buildJsonObject {
            put("schema_version", "1.0")
            put("timestamp_ms", tMs)
            put("coordinate_system", "ego_local_m")
            put("ego_id", sc.egoId)
            putJsonArray("vehicles") {
                for (s in states) {
                    val active = outEvents.filter { it.vehicle == s.id && it.activeAt(tMs) }
                    if (active.any { it.type == "sensor_dropout" }) continue
                    val l = toEgo(ego.x, ego.y, ego.h, s.x, s.y, s.vx, s.vy, s.h)
                    var (x, y, h) = Triple(l.x, l.y, l.heading)
                    for (e in active) { // sensor_noise: 위치·heading 가우시안 노이즈 (seed 고정)
                        val std = e.value!!.toDouble()
                        x += rng.nextGaussian() * std
                        y += rng.nextGaussian() * std
                        h += rng.nextGaussian() * (e.headingStdRad ?: 0.02)
                    }
                    addJsonObject {
                        put("id", s.id)
                        put("x", round(x, 3)); put("y", round(y, 3))
                        put("vx", round(l.vx, 3)); put("vy", round(l.vy, 3))
                        put("heading_rad", round(h, 6))
                        put("length_m", s.lengthM); put("width_m", s.widthM)
                        put("lane_id", laneId(s.lane))
                    }
                }
            }
        }
        // 계약 외 보조 파일: 01번이 이전 프레임을 현재 자차 좌표로 옮길 때 쓰는 월드 자차 자세
        val w = toEgo(0.0, 0.0, 0.0, ego.x, ego.y, 0.0, 0.0, ego.h)
        poses += buildJsonObject {
            put("timestamp_ms", tMs)
            put("x", round(w.x, 3)); put("y", round(w.y, 3)); put("heading_rad", round(w.heading, 6))
        }
    }
    val manifest = buildJsonObject {
        put("scenario", sc.name)
        put("file", "${sc.name}.jsonl")
        put("ego_pose_file", "${sc.name}.ego_pose.jsonl")
        put("description", sc.description)
        put("duration_ms", worldFrames.last().tMs)
        put("step_ms", sc.stepMs)
        put("sim_substep_ms", sc.stepMs.toDouble() / SUBSTEPS)
        put("frame_count", frames.size)
        put("seed", sc.seed)
        put("ego_id", sc.egoId)
        put("vehicle_count", sc.vehicleIds.size)
        putJsonArray("vehicle_ids") { sc.vehicleIds.forEach { add(it) } }
        putJsonObject("lane_id_map") {
            for (i in 0 until sc.lanes) put(laneId(i), "highway-env lane ('0','1',$i), 왼쪽에서 ${i + 1}번째")
        }
        putJsonArray("event_annotation") {
            for (e in sc.events) addJsonObject {
                put("t_ms", (e.tS * 1000).toInt())
                e.tEndS?.let { put("t_end_ms", (it * 1000).toInt()) }
                put("type", e.type)
                put("vehicle_id", e.vehicle)
                e.value?.let { put("value", JsonPrimitive(it)) }
                put("intentional", true)
                put("origin", "forced")
                e.note?.let { put("note", it) }
            }
        }
        // 시뮬레이터가 실제로 판정한 충돌(정답 라벨). 위험도 판정이 아님.
        putJsonArray("observed_collisions") {
            for (c in collisions) addJsonObject { put("t_ms", c.tMs); put("vehicle_id", c.vehicleId) }
        }
        // request_lane_change 에 대한 시뮬레이터의 판단(진입·취소·중단·완료). 강제 이벤트가 아니라 안전 기준에 따른 결과.
        // VehicleFrame v1 에 방향지시등 필드가 없어 accepted~completed 구간을 방향지시등 점등 구간으로 여기에 기록한다.
        if (decisions.isNotEmpty()) putJsonArray("lane_change_decisions") {
            for (d in decisions) addJsonObject {
                put("t_ms", d.tMs)
                put("vehicle_id", d.vehicleId)
                put("from_lane", laneId(d.fromLane))
                put("to_lane", laneId(d.toLane))
                put("result", d.result)
                put("reason", d.reason)
            }
        }
        putJsonObject("provenance") {
            put("engine", "PRISM simulation-engine $ENGINE_VERSION (Kotlin/JVM)")
            put("simulator", "highway-env $HIGHWAY_ENV_VERSION (MIT) 차량 모델을 Kotlin으로 이식")
            put("data_source", "synthetic simulation (실제 주행·실차 ADAS 데이터 아님)")
            // 강제(forced) = event_annotation 에 적힌 것만. 그 외 모든 움직임은 시뮬레이터 자연 행동.
            put("forced_behavior", "event_annotation 의 이벤트(목표속도 변경, 차선 변경 명령·요청, 센서 누락·노이즈 주입)")
            put("natural_behavior", if (decisions.isEmpty()) "highway-env IDM 종방향 반응(감속·추종)과 차선 추종 조향. 자동 차선변경(MOBIL)은 꺼둠"
                else "highway-env IDM 종방향 반응(감속·추종)과 차선 추종 조향. 차선 변경 요청은 MOBIL 안전 기준·최소 차간 시간으로 진입/취소/중단을 판단(lane_change_decisions)")
        }
    }
    return RunResult(frames, poses, manifest)
}

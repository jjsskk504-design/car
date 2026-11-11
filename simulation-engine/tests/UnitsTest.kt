package com.prism.simulation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitsTest {
    @Test fun contractExampleFromSpecIsValid() { // 지시서 02장 VehicleFrame v1 예시 그대로
        assertVehicleFrameV1(Json.parseToJsonElement(
            """{"schema_version":"1.0","timestamp_ms":1000,"coordinate_system":"ego_local_m","ego_id":"ego","vehicles":[
            {"id":"ego","x":0,"y":0,"vx":0,"vy":25,"heading_rad":1.570796,"length_m":4.6,"width_m":1.8,"lane_id":"L2"},
            {"id":"car_01","x":3.6,"y":20,"vx":-0.3,"vy":20,"heading_rad":1.59,"length_m":4.4,"width_m":1.8,"lane_id":"L3"}]}""",
        ).jsonObject)
    }

    @Test fun framesMatchSchemaAndAreFinite() = RESULTS.forEach { (name, r) ->
        for (f in r.frames) {
            assertVehicleFrameV1(f)
            for (v in f.vehicles()) for (k in listOf("x", "y", "vx", "vy", "heading_rad")) assertTrue(v.d(k).isFinite(), "$name $k")
        }
    }

    @Test fun timeAxis() = RESULTS.forEach { (name, r) ->
        val ts = r.frames.map { it.i("timestamp_ms") }
        val step = r.manifest.i("step_ms")
        assertEquals(0, ts.first(), name)
        assertTrue(ts.zipWithNext().all { (a, b) -> b - a == step }, name)
        assertTrue(ts.last() >= 10_000, "$name: 시나리오 최소 10초")
    }

    @Test fun egoIsOriginFacingForward() = RESULTS.forEach { (name, r) ->
        for (f in r.frames) {
            val ego = f.vehicles().first { it.s("id") == f.s("ego_id") }
            assertTrue(abs(ego.d("x")) < 1e-6 && abs(ego.d("y")) < 1e-6 && abs(ego.d("vx")) < 1e-6, name)
            assertEquals(PI / 2, ego.d("heading_rad"), 1e-6, name)
        }
    }

    /** 자차 vy(m/s) == 월드 자차 위치 변화(m) / 시간(s): 단위(m, m/s, ms) 일관성 검증 */
    @Test fun speedUnitsAreMetersPerSecond() = RESULTS.forEach { (name, r) ->
        val dt = r.manifest.i("step_ms") / 1000.0
        fun egoVy(i: Int) = r.frames[i].vehicles().first { it.s("id") == "ego" }.d("vy")
        for (i in 0 until r.frames.size - 1) {
            val moved = hypot(r.poses[i + 1].d("x") - r.poses[i].d("x"), r.poses[i + 1].d("y") - r.poses[i].d("y"))
            assertEquals((egoVy(i) + egoVy(i + 1)) / 2, moved / dt, 0.5, "$name frame $i")
        }
        for (f in r.frames) assertTrue(f.vehicles().all { hypot(it.d("vx"), it.d("vy")) < 60 }, name)
    }

    /** 01번용 보조 파일 검증: 로컬 위치+자차 자세로 복원한 월드 위치의 변화가 속도*dt 와 일치. */
    @Test fun egoPoseSidecarReconstructsMotion() = RESULTS.forEach { (name, r) ->
        val dt = r.manifest.i("step_ms") / 1000.0
        val noisy = r.manifest.getValue("event_annotation").jsonArray.map { it.jsonObject }
            .filter { it.s("type") == "sensor_noise" }.map { it.s("vehicle_id") }.toSet()
        fun world(i: Int): Map<String, DoubleArray> {
            val p = r.poses[i]
            val a = p.d("heading_rad") - PI / 2
            val c = cos(a)
            val s = sin(a)
            return r.frames[i].vehicles().associate { v ->
                v.s("id") to doubleArrayOf(
                    p.d("x") + c * v.d("x") - s * v.d("y"), p.d("y") + s * v.d("x") + c * v.d("y"),
                    c * v.d("vx") - s * v.d("vy"), s * v.d("vx") + c * v.d("vy"),
                )
            }
        }
        for (i in 0 until r.frames.size - 1) {
            val a = world(i)
            val b = world(i + 1)
            for (vid in (a.keys intersect b.keys) - noisy) for (ax in 0..1) {
                // 조향이 계단식으로 바뀌면 구간 내 속도가 점프하므로, 이동량은 양 끝 속도*dt 사이에 있어야 한다
                val moved = b.getValue(vid)[ax] - a.getValue(vid)[ax]
                val lo = minOf(a.getValue(vid)[2 + ax], b.getValue(vid)[2 + ax]) * dt
                val hi = maxOf(a.getValue(vid)[2 + ax], b.getValue(vid)[2 + ax]) * dt
                assertTrue(moved in lo - 0.05..hi + 0.05, "$name $vid frame $i axis $ax: $moved !in [$lo, $hi]")
            }
        }
    }
}

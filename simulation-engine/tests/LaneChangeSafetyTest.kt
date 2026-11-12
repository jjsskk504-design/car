package com.prism.simulation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 안전 확인 차선 변경(request_lane_change)·정지 차량 규칙 검증.
 * 차선 중심 x(자차 기준): 자차 차선 0, 왼쪽 -4, 오른쪽 +4 (LANE_WIDTH 4 m).
 */
class LaneChangeSafetyTest {
    private fun decisions(name: String) = RESULTS.getValue(name).manifest["lane_change_decisions"]?.jsonArray
        ?.map { it.jsonObject }.orEmpty()
    private fun JsonObject.str(k: String) = s(k)
    private fun track(name: String, id: String) = RESULTS.getValue(name).frames.mapNotNull { f ->
        f.vehicles().firstOrNull { it.s("id") == id }?.let { f.i("timestamp_ms") to it }
    }

    @Test fun noCollisionsAndNoReversingAnywhere() = RESULTS.forEach { (name, r) ->
        assertTrue(r.manifest.getValue("observed_collisions").jsonArray.isEmpty(), "$name 충돌")
        for (f in r.frames) for (v in f.vehicles()) assertTrue(v.d("vy") >= -1e-6, "$name ${v.s("id")} 후진 vy=${v.d("vy")}")
    }

    @Test fun safeLaneChange_isSignalledSmoothAndCentred() {
        val d = decisions("lane_change_safe").filter { it.str("vehicle_id") == "car_31" }
        assertEquals(listOf("accepted", "completed"), d.map { it.str("result") })
        val (t0, t1) = d.map { it.i("t_ms") }
        assertTrue(t1 - t0 >= 2500, "차선 변경이 너무 급함: ${t1 - t0} ms")
        val tr = track("lane_change_safe", "car_31")
        // 오른쪽(+4) → 자차 차선(0) 으로 한 방향으로만 이동, 넘치지 않음, heading 은 완만
        assertTrue(tr.zipWithNext().all { (a, b) -> b.second.d("x") <= a.second.d("x") + 0.05 }, "횡이동 방향이 바뀜")
        assertTrue(tr.all { it.second.d("x") > -0.3 }, "목표 차선 중앙을 지나침")
        assertTrue(tr.all { abs(it.second.d("heading_rad") - PI / 2) < 0.15 }, "heading 변화가 과도함")
        assertTrue(abs(tr.last().second.d("x")) < 0.3, "목표 차선 중앙 정렬 안 됨")
    }

    @Test fun laneChangeCancelled_beforeStart_whenNoGap() {
        val d = decisions("lane_change_cancel").first { it.str("vehicle_id") == "car_41" }
        assertEquals("rejected", d.str("result"))
        assertTrue(track("lane_change_cancel", "car_41").all { abs(it.second.d("x") - 4.0) < 0.3 }, "취소됐는데 차선을 벗어남")
    }

    @Test fun laneChangeAborted_midway_returnsToOriginalLane() {
        val d = decisions("lane_change_cancel").filter { it.str("vehicle_id") == "car_42" }
        assertEquals(listOf("accepted", "aborted"), d.map { it.str("result") })
        val tr = track("lane_change_cancel", "car_42")
        assertTrue(tr.all { it.second.d("x") > -2.0 }, "중단 전에 차선 경계를 넘음") // 왼쪽 차선(-4)으로 넘어가지 않음
        assertTrue(tr.minOf { it.second.d("x") } < -0.3, "진입을 시작하지 않음")
        assertTrue(abs(tr.last().second.d("x")) < 0.3, "원래 차선 중앙으로 복귀하지 않음")
    }

    @Test fun stoppedEgo_staysPut_rearVehicleStopsBehind_thenRestart() {
        val r = RESULTS.getValue("stop_and_go")
        val poses = r.poses.map { it.i("timestamp_ms") to it.d("y") } // 자차 월드 전방 위치
        val before = poses.filter { it.first < 9500 }.map { it.second }
        assertTrue(before.max() - before.min() < 1e-6, "정지 중 자차 위치 변함")
        assertTrue(poses.last().second - before.last() > 1.0, "재출발하지 않음")
        val rear = track("stop_and_go", "car_23")
        assertTrue(rear.all { it.second.d("y") < 0 }, "후방 차량이 자차를 지나침")
        val minGap = rear.filter { it.first < 9500 }.minOf { -it.second.d("y") - 5.0 } // 범퍼 간격(차 길이 5 m)
        assertTrue(minGap >= 2.0, "후방 차량 정지 간격 부족: $minGap m")
        val d = decisions("stop_and_go").map { it.str("result") }
        assertEquals(listOf("rejected", "accepted", "completed"), d)
    }
}

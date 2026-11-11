package com.prism.simulation.adapters

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** VehicleFrame v1 ego_local_m 값: x=오른쪽+, y=전방+, heading은 +x에서 반시계(전방=π/2). */
data class EgoLocal(val x: Double, val y: Double, val vx: Double, val vy: Double, val heading: Double)

/** 각도를 (-π, π] 로 정규화. */
fun wrapAngle(a: Double): Double {
    var r = (a + PI) % (2 * PI)
    if (r <= 0) r += 2 * PI
    return r - PI
}

/**
 * 월드 좌표(HighwayEnv 규약: x=진행, y=진행 방향의 오른쪽, heading 양수=오른쪽 회전)를 자차 기준으로 바꾼다.
 * 속도는 절대속도를 자차 축으로 회전한 값이다(상대속도 아님).
 */
fun toEgo(
    egoX: Double, egoY: Double, egoH: Double,
    x: Double, y: Double, vx: Double, vy: Double, h: Double,
): EgoLocal {
    val c = cos(egoH)
    val s = sin(egoH)
    val dx = x - egoX
    val dy = y - egoY
    val fwd = dx * c + dy * s
    val right = -dx * s + dy * c
    val vFwd = vx * c + vy * s
    val vRight = -vx * s + vy * c
    return EgoLocal(right, fwd, vRight, vFwd, wrapAngle(PI / 2 - (h - egoH)))
}

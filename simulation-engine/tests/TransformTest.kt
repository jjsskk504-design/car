package com.prism.simulation

import com.prism.simulation.adapters.EgoLocal
import com.prism.simulation.adapters.toEgo
import com.prism.simulation.adapters.wrapAngle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformTest {
    private fun assertClose(expected: EgoLocal, got: EgoLocal) {
        val e = listOf(expected.x, expected.y, expected.vx, expected.vy, expected.heading)
        val g = listOf(got.x, got.y, got.vx, got.vy, got.heading)
        e.zip(g).forEach { (a, b) -> assertEquals(a, b, 1e-9, "expected $expected got $got") }
    }

    // 고정 예시 3종 (자차 이동·회전 포함). 입력은 HighwayEnv 월드(x=진행, y=오른쪽)
    @Test fun rightLaneVehicleAhead() = // 자차 원점·정면, 오른쪽 차선 10m 앞 -> x=3.5 오른쪽, y=10 전방
        assertClose(EgoLocal(3.5, 10.0, 0.0, 20.0, PI / 2), toEgo(0.0, 0.0, 0.0, 10.0, 3.5, 20.0, 0.0, 0.0))

    @Test fun egoMovedLeftLaneVehicle() = // 자차가 (100,4)로 이동, 왼쪽 차선(y=0) 20m 앞 -> x=-4
        assertClose(EgoLocal(-4.0, 20.0, 0.0, 25.0, PI / 2), toEgo(100.0, 4.0, 0.0, 120.0, 0.0, 25.0, 0.0, 0.0))

    @Test fun egoRotatedRight() = // 자차가 오른쪽으로 0.1rad 회전 -> heading 0 차량은 상대적으로 왼쪽으로 0.1
        assertClose(EgoLocal(0.0, 10.0, 0.0, 0.0, PI / 2 + 0.1), toEgo(0.0, 0.0, 0.1, 10 * cos(0.1), 10 * sin(0.1), 0.0, 0.0, 0.0))

    @Test fun egoItselfIsOriginFacingForward() =
        assertClose(EgoLocal(0.0, 0.0, 0.0, 25.0, PI / 2), toEgo(50.0, 4.0, 0.2, 50.0, 4.0, 25 * cos(0.2), 25 * sin(0.2), 0.2))

    @Test fun wrapAngleRange() {
        assertEquals(PI, wrapAngle(3 * PI), 1e-9)
        assertEquals(PI / 2, wrapAngle(-3 * PI / 2), 1e-9)
        assertTrue(wrapAngle(-PI) > -PI && wrapAngle(-PI) <= PI)
    }
}

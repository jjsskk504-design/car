package ai.prism.visualization.rendering

import ai.prism.visualization.model.VehicleFrame
import kotlin.math.PI

/**
 * 리플레이에서 10 Hz 프레임 사이를 트래킹 ID 기준으로 선형 보간한다.
 * 두 프레임에 모두 있는 ID 만 보간하고, 한쪽에만 있으면 가까운 프레임의 상태를 그대로 쓴다
 * (없던 차량을 만들어내거나 사라진 차량을 끌고 다니지 않음).
 * 주행 중 실시간 화면에서는 지연을 만들지 않도록 사용하지 않는다.
 */
object FrameInterpolator {
    fun lerp(a: VehicleFrame, b: VehicleFrame, alpha: Float): VehicleFrame {
        val t = alpha.coerceIn(0f, 1f)
        val nearest = if (t < 0.5f) a else b
        val bById = b.vehicles.associateBy { it.id }
        val vehicles = nearest.vehicles.map { v ->
            val va = a.vehicles.firstOrNull { it.id == v.id }
            val vb = bById[v.id]
            if (va == null || vb == null) v else va.copy(
                x = mix(va.x, vb.x, t), y = mix(va.y, vb.y, t),
                vx = mix(va.vx, vb.vx, t), vy = mix(va.vy, vb.vy, t),
                headingRad = mixAngle(va.headingRad, vb.headingRad, t),
                laneId = if (t < 0.5f) va.laneId else vb.laneId,
            )
        }
        return VehicleFrame(
            timestampMs = (a.timestampMs + (b.timestampMs - a.timestampMs) * t).toLong(),
            egoId = nearest.egoId,
            vehicles = vehicles,
        )
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** 최단 각도 방향으로 보간 */
    fun mixAngle(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % (2 * PI).toFloat()
        if (d > PI) d -= (2 * PI).toFloat()
        if (d < -PI) d += (2 * PI).toFloat()
        return a + d * t
    }
}

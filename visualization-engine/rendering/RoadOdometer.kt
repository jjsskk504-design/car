package ai.prism.visualization.rendering

/**
 * 자차 누적 주행거리(도로 표시 스크롤용).
 *
 * 레이더는 자차 기준 좌표(ego_local_m)라 자차가 화면에 고정된다. 이때 도로 차선까지 고정하면
 * 자차가 25 m/s 로 달리고 있어도 "정지"한 것처럼 보이고, 자차보다 느린 차량(상대속도 < 0)은
 * 화면 아래로 밀려 "후진"하는 것처럼 보인다. 차선 표시를 자차 이동량만큼 뒤로 흘려 보내면
 * 도로에 대한 각 차량의 실제(절대) 진행 방향이 보인다.
 *
 * 입력은 VehicleFrame 의 자차 전방 속도(vy, 자차 축 기준 절대속도)뿐이며 값을 만들어내지 않는다.
 * 속도가 없으면(null·NaN) 거리를 더하지 않아 도로가 멈춘 채로 남는다.
 * 리플레이를 뒤로 이동하면(dt < 0) 거리도 줄어 도로가 거꾸로 흐른다(되감기와 일치).
 */
class RoadOdometer {
    /** 누적 주행거리(m). 첫 프레임 = 0. */
    var distanceM: Double = 0.0
        private set
    private var lastT: Long? = null
    private var lastV: Float? = null

    /** 새 프레임(시각 ms, 자차 전방 속도 m/s)을 반영하고 누적 거리를 돌려준다. */
    fun advance(timestampMs: Long?, egoSpeedMps: Float?): Double {
        val v = egoSpeedMps?.takeIf { it.isFinite() }
        val t0 = lastT
        if (timestampMs != null && t0 != null && v != null) {
            val dtS = (timestampMs - t0) / 1000.0
            val v0 = lastV ?: v
            distanceM += (v0 + v) / 2.0 * dtS // 사다리꼴 적분(프레임 간 등가속 가정)
        }
        if (timestampMs != null) { lastT = timestampMs; lastV = v }
        return distanceM
    }

    fun reset() { distanceM = 0.0; lastT = null; lastV = null }
}

package ai.prism.visualization.model

/**
 * 원인 코드(reason_codes) → 운전자용 짧은 한국어 문구.
 * 팀 저장소의 실제 RiskFrame 샘플(vehicle-integration assets)에서 쓰는 코드 7개를 우선 등록했다.
 * 목록에 없는 코드는 코드 문자열을 그대로 보여준다.
 */
object CauseCatalog {
    private val texts = mapOf(
        "HARD_BRAKING" to "급감속",
        "FRONT_CLOSING" to "앞 차량과 간격 좁아짐",
        "REAR_CLOSING" to "후방 차량 접근",
        "CHAIN_DECEL" to "연쇄 감속",
        "LANE_CHANGE_INTRUSION" to "내 차로 진입",
        "PATH_OVERLAP" to "예상 경로 겹침",
        "SENSOR_DROPOUT" to "관측 끊김",
    )

    fun text(code: String): String = texts[code] ?: code

    fun describe(risk: VehicleRisk): String {
        risk.message?.let { return it }
        val base = risk.reasonCodes.joinToString(" · ") { text(it) }.ifEmpty { "원인 정보 없음" }
        return risk.ttcS?.let { "$base (TTC %.1f초)".format(it) } ?: base
    }

    private val modes = mapOf(
        "lane_keep" to "차선 유지",
        "braking" to "감속",
        "lane_change_left" to "좌측 차선 변경",
        "lane_change_right" to "우측 차선 변경",
    )

    fun maneuver(mode: String): String = modes[mode.lowercase()] ?: mode
}

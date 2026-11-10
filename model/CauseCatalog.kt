package ai.prism.visualization.model

/**
 * 원인 코드 → 운전자용 짧은 한국어 문구.
 * 원인 코드 목록은 위험 판정 엔진 담당과 합의 전이므로 여기 없는 코드는 코드 문자열을 그대로 보여준다.
 */
object CauseCatalog {
    private val texts = mapOf(
        "LEAD_HARD_BRAKE" to "앞 차량 급감속",
        "TTC_LOW" to "충돌 여유 시간 짧음",
        "HEADWAY_SHORT" to "차간 거리 부족",
        "REAR_CLOSING" to "후방 차량 접근",
        "PREDICTED_CUT_IN" to "내 차로 진입 예상",
        "LANE_INTRUSION" to "차선 침범",
        "CHAIN_DECELERATION" to "연쇄 감속",
        "SENSOR_DROPOUT" to "관측 끊김",
        "LOW_CONFIDENCE" to "예측 신뢰도 낮음",
    )

    fun text(code: String): String = texts[code] ?: code

    fun describe(risk: VehicleRisk): String =
        risk.message ?: risk.causeCodes.joinToString(" · ") { text(it) }.ifEmpty { "원인 정보 없음" }

    private val maneuvers = mapOf(
        "KEEP_LANE" to "차선 유지",
        "LANE_CHANGE_LEFT" to "좌측 차선 변경",
        "LANE_CHANGE_RIGHT" to "우측 차선 변경",
        "BRAKE" to "감속",
    )

    fun maneuver(code: String): String = maneuvers[code] ?: code
}

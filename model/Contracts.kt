package ai.prism.visualization.model

/*
 * PRISM 공통 데이터 계약의 시각화 엔진 측 표현.
 *
 * - VehicleFrame v1 은 팀 공지의 필드 목록을 그대로 따른다.
 * - PredictionFrame / RiskFrame 은 저장소 JSON Schema 를 아직 받지 못해
 *   공지된 의미(차량 ID, 예측 시점, 후보 궤적 / 위험 등급, 원인 코드, 위험 영역·궤적)를 기준으로
 *   잠정 필드명을 사용한다. 실제 스키마와 다르면 ContractJson.kt 만 수정하면 되도록 분리했다.
 *
 * 좌표: 자차 기준, x = 오른쪽(m), y = 전방(m). 속도 m/s, 각도 rad, 시간 ms.
 * heading_rad: 같은 좌표계에서 +x 축 기준 반시계 방향(수학 규약). 전방 주행 = π/2.  (README 이슈 #2 참고)
 */

data class Point2(val x: Float, val y: Float)

data class VehicleState(
    val id: String,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val headingRad: Float,
    val lengthM: Float,
    val widthM: Float,
    val laneId: Int?,
)

data class VehicleFrame(
    val timestampMs: Long,
    val egoId: String,
    val vehicles: List<VehicleState>,
)

/** 후보 궤적의 한 점. tOffsetMs = 예측 기준 시점으로부터의 경과 시간. */
data class TrajectoryPoint(
    val tOffsetMs: Long,
    val x: Float,
    val y: Float,
    val headingRad: Float?,
)

/** 예측 엔진이 내보내는 행동 후보. maneuver 는 표시용 문자열(예: KEEP_LANE, LANE_CHANGE_LEFT, BRAKE). */
data class TrajectoryCandidate(
    val candidateId: String,
    val maneuver: String,
    val points: List<TrajectoryPoint>,
)

data class VehiclePrediction(
    val vehicleId: String,
    val candidates: List<TrajectoryCandidate>,
)

data class PredictionFrame(
    val timestampMs: Long,
    val predictions: List<VehiclePrediction>,
)

enum class RiskLevel {
    NORMAL, WATCH, WARNING, UNKNOWN;

    companion object {
        /** 알 수 없는 문자열은 UNKNOWN 으로 처리한다(과장 경고 방지). */
        fun parse(s: String?): RiskLevel =
            entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

data class VehicleRisk(
    val vehicleId: String,
    val level: RiskLevel,
    val causeCodes: List<String>,
    /** 이 위험을 유발한 것으로 추정되는 차량들(연쇄 위험 화살표에 사용). */
    val sourceVehicleIds: List<String> = emptyList(),
    /** 위험 점유 영역 다각형(자차 기준 좌표). 비어 있으면 그리지 않는다. */
    val riskRegion: List<Point2> = emptyList(),
    /** 위험 판단에 사용된 PredictionFrame 후보 ID. */
    val trajectoryCandidateId: String? = null,
    /** 사람이 읽을 수 있는 설명(선택). 없으면 원인 코드로 문구를 만든다. */
    val message: String? = null,
)

data class RiskFrame(
    val timestampMs: Long,
    val risks: List<VehicleRisk>,
)

/** 도로 형상. 현재 VehicleFrame v1 에 차선 형상이 없어 호출 측에서 제공한다(직선 도로 가정). */
data class RoadConfig(
    val laneCount: Int = 3,
    val laneWidthM: Float = 3.5f,
    /** 자차가 있는 차선(왼쪽부터 0). */
    val egoLaneIndex: Int = 1,
) {
    init {
        require(laneCount >= 1) { "laneCount must be >= 1" }
        require(egoLaneIndex in 0 until laneCount) { "egoLaneIndex out of range" }
    }

    /** 도로 왼쪽 끝의 x(m, 자차 기준). */
    val leftEdgeX: Float get() = -(egoLaneIndex + 0.5f) * laneWidthM
    val rightEdgeX: Float get() = leftEdgeX + laneCount * laneWidthM
    val widthM: Float get() = laneCount * laneWidthM
}

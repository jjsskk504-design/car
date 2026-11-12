package ai.prism.visualization.model

/*
 * PRISM 공통 데이터 계약의 시각화 엔진 측 표현.
 *
 * 기준(팀 저장소 qwon4466/PRISM, 2026-11-11 확인):
 *  - VehicleFrame v1 : simulation-engine/schema/vehicle_frame.v1.schema.json
 *  - PredictionFrame : prediction-engine/src/main/resources/prediction_frame.schema.json
 *  - RiskFrame       : vehicle-integration/replay/.../Contracts.kt (JSON Schema 파일 없음)
 * 공통 contracts/ 가 확정되면 그것이 최종 기준이며, 이 파일과 ContractJson.kt 만 맞추면 된다.
 *
 * 좌표: 자차 기준(coordinate_system = "ego_local_m"), x = 오른쪽(m), y = 전방(m). 속도 m/s, 각도 rad, 시간 ms.
 * heading_rad: +x 축 기준 반시계, 전방 주행 = π/2 (시뮬레이션·예측 엔진 샘플과 일치 확인).
 * 예측 궤적·위험 영역 좌표: 기준 시점의 자차 좌표계에 고정(prediction-engine DATA_CONTRACT.md).
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
    /** 계약상 문자열(예: "L2"). 표시용으로만 사용. */
    val laneId: String?,
)

data class VehicleFrame(
    val timestampMs: Long,
    val egoId: String,
    val vehicles: List<VehicleState>,
    val schemaVersion: String = SCHEMA_VERSION,
    val coordinateSystem: String = COORDINATE_SYSTEM,
)

const val SCHEMA_VERSION = "1.0"
const val COORDINATE_SYSTEM = "ego_local_m"

/** 예측 궤적의 한 점. dtMs = 기준 시점으로부터의 상대 시간(1~5000 ms). */
data class TrajectoryPoint(
    val dtMs: Long,
    val x: Float,
    val y: Float,
    val headingRad: Float?,
)

/** 예측 엔진의 미래 궤적 하나. mode 예: lane_keep, braking, lane_change_left. confidence 는 보정 전이면 null. */
data class Trajectory(
    val mode: String,
    val confidence: Float?,
    val points: List<TrajectoryPoint>,
)

data class VehiclePrediction(
    val vehicleId: String,
    val trajectories: List<Trajectory>,
)

data class PredictionFrame(
    val timestampMs: Long,
    val predictions: List<VehiclePrediction>,
    val schemaVersion: String = SCHEMA_VERSION,
)

enum class RiskLevel {
    NORMAL, WATCH, WARNING, UNKNOWN;

    companion object {
        /** 알 수 없는 문자열은 UNKNOWN 으로 처리한다(과장 경고 방지). */
        fun parse(s: String?): RiskLevel =
            entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** 미래 시점 dtMs 의 위험 점유 영역(다각형, 기준 시점 자차 좌표). */
data class RiskZone(val dtMs: Long, val polygon: List<Point2>)

/** RiskFrame.risk_objects[] 한 항목. */
data class VehicleRisk(
    val vehicleId: String,
    val level: RiskLevel,
    val reasonCodes: List<String>,
    val ttcS: Float? = null,
    val riskZones: List<RiskZone> = emptyList(),
    // ---- 이하 계약에 없는 선택 필드(제안). 없으면 해당 표시만 생략한다.
    /** 이 위험을 유발한 차량들(연쇄 위험 화살표). JSON: source_vehicle_ids */
    val sourceVehicleIds: List<String> = emptyList(),
    /** 위험 판단에 쓰인 예측 궤적의 mode. JSON: trajectory_mode */
    val trajectoryMode: String? = null,
    /** 사람이 읽는 설명. JSON: message */
    val message: String? = null,
)

data class RiskFrame(
    val timestampMs: Long,
    val risks: List<VehicleRisk>,
    val schemaVersion: String = SCHEMA_VERSION,
)

/** 도로 형상. VehicleFrame v1 에 차선 형상이 없어 호출 측에서 제공한다(직선 도로 가정). */
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

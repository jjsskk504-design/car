package com.prism.vehicle.replay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * PRISM 공통 데이터 계약 v1 DTO.
 * 기준: PRISM 5인 독립개발 지시서 02장 예시. 공통 contracts/ JSON Schema 가 배포되면 그쪽이 최종 기준이며,
 * 불일치는 이 파일을 임의로 바꾸지 말고 이슈로 보고한다.
 * 좌표: 자차 기준 x=오른쪽+, y=전방+ / 단위 m, m/s, rad, ms.
 */

const val SUPPORTED_SCHEMA_MAJOR = "1"

@Serializable
data class VehicleFrame(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("coordinate_system") val coordinateSystem: String? = null,
    @SerialName("ego_id") val egoId: String,
    val vehicles: List<VehicleState>,
)

@Serializable
data class VehicleState(
    val id: String,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    @SerialName("heading_rad") val headingRad: Double,
    @SerialName("length_m") val lengthM: Double,
    @SerialName("width_m") val widthM: Double,
    @SerialName("lane_id") val laneId: String? = null,
)

@Serializable
data class PredictionFrame(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    val predictions: List<VehiclePrediction>,
)

@Serializable
data class VehiclePrediction(
    @SerialName("vehicle_id") val vehicleId: String,
    val trajectories: List<Trajectory>,
)

@Serializable
data class Trajectory(
    val mode: String,
    // 검증 전 값은 확률이 아니다. 근거가 없으면 null.
    val confidence: Double? = null,
    val points: List<TrajectoryPoint>,
)

@Serializable
data class TrajectoryPoint(
    @SerialName("dt_ms") val dtMs: Long,
    val x: Double,
    val y: Double,
    @SerialName("heading_rad") val headingRad: Double? = null,
)

@Serializable
enum class RiskLevel { NORMAL, WATCH, WARNING, UNKNOWN }

@Serializable
data class RiskFrame(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("risk_objects") val riskObjects: List<RiskObject>,
)

@Serializable
data class RiskObject(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("risk_level") val riskLevel: RiskLevel,
    @SerialName("reason_codes") val reasonCodes: List<String> = emptyList(),
    @SerialName("ttc_s") val ttcS: Double? = null,
    @SerialName("risk_zones") val riskZones: List<RiskZone> = emptyList(),
)

@Serializable
data class RiskZone(
    @SerialName("dt_ms") val dtMs: Long,
    val polygon: List<Point2>,
)

@Serializable
data class Point2(val x: Double, val y: Double)

/** 03번 시뮬레이션 엔진의 scenario_manifest.json (지시서 PART 03 C-P1 필드). */
@Serializable
data class ScenarioManifest(
    @SerialName("scenario_id") val scenarioId: String,
    val title: String = scenarioId,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("step_ms") val stepMs: Long,
    val seed: Long? = null,
    @SerialName("ego_id") val egoId: String,
    @SerialName("event_annotation") val eventAnnotation: List<EventAnnotation> = emptyList(),
    val provenance: String,
    // true 면 다른 엔진 산출물이 아닌 이 저장소의 Mock 데이터. 화면에 표시한다.
    val mock: Boolean = false,
)

@Serializable
data class EventAnnotation(
    @SerialName("t_ms") val tMs: Long,
    val label: String,
)

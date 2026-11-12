package com.prism.vehicle.adapters

import ai.riskradar.engine02.EgoLocalTransformV1
import ai.riskradar.engine02.Engine02RiskFrameAdapter
import ai.riskradar.engine02.IntentEngine
import ai.riskradar.engine02.IntentFrame
import ai.riskradar.engine02.Observation
import ai.riskradar.engine02.PrismInputAdapter
import ai.riskradar.engine02.PrismVehicle
import ai.riskradar.engine02.PrismVehicleFrame
import ai.riskradar.engine02.RiskObjectV1
import ai.riskradar.engine02.VerifiedRoadProjection
import com.prism.prediction.Pose2D
import com.prism.prediction.PredictionEngine
import com.prism.vehicle.replay.Issue
import com.prism.vehicle.replay.Parsed
import com.prism.vehicle.replay.Point2
import com.prism.vehicle.replay.PredictionFrame
import com.prism.vehicle.replay.RiskFrame
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.RiskObject
import com.prism.vehicle.replay.RiskZone
import com.prism.vehicle.replay.Severity
import com.prism.vehicle.replay.Trajectory
import com.prism.vehicle.replay.TrajectoryPoint
import com.prism.vehicle.replay.VehicleFrame
import com.prism.vehicle.replay.VehiclePrediction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import com.prism.prediction.PredictionFrame as P01PredictionFrame
import com.prism.prediction.VehicleFrame as P01VehicleFrame
import com.prism.prediction.VehicleState as P01VehicleState

/*
 * 01 예측·02 위험도 엔진을 앱 안에서 실행한다.
 * 시나리오 폴더에 prediction_frames / risk_frames 파일이 있으면(Mock 시나리오) 그 파일을 그대로 쓴다.
 */

/** 03 보조 파일 ego_pose.jsonl 한 줄: 월드에서 자차 자세. x=오른쪽, y=전방, heading 전방=π/2. */
@Serializable
data class EgoPose(
    @SerialName("timestamp_ms") val timestampMs: Long,
    val x: Double,
    val y: Double,
    @SerialName("heading_rad") val headingRad: Double,
)

/** 앱·테스트 공용 로더: 파일 + 01·02 엔진. */
fun engineLoader(source: TextSource): Pair<JsonFileProviders, ScenarioLoader> {
    val files = JsonFileProviders(source)
    return files to ScenarioLoader(
        files,
        EnginePredictionProvider(files, files, files::egoPoses),
        EngineRiskProvider(files, files, files::egoPoses),
    )
}

/** 각 VehicleFrame 시점마다 직전 [historyMs] 기록으로 01 PredictionEngine 을 돌린다. */
class EnginePredictionProvider(
    private val files: PredictionDataProvider,
    private val scenarios: ScenarioDataProvider,
    private val poses: (String) -> Map<Long, EgoPose>,
    private val historyMs: Long = 3000,
) : PredictionDataProvider {
    override fun predictionFrames(scenarioId: String): Parsed<List<PredictionFrame>> {
        files.predictionFrames(scenarioId).let { if (it.value != null) return it }
        // 차량 파일 오류는 ScenarioLoader 가 이미 보고한다
        val frames = scenarios.vehicleFrames(scenarioId).value?.sortedBy { it.timestampMs } ?: return Parsed(null, emptyList())
        val pose = poses(scenarioId)
        val issues = mutableListOf<Issue>()
        val out = frames.mapIndexedNotNull { i, f ->
            val window = frames.subList(0, i + 1).takeLastWhile { f.timestampMs - it.timestampMs <= historyMs }
            // 03 이 준 실제 자차 자세가 있으면 쓰고, 하나라도 없으면 01 이 속도로 추정한다
            val windowPoses = window.map { pose[it.timestampMs] }.takeIf { null !in it }
                ?.map { Pose2D(it!!.x, it.y, it.headingRad - PI / 2) }
            try {
                PredictionEngine.predict(window.map { it.toPart01() }, egoPoses = windowPoses).toPart05()
            } catch (e: IllegalArgumentException) {
                issues += Issue(Severity.WARNING, "01@${f.timestampMs}", "예측 실패: ${e.message}")
                null
            }
        }
        return Parsed(out, issues)
    }
}

/**
 * 02 engine02 를 프레임 순서대로 돌려 RiskFrame v1 을 만든다.
 * 02 는 "검증된 도로 좌표"를 요구하므로 03 시뮬레이터의 실제 자차 자세(ego_pose)와 직선 도로 형상으로 만든다.
 * ego_pose 가 없으면(실차·Mock) 도로 좌표를 추측하지 않고 계산하지 않는다.
 */
class EngineRiskProvider(
    private val files: RiskDataProvider,
    private val scenarios: ScenarioDataProvider,
    private val poses: (String) -> Map<Long, EgoPose>,
) : RiskDataProvider {
    override fun riskFrames(scenarioId: String): Parsed<List<RiskFrame>> {
        files.riskFrames(scenarioId).let { if (it.value != null) return it }
        val frames = scenarios.vehicleFrames(scenarioId).value?.sortedBy { it.timestampMs } ?: return Parsed(null, emptyList())
        val pose = poses(scenarioId)
        if (pose.isEmpty()) {
            return Parsed(null, listOf(Issue(Severity.WARNING, "$scenarioId/ego_pose.jsonl", "파일 없음: 02 위험도를 계산하지 않음")))
        }
        val engine = IntentEngine()
        val issues = mutableListOf<Issue>()
        val out = frames.mapNotNull { f ->
            val p = pose[f.timestampMs]
            if (p == null) {
                issues += Issue(Severity.WARNING, "02@${f.timestampMs}", "같은 시각 ego_pose 없음")
                return@mapNotNull null
            }
            try {
                riskAt(engine, f, p, scenarioId)
            } catch (e: IllegalArgumentException) {
                issues += Issue(Severity.WARNING, "02@${f.timestampMs}", "위험도 계산 실패: ${e.message}")
                null
            }
        }
        return Parsed(out, issues)
    }

    private fun riskAt(engine: IntentEngine, f: VehicleFrame, p: EgoPose, scenarioId: String): RiskFrame {
        val psi = p.headingRad - PI / 2 // 자차 축이 도로 축에서 돌아간 각
        val c = cos(psi)
        val s = sin(psi)
        val projections = f.vehicles.associate { v ->
            val lane = v.laneId?.removePrefix("L")?.toIntOrNull()?.minus(1)
            v.id to VerifiedRoadProjection(
                trackId = v.id, timestampMs = f.timestampMs,
                dBodyCenterM = p.x + v.x * c - v.y * s,
                sBodyCenterM = p.y + v.x * s + v.y * c,
                vdMps = v.vx * c - v.vy * s,
                vsMps = v.vx * s + v.vy * c,
                yawRelativeRoadRad = wrap(v.headingRad + psi - PI / 2),
                laneCenterDM = (lane ?: 0) * SIM03_LANE_WIDTH_M,
                laneWidthM = SIM03_LANE_WIDTH_M,
                laneReference = SIM03_ROAD,
                identityEpoch = scenarioId,
                geometryReliable = lane != null,
            )
        }
        val input = PrismVehicleFrame(
            f.schemaVersion, f.timestampMs, f.coordinateSystem, f.egoId,
            f.vehicles.map { PrismVehicle(it.id, it.x, it.y, it.vx, it.vy, it.headingRad, it.lengthM, it.widthM, it.laneId) },
        )
        val pairs = mutableListOf<Pair<Observation, IntentFrame>>()
        val blocked = mutableListOf<RiskObject>()
        for (v in f.vehicles) {
            if (v.id == f.egoId) continue
            val r = PrismInputAdapter.convert(input, v.id, projections)
            val o = r.observation
            if (o == null) blocked += RiskObject(v.id, RiskLevel.UNKNOWN, listOf("ENGINE02_INPUT_BLOCKED", r.reason))
            else pairs += o to engine.observe(o, f.timestampMs)
        }
        val transform = EgoLocalTransformV1(f.timestampMs, SIM03_ROAD, p.x, p.y, -psi, verified = true)
        val v1 = Engine02RiskFrameAdapter.convert(f.timestampMs, f.vehicles.map { it.id }.toSet(), pairs, transform)
        return RiskFrame(v1.schemaVersion, v1.timestampMs, v1.riskObjects.map { it.toPart05() } + blocked)
    }

    private companion object {
        // shortcut: 03 HighwaySim 의 직선 도로 차선 폭(StraightLane.DEFAULT_WIDTH) 고정값, 03 이 도로 형상을 파일로 내보내면 그 값을 읽도록 교체
        const val SIM03_LANE_WIDTH_M = 4.0
        const val SIM03_ROAD = "sim03-straight-road"

        fun wrap(a: Double) = atan2(sin(a), cos(a))
    }
}

private fun VehicleFrame.toPart01() = P01VehicleFrame(
    timestampMs, egoId,
    vehicles.map { P01VehicleState(it.id, it.x, it.y, it.vx, it.vy, it.headingRad, it.lengthM, it.widthM, it.laneId ?: "") },
)

private fun P01PredictionFrame.toPart05() = PredictionFrame(
    schemaVersion, timestampMs,
    predictions.map { vp ->
        VehiclePrediction(vp.vehicleId, vp.trajectories.map { t ->
            Trajectory(t.mode, t.confidence, t.points.map { TrajectoryPoint(it.dtMs, it.x, it.y, it.headingRad) })
        })
    },
)

private fun RiskObjectV1.toPart05() = RiskObject(
    vehicleId, RiskLevel.valueOf(riskLevel.name), reasonCodes, ttcS,
    riskZones.map { z -> RiskZone(z.dtMs, z.polygon.map { Point2(it.x, it.y) }) },
)

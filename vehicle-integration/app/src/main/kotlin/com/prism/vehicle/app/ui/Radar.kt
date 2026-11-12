package com.prism.vehicle.app.ui

import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.SceneOptions
import ai.prism.visualization.ui.RadarConfig
import ai.prism.visualization.ui.RadarScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.prism.vehicle.replay.PredictionFrame
import com.prism.vehicle.replay.RiskFrame
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.ScenarioTimeline
import com.prism.vehicle.replay.Snapshot
import com.prism.vehicle.replay.VehicleFrame
import ai.prism.visualization.model.Point2 as VizPoint2
import ai.prism.visualization.model.PredictionFrame as VizPredictionFrame
import ai.prism.visualization.model.RiskFrame as VizRiskFrame
import ai.prism.visualization.model.RiskLevel as VizRiskLevel
import ai.prism.visualization.model.RiskZone as VizRiskZone
import ai.prism.visualization.model.Trajectory as VizTrajectory
import ai.prism.visualization.model.TrajectoryPoint as VizTrajectoryPoint
import ai.prism.visualization.model.VehicleFrame as VizVehicleFrame
import ai.prism.visualization.model.VehiclePrediction as VizVehiclePrediction
import ai.prism.visualization.model.VehicleState as VizVehicleState

fun riskColor(level: RiskLevel?): Color = when (level) {
    RiskLevel.WARNING -> Color(0xFFE53935)
    RiskLevel.WATCH -> Color(0xFFFDD835)
    RiskLevel.NORMAL, null -> Color(0xFF9E9E9E)
    RiskLevel.UNKNOWN -> Color(0xFFB0BEC5)
}

/** 04 visualization-engine 의 RadarScreen 으로 그린다. 05 계약 DTO → 04 모델 변환만 한다. */
@Composable
fun RadarSlot(t: ScenarioTimeline, snap: Snapshot, mode: DisplayMode, modifier: Modifier) {
    val lanes = remember(t) { LaneLayout.of(t.vehicleFrames) }
    val sceneOptions = remember(t) { SceneOptions(staleAfterMs = t.manifest.stepMs * 2) } // 05 의 stale 기준과 같게
    val frame = snap.vehicle
    RadarScreen(
        vehicleFrame = remember(frame) { frame?.toViz() },
        predictionFrame = remember(snap.prediction) { snap.prediction?.toViz() },
        riskFrame = remember(snap.risk) { snap.risk?.toViz() },
        mode = mode,
        config = RadarConfig(road = lanes.roadAt(frame), sceneOptions = sceneOptions),
        nowMs = snap.tMs,
        modifier = modifier,
    )
}

/** VehicleFrame v1 에 차선 형상이 없어 lane_id("L1"=왼쪽)와 차량 가로 위치로 차선 수·폭을 추정한다. */
internal class LaneLayout(private val laneCount: Int, private val laneWidthM: Float) {
    fun roadAt(frame: VehicleFrame?): RoadConfig {
        val ego = frame?.vehicles?.firstOrNull { it.id == frame.egoId }?.laneIndex() ?: return RoadConfig()
        return RoadConfig(maxOf(laneCount, ego + 1), laneWidthM, ego)
    }

    companion object {
        fun of(frames: List<VehicleFrame>): LaneLayout {
            val widths = mutableListOf<Double>()
            var count = 0
            for (f in frames) {
                val ego = f.vehicles.firstOrNull { it.id == f.egoId }?.laneIndex() ?: continue
                for (v in f.vehicles) {
                    val i = v.laneIndex() ?: continue
                    count = maxOf(count, i + 1)
                    if (i != ego) (v.x / (i - ego)).takeIf { it in 2.5..5.0 }?.let(widths::add)
                }
            }
            val width = widths.sorted().getOrNull(widths.size / 2) ?: 3.5 // 차선 변경 중 차량이 섞여도 흔들리지 않게 중앙값
            return LaneLayout(maxOf(count, 1), width.toFloat())
        }

        private fun com.prism.vehicle.replay.VehicleState.laneIndex() = laneId?.removePrefix("L")?.toIntOrNull()?.minus(1)?.takeIf { it >= 0 }
    }
}

private fun VehicleFrame.toViz() = VizVehicleFrame(
    timestampMs, egoId,
    vehicles.map {
        VizVehicleState(
            it.id, it.x.toFloat(), it.y.toFloat(), it.vx.toFloat(), it.vy.toFloat(),
            it.headingRad.toFloat(), it.lengthM.toFloat(), it.widthM.toFloat(), it.laneId,
        )
    },
    schemaVersion,
)

private fun PredictionFrame.toViz() = VizPredictionFrame(
    timestampMs,
    predictions.map { vp ->
        VizVehiclePrediction(vp.vehicleId, vp.trajectories.map { tr ->
            VizTrajectory(tr.mode, tr.confidence?.toFloat(), tr.points.map {
                VizTrajectoryPoint(it.dtMs, it.x.toFloat(), it.y.toFloat(), it.headingRad?.toFloat())
            })
        })
    },
    schemaVersion,
)

private fun RiskFrame.toViz() = VizRiskFrame(
    timestampMs,
    riskObjects.map { o ->
        VehicleRisk(
            o.vehicleId, VizRiskLevel.valueOf(o.riskLevel.name), o.reasonCodes, o.ttcS?.toFloat(),
            o.riskZones.map { z -> VizRiskZone(z.dtMs, z.polygon.map { VizPoint2(it.x.toFloat(), it.y.toFloat()) }) },
        )
    },
    schemaVersion,
)

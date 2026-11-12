package ai.prism.visualization.overlays

import ai.prism.visualization.overlays.RiskOverlay.drawGhost
import ai.prism.visualization.overlays.RiskOverlay.drawRegion
import ai.prism.visualization.overlays.RiskOverlay.drawRelation
import ai.prism.visualization.overlays.RiskOverlay.drawRiskIcon
import ai.prism.visualization.overlays.TrajectoryOverlay.drawTrajectory
import ai.prism.visualization.overlays.VehicleRenderer.drawVehicleAnnotations
import ai.prism.visualization.overlays.VehicleRenderer.drawVehicleBody
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RegionKind
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.Scene
import ai.prism.visualization.rendering.SceneVehicle
import ai.prism.visualization.rendering.ViewportTransform
import ai.prism.visualization.rendering.severity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scene 전체를 그린다. Compose Canvas 와 오프스크린(벤치마크·스냅샷) 양쪽에서 같은 함수를 쓴다.
 * 그리는 순서(위험 표시가 차량 그래픽을 가리지 않도록):
 *   도로 → 미래 점유 영역 → 충돌 위험 영역 → 예측 이동 경로 → 차량(낮은 등급부터)
 *   → 연쇄 위험 연결선 → 위치 미상 차량 → 라벨 칩(ID·등급·원인) → 화면 밖 위험 표시.
 * 영역·경로는 옅게 차량 아래에, 연결선·라벨은 차량 위에 둔다.
 *
 * 주행 모드에서 화면 밖(보이지 않는) 차량의 궤적·영역은 그리지 않고, 가장자리에 방향·거리 표시만 남긴다.
 */
fun DrawScope.drawRadarScene(scene: Scene, transform: ViewportTransform, textMeasurer: TextMeasurer?, roadOffsetM: Float = 0f) {
    val inView: (SceneVehicle) -> Boolean = { transform.isVisibleY(it.center.y, marginM = 0f) }
    val byId = scene.vehicles.associateBy { it.id }
    val hiddenIds = if (scene.mode == DisplayMode.DRIVING) scene.vehicles.filter { !inView(it) }.map { it.id }.toSet() else emptySet()
    val drawable = scene.vehicles
        .filter { transform.isVisibleY(it.center.y) }
        .sortedBy { if (it.isEgo) 99 else it.level.severity }
    val showId = scene.mode == DisplayMode.DETAIL

    drawRoad(transform, roadOffsetM)
    val regions = scene.regions.filter { it.vehicleId !in hiddenIds }
    regions.filter { it.kind == RegionKind.OCCUPANCY }.forEach { drawRegion(it, transform) }
    regions.filter { it.kind == RegionKind.CONFLICT }.forEach { drawRegion(it, transform) }
    scene.trajectories.filter { it.vehicleId !in hiddenIds }.sortedBy { it.emphasized }.forEach { drawTrajectory(it, transform) }
    drawable.forEach { drawVehicleBody(it, transform) }
    scene.relations.forEach { drawRelation(it, byId, transform) }
    scene.ghosts.forEach { drawGhost(it, transform, textMeasurer) }
    drawable.forEach { drawVehicleAnnotations(it, transform, textMeasurer, showId) }
    scene.vehicles
        .filter { !it.isEgo && !inView(it) && it.level != DisplayLevel.NORMAL }
        .forEach { drawOffscreenMarker(it, transform, textMeasurer) }
}

/** 화면 위/아래 가장자리에 위험 차량 방향(셰브런) + 등급 아이콘 + 거리(m). */
private fun DrawScope.drawOffscreenMarker(v: SceneVehicle, t: ViewportTransform, tm: TextMeasurer?) {
    val ahead = v.center.y > 0
    val x = t.toScreen(v.center.x, 0f).x
    val edgeY = if (ahead) px(16f) else size.height - px(16f)
    val dir = if (ahead) -1f else 1f
    val style = RiskStyle.of(v.level)
    val color = Color(style.fill).let { if (v.level == DisplayLevel.UNKNOWN) Color(style.stroke) else it }
    val chevron = Path().apply {
        moveTo(x - px(10f), edgeY - dir * px(2f))
        lineTo(x, edgeY + dir * px(8f))
        lineTo(x + px(10f), edgeY - dir * px(2f))
        close()
    }
    drawPath(chevron, color)
    drawRiskIcon(style, Offset(x - px(26f), edgeY), tm)
    if (tm != null) {
        val layout = tm.measure("${abs(v.center.y).roundToInt()}m",
            TextStyle(color = RadarColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        drawText(layout, topLeft = Offset(x + px(14f), edgeY - layout.size.height / 2f))
    }
}

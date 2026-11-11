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
 * 그리는 순서: 도로 → 차체(낮은 등급부터) → 위험 영역 → 예측 궤적 → 관계 화살표 → 위치 미상
 *            → 위험 아이콘·트래킹 ID → 화면 밖 위험 표시.
 * 위험 영역(반투명)과 예측 궤적은 자동차 그래픽 위에 겹쳐 그리고, 글자·아이콘은 가려지지 않도록 맨 위에 둔다.
 *
 * 주행 모드에서 화면 밖(보이지 않는) 차량의 궤적·영역은 그리지 않고, 가장자리에 방향·거리 표시만 남긴다.
 */
fun DrawScope.drawRadarScene(scene: Scene, transform: ViewportTransform, textMeasurer: TextMeasurer?) {
    val inView: (SceneVehicle) -> Boolean = { transform.isVisibleY(it.center.y, marginM = 0f) }
    val byId = scene.vehicles.associateBy { it.id }
    val hiddenIds = if (scene.mode == DisplayMode.DRIVING) scene.vehicles.filter { !inView(it) }.map { it.id }.toSet() else emptySet()
    val drawable = scene.vehicles
        .filter { transform.isVisibleY(it.center.y) }
        .sortedBy { if (it.isEgo) 99 else it.level.severity }
    val showId = scene.mode == DisplayMode.DETAIL

    drawRoad(transform)
    drawable.forEach { drawVehicleBody(it, transform) }
    scene.regions.filter { it.vehicleId !in hiddenIds }.forEach { drawRegion(it, transform) }
    scene.trajectories.filter { it.vehicleId !in hiddenIds }.sortedBy { it.emphasized }.forEach { drawTrajectory(it, transform) }
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

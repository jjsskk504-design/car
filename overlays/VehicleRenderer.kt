package ai.prism.visualization.overlays

import ai.prism.visualization.model.Point2
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneVehicle
import ai.prism.visualization.rendering.ViewportTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 차량 한 대: 회전된 차체 다각형 + 진행 방향 삼각형 + (필요 시) 위험 아이콘과 트래킹 ID.
 * 모든 꼭짓점이 [ViewportTransform] 을 거치므로 횡방향 확대에서도 회전이 일관된다.
 */
object VehicleRenderer {

    fun DrawScope.drawVehicle(
        v: SceneVehicle,
        t: ViewportTransform,
        textMeasurer: TextMeasurer?,
        showId: Boolean,
    ) {
        val style = RiskStyle.of(v.level)
        val w = ai.prism.visualization.rendering.VehicleGeometry.displayWidthM(v.lengthM, v.widthM, t)
        val corners = ai.prism.visualization.rendering.VehicleGeometry.corners(v.center.x, v.center.y, v.headingRad, v.lengthM, w)
        val nose = ai.prism.visualization.rendering.VehicleGeometry.nose(v.center.x, v.center.y, v.headingRad, v.lengthM, w)
        val body = polygon(corners, t)
        drawPath(body, Color(style.fill))
        val strokeEffect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(px(6f), px(4f))) else null
        drawPath(body, Color(style.stroke), style = Stroke(px(style.strokeWidthDp), pathEffect = strokeEffect))
        // 진행 방향
        val noseColor = if (v.level == DisplayLevel.UNKNOWN) Color(style.stroke) else Color(0xCC14171C)
        drawPath(polygon(nose, t), noseColor)

        // 원인 차량 표시: 색이 아닌 이중 테두리
        if (v.isSource) {
            val outer = expand(corners, v.center, 0.6f)
            drawPath(polygon(outer, t), Color(RiskStyle.WATCH.fill),
                style = Stroke(px(1.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(4f), px(3f)))))
        }

        val c = t.toScreen(v.center)
        if (style.icon != ai.prism.visualization.rendering.RiskIcon.NONE) {
            val halfW = corners.maxOf { t.toScreen(it).x } - c.x
            with(RiskOverlay) { drawRiskIcon(style, Offset(c.x + halfW + px(14f), c.y), textMeasurer) }
        }
        if (textMeasurer != null && (showId || v.isEgo)) {
            val label = if (v.isEgo) "자차" else v.id
            val layout = textMeasurer.measure(label, TextStyle(color = RadarColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            val halfW = corners.maxOf { t.toScreen(it).x } - c.x
            val x = c.x - halfW - layout.size.width - px(6f)
            drawText(layout, topLeft = Offset(x, c.y - layout.size.height / 2f))
        }
    }

    internal fun polygon(points: List<Point2>, t: ViewportTransform): Path = Path().apply {
        points.forEachIndexed { i, p ->
            val s = t.toScreen(p)
            if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
        }
        close()
    }

    private fun expand(ps: List<Point2>, c: Point2, m: Float) = ps.map { p ->
        val dx = p.x - c.x; val dy = p.y - c.y
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.01f)
        Point2(p.x + dx / len * m, p.y + dy / len * m)
    }
}

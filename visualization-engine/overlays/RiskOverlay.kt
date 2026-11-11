package ai.prism.visualization.overlays

import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.RiskIcon
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneGhost
import ai.prism.visualization.rendering.SceneRegion
import ai.prism.visualization.rendering.SceneRelation
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
import kotlin.math.hypot

/**
 * 위험 표현: 영역(반투명 다각형), 아이콘(색 + 모양 + 글자), 연쇄 위험 화살표, 위치 미상 차량(점선 + ?).
 * - NORMAL: 회색, 아이콘 없음
 * - WATCH: 노란색, 테두리 삼각형 "!"
 * - WARNING: 빨간색, 채운 삼각형 "!!"
 * - UNKNOWN: 점선 테두리, 원 "?" (빨간색을 쓰지 않음 = 경고 억제)
 * 깜빡임·펄스 애니메이션은 사용하지 않는다.
 */
object RiskOverlay {

    fun DrawScope.drawRegion(r: SceneRegion, t: ViewportTransform) {
        val style = RiskStyle.of(r.level)
        val path = VehicleRenderer.polygon(r.polygon, t)
        val alpha = when (r.level) {
            DisplayLevel.WARNING -> 0.22f
            DisplayLevel.WATCH -> 0.16f
            else -> 0.10f
        } * (1f - 0.5f * (r.dtMs.coerceIn(0L, 4000L) / 4000f)) // 먼 미래 영역일수록 옅게
        drawPath(path, Color(style.fill).copy(alpha = alpha))
        val dashed = r.level == DisplayLevel.UNKNOWN
        drawPath(path, Color(style.fill).copy(alpha = 0.6f), style = Stroke(px(1.5f),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(px(6f), px(5f))) else null))
    }

    fun DrawScope.drawGhost(g: SceneGhost, t: ViewportTransform, textMeasurer: TextMeasurer?) {
        val style = RiskStyle.UNKNOWN
        if (g.region.size >= 3) {
            val path = VehicleRenderer.polygon(g.region, t)
            drawPath(path, Color(style.fill))
            drawPath(path, Color(style.stroke), style = Stroke(px(2f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(6f), px(5f)))))
        }
        g.anchor?.let { a ->
            val s = t.toScreen(a)
            drawRiskIcon(style, Offset(s.x, s.y), textMeasurer)
        }
    }

    fun DrawScope.drawRelation(rel: SceneRelation, vehicles: Map<String, SceneVehicle>, t: ViewportTransform) {
        val from = vehicles[rel.fromId] ?: return
        val to = vehicles[rel.toId] ?: return
        val a = t.toScreen(from.center); val b = t.toScreen(to.center)
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < px(20f)) return
        val ux = dx / len; val uy = dy / len
        val start = Offset(a.x + ux * px(16f), a.y + uy * px(16f))
        val end = Offset(b.x - ux * px(18f), b.y - uy * px(18f))
        val color = Color(RiskStyle.of(rel.level).fill)
        drawLine(color, start, end, px(2.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(8f), px(5f))))
        val nx = -uy; val ny = ux
        val head = Path().apply {
            moveTo(end.x, end.y)
            lineTo(end.x - ux * px(10f) + nx * px(6f), end.y - uy * px(10f) + ny * px(6f))
            lineTo(end.x - ux * px(10f) - nx * px(6f), end.y - uy * px(10f) - ny * px(6f))
            close()
        }
        drawPath(head, color)
    }

    /** 색 + 모양 + 글자로 등급을 표현하는 아이콘(색약 운전자 고려). */
    fun DrawScope.drawRiskIcon(style: RiskStyle, center: Offset, textMeasurer: TextMeasurer?) {
        val r = px(12f)
        val fill = Color(style.fill)
        when (style.icon) {
            RiskIcon.NONE -> return
            RiskIcon.TRIANGLE, RiskIcon.TRIANGLE_FILLED -> {
                val tri = Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + r * 1.05f, center.y + r * 0.8f)
                    lineTo(center.x - r * 1.05f, center.y + r * 0.8f)
                    close()
                }
                if (style.icon == RiskIcon.TRIANGLE_FILLED) {
                    drawPath(tri, fill)
                    drawPath(tri, Color.White, style = Stroke(px(1.5f)))
                } else {
                    drawPath(tri, RadarColors.background)
                    drawPath(tri, fill, style = Stroke(px(2f)))
                }
            }
            RiskIcon.QUESTION -> {
                drawCircle(RadarColors.background, r, center)
                drawCircle(Color(style.stroke), r, center, style = Stroke(px(1.5f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(3f), px(2.5f)))))
            }
        }
        if (textMeasurer != null) {
            val textColor = when (style.icon) {
                RiskIcon.TRIANGLE_FILLED -> Color.White
                RiskIcon.TRIANGLE -> fill
                else -> Color(style.stroke)
            }
            val layout = textMeasurer.measure(style.glyph, TextStyle(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            val dy = if (style.icon == RiskIcon.QUESTION) 0f else px(2f)
            drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f + dy))
        }
    }
}

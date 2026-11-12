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

    /**
     * 위험 영역 두 종류를 구분해 그린다(차량 그래픽 아래 레이어).
     * - 미래 점유 영역(OCCUPANCY): 아주 옅은 채움 + 가는 점선. 먼 미래일수록 더 옅게.
     * - 충돌 위험 영역(CONFLICT): 같은 시점 자차 점유 영역과 겹침. 조금 더 진한 채움 + 실선 테두리.
     */
    fun DrawScope.drawRegion(r: SceneRegion, t: ViewportTransform) {
        val style = RiskStyle.of(r.level)
        val path = VehicleRenderer.polygon(r.polygon, t)
        val fade = 1f - 0.5f * (r.dtMs.coerceIn(0L, 4000L) / 4000f)
        val base = Color(style.fill)
        when (r.kind) {
            ai.prism.visualization.rendering.RegionKind.OCCUPANCY -> {
                drawPath(path, base.copy(alpha = 0.07f * fade))
                drawPath(path, base.copy(alpha = 0.45f * fade), style = Stroke(px(1f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(5f), px(5f)))))
            }
            ai.prism.visualization.rendering.RegionKind.CONFLICT -> {
                drawPath(path, base.copy(alpha = 0.16f * fade))
                drawPath(path, base.copy(alpha = 0.85f), style = Stroke(px(2f)))
            }
        }
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

    /**
     * 연쇄 위험 관계: 원인 차량 → 영향받는 차량. RiskFrame 의 source_vehicle_ids 로 확인된 관계만 그린다.
     * 시작점은 속이 빈 원(원인), 끝은 화살촉(영향받는 차량). 색은 영향받는 차량의 등급.
     * 어두운 테두리선을 먼저 깔아 도로·영역 위에서도 잘 보이게 한다. 차량 그래픽 위 레이어.
     */
    fun DrawScope.drawRelation(rel: SceneRelation, vehicles: Map<String, SceneVehicle>, t: ViewportTransform) {
        val from = vehicles[rel.fromId] ?: return
        val to = vehicles[rel.toId] ?: return
        val a = t.toScreen(from.center); val b = t.toScreen(to.center)
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < px(24f)) return
        val ux = dx / len; val uy = dy / len
        // 차량 화면 크기만큼 떨어진 곳에서 시작·끝(차량을 가리지 않음)
        fun radius(v: SceneVehicle) = maxOf(v.lengthM * t.pxPerMeterY, v.widthM * t.pxPerMeterX) / 2f + px(4f)
        val start = Offset(a.x + ux * radius(from), a.y + uy * radius(from))
        val end = Offset(b.x - ux * radius(to), b.y - uy * radius(to))
        if (hypot(end.x - start.x, end.y - start.y) < px(12f)) return
        val color = Color(RiskStyle.of(rel.level).fill)
        val outline = RadarColors.background.copy(alpha = 0.85f)
        drawLine(outline, start, end, px(5f))
        drawLine(color, start, end, px(2.5f))
        // 시작: 원인 표시(속 빈 원)
        drawCircle(outline, px(6f), start)
        drawCircle(Color(RiskStyle.WATCH.fill), px(4.5f), start, style = Stroke(px(2f)))
        // 끝: 화살촉
        val nx = -uy; val ny = ux
        val head = Path().apply {
            moveTo(end.x, end.y)
            lineTo(end.x - ux * px(12f) + nx * px(7f), end.y - uy * px(12f) + ny * px(7f))
            lineTo(end.x - ux * px(12f) - nx * px(7f), end.y - uy * px(12f) - ny * px(7f))
            close()
        }
        drawPath(head, outline, style = Stroke(px(3f)))
        drawPath(head, color)
    }

    /** 색 + 모양 + 글자로 등급을 표현하는 아이콘(색약 운전자 고려). */
    fun DrawScope.drawRiskIcon(style: RiskStyle, center: Offset, textMeasurer: TextMeasurer?, radiusDp: Float = 12f) {
        val r = px(radiusDp)
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
            val layout = textMeasurer.measure(style.glyph, TextStyle(color = textColor, fontSize = (11f * radiusDp / 12f).sp, fontWeight = FontWeight.Bold))
            val dy = if (style.icon == RiskIcon.QUESTION) 0f else px(2f)
            drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f + dy))
        }
    }
}

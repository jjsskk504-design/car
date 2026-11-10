package com.prism.vehicle.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.Snapshot
import kotlin.math.PI
import kotlin.math.min

/*
 * shortcut: 04번 prism-visualization-engine 의 PrismRadarView 가 오기 전까지 쓰는 최소 표시용 Placeholder.
 * 렌더링 품질·혼잡 억제·깜빡임 감소는 04번 책임이므로 여기서 구현하지 않는다.
 * 04번 AAR 이 오면 이 함수 호출부(Screens.kt 의 RadarSlot) 한 곳만 PrismRadarView 로 바꾼다.
 */

private const val X_RANGE_M = 9.0   // 좌우 ±9m (3차선)
private const val Y_MIN_M = -30.0
private const val Y_MAX_M = 70.0
private const val LANE_W = 3.6

fun riskColor(level: RiskLevel?): Color = when (level) {
    RiskLevel.WARNING -> Color(0xFFE53935)
    RiskLevel.WATCH -> Color(0xFFFDD835)
    RiskLevel.NORMAL, null -> Color(0xFF9E9E9E)
    RiskLevel.UNKNOWN -> Color(0xFFB0BEC5)
}

@Composable
fun PlaceholderRadar(snapshot: Snapshot, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        drawRect(Color(0xFF15181D))
        val scale = min(size.width / (2 * X_RANGE_M), size.height / (Y_MAX_M - Y_MIN_M)).toFloat()
        val cx = size.width / 2
        fun px(x: Double, y: Double) = Offset(cx + (x * scale).toFloat(), size.height - ((y - Y_MIN_M) * scale).toFloat())

        // 고정 3차선 (표시용). 실제 차선 기하는 03/04 담당.
        val dash = PathEffect.dashPathEffect(floatArrayOf(24f, 24f))
        for (i in -1..2) {
            val x = (i - 0.5) * LANE_W
            val edge = i == -1 || i == 2
            drawLine(Color(0xFF5F6368), px(x, Y_MIN_M), px(x, Y_MAX_M), 3f, pathEffect = if (edge) null else dash)
        }

        val frame = snapshot.vehicle ?: return@Canvas
        val alpha = if (snapshot.stale) 0.35f else 1f

        // 위험 영역 (source timestamp 좌표계 그대로)
        snapshot.risk?.riskObjects?.forEach { o ->
            o.riskZones.forEach { z ->
                val path = Path()
                z.polygon.forEachIndexed { i, p -> px(p.x, p.y).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                path.close()
                drawPath(path, riskColor(o.riskLevel).copy(alpha = 0.25f * alpha))
            }
        }

        // 예측 궤적
        snapshot.prediction?.predictions?.forEach { vp ->
            val start = frame.vehicles.firstOrNull { it.id == vp.vehicleId } ?: return@forEach
            vp.trajectories.forEach { t ->
                val path = Path().apply { px(start.x, start.y).let { moveTo(it.x, it.y) } }
                t.points.sortedBy { it.dtMs }.forEach { p -> px(p.x, p.y).let { path.lineTo(it.x, it.y) } }
                drawPath(path, Color(0xFF64B5F6).copy(alpha = 0.8f * alpha), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            }
        }

        // 차량
        frame.vehicles.forEach { v ->
            val isEgo = v.id == frame.egoId
            val level = if (snapshot.stale) RiskLevel.UNKNOWN else snapshot.riskOf(v.id)?.riskLevel
            val color = if (isEgo) Color(0xFF4FC3F7) else riskColor(level)
            val center = px(v.x, v.y)
            val w = (v.widthM * scale).toFloat()
            val l = (v.lengthM * scale).toFloat()
            // heading_rad: +x 에서 반시계. 화면은 y 가 아래로 증가하므로 부호 반전.
            val deg = (-(v.headingRad - PI / 2) * 180 / PI).toFloat()
            rotate(deg, center) {
                val topLeft = Offset(center.x - w / 2, center.y - l / 2)
                if (level == RiskLevel.UNKNOWN && !isEgo) {
                    drawRect(color.copy(alpha = alpha), topLeft, Size(w, l), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                } else {
                    drawRect(color.copy(alpha = alpha), topLeft, Size(w, l))
                }
            }
            val tag = when {
                isEgo -> "EGO"
                level == RiskLevel.WARNING -> "! ${v.id}"
                level == RiskLevel.UNKNOWN -> "? ${v.id}"
                else -> v.id
            }
            label(measurer, tag, Offset(center.x + w / 2 + 6f, center.y - 14f), Color.White.copy(alpha = alpha))
        }
    }
}

private fun DrawScope.label(m: androidx.compose.ui.text.TextMeasurer, text: String, at: Offset, color: Color) {
    drawText(m, text, at, TextStyle(color = color, fontSize = 13.sp))
}

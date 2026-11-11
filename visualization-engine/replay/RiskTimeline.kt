package ai.prism.visualization.replay

import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.overlays.RadarColors
import ai.prism.visualization.rendering.RiskStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 차량별 위험 등급 타임라인. 색 + 막대 높이로 등급을 구분한다(색만으로 전달하지 않음).
 * NORMAL = 가는 선, UNKNOWN = 1/3 높이 점선 테두리, WATCH = 2/3 높이, WARNING = 전체 높이.
 */
@Composable
fun RiskTimeline(
    session: ReplaySession,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxRows: Int = 4,
) {
    val tm = rememberTextMeasurer()
    val rows = remember(session) { listOf("전체") + session.flaggedVehicleIds().take(maxRows) }
    val overall = remember(session) { session.overallLevels() }
    val rowH = 22.dp
    Canvas(
        modifier
            .height(rowH * rows.size + 4.dp)
            .semantics { contentDescription = "위험 등급 타임라인: ${rows.drop(1).joinToString()}" }
            .pointerInput(session) {
                val label = 56.dp.toPx()
                fun seek(x: Float) {
                    val f = ((x - label) / (size.width - label)).coerceIn(0f, 1f)
                    onSeek((f * session.durationMs).toLong())
                }
                detectTapGestures { seek(it.x) }
            }
            .pointerInput(session, "drag") {
                val label = 56.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    val f = ((change.position.x - label) / (size.width - label)).coerceIn(0f, 1f)
                    onSeek((f * session.durationMs).toLong())
                }
            },
    ) {
        val label = 56.dp.toPx()
        val w = size.width - label
        val h = rowH.toPx()
        val n = session.frames.size
        val cell = w / n
        rows.forEachIndexed { r, name ->
            val top = r * h
            drawText(tm, name, Offset(0f, top + 1f), TextStyle(color = RadarColors.subtext, fontSize = 12.sp))
            val baseline = top + h - 3f
            drawLine(Color(0xFF3A414C), Offset(label, baseline), Offset(size.width, baseline), 1f)
            for (i in 0 until n) {
                val lv = if (r == 0) overall[i] else session.levelOf(i, name)
                val frac = when (lv) {
                    RiskLevel.NORMAL -> continue
                    RiskLevel.UNKNOWN -> 0.34f
                    RiskLevel.WATCH -> 0.67f
                    RiskLevel.WARNING -> 1f
                }
                val bh = (h - 5f) * frac
                val x = label + i * cell
                val style = RiskStyle.of(lv)
                if (lv == RiskLevel.UNKNOWN) {
                    drawRect(Color(style.stroke), Offset(x, baseline - bh), Size(cell + 0.5f, bh),
                        style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                } else {
                    drawRect(Color(style.fill), Offset(x, baseline - bh), Size(cell + 0.5f, bh))
                }
            }
        }
        val cx = label + w * (positionMs.toFloat() / session.durationMs.coerceAtLeast(1))
        drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), 2.dp.toPx())
    }
}

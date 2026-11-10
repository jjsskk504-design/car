package ai.prism.visualization.overlays

import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneTrajectory
import ai.prism.visualization.rendering.ViewportTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.hypot

/**
 * 예측 궤적(향후 3~5초).
 * - 위험 판단에 쓰인 후보: 실선 + 끝 화살표
 * - 그 외 후보(정차 후 상세 모드에서만): 가는 점선
 * - 차선 변경 후보: 진행 방향 마디 점으로 경로 굴곡을 강조
 */
object TrajectoryOverlay {

    fun DrawScope.drawTrajectory(tr: SceneTrajectory, t: ViewportTransform) {
        if (tr.points.size < 2) return
        val base = if (tr.level == DisplayLevel.NORMAL) Color(0xFF9AA3AE) else Color(RiskStyle.of(tr.level).fill)
        val color = if (tr.emphasized) base.copy(alpha = 0.95f) else base.copy(alpha = 0.45f)
        val width = if (tr.emphasized) px(3f) else px(1.5f)
        val pts = tr.points.map { t.toScreen(it) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val effect = when {
            tr.level == DisplayLevel.UNKNOWN -> PathEffect.dashPathEffect(floatArrayOf(px(3f), px(4f)))
            !tr.emphasized -> PathEffect.dashPathEffect(floatArrayOf(px(5f), px(5f)))
            else -> null
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, pathEffect = effect))

        if (tr.emphasized) {
            // 시간 마디(후보 점)
            for (i in 1 until pts.size - 1) drawCircle(color, px(2.5f), Offset(pts[i].x, pts[i].y))
            // 끝 화살표
            val a = pts[pts.size - 2]; val b = pts.last()
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = hypot(dx, dy)
            if (len > 0.5f) {
                val ux = dx / len; val uy = dy / len
                val nx = -uy; val ny = ux
                val head = Path().apply {
                    moveTo(b.x + ux * px(4f), b.y + uy * px(4f))
                    lineTo(b.x - ux * px(8f) + nx * px(6f), b.y - uy * px(8f) + ny * px(6f))
                    lineTo(b.x - ux * px(8f) - nx * px(6f), b.y - uy * px(8f) - ny * px(6f))
                    close()
                }
                drawPath(head, color)
            }
        }
    }
}

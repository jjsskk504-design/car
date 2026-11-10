package ai.prism.visualization.overlays

import ai.prism.visualization.rendering.ViewportTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

object RadarColors {
    val background = Color(0xFF14171C)
    val asphalt = Color(0xFF242932)
    val laneLine = Color(0xFF6B7380)
    val edgeLine = Color(0xFF9AA2AE)
    val chevron = Color(0xFF3A414C)
    val text = Color(0xFFE8EAED)
    val subtext = Color(0xFFA3ABB7)
    val labelBg = Color(0xCC14171C)
}

/**
 * 도로·차선·진행 방향 표시.
 * 운전 중 시선 분산을 줄이기 위해 차선 점선을 스크롤 애니메이션하지 않고 고정 위치에 그린다.
 */
fun DrawScope.drawRoad(t: ViewportTransform) {
    val road = t.road
    drawRect(RadarColors.background)
    val left = t.roadLeftPx
    val right = t.roadRightPx
    drawRect(RadarColors.asphalt, Offset(left, 0f), Size(right - left, size.height))
    val edge = px(2f)
    drawLine(RadarColors.edgeLine, Offset(left, 0f), Offset(left, size.height), edge)
    drawLine(RadarColors.edgeLine, Offset(right, 0f), Offset(right, size.height), edge)

    val dash = 6f
    val gap = 6f
    val yStart = -t.config.behindM - 12f
    val yEnd = t.config.aheadM + 12f
    for (i in 1 until road.laneCount) {
        val x = road.leftEdgeX + i * road.laneWidthM
        var y = yStart - (yStart % (dash + gap))
        while (y < yEnd) {
            val a = t.toScreen(x, y)
            val b = t.toScreen(x, y + dash)
            drawLine(RadarColors.laneLine, Offset(a.x, a.y), Offset(b.x, b.y), px(2f))
            y += dash + gap
        }
    }

    // 차선별 진행 방향 셰브런(전방 = 화면 위)
    val chevronStep = 30f
    for (lane in 0 until road.laneCount) {
        val cx = road.leftEdgeX + (lane + 0.5f) * road.laneWidthM
        var y = -chevronStep
        while (y < yEnd) {
            if (y != 0f) drawChevron(t, cx, y, road.laneWidthM * 0.18f)
            y += chevronStep
        }
    }
}

private fun DrawScope.drawChevron(t: ViewportTransform, cx: Float, cy: Float, halfWidthM: Float) {
    val tip = t.toScreen(cx, cy + 1.2f)
    val l = t.toScreen(cx - halfWidthM, cy)
    val r = t.toScreen(cx + halfWidthM, cy)
    val path = Path().apply {
        moveTo(l.x, l.y); lineTo(tip.x, tip.y); lineTo(r.x, r.y)
    }
    drawPath(path, RadarColors.chevron, style = androidx.compose.ui.graphics.drawscope.Stroke(px(2f)))
}


/** dp → px */
internal fun DrawScope.px(dp: Float): Float = dp * density

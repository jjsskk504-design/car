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

/** 차선 점선(12 m)과 셰브런(30 m) 주기의 공배수. 도로 스크롤 위상은 이 값으로 나눈 나머지만 쓰면 된다. */
const val ROAD_PATTERN_PERIOD_M = 60f

/**
 * 도로·차선·진행 방향 표시.
 *
 * 차선 점선과 셰브런은 도로에 붙어 있는 표식이므로 자차 기준 좌표에서는 자차 주행거리([roadOffsetM])만큼
 * 뒤(-y)로 이동한 위치에 그린다. 이렇게 해야 자차가 달리는 중임이 보이고, 자차보다 느린 차량도
 * 도로에 대해 앞으로 진행하는 것으로 보인다(고정 도로에서는 후진처럼 보이던 문제).
 * [roadOffsetM] = 0 이면 예전과 같은 고정 배치다.
 */
fun DrawScope.drawRoad(t: ViewportTransform, roadOffsetM: Float = 0f) {
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
        var y = RoadPattern.firstMark(yStart, dash + gap, roadOffsetM)
        while (y < yEnd) {
            val a = t.toScreen(x, y)
            val b = t.toScreen(x, y + dash)
            drawLine(RadarColors.laneLine, Offset(a.x, a.y), Offset(b.x, b.y), px(2f))
            y += dash + gap
        }
    }

    // 차선별 진행 방향 셰브런(전방 = 화면 위). 자차 아래에 겹치는 것은 생략
    val chevronStep = 30f
    for (lane in 0 until road.laneCount) {
        val cx = road.leftEdgeX + (lane + 0.5f) * road.laneWidthM
        var y = RoadPattern.firstMark(yStart, chevronStep, roadOffsetM)
        while (y < yEnd) {
            if (kotlin.math.abs(y) > 3f) drawChevron(t, cx, y, road.laneWidthM * 0.18f)
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


/** 도로 표식 배치 계산(그리기와 분리해 JVM 테스트 가능). */
object RoadPattern {
    /**
     * 도로 고정 표식(월드 y = k·period)이 자차 주행거리 [offsetM] 뒤 자차 기준 좌표에서 놓이는 위치 중
     * [fromY] 이하인 가장 큰 값. 이후 period 간격으로 그리면 된다.
     */
    fun firstMark(fromY: Float, period: Float, offsetM: Float): Float {
        val phase = ((offsetM % period) + period) % period
        return kotlin.math.floor((fromY + phase) / period) * period - phase
    }
}

/** dp → px */
internal fun DrawScope.px(dp: Float): Float = dp * density

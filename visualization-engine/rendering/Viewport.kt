package ai.prism.visualization.rendering

import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.RoadConfig
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 화면에 보일 범위와 횡방향 확대 비율. */
data class ViewportConfig(
    val aheadM: Float = 60f,
    val behindM: Float = 25f,
    /** 도로가 차지할 목표 화면 폭 비율. */
    val roadWidthFraction: Float = 0.58f,
    /**
     * 실제 비율이면 3차로 도로가 화면 폭의 10% 정도로 좁아진다.
     * 탑다운 레이더 표시 관례에 따라 횡방향만 확대하되 이 배율을 넘지 않는다.
     * (차량 회전은 같은 변환을 거쳐 일관성을 유지하고, 차체 폭은 [VehicleGeometry.displayWidthM] 으로 보정)
     */
    val maxLateralExaggeration: Float = 5f,
)

/** 화면 픽셀 좌표. y 는 아래로 증가. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * 자차 기준 좌표(m) → 화면 픽셀 좌표 변환.
 * 자차는 가로 중앙, 세로는 [ViewportConfig.aheadM] / (ahead+behind) 지점에 고정된다.
 * 매 프레임 같은 규칙을 쓰므로 프레임 간 좌표계가 일관된다.
 */
class ViewportTransform(
    val widthPx: Float,
    val heightPx: Float,
    val road: RoadConfig,
    val config: ViewportConfig = ViewportConfig(),
) {
    val pxPerMeterY: Float = heightPx / (config.aheadM + config.behindM)
    val pxPerMeterX: Float = min(
        pxPerMeterY * config.maxLateralExaggeration,
        widthPx * config.roadWidthFraction / road.widthM,
    )
    val egoScreenY: Float = heightPx * config.aheadM / (config.aheadM + config.behindM)

    /** 도로 중심선이 화면 중앙에 오도록 자차의 x 오프셋을 보정한다. */
    private val roadCenterX: Float = (road.leftEdgeX + road.rightEdgeX) / 2f
    private val originX: Float = widthPx / 2f - roadCenterX * pxPerMeterX

    fun toScreen(x: Float, y: Float) = ScreenPoint(originX + x * pxPerMeterX, egoScreenY - y * pxPerMeterY)
    fun toScreen(p: Point2) = toScreen(p.x, p.y)

    fun toWorld(sx: Float, sy: Float) = Point2((sx - originX) / pxPerMeterX, (egoScreenY - sy) / pxPerMeterY)

    fun isVisibleY(y: Float, marginM: Float = 10f) = y >= -config.behindM - marginM && y <= config.aheadM + marginM

    val roadLeftPx: Float get() = toScreen(road.leftEdgeX, 0f).x
    val roadRightPx: Float get() = toScreen(road.rightEdgeX, 0f).x
}

/** 차량 다각형 계산(회전 포함). heading 은 +x 축 기준 반시계(rad). */
object VehicleGeometry {
    /**
     * 횡방향 확대 때문에 차체가 납작하게 보이지 않도록 화면용 폭(m)을 줄인다.
     * 화면상 폭 ≤ 화면상 길이 × 0.6, 그리고 ≤ 차선 폭 × 0.6.
     */
    fun displayWidthM(lengthM: Float, widthM: Float, t: ViewportTransform): Float {
        val lenPx = lengthM * t.pxPerMeterY
        val maxWPx = min(lenPx * 0.6f, t.road.laneWidthM * t.pxPerMeterX * 0.6f)
        return min(widthM * t.pxPerMeterX, maxWPx) / t.pxPerMeterX
    }

    /** 시계 방향 순서의 네 꼭짓점: 앞-오른, 뒤-오른, 뒤-왼, 앞-왼 (차량 진행 방향 기준). */
    fun corners(cx: Float, cy: Float, headingRad: Float, lengthM: Float, widthM: Float): List<Point2> {
        val ux = cos(headingRad); val uy = sin(headingRad)   // 진행 방향
        val nx = -uy; val ny = ux                            // 진행 방향의 왼쪽
        val hl = lengthM / 2; val hw = widthM / 2
        return listOf(
            Point2(cx + ux * hl - nx * hw, cy + uy * hl - ny * hw),
            Point2(cx - ux * hl - nx * hw, cy - uy * hl - ny * hw),
            Point2(cx - ux * hl + nx * hw, cy - uy * hl + ny * hw),
            Point2(cx + ux * hl + nx * hw, cy + uy * hl + ny * hw),
        )
    }

    /** 진행 방향을 나타내는 앞쪽 삼각형(차량 내부). */
    fun nose(cx: Float, cy: Float, headingRad: Float, lengthM: Float, widthM: Float): List<Point2> {
        val ux = cos(headingRad); val uy = sin(headingRad)
        val nx = -uy; val ny = ux
        val tip = lengthM / 2 - 0.3f
        val base = lengthM / 2 - minOf(1.6f, lengthM * 0.35f)
        val hw = widthM * 0.32f
        return listOf(
            Point2(cx + ux * tip, cy + uy * tip),
            Point2(cx + ux * base - nx * hw, cy + uy * base - ny * hw),
            Point2(cx + ux * base + nx * hw, cy + uy * base + ny * hw),
        )
    }
}

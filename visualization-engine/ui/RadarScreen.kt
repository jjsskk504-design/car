package ai.prism.visualization.ui

import ai.prism.visualization.model.PredictionFrame
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.overlays.ROAD_PATTERN_PERIOD_M
import ai.prism.visualization.overlays.RadarColors
import ai.prism.visualization.overlays.RiskOverlay.drawRiskIcon
import ai.prism.visualization.overlays.drawRadarScene
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.RoadOdometer
import ai.prism.visualization.rendering.Scene
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.rendering.SceneOptions
import ai.prism.visualization.rendering.ViewportConfig
import ai.prism.visualization.rendering.ViewportTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

/** RadarScreen 설정 묶음. */
data class RadarConfig(
    val road: RoadConfig = RoadConfig(),
    val viewport: ViewportConfig = ViewportConfig(),
    val sceneOptions: SceneOptions = SceneOptions(),
)

/** UI 테스트·통합 테스트에서 쓰는 태그. */
object RadarTestTags {
    const val CANVAS = "prism.radar.canvas"
    const val STATUS = "prism.radar.status"
    const val NOTICE = "prism.radar.notice"
}

/**
 * 주변 차량 탑다운 레이더 화면(공개 API).
 *
 * @param vehicleFrame 최신 VehicleFrame. null 이면 "차량 데이터 없음" 안내만 표시.
 * @param predictionFrame 예측 후보 궤적(선택).
 * @param riskFrame 위험 판정 결과(선택). 없으면 모든 차량을 중립색으로 표시.
 * @param mode 주행 중이면 [DisplayMode.DRIVING](강조 최소화), 정차 후 분석은 [DisplayMode.DETAIL].
 * @param nowMs 현재 시각(ms, VehicleFrame 과 같은 시계). 지정하면 데이터 지연을 감지해 위험 표시를 보류한다.
 * @param onVehicleClick 차량 탭 콜백(정차 후 상세 화면용). 주행 모드에서는 호출하지 않는다.
 */
@Composable
fun RadarScreen(
    vehicleFrame: VehicleFrame?,
    modifier: Modifier = Modifier,
    predictionFrame: PredictionFrame? = null,
    riskFrame: RiskFrame? = null,
    mode: DisplayMode = DisplayMode.DRIVING,
    config: RadarConfig = RadarConfig(),
    nowMs: Long? = null,
    showStatusBar: Boolean = true,
    onVehicleClick: ((String) -> Unit)? = null,
) {
    val scene = remember(vehicleFrame, predictionFrame, riskFrame, mode, nowMs, config.sceneOptions) {
        SceneBuilder.build(vehicleFrame, predictionFrame, riskFrame, mode, nowMs, config.sceneOptions)
    }
    RadarSceneView(scene, modifier, config, showStatusBar, if (mode == DisplayMode.DETAIL) onVehicleClick else null)
}

/** 이미 만든 [Scene] 을 그리는 하위 API(리플레이·벤치마크용). */
@Composable
fun RadarSceneView(
    scene: Scene,
    modifier: Modifier = Modifier,
    config: RadarConfig = RadarConfig(),
    showStatusBar: Boolean = true,
    onVehicleClick: ((String) -> Unit)? = null,
) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(RadarColors.background)) {
        if (showStatusBar) RadarStatusBar(scene, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        // 차량 ID·아이콘 글자 레이아웃 캐시(기본 8개는 차량 수보다 작아 매 프레임 재측정됨)
        val tm = rememberTextMeasurer(cacheSize = 256)
        // 자차 주행거리만큼 도로 표식을 흘려 보낸다(자차 고정 화면에서 자차가 정지한 것처럼 보이지 않도록)
        val odometer = remember { RoadOdometer() }
        val roadOffsetM = remember(scene, config.viewport.scrollRoadWithEgo) {
            if (!config.viewport.scrollRoadWithEgo) 0f
            else (odometer.advance(scene.timestampMs, scene.egoSpeedMps) % ROAD_PATTERN_PERIOD_M).toFloat()
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds() // 화면 밖 차선·궤적이 상태 줄 위로 그려지지 않도록
                .testTag(RadarTestTags.CANVAS)
                .semantics { contentDescription = "주변 차량 레이더: ${scene.summary()}" }
                .pointerInput(scene, onVehicleClick, config) {
                    if (onVehicleClick == null) return@pointerInput
                    detectTapGestures { pos ->
                        val t = ViewportTransform(size.width.toFloat(), size.height.toFloat(), config.road, config.viewport)
                        val hit = scene.vehicles.filter { !it.isEgo }.minByOrNull {
                            val s = t.toScreen(it.center); hypot(s.x - pos.x, s.y - pos.y)
                        }
                        if (hit != null) {
                            val s = t.toScreen(hit.center)
                            if (hypot(s.x - pos.x, s.y - pos.y) < 48.dp.toPx()) onVehicleClick(hit.id)
                        }
                    }
                },
        ) {
            val t = ViewportTransform(size.width, size.height, config.road, config.viewport)
            drawRadarScene(scene, t, tm, roadOffsetM)
        }
    }
}

/** 상단 상태 줄: 가장 높은 등급(아이콘 + 글자 + 대수)과 데이터 이상 안내. 색만으로 전달하지 않는다. */
@Composable
fun RadarStatusBar(scene: Scene, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(RadarTestTags.STATUS)) {
            val top = scene.highestLevel
            val style = RiskStyle.of(top)
            // 데이터가 오래되면 판단 보류(파란색)와 구분해 회색으로만 표시
            if (top != DisplayLevel.NORMAL && !scene.stale) {
                LevelIcon(top, Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
            }
            val text = when (top) {
                DisplayLevel.NORMAL, DisplayLevel.EGO -> "강조할 위험 없음"
                else -> listOf(DisplayLevel.WARNING, DisplayLevel.WATCH, DisplayLevel.UNKNOWN)
                    .filter { scene.count(it) > 0 }
                    .joinToString(" · ") { "${RiskStyle.of(it).label} ${scene.count(it)}대" }
            }
            Text(
                text,
                color = when {
                    scene.stale -> RadarColors.subtext
                    top == DisplayLevel.WARNING || top == DisplayLevel.WATCH -> Color(style.fill)
                    else -> RadarColors.text
                },
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        scene.notices.forEach { n ->
            Text("ⓘ ${n.text}", color = RadarColors.text, fontSize = 16.sp, modifier = Modifier.testTag(RadarTestTags.NOTICE))
        }
    }
}

/** 등급 아이콘(범례·상태줄·리플레이 패널 공용). */
@Composable
fun LevelIcon(level: DisplayLevel, modifier: Modifier = Modifier.size(20.dp)) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val style = RiskStyle.of(level)
        if (style.icon == ai.prism.visualization.rendering.RiskIcon.NONE) {
            drawRoundRect(Color(style.fill), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        } else {
            drawRiskIcon(style, Offset(size.width / 2, size.height / 2), tm)
        }
    }
}

/** 범례 한 줄에 쓰는 표시 견본. */
enum class LegendSwatch { PATH, OCCUPANCY, CONFLICT, RELATION, STALE }

@Composable
private fun SwatchIcon(kind: LegendSwatch, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val red = Color(RiskStyle.WARNING.fill)
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        val box = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.7f)
        val tl = Offset(w * 0.1f, h * 0.15f)
        when (kind) {
            LegendSwatch.PATH -> {
                drawLine(red.copy(alpha = 0.8f), Offset(w * 0.1f, h * 0.8f), Offset(w * 0.9f, h * 0.2f), 2.5.dp.toPx())
                drawCircle(red, 2.5.dp.toPx(), Offset(w * 0.5f, h * 0.5f))
            }
            LegendSwatch.OCCUPANCY -> {
                drawRect(red.copy(alpha = 0.10f), tl, box)
                drawRect(red.copy(alpha = 0.6f), tl, box, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = dash))
            }
            LegendSwatch.CONFLICT -> {
                drawRect(red.copy(alpha = 0.22f), tl, box)
                drawRect(red, tl, box, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            LegendSwatch.RELATION -> {
                drawCircle(Color(RiskStyle.WATCH.fill), 3.5.dp.toPx(), Offset(w * 0.15f, h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                drawLine(red, Offset(w * 0.25f, h / 2), Offset(w * 0.8f, h / 2), 2.dp.toPx())
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.95f, h / 2); lineTo(w * 0.75f, h * 0.3f); lineTo(w * 0.75f, h * 0.7f); close()
                }
                drawPath(p, red)
            }
            LegendSwatch.STALE -> {
                drawRoundRect(Color(RiskStyle.NORMAL.fill).copy(alpha = 0.45f), tl, box)
                drawRoundRect(Color(RiskStyle.NORMAL.fill), tl, box, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = dash))
            }
        }
    }
}

/** 등급·표시 범례: 색 + 아이콘 + 글자 + 선 모양. */
@Composable
fun RiskLegend(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            DisplayLevel.NORMAL to "정상 · 회색, 강조 없음",
            DisplayLevel.WATCH to "주의 · 노란 외곽선 + !",
            DisplayLevel.WARNING to "경고 · 빨간 외곽선 + !!",
            DisplayLevel.UNKNOWN to "판단 보류 · 파란 점선 + ?",
        ).forEach { (lv, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelIcon(lv, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, color = RadarColors.subtext, fontSize = 14.sp)
            }
        }
        listOf(
            LegendSwatch.STALE to "데이터 지연 · 회색 점선, 흐리게",
            LegendSwatch.PATH to "예측 이동 경로",
            LegendSwatch.OCCUPANCY to "미래 점유 영역 · 옅은 점선",
            LegendSwatch.CONFLICT to "충돌 위험 영역 · 자차와 겹침, 실선",
            LegendSwatch.RELATION to "연쇄 위험 · ○ 원인 → 영향받는 차량",
        ).forEach { (k, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwatchIcon(k, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, color = RadarColors.subtext, fontSize = 14.sp)
            }
        }
    }
}

package ai.prism.visualization.replay

import ai.prism.visualization.model.CauseCatalog
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.overlays.RadarColors
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.rendering.toDisplay
import ai.prism.visualization.ui.LevelIcon
import ai.prism.visualization.ui.RadarConfig
import ai.prism.visualization.ui.RadarSceneView
import ai.prism.visualization.ui.RiskLegend
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ReplayTestTags {
    const val PLAY = "prism.replay.play"
    const val SLIDER = "prism.replay.slider"
    const val TIME = "prism.replay.time"
    const val CAUSE = "prism.replay.cause"
    const val LOCKED = "prism.replay.locked"
    const val TIMELINE = "prism.replay.timeline"
}

/** 리플레이 재생 상태. 화면 회전·프로세스 재생성 후에도 위치·선택 차량이 복원된다. */
@Stable
class ReplayState(positionMs: Long, playing: Boolean, speed: Float, selectedVehicleId: String?) {
    var positionMs by mutableLongStateOf(positionMs)
    var playing by mutableStateOf(playing)
    var speed by mutableFloatStateOf(speed)
    var selectedVehicleId by mutableStateOf(selectedVehicleId)

    companion object {
        val Saver: Saver<ReplayState, Any> = Saver(
            save = { listOf(it.positionMs, it.playing, it.speed, it.selectedVehicleId ?: "") },
            restore = { v ->
                val l = v as List<*>
                ReplayState(l[0] as Long, l[1] as Boolean, l[2] as Float, (l[3] as String).ifEmpty { null })
            },
        )
    }
}

@Composable
fun rememberReplayState(sessionId: String, autoPlay: Boolean = false): ReplayState =
    rememberSaveable(sessionId, saver = ReplayState.Saver) { ReplayState(0L, autoPlay, 1f, null) }

/**
 * 정차 후 리플레이 화면(공개 API).
 *
 * @param isVehicleStopped 차량 정차 여부(vehicle-integration 이 Pleos Vehicle SDK 로 판단해 전달).
 *   false 이면 상세 분석을 숨기고 안내 문구만 보여준다.
 */
@Composable
fun ReplayScreen(
    session: ReplaySession,
    modifier: Modifier = Modifier,
    isVehicleStopped: Boolean = true,
    state: ReplayState = rememberReplayState(session.id),
    config: RadarConfig = RadarConfig(road = session.road),
) {
    if (!isVehicleStopped) {
        LaunchedEffect(Unit) { state.playing = false }
        Box(modifier.background(RadarColors.background).testTag(ReplayTestTags.LOCKED), contentAlignment = Alignment.Center) {
            Text("주행 중에는 상세 분석을 표시하지 않습니다.\n정차 후 확인하세요.",
                color = RadarColors.text, fontSize = 18.sp)
        }
        return
    }

    // 재생 루프
    LaunchedEffect(state.playing, session.id) {
        if (!state.playing) return@LaunchedEffect
        if (state.positionMs >= session.durationMs) state.positionMs = 0
        var last = withFrameNanos { it }
        while (state.playing) {
            withFrameNanos { now ->
                // 일시정지가 같은 프레임에 들어오면 더 진행하지 않는다
                if (state.playing) {
                    state.positionMs = (state.positionMs + ((now - last) / 1_000_000f * state.speed).toLong())
                        .coerceAtMost(session.durationMs)
                }
                last = now
            }
            if (state.positionMs >= session.durationMs) state.playing = false
        }
    }

    val frame = session.frameAt(state.positionMs)
    val scene = remember(frame) {
        SceneBuilder.build(frame.vehicle, frame.prediction, frame.risk, DisplayMode.DETAIL, null, config.sceneOptions)
    }
    val autoSelected = scene.vehicles.filter { !it.isEgo }.maxByOrNull {
        when (it.level) { DisplayLevel.WARNING -> 3; DisplayLevel.WATCH -> 2; DisplayLevel.UNKNOWN -> 1; else -> 0 }
    }?.takeIf { it.level != DisplayLevel.NORMAL }?.id ?: scene.ghosts.firstOrNull()?.vehicleId
    val selectedId = state.selectedVehicleId ?: autoSelected
    val events = remember(session) { session.events() }

    Column(modifier.background(RadarColors.background).padding(12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadarSceneView(scene, Modifier.weight(1f).fillMaxHeight(), config,
                        onVehicleClick = { state.selectedVehicleId = it })
                    DetailPanel(session, frame, selectedId, events, state, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadarSceneView(scene, Modifier.weight(1.2f).fillMaxWidth(), config,
                        onVehicleClick = { state.selectedVehicleId = it })
                    DetailPanel(session, frame, selectedId, events, state, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RiskTimeline(session, state.positionMs, onSeek = { state.positionMs = it; state.playing = false },
            modifier = Modifier.fillMaxWidth().testTag(ReplayTestTags.TIMELINE))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { state.playing = !state.playing }, modifier = Modifier.testTag(ReplayTestTags.PLAY)) {
                Text(if (state.playing) "일시정지" else "재생")
            }
            Spacer(Modifier.width(10.dp))
            Text("%.1f / %.1f초".format(state.positionMs / 1000f, session.durationMs / 1000f),
                color = RadarColors.text, fontSize = 14.sp, modifier = Modifier.testTag(ReplayTestTags.TIME))
            Spacer(Modifier.width(10.dp))
            Slider(
                value = state.positionMs.toFloat(),
                onValueChange = { state.positionMs = it.toLong(); state.playing = false },
                valueRange = 0f..session.durationMs.toFloat(),
                modifier = Modifier.weight(1f).testTag(ReplayTestTags.SLIDER),
            )
            Spacer(Modifier.width(8.dp))
            listOf(0.5f, 1f, 2f).forEach { s ->
                FilterChip(selected = state.speed == s, onClick = { state.speed = s }, label = { Text("${s}x") })
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun DetailPanel(
    session: ReplaySession,
    frame: ReplayFrame,
    selectedId: String?,
    events: List<RiskEvent>,
    state: ReplayState,
    modifier: Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1F25)).padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1) 시나리오
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.title, color = RadarColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (session.description.isNotEmpty()) Text(session.description, color = RadarColors.subtext, fontSize = 13.sp)
        }

        // 2) 선택 차량 카드: 등급 → 차량 → 원인 → 근거 순서로 읽히게
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF232832)).padding(12.dp)
                .testTag(ReplayTestTags.CAUSE),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val risk = frame.risk?.risks?.firstOrNull { it.vehicleId == selectedId }
            if (selectedId == null) {
                Text("현재 강조된 위험 차량이 없습니다. 차량을 눌러 상세 정보를 볼 수 있습니다.",
                    color = RadarColors.subtext, fontSize = 15.sp)
            } else {
                val lv = risk?.level ?: RiskLevel.NORMAL
                val present = frame.vehicle.vehicles.any { it.id == selectedId }
                val shown = if (!present && lv != RiskLevel.NORMAL) DisplayLevel.UNKNOWN else lv.toDisplay()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelPill(shown)
                    Spacer(Modifier.weight(1f))
                    if (state.selectedVehicleId != null) {
                        Text("자동 선택으로", color = RadarColors.subtext, fontSize = 13.sp,
                            modifier = Modifier.clickable { state.selectedVehicleId = null })
                    }
                }
                Text("차량 $selectedId · ${RiskStyle.of(shown).label}", color = RadarColors.text,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (!present) Text("이 시점에 차량 위치가 관측되지 않았습니다(판단 보류).",
                    color = Color(RiskStyle.UNKNOWN.stroke), fontSize = 14.sp)
                if (risk != null && lv != RiskLevel.NORMAL) {
                    Text("원인: ${CauseCatalog.describe(risk)}", color = RadarColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (risk.sourceVehicleIds.isNotEmpty()) Text("영향을 준 차량: ${risk.sourceVehicleIds.joinToString()}",
                        color = Color(RiskStyle.WATCH.fill), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    InfoRow("원인 코드", risk.reasonCodes.joinToString().ifEmpty { "-" })
                    risk.ttcS?.let { InfoRow("TTC", "%.1f초".format(it)) }
                    if (risk.riskZones.isNotEmpty()) InfoRow("위험 영역", risk.riskZones.joinToString { "%.1f초".format(it.dtMs / 1000f) })
                }
                frame.prediction?.predictions?.firstOrNull { it.vehicleId == selectedId }?.let { p ->
                    Text("예측 궤적", color = RadarColors.subtext, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp))
                    p.trajectories.forEach { c ->
                        val used = risk?.trajectoryMode != null && risk.trajectoryMode == c.mode
                        val horizon = c.points.maxOfOrNull { pt -> pt.dtMs }?.let { ms -> "%.1f초".format(ms / 1000f) } ?: "-"
                        Text("• ${CauseCatalog.maneuver(c.mode)} ($horizon)" + if (used) " — 위험 판단 근거" else "",
                            color = if (used) RadarColors.text else RadarColors.subtext, fontSize = 14.sp,
                            fontWeight = if (used) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        // 3) 위험 등급 변화(시간순). 현재 재생 위치 이전 이벤트는 밝게, 이후는 흐리게, 가장 최근 이벤트는 배경 강조
        Text("위험 등급 변화 ${events.size}건", color = RadarColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (events.isEmpty()) Text("기록된 위험 등급 변화 없음", color = RadarColors.subtext, fontSize = 14.sp)
        val current = events.lastOrNull { it.tMs <= state.positionMs }
        events.forEach { e ->
            val past = e.tMs <= state.positionMs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (e === current) Color(0xFF2A303A) else Color.Transparent)
                    .clickable { state.positionMs = e.tMs; state.playing = false; state.selectedVehicleId = e.vehicleId }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%.1f초".format(e.tMs / 1000f), color = RadarColors.subtext, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                LevelIcon(e.to.toDisplay(), Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("${e.vehicleId}  ${RiskStyle.of(e.from.toDisplay()).label} → ${RiskStyle.of(e.to.toDisplay()).label}",
                        color = if (past) RadarColors.text else RadarColors.subtext, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val src = if (e.sourceVehicleIds.isNotEmpty()) " · 원인 차량 ${e.sourceVehicleIds.joinToString()}" else ""
                    Text(e.cause + src, color = RadarColors.subtext, fontSize = 13.sp)
                }
            }
        }

        Text("범례", color = RadarColors.subtext, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        RiskLegend()
        Text("기록 데이터 기반 분석 화면입니다. 차량의 기존 안전 시스템을 대체하지 않습니다.",
            color = RadarColors.subtext, fontSize = 12.sp)
    }
}

/** 등급 배지: 아이콘 + 글자, 경고는 채운 배경. */
@Composable
private fun LevelPill(level: DisplayLevel) {
    val style = RiskStyle.of(level)
    val (bg, fg) = when (level) {
        DisplayLevel.WARNING -> Color(style.fill) to Color.White
        DisplayLevel.WATCH -> Color(style.fill).copy(alpha = 0.18f) to Color(style.fill)
        DisplayLevel.UNKNOWN -> Color(style.stroke).copy(alpha = 0.18f) to Color(style.stroke)
        else -> Color(0xFF3A414C) to RadarColors.text
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelIcon(level, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(style.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, color = RadarColors.subtext, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = RadarColors.text, fontSize = 14.sp)
    }
}

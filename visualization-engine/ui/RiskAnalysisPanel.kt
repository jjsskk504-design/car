package ai.prism.visualization.ui

import ai.prism.visualization.overlays.RadarColors
import ai.prism.visualization.rendering.DataHealth
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.RiskAnalysis
import ai.prism.visualization.rendering.RiskItem
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.toDisplay
import ai.prism.visualization.replay.RiskEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RiskAnalysisTestTags {
    const val PANEL = "prism.analysis.panel"
    const val COUNTS = "prism.analysis.counts"
    const val PRIMARY = "prism.analysis.primary"
    const val CHAIN = "prism.analysis.chain"
    const val SENSOR = "prism.analysis.sensor"
}

private val CARD = Color(0xFF1B1F25)
private val CARD_INNER = Color(0xFF232832)

/**
 * 위험 분석 패널(공개 API). [ai.prism.visualization.rendering.RiskAnalyzer] 결과를 그대로 보여 준다.
 * 표시하는 값은 모두 입력 프레임에서 온 것이다. TTC 는 RiskFrame 이 준 경우에만 보인다.
 *
 * @param events 지금까지 발생한 위험 등급 변화(시간순). 없으면 섹션을 생략한다.
 * @param simulationNote 시뮬레이션 데이터일 때 하단에 보여 줄 문구. null 이면 생략.
 */
@Composable
fun RiskAnalysisPanel(
    analysis: RiskAnalysis,
    modifier: Modifier = Modifier,
    events: List<RiskEvent> = emptyList(),
    simulationNote: String? = "Mock JSON 시뮬레이션 데이터 · 위치·거리는 시뮬레이션 좌표 기준 · 실제 차량 안전 시스템이 아닙니다",
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(CARD).padding(16.dp)
            .verticalScroll(rememberScrollState()).testTag(RiskAnalysisTestTags.PANEL),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Section("현재 위험 상태") { Counts(analysis) }
        Section("주요 위험") { Primary(analysis) }
        Section("연쇄 위험 분석") { Chains(analysis) }
        Section("센서 · 데이터 상태") { Sensor(analysis) }
        if (events.isNotEmpty()) Section("이벤트 발생 순서") { Events(events) }
        simulationNote?.let { Text(it, color = RadarColors.subtext, fontSize = 13.sp) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = RadarColors.subtext, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Counts(a: RiskAnalysis) {
    val stale = a.sensor.health == DataHealth.STALE || a.sensor.health == DataHealth.OUT_OF_SYNC
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(RiskAnalysisTestTags.COUNTS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (a.overall != DisplayLevel.NORMAL && !stale) { LevelIcon(a.overall, Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)) }
            val text = when {
                stale -> "판단 보류 · 데이터 신뢰 불가"
                a.overall == DisplayLevel.NORMAL || a.overall == DisplayLevel.EGO -> "강조할 위험 없음"
                else -> "전체 위험 수준: ${RiskStyle.of(a.overall).label}"
            }
            Text(text, color = levelTextColor(if (stale) DisplayLevel.UNKNOWN else a.overall), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CountTile(DisplayLevel.WARNING, a.warningCount, Modifier.weight(1f))
            CountTile(DisplayLevel.WATCH, a.watchCount, Modifier.weight(1f))
            CountTile(DisplayLevel.UNKNOWN, a.unknownCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CountTile(level: DisplayLevel, count: Int, modifier: Modifier) {
    val active = count > 0
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (active && level == DisplayLevel.WARNING) Color(RiskStyle.WARNING.fill).copy(alpha = 0.22f) else CARD_INNER)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelIcon(level, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("${count}대", color = if (active) levelTextColor(level) else RadarColors.subtext, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(RiskStyle.of(level).label, color = RadarColors.subtext, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Primary(a: RiskAnalysis) {
    val p = a.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD_INNER).padding(14.dp).testTag(RiskAnalysisTestTags.PRIMARY),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (p == null) {
            Text("현재 주의가 필요한 차량이 없습니다.", color = RadarColors.text, fontSize = 18.sp)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelIcon(p.level, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("차량 ${p.vehicleId}", color = RadarColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(RiskStyle.of(p.level).label, color = levelTextColor(p.level), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("원인: ${p.reasonText}", color = RadarColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        KeyValue("상대 위치", p.relativePosition ?: "관측 안 됨(위치 모름)")
        if (p.sourceVehicleIds.isNotEmpty()) KeyValue("영향을 준 차량", p.sourceVehicleIds.joinToString(), Color(RiskStyle.WATCH.fill))
        KeyValue("내 경로와 겹침", if (p.conflictsWithEgo) "있음(충돌 위험 영역)" else "없음")
        p.ttcS?.let { KeyValue("TTC(엔진 제공)", "%.1f초".format(it)) }
        if (p.unobserved) Text("위치가 관측되지 않아 경고 대신 판단 보류로 표시합니다.", color = Color(RiskStyle.UNKNOWN.stroke), fontSize = 15.sp)
        val rest = a.items.drop(1).take(3)
        if (rest.isNotEmpty()) {
            Spacer(Modifier.width(1.dp))
            Text("그 외 위험 차량", color = RadarColors.subtext, fontSize = 14.sp)
            rest.forEach { OtherItem(it) }
        }
    }
}

@Composable
private fun OtherItem(i: RiskItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LevelIcon(i.level, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("${i.vehicleId} · ${RiskStyle.of(i.level).label} · ${i.reasonText}" + (i.relativePosition?.let { " · $it" } ?: ""),
            color = RadarColors.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Chains(a: RiskAnalysis) {
    Column(Modifier.testTag(RiskAnalysisTestTags.CHAIN), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (a.chains.isEmpty()) {
            Text("확인된 연쇄 관계 없음", color = RadarColors.text, fontSize = 17.sp)
            Text("위험 판정 데이터에 원인 차량(source_vehicle_ids)이 있을 때만 연결합니다.", color = RadarColors.subtext, fontSize = 13.sp)
            return@Column
        }
        val levelOf = a.items.associate { it.vehicleId to it.level }
        a.chains.forEach { path ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD_INNER).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                path.forEachIndexed { i, id ->
                    if (i > 0) Text("  →  ", color = RadarColors.subtext, fontSize = 18.sp)
                    val lv = levelOf[id] ?: DisplayLevel.NORMAL
                    if (lv != DisplayLevel.NORMAL) { LevelIcon(lv, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(id, color = if (i == 0) Color(RiskStyle.WATCH.fill) else levelTextColor(lv), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("  →  내 차량", color = RadarColors.subtext, fontSize = 16.sp)
            }
            Text("최초 원인 ${path.first()} · 2차 영향 ${path.drop(1).joinToString()}", color = RadarColors.subtext, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Sensor(a: RiskAnalysis) {
    val s = a.sensor
    val (dot, color) = when (s.health) {
        DataHealth.OK -> Color(0xFF3FB27F) to RadarColors.text
        DataHealth.PARTIAL -> Color(RiskStyle.UNKNOWN.stroke) to Color(RiskStyle.UNKNOWN.stroke)
        else -> Color(0xFF8A919C) to RadarColors.text
    }
    Column(Modifier.testTag(RiskAnalysisTestTags.SENSOR), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(s.summary, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (s.missingVehicleIds.isNotEmpty()) KeyValue("관측 누락 차량", s.missingVehicleIds.joinToString())
        if (s.invalidVehicleCount > 0) KeyValue("데이터 오류 제외", "${s.invalidVehicleCount}건")
        if (s.health != DataHealth.OK) Text("이 상태에서는 위험 판단에 한계가 있습니다. 차량의 기본 안전 기능과 직접 확인을 우선하세요.",
            color = RadarColors.subtext, fontSize = 14.sp)
    }
}

@Composable
private fun Events(events: List<RiskEvent>) {
    val shown = events.takeLast(6)
    if (events.size > shown.size) Text("이전 ${events.size - shown.size}건 생략", color = RadarColors.subtext, fontSize = 13.sp)
    shown.forEach { e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%.1f초".format(e.tMs / 1000f), color = RadarColors.subtext, fontSize = 14.sp, modifier = Modifier.width(52.dp))
            LevelIcon(e.to.toDisplay(), Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${e.vehicleId} ${RiskStyle.of(e.from.toDisplay()).label} → ${RiskStyle.of(e.to.toDisplay()).label} · ${e.cause}",
                color = RadarColors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, valueColor: Color = RadarColors.text) {
    Row {
        Text(key, color = RadarColors.subtext, fontSize = 15.sp, modifier = Modifier.width(148.dp).padding(end = 8.dp))
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun levelTextColor(l: DisplayLevel): Color = when (l) {
    DisplayLevel.WARNING -> Color(RiskStyle.WARNING.fill)
    DisplayLevel.WATCH -> Color(RiskStyle.WATCH.fill)
    DisplayLevel.UNKNOWN -> Color(RiskStyle.UNKNOWN.stroke)
    else -> RadarColors.text
}

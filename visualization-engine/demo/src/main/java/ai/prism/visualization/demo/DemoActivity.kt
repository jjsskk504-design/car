package ai.prism.visualization.demo

import ai.prism.visualization.overlays.RadarColors
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.replay.ReplayJson
import ai.prism.visualization.replay.ReplayScreen
import ai.prism.visualization.replay.ReplaySession
import ai.prism.visualization.replay.rememberReplayState
import ai.prism.visualization.ui.RadarConfig
import ai.prism.visualization.ui.RadarScreen
import ai.prism.visualization.ui.RiskLegend
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import ai.prism.visualization.rendering.DataHealth
import ai.prism.visualization.rendering.RiskAnalyzer
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.rendering.ViewportConfig
import ai.prism.visualization.ui.RadarSceneView
import ai.prism.visualization.ui.RiskAnalysisPanel
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.produceState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 시각화 엔진 데모.
 * - 주행 화면: 샘플 JSON 을 실시간처럼 재생하며 DisplayMode.DRIVING 으로 표시
 * - 정차 후 리플레이: ReplayScreen
 * - 인텐트 extra: scenario=<id>, tab=drive|replay, stopped=true|false, stress=<차량 수>(FPS 측정 모드)
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stress = intent.getIntExtra("stress", 0)
        val initialScenario = ScenarioRepository.catalog.indexOfFirst { it.first == intent.getStringExtra("scenario") }.coerceAtLeast(0)
        val initialTab = if (intent.getStringExtra("tab") == "replay") 1 else 0
        val initialStopped = intent.getStringExtra("stopped")?.toBooleanStrictOrNull() ?: (initialTab == 1)
        // 스크린샷용: 특정 시점(ms)에 멈춘 화면
        val fixedTimeMs = intent.getLongExtra("t_ms", -1L).takeIf { it >= 0 }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (stress > 0) StressScreen(stress)
                else DemoApp(initialScenario, initialTab, initialStopped, fixedTimeMs)
            }
        }
    }
}

/**
 * 주행 화면 재생 상태(시연용). 10 Hz 기록을 실시간처럼 재생한다.
 * 같은 시나리오는 항상 같은 프레임 순서로 재생되므로(미리 계산된 Mock) 반복해도 결과가 같다.
 */
private class LivePlayback(initialMs: Long, playing: Boolean) {
    var clockMs by mutableLongStateOf(initialMs)
    var playing by mutableStateOf(playing)
    /** 데이터 지연 시뮬레이션을 켠 뒤 흐른 시간(재생 일시정지와 무관하게 흐름). */
    var delayMs by mutableLongStateOf(0L)
}

@Composable
private fun DemoApp(initialScenario: Int, initialTab: Int, initialStopped: Boolean, fixedTimeMs: Long? = null) {
    var scenario by rememberSaveable { mutableIntStateOf(initialScenario) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var stopped by rememberSaveable { androidx.compose.runtime.mutableStateOf(initialStopped) }
    var simulateDelay by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val context = LocalContext.current
    val (scenarioId, scenarioTitle) = ScenarioRepository.catalog[scenario]
    // JSON 파싱은 메인 스레드 밖에서, 선택한 시나리오만 지연 로딩
    val session by produceState<ReplaySession?>(null, scenarioId) {
        value = withContext(Dispatchers.IO) { ScenarioRepository.load(context, scenarioId) }
    }
    // 시나리오가 바뀌면 재생 위치·상태를 초기화(이전 시나리오 상태가 남지 않도록)
    val playback = remember(scenarioId) { LivePlayback(fixedTimeMs ?: 0L, playing = fixedTimeMs == null) }
    val s = session?.takeIf { it.id == scenarioId }

    val delayOn by rememberUpdatedState(simulateDelay)
    LaunchedEffect(scenarioId, simulateDelay) { playback.delayMs = 0L }
    // 주행 화면용 루프: 재생 중이면 시계를 진행(끝나면 1.5초 멈춘 뒤 처음부터 반복),
    // 지연 시뮬레이션 중이면 지연 시간을 누적. 고정 시점(t_ms) 캡처 모드에서는 돌지 않는다.
    if (s != null && tab == 0 && fixedTimeMs == null) {
        LaunchedEffect(s.id) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val dt = (now - last) / 1_000_000
                if (playback.playing) playback.clockMs = (playback.clockMs + dt) % (s.durationMs + 1500)
                if (delayOn) playback.delayMs += dt
                last = now
            }
        }
    }
    // 데이터는 10 Hz 이므로 화면도 100 ms 단위로만 갱신(매 vsync 재구성 방지)
    val tick by remember(scenarioId) { derivedStateOf { playback.clockMs / 100 } }
    val clockQ = tick * 100
    // 데이터 지연 시뮬레이션: 켠 순간의 프레임에 멈추고 시계만 흐르게 한다
    val frozenAt = remember(scenarioId, simulateDelay) { clockQ.coerceAtMost(session?.durationMs ?: Long.MAX_VALUE) }
    val delayTick by remember(scenarioId) { derivedStateOf { playback.delayMs / 100 } }

    // 주행 화면의 장면·분석(헤더의 데이터 상태와 오른쪽 패널이 같은 값을 쓰도록 한 번만 계산)
    val live = if (s != null && tab == 0) {
        val dataT = if (simulateDelay) frozenAt else clockQ.coerceAtMost(s.durationMs)
        val frame = s.frames[s.indexAt(dataT)]
        // 현재 시각: 지연 중이면 멈춘 프레임 시각 + 지연 경과 시간(→ 500ms 넘으면 SceneBuilder 가 지연으로 판정)
        val nowMs = s.startMs + if (simulateDelay) frozenAt + delayTick * 100 else dataT
        val config = demoRadarConfig(s)
        val scene = remember(frame, nowMs) { SceneBuilder.build(frame.vehicle, frame.prediction, frame.risk, DisplayMode.DRIVING, nowMs, config.sceneOptions) }
        val analysis = remember(scene) { RiskAnalyzer.analyze(scene, frame.risk, s.road) }
        Triple(scene, analysis, config)
    } else null

    Column(Modifier.fillMaxSize().background(RadarColors.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // A. 상단 헤더(운전자 화면)
        DemoHeader(
            scenarioTitle = scenarioTitle,
            running = if (tab == 0) playback.playing else null,
            dataStatus = when {
                tab == 1 -> "기록 재생"
                live == null -> "불러오는 중"
                else -> live.second.sensor.summary
            },
            dataOk = live?.second?.sensor?.health == DataHealth.OK,
        )
        Spacer(Modifier.height(10.dp))

        // B·C. 도로 시각화 + 위험 분석 패널
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s == null -> Text("시나리오 불러오는 중…", color = RadarColors.subtext, fontSize = 18.sp)
                tab == 1 -> {
                    val st = rememberReplayState(s.id, autoPlay = fixedTimeMs == null)
                    LaunchedEffect(s.id) { if (fixedTimeMs != null) st.positionMs = fixedTimeMs }
                    ReplayScreen(s, Modifier.fillMaxSize(), isVehicleStopped = stopped, state = st)
                }
                live != null -> {
                    val (scene, analysis, config) = live
                    // 이벤트 목록은 화면에 그려진 데이터 시점까지만(지연 중 멈춘 프레임 이후 이벤트가 보이지 않도록)
                    val events = remember(s.id) { s.events() }.filter { it.tMs <= (scene.timestampMs?.minus(s.startMs) ?: clockQ) }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wide = maxWidth >= 900.dp
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                RadarSceneView(scene, Modifier.weight(0.45f).fillMaxHeight(), config)
                                RiskAnalysisPanel(analysis, Modifier.weight(0.55f).fillMaxHeight(), events)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                RadarSceneView(scene, Modifier.weight(0.55f).fillMaxWidth(), config)
                                RiskAnalysisPanel(analysis, Modifier.weight(0.45f).fillMaxWidth(), events)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 시연 조작부(운전자 화면과 분리)
        DemoControls(
            scenario = scenario, onScenario = { scenario = it },
            tab = tab, onTab = { tab = it },
            stopped = stopped, onStopped = { stopped = it },
            simulateDelay = simulateDelay, onDelay = { simulateDelay = it },
            liveControls = if (tab == 0 && s != null) LiveControls(
                playing = playback.playing,
                timeText = "%.1f / %.1f초".format(clockQ.coerceAtMost(s.durationMs) / 1000f, s.durationMs / 1000f),
                onPlayPause = { playback.playing = !playback.playing },
                onNextEvent = {
                    val events = s.events()
                    val next = events.firstOrNull { it.tMs > playback.clockMs + 50 } ?: events.firstOrNull()
                    playback.playing = false
                    playback.clockMs = next?.tMs ?: 0L
                },
                onReset = { playback.clockMs = 0L },
            ) else null,
        )
    }
}

/** 주행 화면 도로: 패널 폭이 줄어든 대신 도로가 패널 폭의 80%까지 쓰도록 확대. */
private fun demoRadarConfig(s: ReplaySession) = RadarConfig(
    road = s.road,
    viewport = ViewportConfig(roadWidthFraction = 0.80f),
)

@Composable
private fun DemoHeader(scenarioTitle: String, running: Boolean?, dataStatus: String, dataOk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("RISK RADAR 2.0", color = RadarColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("연쇄 위험 시각화 · PRISM", color = RadarColors.subtext, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        when (running) {
            true -> HeaderPill("● 작동 중", Color(0xFF3FB27F))
            false -> HeaderPill("❚❚ 일시정지", RadarColors.subtext)
            null -> HeaderPill("■ 정차 후 리플레이", RadarColors.subtext)
        }
        HeaderPill("시뮬레이션 · Mock JSON", Color(0xFFB8C0CC))
        HeaderPill("시나리오: $scenarioTitle", RadarColors.text)
        HeaderPill("데이터: ${dataStatus.removePrefix("데이터 ")}", if (dataOk) Color(0xFF3FB27F) else Color(0xFF7DB8FF))
    }
}

@Composable
private fun HeaderPill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1F242C)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private class LiveControls(
    val playing: Boolean,
    val timeText: String,
    val onPlayPause: () -> Unit,
    val onNextEvent: () -> Unit,
    val onReset: () -> Unit,
)

/** 시연용 조작부. 실제 운전자 화면에는 들어가지 않는 영역이라 배경·테두리로 구분한다. */
@Composable
private fun DemoControls(
    scenario: Int, onScenario: (Int) -> Unit,
    tab: Int, onTab: (Int) -> Unit,
    stopped: Boolean, onStopped: (Boolean) -> Unit,
    simulateDelay: Boolean, onDelay: (Boolean) -> Unit,
    liveControls: LiveControls?,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C0E11))
            .border(1.dp, Color(0xFF2E343D), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("시연 조작부 · 운전자 화면 아님", color = RadarColors.subtext, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())) {
            ScenarioRepository.catalog.forEachIndexed { i, (_, title) ->
                FilterChip(selected = i == scenario, onClick = { onScenario(i) }, label = { Text(title, fontSize = 16.sp) },
                    modifier = Modifier.height(48.dp))
            }
            Spacer(Modifier.width(12.dp))
            FilterChip(selected = tab == 0, onClick = { onTab(0) }, label = { Text("주행 화면", fontSize = 16.sp) }, modifier = Modifier.height(48.dp))
            FilterChip(selected = tab == 1, onClick = { onTab(1) }, label = { Text("정차 후 리플레이", fontSize = 16.sp) }, modifier = Modifier.height(48.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())) {
            if (liveControls != null) {
                Button(onClick = liveControls.onPlayPause, modifier = Modifier.height(48.dp)) {
                    Text(if (liveControls.playing) "일시정지" else "재생", fontSize = 16.sp)
                }
                FilledTonalButton(onClick = liveControls.onNextEvent, modifier = Modifier.height(48.dp)) { Text("다음 위험 이벤트", fontSize = 16.sp) }
                FilledTonalButton(onClick = liveControls.onReset, modifier = Modifier.height(48.dp)) { Text("처음으로", fontSize = 16.sp) }
                Text(liveControls.timeText, color = RadarColors.text, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text("차량 정차(리플레이 허용)", color = RadarColors.subtext, fontSize = 15.sp)
            Switch(checked = stopped, onCheckedChange = onStopped)
            if (tab == 0) {
                Spacer(Modifier.width(4.dp))
                Text("데이터 지연 시뮬레이션", color = RadarColors.subtext, fontSize = 15.sp)
                Switch(checked = simulateDelay, onCheckedChange = onDelay)
            }
        }
    }
}

object ScenarioRepository {
    private const val DIR = "scenarios"
    val catalog = listOf(
        "normal" to "정상 주행", "front_brake" to "전방 급감속", "lane_change" to "차선 변경",
        "chain" to "연쇄 위험", "sensor_dropout" to "센서 누락",
    )
    private val cache = ConcurrentHashMap<String, ReplaySession>()

    /** assets/scenarios/<id>.json 을 읽는다. 없거나 깨졌으면 Mock 생성기로 대체한다. */
    fun load(context: android.content.Context, id: String): ReplaySession = cache.getOrPut(id) {
        runCatching {
            val parsed = ReplayJson.parse(context.assets.open("$DIR/$id.json").bufferedReader().use { it.readText() })
            if (parsed.warnings.isNotEmpty()) Log.w("PRISM_VIZ", "$id: ${parsed.warnings.size} warnings, first=${parsed.warnings.first()}")
            parsed.session
        }.onFailure { Log.e("PRISM_VIZ", "failed to load $id", it) }
            .getOrElse { MockScenarios.all().first { it.id == id } }
    }
}

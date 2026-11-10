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

@Composable
private fun DemoApp(initialScenario: Int, initialTab: Int, initialStopped: Boolean, fixedTimeMs: Long? = null) {
    var scenario by rememberSaveable { mutableIntStateOf(initialScenario) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var stopped by rememberSaveable { androidx.compose.runtime.mutableStateOf(initialStopped) }
    var simulateDelay by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val context = LocalContext.current
    val scenarioId = ScenarioRepository.catalog[scenario].first
    // JSON 파싱은 메인 스레드 밖에서, 선택한 시나리오만 지연 로딩
    val session by produceState<ReplaySession?>(null, scenarioId) {
        value = withContext(Dispatchers.IO) { ScenarioRepository.load(context, scenarioId) }
    }

    Column(Modifier.fillMaxSize().background(RadarColors.background).safeDrawingPadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("PRISM 시각화", color = RadarColors.text, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            ScenarioRepository.catalog.forEachIndexed { i, (_, title) ->
                FilterChip(selected = i == scenario, onClick = { scenario = i }, label = { Text(title) })
            }
            Spacer(Modifier.width(12.dp))
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("주행 화면") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("정차 후 리플레이") })
            Spacer(Modifier.width(8.dp))
            Text("정차", color = RadarColors.subtext, fontSize = 16.sp)
            Switch(checked = stopped, onCheckedChange = { stopped = it })
            if (tab == 0) {
                Text("지연 시뮬", color = RadarColors.subtext, fontSize = 16.sp)
                Switch(checked = simulateDelay, onCheckedChange = { simulateDelay = it })
            }
        }
        Spacer(Modifier.padding(4.dp))
        val s = session
        if (s == null || s.id != scenarioId) Text("시나리오 불러오는 중…", color = RadarColors.subtext)
        else if (tab == 0) LiveDriving(s, simulateDelay, Modifier.weight(1f), fixedTimeMs)
        else {
            val st = rememberReplayState(s.id, autoPlay = fixedTimeMs == null)
            LaunchedEffect(s.id) { if (fixedTimeMs != null) st.positionMs = fixedTimeMs }
            ReplayScreen(s, Modifier.weight(1f), isVehicleStopped = stopped, state = st)
        }
    }
}

/** 실시간 화면 흉내: 10 Hz 프레임을 반복 재생. 지연 시뮬 시 차량 프레임을 멈추고 시계만 흐르게 한다. */
@Composable
private fun LiveDriving(session: ReplaySession, simulateDelay: Boolean, modifier: Modifier, fixedTimeMs: Long? = null) {
    var clockMs by remember(session.id) { mutableLongStateOf(fixedTimeMs ?: 0L) }
    var frozenAt by remember(session.id, simulateDelay) { mutableLongStateOf(clockMs) }
    LaunchedEffect(session.id) {
        if (fixedTimeMs != null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            clockMs = (clockMs + (now - last) / 1_000_000) % (session.durationMs + 1500)
            last = now
        }
    }
    // 데이터는 10 Hz 이므로 화면도 100 ms 단위로만 갱신(매 vsync 재구성 방지)
    val tick by remember(session.id) { derivedStateOf { clockMs / 100 } }
    val clockQ = tick * 100
    val dataT = if (simulateDelay) frozenAt else clockQ
    val frame = session.frames[session.indexAt(dataT)]
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadarScreen(
            vehicleFrame = frame.vehicle,
            predictionFrame = frame.prediction,
            riskFrame = frame.risk,
            mode = DisplayMode.DRIVING,
            config = RadarConfig(road = session.road),
            nowMs = session.startMs + clockQ.coerceAtMost(if (simulateDelay) Long.MAX_VALUE else session.durationMs),
            modifier = Modifier.weight(1.3f).fillMaxHeight(),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(session.title, color = RadarColors.text, fontSize = 24.sp)
            Text(session.description, color = RadarColors.subtext, fontSize = 17.sp)
            Text("주행 모드: 위험 차량과 그 예측 경로만 강조합니다. 상세 원인은 정차 후 리플레이에서 확인합니다.",
                color = RadarColors.subtext, fontSize = 15.sp)
            RiskLegend()
            Text("Mock JSON 기반 시연입니다. 실제 위험 판정 결과가 아니며 차량 안전 시스템을 대체하지 않습니다.",
                color = RadarColors.subtext, fontSize = 13.sp)
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

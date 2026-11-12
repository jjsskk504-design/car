package com.prism.vehicle.app

import android.app.Application
import android.content.res.AssetManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prism.vehicle.adapters.Gear
import com.prism.vehicle.adapters.engineLoader
import com.prism.vehicle.adapters.LoadResult
import com.prism.vehicle.adapters.SupportStatus
import com.prism.vehicle.adapters.SdkFeature
import com.prism.vehicle.adapters.TextSource
import com.prism.vehicle.adapters.VehicleSdkProvider
import com.prism.vehicle.adapters.VehicleSdkState
import com.prism.vehicle.replay.Issue
import com.prism.vehicle.replay.ReplayClock
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.ScenarioTimeline
import com.prism.vehicle.sdk.PleosVehicleSdkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

enum class AppMode(val label: String) {
    SIMULATION("Simulation Mode"),
    PLEOS_CONTEXT("Pleos Context Mode"),
    ADAS("ADAS Mode"),
}

enum class Screen { SELECT, DRIVE, REPLAY, ERROR }

data class UiState(
    val mode: AppMode = AppMode.SIMULATION,
    val screen: Screen = Screen.SELECT,
    val scenarioIds: List<String> = emptyList(),
    val loading: Boolean = false,
    val timeline: ScenarioTimeline? = null,
    val failedScenario: String? = null,
    val issues: List<Issue> = emptyList(),
    val clock: ReplayClock = ReplayClock(durationMs = 0),
    /** Simulation Mode 에서는 실제 기어가 없으므로 정차(P)를 화면 버튼으로 가정한다. */
    val simulatedParked: Boolean = false,
    val sdk: VehicleSdkState = VehicleSdkState(),
) {
    val parked: Boolean
        get() = when (mode) {
            AppMode.SIMULATION -> simulatedParked
            AppMode.PLEOS_CONTEXT -> sdk.gear == Gear.PARK
            AppMode.ADAS -> false
        }

    val replayBlockedReason: String?
        get() = when {
            parked -> null
            mode == AppMode.SIMULATION -> "정차(P) 상태에서만 리플레이를 열 수 있습니다. '정차 가정' 을 켜세요."
            sdk.features[SdkFeature.GEAR]?.status != SupportStatus.CONFIRMED ->
                "기어 상태를 읽지 못해 정차 여부를 확인할 수 없습니다 (${sdk.features[SdkFeature.GEAR]?.detail})"
            else -> "현재 기어 ${sdk.gear}. P단에서만 리플레이를 열 수 있습니다."
        }
}

class PrismViewModel(app: Application) : AndroidViewModel(app) {
    private val engines = engineLoader(AssetTextSource(app.assets)) // 파일 + 01·02 엔진
    private val providers = engines.first
    private val loader = engines.second
    private val pleos: VehicleSdkProvider = PleosVehicleSdkProvider(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var ticker: Job? = null
    private var lastLevel: RiskLevel? = null
    private var foreground = false

    init {
        viewModelScope.launch { pleos.state.collect { s -> _ui.update { it.copy(sdk = s) } } }
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) { providers.scenarioIds() }
            _ui.update { it.copy(scenarioIds = ids) }
        }
    }

    fun setMode(mode: AppMode) {
        if (mode == AppMode.ADAS) return // 공식 권한 확인 전 비활성
        _ui.update { it.copy(mode = mode) }
        if (mode == AppMode.PLEOS_CONTEXT && foreground) pleos.start() else pleos.stop()
    }

    fun openScenario(id: String) {
        pause()
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { loader.load(id) }
            _ui.update {
                when (result) {
                    is LoadResult.Ready -> it.copy(
                        loading = false, screen = Screen.DRIVE, timeline = result.timeline, failedScenario = null,
                        issues = result.timeline.issues, clock = ReplayClock(durationMs = result.timeline.durationMs),
                    )
                    is LoadResult.Failed -> it.copy(
                        loading = false, screen = Screen.ERROR, timeline = null, failedScenario = id, issues = result.issues,
                    )
                }
            }
            lastLevel = null
        }
    }

    fun back() {
        pause()
        _ui.update { it.copy(screen = if (it.screen == Screen.REPLAY) Screen.DRIVE else Screen.SELECT) }
    }

    fun setSimulatedParked(parked: Boolean) {
        _ui.update { it.copy(simulatedParked = parked) }
        if (!parked && _ui.value.screen == Screen.REPLAY) back()
    }

    fun openReplay() {
        if (_ui.value.replayBlockedReason != null) return
        pause()
        _ui.update { it.copy(screen = Screen.REPLAY) }
    }

    fun play() {
        _ui.update { it.copy(clock = it.clock.play()) }
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive && _ui.value.clock.playing) {
                delay(FRAME_MS)
                val now = SystemClock.elapsedRealtime()
                _ui.update { it.copy(clock = it.clock.advance(now - last)) }
                last = now
                announceWarningIfNeeded()
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        _ui.update { it.copy(clock = it.clock.pause()) }
    }

    fun seek(ms: Long) = _ui.update { it.copy(clock = it.clock.seek(ms)) }
    fun setSpeed(s: Float) = _ui.update { it.copy(clock = it.clock.withSpeed(s)) }
    fun speakTest() = pleos.speak("PRISM 음성 안내 테스트입니다")

    /** 주행 화면에서 WARNING 으로 바뀌는 순간 한 번만 음성 안내 (Pleos Context Mode). */
    private fun announceWarningIfNeeded() {
        val s = _ui.value
        val level = s.timeline?.snapshotAt(s.clock.positionMs)?.topLevel
        if (s.mode == AppMode.PLEOS_CONTEXT && s.screen == Screen.DRIVE && level == RiskLevel.WARNING && lastLevel != RiskLevel.WARNING) {
            pleos.speak("주변 차량 위험 상황입니다. 시뮬레이션 안내입니다.")
        }
        lastLevel = level
    }

    /** Activity onStart/onStop 에 맞춰 SDK 세션을 열고 닫는다(백그라운드에서 리스너가 남지 않게). */
    fun onForeground() {
        foreground = true
        if (_ui.value.mode == AppMode.PLEOS_CONTEXT) pleos.start()
    }

    fun restartSdk() {
        pleos.stop()
        if (_ui.value.mode == AppMode.PLEOS_CONTEXT && foreground) pleos.start()
    }

    fun onBackground() {
        foreground = false
        pause()
        pleos.stop()
    }

    override fun onCleared() {
        pleos.stop()
    }

    private companion object { const val FRAME_MS = 33L }
}

class AssetTextSource(private val assets: AssetManager) : TextSource {
    override fun read(path: String): String? = try {
        assets.open(path).bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }
}

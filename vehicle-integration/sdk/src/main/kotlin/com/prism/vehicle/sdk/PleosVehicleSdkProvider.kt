package com.prism.vehicle.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.prism.vehicle.adapters.Gear
import com.prism.vehicle.adapters.Location
import com.prism.vehicle.adapters.SdkFeature
import com.prism.vehicle.adapters.SupportStatus
import com.prism.vehicle.adapters.VehicleSdkProvider
import com.prism.vehicle.adapters.VehicleSdkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Context Mode 용 어댑터. 읽기 전용(기어·위치·내비 상태)과 TTS 만 사용한다.
 * 수명주기: initialize → capability/read → register listener … unregister → release (공식 샘플 README 순서).
 * 모든 SDK 호출은 실패해도 앱을 죽이지 않고 해당 기능 상태를 FAILED 로 남긴다.
 */
class PleosVehicleSdkProvider(context: Context) : VehicleSdkProvider {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VehicleSdkState())
    override val state: StateFlow<VehicleSdkState> = _state.asStateFlow()

    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var navi: NaviHelper? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun mark(f: SdkFeature, s: SupportStatus, detail: String = "") {
        Log.i(TAG, "$f -> $s $detail")
        _state.update { it.with(f, s, detail) }
    }

    private fun fail(f: SdkFeature, e: Throwable) =
        mark(f, SupportStatus.FAILED, "${e.javaClass.simpleName}: ${e.message}")

    // 같은 인스턴스로 register/unregister 해야 한다.
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(exception: Exception) = fail(SdkFeature.GEAR, exception)
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            if (data == null) return
            _state.update { it.copy(location = Location(data.latitude, data.longitude, data.heading, data.timestampMillis)) }
            mark(SdkFeature.FUSED_LOCATION, SupportStatus.CONFIRMED, "lat=%.5f lon=%.5f".format(data.latitude, data.longitude))
        }
        override fun onError(exception: Exception) = fail(SdkFeature.FUSED_LOCATION, exception)
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            _state.update { it.copy(navigationStatus = status.toString()) }
            mark(SdkFeature.NAVI_HELPER, SupportStatus.CONFIRMED, "onNavigationStatus=${status.toString()}")
        }
        override fun onError(errorCode: NaviErrorCode) =
            mark(SdkFeature.NAVI_HELPER, SupportStatus.FAILED, "NaviErrorCode=${errorCode.toString()}")
    }

    override fun start() {
        startVehicle()
        startFusedLocation()
        startNavi()
        startTts()
    }

    private fun startVehicle() {
        if (vehicle != null) return
        mark(SdkFeature.VEHICLE_SERVICE, SupportStatus.PENDING)
        val v = runCatching { Vehicle(appContext).also { it.initialize() } }
            .onFailure { fail(SdkFeature.VEHICLE_SERVICE, it); mark(SdkFeature.GEAR, SupportStatus.FAILED, "Vehicle 세션 없음") }
            .getOrNull() ?: return
        vehicle = v
        mark(SdkFeature.VEHICLE_SERVICE, SupportStatus.CONFIRMED, "initialize() 완료")
        mark(SdkFeature.GEAR, SupportStatus.PENDING)
        runCatching {
            val gear = v.getGear()
            gear.getCurrentGearSelectionState({ onGear(it) }, { fail(SdkFeature.GEAR, it) })
            gear.registerGearSelection(gearListener)
        }.onFailure { fail(SdkFeature.GEAR, it) }
    }

    private fun onGear(g: VehicleGear?) {
        val mapped = when (g) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            VehicleGear.GEAR_UNKNOWN, null -> Gear.UNKNOWN
            VehicleGear.GEAR_DRIVE -> Gear.DRIVE
            else -> Gear.OTHER
        }
        _state.update { it.copy(gear = mapped) }
        mark(SdkFeature.GEAR, SupportStatus.CONFIRMED, g?.name ?: "null")
    }

    private fun startFusedLocation() {
        if (fused != null) return
        mark(SdkFeature.FUSED_LOCATION, SupportStatus.PENDING)
        runCatching {
            val f = FusedLocation(appContext)
            f.initialize()
            fused = f
            if (!f.isSupported()) {
                mark(SdkFeature.FUSED_LOCATION, SupportStatus.UNSUPPORTED, "isSupported()=false")
                return
            }
            f.getLastKnownLocation(locationListener)
            f.registerFusedLocationCallback(locationListener)
        }.onFailure { fail(SdkFeature.FUSED_LOCATION, it) }
    }

    private fun startNavi() {
        if (navi != null) return
        mark(SdkFeature.NAVI_HELPER, SupportStatus.PENDING)
        runCatching {
            val n = NaviHelper(appContext)
            navi = n
            n.initialize(
                {
                    runCatching {
                        n.addListener(naviListener)
                        n.getRouteStateInfo()
                        mark(SdkFeature.NAVI_HELPER, SupportStatus.CONFIRMED, "initialize 성공, 상태 응답 대기")
                    }.onFailure { fail(SdkFeature.NAVI_HELPER, it) }
                },
                { fail(SdkFeature.NAVI_HELPER, it) },
            )
        }.onFailure { fail(SdkFeature.NAVI_HELPER, it) }
    }

    private fun startTts() {
        if (tts != null) return
        mark(SdkFeature.TEXT_TO_SPEECH, SupportStatus.PENDING)
        runCatching {
            val t = TextToSpeech(appContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ttsReady = true
                    mark(SdkFeature.TEXT_TO_SPEECH, SupportStatus.CONFIRMED, "ON_DEVICE initialize 성공")
                }
                override fun onFailure(error: ErrorType) =
                    mark(SdkFeature.TEXT_TO_SPEECH, SupportStatus.FAILED, "ErrorType=${error.name}")
            })
        }.onFailure { fail(SdkFeature.TEXT_TO_SPEECH, it) }
    }

    override fun speak(text: String): Boolean {
        val t = tts ?: return false
        if (!ttsReady) return false
        return runCatching { t.speak(text, true) }.onFailure { fail(SdkFeature.TEXT_TO_SPEECH, it) }.isSuccess
    }

    override fun stop() {
        vehicle?.let { v ->
            runCatching { v.getGear().unregisterGearSelection(gearListener) }
            runCatching { v.release() }
        }
        vehicle = null
        fused?.let { f ->
            runCatching { f.unregisterFusedLocationCallback(locationListener) }
            runCatching { f.release() }
        }
        fused = null
        navi?.let { n ->
            runCatching { n.removeListener(naviListener) }
            runCatching { n.release() }
        }
        navi = null
        tts?.let { t -> runCatching { t.stop() }; runCatching { t.release() } }
        tts = null
        ttsReady = false
        Log.i(TAG, "released all Pleos SDK sessions")
    }

    private companion object { const val TAG = "PrismPleosSdk" }
}

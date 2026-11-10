package com.prism.vehicle.adapters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 실제 차량(Pleos SDK)과의 경계. 주변 차량 위험 데이터는 여기서 오지 않는다. */
interface VehicleSdkProvider {
    val state: StateFlow<VehicleSdkState>

    /** SDK 연결·capability 확인·리스너 등록. 실패는 상태로만 보고하고 예외를 던지지 않는다. */
    fun start()

    /** 리스너 해제 후 세션 release. 여러 번 불려도 안전해야 한다. */
    fun stop()

    /** 안내 음성. 지원 안 되면 false. */
    fun speak(text: String): Boolean
}

enum class SdkFeature(val label: String) {
    VEHICLE_SERVICE("Vehicle SDK 연결"),
    GEAR("차량 상태: 기어(P단 판정)"),
    FUSED_LOCATION("FusedLocation 위치"),
    NAVI_HELPER("NaviHelper 내비 연동"),
    TEXT_TO_SPEECH("TextToSpeech 음성 안내"),
    ADAS_OBJECTS("ADAS 주변 차량 객체"),
}

enum class SupportStatus {
    /** 실제 호출이 성공함 */
    CONFIRMED,
    /** 호출했으나 실패(서비스 없음·권한 거부·오류) */
    FAILED,
    /** 공개 SDK 에 API 가 없거나 권한이 확인되지 않아 구현하지 않음 */
    UNSUPPORTED,
    /** 아직 호출하지 않음 */
    UNTESTED,
    PENDING,
}

data class FeatureStatus(val status: SupportStatus, val detail: String = "")

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, OTHER, UNKNOWN }

data class Location(val latitude: Double, val longitude: Double, val headingDeg: Float, val timestampMs: Long)

data class VehicleSdkState(
    val features: Map<SdkFeature, FeatureStatus> = SdkFeature.entries.associateWith { FeatureStatus(SupportStatus.UNTESTED) } +
        (SdkFeature.ADAS_OBJECTS to ADAS_UNSUPPORTED),
    val gear: Gear = Gear.UNKNOWN,
    val location: Location? = null,
    val navigationStatus: String? = null,
) {
    fun with(feature: SdkFeature, status: SupportStatus, detail: String = "") =
        copy(features = features + (feature to FeatureStatus(status, detail)))

    companion object {
        val ADAS_UNSUPPORTED = FeatureStatus(
            SupportStatus.UNSUPPORTED,
            "공개 Vehicle SDK 2.3.2 에 주변 차량 객체 API 없음. 공식 권한 확인 전까지 ADAS Mode 비활성",
        )
    }
}

/** Simulation Mode: SDK 를 전혀 호출하지 않는다. */
class NoVehicleSdkProvider : VehicleSdkProvider {
    private val _state = MutableStateFlow(VehicleSdkState())
    override val state: StateFlow<VehicleSdkState> = _state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
    override fun speak(text: String) = false
}

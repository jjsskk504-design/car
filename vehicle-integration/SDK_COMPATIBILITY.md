# Pleos SDK 호환성 (실측)

- 확인일: 2026-11-10
- 환경: AVD `Pleos_Connect_v2.1.0` (Android 14 / API 34, system image `android-34/pleos_car_emulator`, 2560×1440)
- 확인 방법: 앱의 Pleos Context Mode 에서 실제 SDK 호출 → `adb logcat -s PrismPleosSdk` 와 앱 내 SDK 상태 패널(`tests/reports/screenshots/02_pleos_context_sdk_confirmed.png`)
- 실차: **미확인**. 아래는 모두 에뮬레이터 결과다.

## SDK별 상태

| 기능 | SDK (Maven `ai.pleos.playground`) | 필요 권한 (실측) | 상태 | 실제 결과 |
|---|---|---|---|---|
| Vehicle 서비스 연결 | `Vehicle:2.3.2` | `VEHICLE_BINDING` (AAR 이 선언) | **CONFIRMED** | `initialize()` 완료 |
| 차량 상태: 기어 | `Vehicle:2.3.2` `Gear` | `pleos.car.permission.CAR_DRIVE` | **CONFIRMED** | `getCurrentGearSelectionState` / `registerGearSelection` → `GEAR_PARK` |
| 위치 | `FusedLocation:1.1.3` | `pleos.car.permission.FUSED_LOCATION` (**dangerous**, 런타임 동의) | **CONFIRMED** | `lat=37.42200 lon=-122.08400` (에뮬레이터 기본 위치) |
| 내비 연동 | `NaviHelper:3.0.5` | `pleos.car.permission.NAVI_ROUTE` | **CONFIRMED** (초기화) | `initialize` 성공, `getRouteStateInfo()` 요청. 경로 안내 중이 아니라 상태 이벤트 응답은 미수신 |
| 음성 안내 | `TextToSpeech:3.3.1` (`Mode.ON_DEVICE`) | `pleos.car.permission.TTS_SERVICE` | **CONFIRMED** | `initialize` 성공, `speak()` → 서비스 로그 `onReady` → `onStart` |
| ADAS 주변 차량 객체 | 공개 SDK **없음** | (`ADAS_TRACKED_OBJECT` 권한은 에뮬레이터에 정의됨) | **UNSUPPORTED** | 구현하지 않음. ADAS Mode 비활성 |
| Pleos Emulator | — | — | **CONFIRMED** (조건부) | 앱 설치·실행 OK. 단 에뮬레이터 자체 HAL 서비스가 반복 crash 하며 시스템 UI 가 재시작됨(아래) |

## 권한 시행착오 (기록)

1. 처음 선언한 `CAR_POWERTRAIN`, `CAR_SPEED` 는 이 에뮬레이터에 **정의돼 있지 않다**. 기어는 `SecurityException: getCurrentGearSelectionState need Permission` 으로 실패했다.
2. 권한 없이 호출했을 때의 메시지: FusedLocation `need Permission pleos.car.permission.FUSED_LOCATION`, NaviHelper `Permission [pleos.car.permission.NAVI_ROUTE] is required`, TTS `Permission pleos.car.permission.TTS_SERVICE is required`.
3. `CAR_DRIVE` / `NAVI_ROUTE` / `TTS_SERVICE` 는 normal 권한이라 설치만으로 승인됐다.
4. `FUSED_LOCATION` 은 dangerous 권한이다. 앱이 `ActivityResultContracts.RequestPermission` 으로 요청하면 `GrantPermissionsActivity` 가 뜨지만, 이 차량 홈 화면에서는 동의 창이 앞에 보이지 않았다. 검증은 `adb shell pm grant --user 10 com.prism.vehicle.app pleos.car.permission.FUSED_LOCATION` 으로 했다. **실차·심사 환경에서 동의 창이 정상 표시되는지는 미확인.**
5. 차량 제어 권한(`CONTROL_*`)과 `PRIVATE_*` 는 선언하지 않았다. 앱은 차량을 제어하지 않는다.

## 공식 문서(document.pleos.ai) 대조

| 항목 | 문서 내용 | 이 앱 |
|---|---|---|
| 권한 정책 | 권한은 Pleos App Market 과 사전 협의·승인된 범위에서만 사용. 승인 안 된 권한을 선언하거나 해당 API 를 호출하면 **앱 심사 불통과** | 실제 호출하는 API 의 권한만 선언 (`CAR_INFO` 제거) |
| Vehicle 권한 표 | 조회 `CAR_*`, 제어 `CONTROL_CAR_*`. DrivingMode·Brake·Odometer = `CAR_DRIVE` | `CAR_DRIVE` 만 선언 |
| Gear | **공식 도메인 표·가이드에 없음** (공식 샘플도 Gear 제외) | 에뮬레이터에서 `CAR_DRIVE` 로 동작. **심사 전 Gear 사용 승인 확인 필요**. 승인 안 되면 Pleos Context Mode 의 정차 판정을 다른 공개 API 로 바꿔야 함 |
| FusedLocation | `FUSED_LOCATION` (dangerous). `getLastKnownLocation` 은 null 가능, 콜백 null·`onError` 처리. 개인정보 설정으로 차단 시 `showLocationAccessBlockedPopup()` 으로 안내 가능 | null 무시, onError → FAILED 표시 |
| NaviHelper | `get*Info()` = `NAVI_ROUTE`, 경로 요청 = `NAVI_ROUTE_SEARCH`, 화면 = `NAVI_CUSTOM_MAP` | 조회만 하므로 `NAVI_ROUTE` 만 |
| TextToSpeech | `TTS_SERVICE`. SERVER/HYBRID 는 Client ID·Secret 으로 `registerApp` 필요, **ON_DEVICE 는 등록 불필요** | ON_DEVICE 사용, 키 없음 |
| ADAS | Pleos Connect SDK(Android) 모듈은 Vehicle·NaviHelper·TTS·STT·FusedLocation 5종뿐. ADAS API 는 별도 SDV OS API 문서에만 있음 | ADAS Mode 비활성 유지 |

## ADAS 관련 발견 (팀 공유용)

에뮬레이터에 다음이 존재한다.

- 서비스 패키지: `ai.pleos.playground.service.adas`, HAL `ai.pleos.hardware.adas-V1-sdv-service`
- 권한 정의(normal): `pleos.car.permission.ADAS_TRACKED_OBJECT`, `ADAS_DRIVING_WARN`, `ADAS_LOCAL_MAP`, `ADAS_PARKING`

하지만 Pleos Maven(`nexus-playground.pleos.ai`)에 공개된 아티팩트는 `Vehicle`, `FusedLocation`, `NaviHelper`, `TextToSpeech`, `SpeechToText`, `LLM` 과 그 aidl/data 뿐이고 ADAS SDK 는 없다. 지시서대로 **공식 접근 권한 확인 전까지 ADAS Mode 는 비활성**이며, 이 권한이 일반 개발자에게 허용되는지 주최 측 확인이 필요하다.

## 에뮬레이터 불안정 (앱과 무관)

검증 중 `ai.pleos.hardware.profile.exe`, `ai.pleos.hardware.adas-V1-sdv-service`, `placesense-sdv-service`, `vehicle_diagnostic-V1-sdv-service` 가 SIGABRT 로 반복 종료되며 시스템 UI 가 두 번 재시작됐다. `logcat -b crash` 에 `com.prism.vehicle.app` 의 crash 는 없었다. 재시작 후에도 앱 상태(재생 위치·정차 가정)는 유지됐다.

## 공식 샘플과의 차이

- 공식 샘플 `pleos-sample-vehicle-app` 의 `libs.versions.toml` 은 `vehicleSdk = "2.3.1"` 인데 Maven 에 2.3.1 이 없어 원본이 그대로는 빌드되지 않는다(README 는 2.3.2). 실험 폴더에서만 2.3.2 로 바꾸면 `assembleDebug` 성공.
- 샘플 README 의 수명주기(initialize → capability → read → register … unregister → release)를 따랐다. 샘플에 LICENSE 가 없어 코드는 복사하지 않았다.

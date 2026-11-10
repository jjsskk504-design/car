# PRISM vehicle-integration (PART 05 · Android/Pleos SDK 통합)

PRISM 4개 독립 엔진의 결과(JSON)를 하나의 Android 차량용 앱에서 **오프라인 재생**하고, Pleos SDK 로 확인된 차량 상태·위치만 연동하는 앱이다.

> 이 앱은 기존 차량 안전 시스템을 대체하는 실시간 충돌 방지 장치가 아니다. 화면의 주변 차량 위험은 미리 생성된 시뮬레이션 결과이며, 앱은 차량을 제어(제동·조향)하지 않는다.

## 구조

| 경로 | 종류 | 역할 |
|---|---|---|
| `replay/` | Kotlin JVM | 계약 v1 DTO(VehicleFrame/PredictionFrame/RiskFrame/manifest), JSON·JSONL 파싱, 계약 검증, 타임라인, 재생 시계 |
| `adapters/` | Kotlin JVM | `ScenarioDataProvider` · `PredictionDataProvider` · `RiskDataProvider` · `VehicleSdkProvider` 경계, JSON 파일 구현, `ScenarioLoader` |
| `sdk/` | Android library | `PleosVehicleSdkProvider` — Vehicle(기어) · FusedLocation · NaviHelper · TextToSpeech |
| `app/` | Android app | Compose UI, 모드 전환, 재생/리플레이, 오류 화면, `assets/scenarios/` |
| `tests/` | — | Mock 생성기(`tools/`), 오류 케이스 fixture(`fixtures/`), 테스트 결과(`reports/`) |

## 앱 모드

| 모드 | 데이터 | 상태 |
|---|---|---|
| Simulation Mode | assets 의 시뮬레이션·예측·위험 JSON 재생. SDK 호출 없음. 정차(P)는 화면 스위치로 가정 | 기본, 공모전 시연용 |
| Pleos Context Mode | 기어·위치·내비 상태·TTS 만 실제 Pleos SDK. 주변 차량 위험은 여전히 시뮬레이션이라고 표시 | Pleos 에뮬레이터에서 4개 SDK 모두 CONFIRMED (`SDK_COMPATIBILITY.md`) |
| ADAS Mode | — | **비활성**. 공개 SDK 에 주변 차량 객체 API 없음, 공식 권한 확인 전 구현하지 않음 |

주행 중 화면은 위험 등급 한 줄만 보여 준다. 시간 이동·배속·원인 코드·TTC 는 **정차(P) 후 리플레이** 화면에서만 연다.

## 요구 환경

- JDK 17 (`C:\Users\ichan\.jdks\ms-17.0.17`). 시스템 Java 23 / Android Studio JBR 25 로는 Gradle 8.13 이 동작하지 않는다.
- Android SDK Platform 35, `ANDROID_HOME` 설정
- Pleos SDK 확인용: AVD `Pleos_Connect_v2.1.0` (Android 14, pleos_car_emulator)
- **Gradle 은 영문 경로에서 실행한다.** 한글 경로(`바탕 화면\과제\현대 공모전`)에서는 JVM 테스트가 `ClassNotFoundException` 으로 실패한다(Windows 비유니코드 코드페이지 문제). 이 PC 에서는 `C:\dev\prism` 을 PRISM 폴더 정션으로 만들어 그 경로에서 빌드한다: `cmd /c mklink /J C:\dev\prism "<PRISM 폴더>"`. `android.overridePathCheck=true` 는 한글 경로에서 IDE 동기화가 막히지 않게 남겨 둔다.

## 재현 명령 (설치 / 데모 / 테스트)

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-17.0.17"; .\gradlew.bat assembleDebug
```
```powershell
.\gradlew.bat installDebug; adb shell am start -n com.prism.vehicle.app/.MainActivity
```
```powershell
.\gradlew.bat test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

UI 자동 테스트 (에뮬레이터 연결 상태, Simulation Mode 흐름 7개):

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

빌드가 `... is located outside the root directory 'C:\dev\prism...'` 로 실패하면 정션 경로와 실제 경로가 섞인 것이다. `.\gradlew.bat --stop` 후 `adapters\build`, `replay\build` 를 지우고 다시 빌드한다.

VS Code: `현대 공모전/.vscode/tasks.json` 에 에뮬레이터 실행 · 빌드 · 설치+실행 · logcat · 단위 테스트 · Mock 재생성 태스크가 있다.

## 다른 엔진과 연결하는 방법

시나리오 하나 = `app/src/main/assets/scenarios/<scenario_id>/` 폴더, 목록 = `scenarios/index.json` (문자열 배열).

| 파일 | 보내는 엔진 | 형식 |
|---|---|---|
| `scenario_manifest.json` | 03 simulation | `scenario_id, title, duration_ms, step_ms, seed, ego_id, event_annotation[{t_ms,label}], provenance, mock` |
| `vehicle_frames.jsonl` | 03 simulation | 한 줄에 VehicleFrame 하나 (계약 v1) |
| `prediction_frames.jsonl` | 01 prediction | 한 줄에 PredictionFrame 하나. `timestamp_ms` 는 VehicleFrame 과 같은 값. 없으면 `prediction_frames.json` → `prediction_frame.json` (프레임 하나 또는 배열) 순서로 찾는다 |
| `risk_frames.jsonl` | 02 risk | 한 줄에 RiskFrame 하나. `timestamp_ms` 는 VehicleFrame 과 같은 값. 없으면 `risk_frames.json` → `risk_frame.json` 순서 |

- 예측·위험 파일이 없으면 차량만 재생하고 경고를 남긴다. VehicleFrame 이 없으면 재생하지 않는다.
- 예측·위험은 **같은 timestamp 의 VehicleFrame 좌표계에서만** 그린다. 짝이 없는 프레임은 버리고 경고로 보고한다(다른 시점 좌표를 섞지 않음).
- 직전 프레임이 `step_ms` 의 2배보다 오래되면 "데이터 누락"으로 표시하고 위험 등급을 UNKNOWN 으로 본다.
- 04 visualization: `app/.../ui/Screens.kt` 의 `RadarSlot` 한 곳에서 `PlaceholderRadar` 를 `PrismRadarView(vehicleFrame, predictionFrame, riskFrame, mode)` 로 바꾸고 `app/build.gradle.kts` 에 모듈/AAR 의존성을 추가한다.
- 엔진 결과를 넣은 뒤 `.\gradlew.bat test` 를 돌리면 `AssetScenarioIntegrationTest` 가 ID 연결·좌표계·시간 동기화를 다시 검사한다.

## Mock 데이터

03/01/02 결과가 오기 전까지 `tests/tools/make_mock_scenarios.py` 로 만든 Mock 6종을 쓴다: `normal`, `rear_approach`, `sudden_brake`, `lane_change`, `chain_reaction`, `dropout`. 각 10초, 100ms 간격, 랜덤 없음. manifest 의 `mock: true` 이면 화면에 **MOCK 데이터** 배지가 뜬다. Mock 의 예측(등속/등가속 외삽)과 위험 라벨(간단 임계값)은 01·02 엔진의 알고리즘이 아니며 검증된 위험도가 아니다.

자세한 상태: `INTEGRATION_STATUS.md`, SDK 지원 여부: `SDK_COMPATIBILITY.md`, 출처·라이선스: `THIRD_PARTY_NOTICES.md`.

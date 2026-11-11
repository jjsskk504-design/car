# visualization-engine 통합 안내 (README 요약)

- 받는 사람: 통합 담당자(vehicle-integration)
- 작성일: 2026-11-10
- 상세 내용: [README.md](../README.md) · 테스트: [tests/results/RESULTS.md](../tests/results/RESULTS.md) · 계약 확인 요청: [CONTRACT_CONFIRMATION_REQUEST.md](CONTRACT_CONFIRMATION_REQUEST.md)

## 무엇인가

- 주변 차량과 **다른 엔진이 판정한** 위험 등급을 차량 화면에 그리는 Android 라이브러리 모듈입니다(Jetpack Compose Canvas 2D).
- 위험을 직접 판단하지 않고, 기존 차량 안전 시스템을 대체하지 않습니다.
- 화면은 두 가지입니다.
  - `RadarScreen`: 주행 중 화면. 위험 차량만 강조합니다.
  - `ReplayScreen`: 정차 후 리플레이. 원인과 등급 변화를 보여 줍니다.

## 통합 측에서 할 일

### 1) Gradle (루트 공통 파일, 승인 요청)

```kotlin
// settings.gradle.kts
include(":visualization-engine")
include(":visualization-engine:demo")   // 선택: 데모 앱

// build.gradle.kts (루트)
plugins {
    id("com.android.library") version "9.5.1" apply false
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

// 앱 모듈
dependencies { implementation(project(":visualization-engine")) }
```

이 구성을 가짜 루트 프로젝트로 재현해서 빌드와 단위 테스트가 성공하는 것을 확인했습니다.
- 의존성은 Compose(BOM 2026.02.01)와 Material3뿐이고 Maven 저장소는 `google()`, `mavenCentral()`입니다.
- minSdk 24, compileSdk 37입니다.

### 2) 화면 연결

```kotlin
val vf by vehicleFrames.collectAsState()      // VehicleFrame?
val pf by predictionFrames.collectAsState()   // PredictionFrame?
val rf by riskFrames.collectAsState()         // RiskFrame?
val parked by isParked.collectAsState()       // Pleos Vehicle SDK 기어·속도로 판단

RadarScreen(
    vehicleFrame = vf, predictionFrame = pf, riskFrame = rf,
    mode = if (parked) DisplayMode.DETAIL else DisplayMode.DRIVING,
    config = RadarConfig(road = RoadConfig(laneCount = 3, laneWidthM = 3.5f, egoLaneIndex = 1)),
    nowMs = clockMs,                           // VehicleFrame.timestamp_ms 와 같은 시계
)

ReplayScreen(session = recordedSession, isVehicleStopped = parked)
```

통합 측이 주어야 하는 값은 아래와 같습니다.

| 값 | 왜 필요한가 |
|---|---|
| VehicleFrame / PredictionFrame / RiskFrame | 표시할 데이터. 없으면 각각 안내 문구로 대체(앱이 죽지 않음) |
| `nowMs` (같은 시계) | 데이터가 500ms 넘게 늦으면 경고를 보류(판단 보류)하기 위해 |
| 정차 여부 | 주행 중에는 상세 분석을 숨기기 위해 |
| `RoadConfig` | VehicleFrame에 차선 형상이 없어서(직선 도로 가정) |
| 리플레이 기록 | 프레임 세 개를 `ReplayFrame`으로 묶은 목록 → `ReplaySession` |
| 오픈소스 고지 화면 | Apache-2.0 의존성 NOTICE(THIRD_PARTY_NOTICES.md) |

### 3) JSON으로 받는 경우

```kotlin
val vf = ContractJson.parseVehicleFrame(json).value
val pf = ContractJson.parsePredictionFrame(json).value
val rf = ContractJson.parseRiskFrame(json).value
// 시나리오 폴더(JSONL 3개) → 리플레이
val session = ReplayJson.fromJsonl("chain_reaction", "연쇄 위험", vehicleJsonl, predictionJsonl, riskJsonl).session
// .warnings 는 로그로 남기면 됩니다(깨진 차량은 그 차량만 건너뜀)
```

## 화면이 스스로 지키는 규칙 (통합 시 알아 두실 점)

- RiskFrame 시각이 VehicleFrame과 300ms 넘게 다르거나 데이터가 지연되면 WATCH·WARNING을 **판단 보류(UNKNOWN)**로 낮춥니다.
- 위치를 모르는 차량(RiskFrame에만 있음)은 빨간색으로 그리지 않습니다.
- 주행 모드에서는 위험 상위 3대만 궤적·영역을 그립니다. 화면 밖 위험 차량은 가장자리에 방향과 거리만 표시합니다.
- 깜빡임 같은 애니메이션이 없고, 실시간 화면은 보간하지 않습니다(지연을 만들지 않기 위해).
- 원인 차량 화살표(`source_vehicle_ids`)는 계약 추가를 제안한 필드입니다. 없으면 화살표만 그리지 않습니다.
- 차량은 승용차·SUV·대형 화물차 탑뷰 그래픽으로 그립니다. 자차는 파란색, 다른 차량은 회색이고, 위험 등급은 외곽선 색과 아이콘으로 표시합니다. 차종은 `length_m`·`width_m`으로 추정하며, `vehicle_class` 필드 추가를 제안해 두었습니다.

## 검증 상태

- 테스트 60개 중 59개 통과: JVM 48/48(팀 실제 출력 호환성 4개 포함), 에뮬레이터 계측 11/12. 실패 1건은 렌더링 비용 벤치마크로, 에뮬레이터 과부하 때문입니다. 같은 시점에 이전 버전도 똑같이 실패했습니다(RESULTS.md 2절)
  - 계측 테스트에는 색 전환(픽셀 확인), 생명주기 복구, 가로 해상도 5종이 들어 있습니다.
- FPS: 에뮬레이터 release 빌드, 모든 차량이 매 프레임 움직이는 조건에서 10~50대 약 60, 100대 약 57, 200대 약 46(창 모드 에뮬레이터). 탑뷰 그래픽 적용 후에도 이전 사각형 그래픽보다 같거나 빠름
  - 에뮬레이터 상태에 따라 편차가 있었습니다. 상세는 RESULTS.md 3절에 있습니다.
- 데모 앱은 Mock JSON 5종(정상·전방 급감속·차선 변경·연쇄 위험·센서 누락)으로 단독 실행됩니다.

## 아직 안 된 것

- PR: `feature/visualization` 브랜치는 팀 저장소에 올렸지만 PR은 아직 만들지 않았습니다(gh 미로그인).
- 공통 `contracts/`: 아직 없어서 각 엔진 폴더의 스키마·Contracts.kt를 기준으로 맞췄습니다. RiskFrame은 JSON Schema 파일이 없습니다(계약 확인 요청 문서 2절).
- 실시간 엔진 연결: 팀 시나리오 파일(기록된 출력)로는 검증했지만, 앱에서 실시간 스트림으로 연결하지는 않았습니다.
- 실차 하드웨어 검증
- 곡선 도로

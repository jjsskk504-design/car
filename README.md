# PRISM visualization-engine

주변 차량의 위치·움직임과 **다른 엔진이 계산한** 미래 위험도를 차량용 화면에 표시하는 Android 라이브러리 모듈입니다.
(Jetpack Compose Canvas 기반 2D 탑다운 뷰)

- 담당: 시각화·UI/UX 엔진 / 브랜치: `feature/visualization`
- 이 모듈은 위험을 **판단하지 않습니다.** RiskFrame에 들어온 등급을 운전자가 이해하기 쉽게 표시만 합니다.
- 차량의 기존 안전 시스템(ADAS)을 대체하거나, 실시간 충돌 방지 장치로 쓰는 것을 전제로 하지 않습니다.
- 시연 데이터(`demo/`)는 **손으로 만든 Mock**입니다. 표시된 위험 등급은 어떤 알고리즘 성능도 의미하지 않습니다.

![연쇄 위험 — 주행 화면](docs/screenshots/04a_chain_drive_early.png)

---

## 1. 폴더 구조

```
visualization-engine/
├── build.gradle.kts / settings.gradle.kts   # Android Library 모듈(독립 빌드 가능), demo 앱 포함
├── model/       # 계약 모델(VehicleFrame v1, PredictionFrame, RiskFrame) + JSON 변환 + 원인 코드 문구
├── rendering/   # 좌표 변환(Viewport), 차량 기하, 위험 스타일, SceneBuilder(표시 규칙), 프레임 보간 — Android 의존 없음
├── overlays/    # Compose DrawScope 렌더러: RoadLayer, VehicleRenderer, RiskOverlay, TrajectoryOverlay
├── ui/          # 공개 Composable: RadarScreen, RadarSceneView, RadarStatusBar, RiskLegend, LevelIcon
├── replay/      # ReplaySession / ReplayJson / ReplayScreen / RiskTimeline
├── demo/        # 독립 실행 데모 앱 + Mock 시나리오 생성기 + 샘플 JSON(assets/scenarios)
├── tests/
│   ├── unit/        # JVM 단위 테스트(32개, 데모 모듈 3개 별도)
│   ├── android/     # 계측 테스트: Compose UI, 해상도, 생명주기, 렌더링 비용(12개)
│   └── results/     # 테스트·FPS 측정 결과
├── docs/        # 스크린샷, 시연 영상, 예제 입력·출력
├── README.md
└── THIRD_PARTY_NOTICES.md
```

---

## 2. 빠른 실행

요구 사항: JDK 17, Android SDK(compileSdk 37), AGP 9.5.1, Kotlin 2.2.10. 이 폴더 단독으로 빌드할 수 있습니다.

```bash
cd visualization-engine
./gradlew :testDebugUnitTest :demo:testDebugUnitTest     # JVM 단위 테스트
./gradlew :connectedDebugAndroidTest                     # 계측 테스트(에뮬레이터/기기 필요)
./gradlew :demo:installDebug                             # 데모 앱 설치
```

데모 앱 인텐트 옵션(시연·스크린샷 자동화용):

```bash
adb shell am start -n ai.prism.visualization.demo/.DemoActivity \
  --es scenario chain        # normal | front_brake | lane_change | chain | sensor_dropout
  --es tab drive             # drive(주행 화면) | replay(정차 후 리플레이)
  --es stopped true          # 정차 여부(실제로는 vehicle-integration 이 Pleos Vehicle SDK 로 판단)
  --el t_ms 1800             # 특정 시점에 멈춘 화면
  --ei stress 100            # FPS 측정 모드(차량 N대, logcat 태그 PRISM_FPS)
```

다른 엔진이 없어도 `demo/src/main/assets/scenarios/*.json`(Mock JSON)으로 실행됩니다.

---

## 3. 공개 API

### 3.1 주행 중 레이더

```kotlin
RadarScreen(
    vehicleFrame = vf,                 // VehicleFrame? — null 이면 "차량 데이터 없음"
    predictionFrame = pf,              // PredictionFrame? (선택)
    riskFrame = rf,                    // RiskFrame? — null 이면 모든 차량 중립색 + 안내
    mode = DisplayMode.DRIVING,        // DRIVING(강조 최소화) | DETAIL(정차 후 상세)
    config = RadarConfig(road = RoadConfig(laneCount = 3, laneWidthM = 3.5f, egoLaneIndex = 1)),
    nowMs = clock.nowMs(),             // 지정하면 데이터 지연을 감지해 경고를 보류
    modifier = Modifier.fillMaxSize(),
)
```

### 3.2 정차 후 리플레이

```kotlin
val session: ReplaySession = ReplayJson.parse(jsonString).session   // 또는 ReplaySession(...) 직접 생성
ReplayScreen(
    session = session,
    isVehicleStopped = isParked,       // false 면 상세 분석을 숨김
    state = rememberReplayState(session.id),   // 재생 위치·선택 차량·속도는 화면 재생성 후에도 복원
)
```

### 3.3 하위 API

| API | 용도 |
|---|---|
| `ContractJson.parseVehicleFrame(String \| JSONObject)` | VehicleFrame JSON → 모델. 깨진 차량은 건너뛰고 `ParseResult.warnings`로 보고 |
| `ContractJson.parsePredictionFrame(JSONObject)`, `ContractJson.parseRiskFrame(JSONObject)` | 잠정 계약 JSON → 모델. **문자열이면 `JSONObject(json)`으로 감싸서** 호출 |
| `ContractJson.toJson(VehicleFrame \| PredictionFrame \| RiskFrame \| RoadConfig)` | 모델 → `JSONObject` |
| `ReplayJson.parse(String).session`, `ReplayJson.toJson(ReplaySession): String` | 리플레이 파일(이 모듈 자체 포맷, `prism.visualization.replay/0.1`) |
| `SceneOptions(trajectoryReference, staleAfterMs = 500, riskSyncToleranceMs = 300, maxEmphasizedInDriving = 3)` | 표시 규칙 조정. `RadarConfig(sceneOptions = …)`로 전달 |
| `ViewportConfig(aheadM = 60, behindM = 25, roadWidthFraction = 0.58, maxLateralExaggeration = 5)` | 보이는 범위·횡방향 확대. `RadarConfig(viewport = …)`로 전달 |
| `SceneBuilder.build(...)` | 계약 프레임 → 그리기 독립적인 `Scene`. Android 의존이 없어 JVM 테스트 가능 |
| `RadarSceneView(scene)` | 이미 만든 Scene 을 그림(리플레이·벤치마크) |
| `DrawScope.drawRadarScene(scene, transform, textMeasurer)` | 오프스크린·커스텀 Canvas 에 그리기 |
| `RiskLegend()`, `LevelIcon(level)`, `RadarStatusBar(scene)` | 범례·아이콘·상태 줄 |
| `RadarTestTags`, `ReplayTestTags` | 통합 UI 테스트용 태그 |

---

## 4. 다른 엔진과 연결하는 방법

```
vehicle-integration ──VehicleFrame──┐
prediction-engine  ──PredictionFrame├──▶ (통합 측 StateFlow) ──▶ RadarScreen / ReplayScreen
risk-engine        ──RiskFrame──────┘
```

1. **Gradle 포함** (PRISM 루트의 공통 파일이라 통합 담당자 승인이 필요합니다)
   ```kotlin
   // 루트 settings.gradle.kts
   include(":visualization-engine")
   include(":visualization-engine:demo")      // 데모 앱도 빌드할 때만

   // 루트 build.gradle.kts — 이 모듈은 플러그인을 버전 없이 선언하므로 루트에서 버전을 정해야 함
   plugins {
       id("com.android.library") version "9.5.1" apply false
       id("com.android.application") version "9.5.1" apply false
       id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
   }

   // 앱 모듈
   dependencies { implementation(project(":visualization-engine")) }
   ```
   - 이 폴더의 `settings.gradle.kts`와 `gradlew`는 독립 빌드용입니다. 루트 빌드에서는 쓰이지 않습니다.
   - 위 구성을 가짜 PRISM 루트 프로젝트로 재현해서 `:visualization-engine:demo:assembleDebug`와 `:visualization-engine:testDebugUnitTest`가 성공하는 것을 확인했습니다.
   - Maven 저장소는 `google()`, `mavenCentral()`만 필요합니다.
2. **데이터 공급**: 통합 측이 세 프레임을 `StateFlow`로 모아 `collectAsState()`로 넘기면 됩니다.
   이 모듈은 스레드·구독을 소유하지 않습니다. 메인 스레드에서는 그리기만 합니다.
3. **JSON으로 받는 경우**
   - 문자열 JSON은 `ContractJson.parseVehicleFrame(json)`, `ContractJson.parsePredictionFrame(JSONObject(json))`, `ContractJson.parseRiskFrame(JSONObject(json))`로 바꿉니다.
   - `.value`가 모델이고, `.warnings`는 로그로 남기면 됩니다.
   - Kotlin 객체로 직접 받는다면 `model/Contracts.kt`의 데이터 클래스를 쓰면 됩니다.
4. **정차 여부**: 통합 측이 Pleos Vehicle SDK(기어·속도)로 판단해 `isVehicleStopped`와 `mode`에 넘깁니다.
5. **리플레이 기록**: 통합 측이 프레임 세 개를 `ReplayFrame`으로 묶어 쌓으면 `ReplayScreen`에서 그대로 재생됩니다.
   저장 포맷은 `ReplayJson.toJson`을 씁니다.

---

## 5. 데이터 계약 — 가정과 확인이 필요한 이슈

PRISM 저장소의 JSON Schema를 아직 받지 못해 **팀 공지의 필드 목록을 기준으로 구현**했습니다.
스키마가 다르면 공통 계약은 바꾸지 않고, `model/ContractJson.kt`만 맞추거나 이슈로 보고합니다.
필드별로 공지에 있는 것, 추정한 것, 확인이 필요한 것을 나눈 목록은 [docs/CONTRACT_CONFIRMATION_REQUEST.md](docs/CONTRACT_CONFIRMATION_REQUEST.md)에 있습니다.

| # | 이슈 | 현재 가정 | 요청 |
|---|---|---|---|
| 1 | PredictionFrame·RiskFrame 필드명 미확정 | `predictions[].candidates[].points[] {t_offset_ms,x,y,heading_rad}`, `risks[] {vehicle_id, level, cause_codes[], source_vehicle_ids[], risk_region[{x,y}], trajectory_candidate_id, message}` | 스키마 확정 시 공유 |
| 2 | `heading_rad` 기준축 | 자차 좌표계에서 +x(오른쪽) 기준 반시계, 전방 = π/2 | 0 = 전방인지 확인 |
| 3 | 예측 궤적·위험 영역의 좌표 기준 | 예측 시점 자차 기준 **지면 좌표**로 받고, 화면에는 자차 이동량을 빼서 **상대 운동**으로 표시(`TrajectoryReference.RELATIVE_TO_EGO`). 위험 영역은 받은 좌표 그대로 그림 | 위험 영역이 지면 기준인지 상대 기준인지 확인 |
| 4 | `vx, vy`가 지면 속도인지 자차 상대 속도인지 | 지면 속도를 자차 축으로 표현한 값 | 확인 |
| 5 | 자차가 `vehicles[]`에 포함되는지 | 포함(`ego_id`로 식별). 없으면 원점에 기본 자차를 그림 | 확인 |
| 6 | `lane_id` 번호 규칙, 차선 형상 | 표시용으로만 사용. 차선 수·폭·자차 차선은 `RoadConfig`로 받음(직선 도로 가정) | 차선 형상이 계약에 필요한지 논의 |
| 7 | 원인 코드 목록 | `CauseCatalog`에 잠정 코드 9개(`LEAD_HARD_BRAKE`, `PREDICTED_CUT_IN`, `CHAIN_DECELERATION`, `SENSOR_DROPOUT` 등). 모르는 코드는 원문 표시 | 위험 판정 엔진과 목록 합의 |
| 8 | 연쇄 위험 표현 | `source_vehicle_ids`(원인 차량)가 있으면 원인 → 위험 차량 화살표를 그림 | 계약에 포함 여부 확인 |

---

## 6. UX 원칙과 구현

| 요구 | 구현 |
|---|---|
| NORMAL 회색 / WATCH 노란색 / WARNING 빨간색 / UNKNOWN 점선·별도 기호 | `RiskStyle`. UNKNOWN은 빨간색을 쓰지 않음 |
| 색상만으로 전달하지 않기 | 등급마다 **아이콘 모양**(없음 / 테두리 삼각형 / 채운 삼각형 / 점선 원), **글자**(! / !! / ?), **선 모양**(실선 / 점선), 상태 줄 **텍스트**(경고 1대 등)를 함께 씀. 타임라인은 **막대 높이**로도 등급을 구분 |
| 과도한 애니메이션 금지 | 깜빡임·펄스 없음. 차선 점선도 스크롤하지 않음. 실시간 화면은 보간하지 않음(지연 방지) |
| 경고 과장 금지 / 센서 누락 시 경고 억제 | 데이터 지연(기본 500ms), RiskFrame 시각 불일치(300ms), **위치를 모르는 차량**은 WATCH/WARNING이어도 UNKNOWN(판단 보류)으로 낮춤 |
| 필요한 위험 객체만 강조 | 주행 모드에서는 위험 등급 상위 3대만 궤적·영역 표시, 위험 판단에 쓰인 후보 궤적만 그림. 화면 밖 위험 차량은 가장자리에 방향 + 거리만 표시 |
| 상세 분석은 정차 후에만 | `ReplayScreen(isVehicleStopped = false)`이면 상세 화면 대신 안내 문구만 표시. 주행 모드에서는 차량 탭 비활성 |
| 차선·차량 방향 표시 | 차선별 진행 방향 셰브런, 차량 앞쪽 삼각형, heading 회전 |
| 트래킹 ID 연결 | ID 기준 보간(리플레이), 상세 모드 ID 라벨, 사라진 ID를 끌고 다니지 않음 |

---

## 7. 오픈소스 조사와 기술 선택

| 저장소 | Kotlin/Android 호환 | PRISM에 필요한 기능 | 에뮬레이터 | 부하 | 결정 |
|---|---|---|---|---|---|
| **Jetpack Compose (androidx)** Apache-2.0, 매일 커밋 | 공식 지원, minSdk 21+ | Canvas `DrawScope`(Path, 점선 PathEffect, 텍스트), 제스처, 상태 복원 | 동작 확인(Pleos AVD) | 2D 경로 수백 개 수준은 충분(아래 측정) | **P0 채택** — Maven 의존성 |
| **Vico** Apache-2.0, 2026-11-08 커밋 | Compose Multiplatform. 최신 버전은 Kotlin 2.4.21로 빌드 | 위험 지표 그래프 | 미검증 | 경량 | **미채택** — 필요한 그래프는 위험 등급 타임라인 하나뿐이라 Canvas로 직접 구현. 의존성과 Kotlin 버전 상향을 피함 |
| **Filament** Apache-2.0, 2026-11-10 커밋, v1.80 | minSdk 21, NDK 네이티브 라이브러리 | 3D 렌더링(PBR) | 가능(GLES/Vulkan) | APK 크기·GPU 부하 큼 | **P2 검토만** — 2D로 시연 요구를 충족 |

적용 방식은 **패키지 설치(Gradle Maven 의존성)**입니다.
- submodule과 vendoring은 쓰지 않습니다. 공식 바이너리로 충분하고 라이선스 고지도 단순해서입니다.
- 기하 변환·렌더러·타임라인은 직접 구현했습니다.

---

## 8. 테스트와 성능

전체 결과: [tests/results/RESULTS.md](tests/results/RESULTS.md)

| 구분 | 내용 | 결과 |
|---|---|---|
| JVM 단위(라이브러리 32 + 데모 3 = 35) | 계약 파싱·깨진 데이터, 좌표 변환·해상도, 등급 스타일·색 전환, 데이터 누락·지연·동기화, 주행 모드 강조 제한, 5개 시나리오 표시 규칙, 리플레이 이벤트·보간, Mock 데이터 충돌 없음 | 통과 |
| Compose UI 계측(12) | 픽셀 색으로 NORMAL→WATCH→WARNING→UNKNOWN 전환 검증, 데이터 누락 안내, 지연 시 경고 억제, 모드별 탭, 재생/일시정지, 원인 패널, 주행 중 잠금, **상태 복원(생명주기)**, 가로 해상도 5종, 렌더링 비용 | 12/12 통과 |
| **합계** | | **47/47 통과** |

**실제 화면 FPS**: 데모 release 빌드를 Pleos_Connect AVD(호스트 GPU, 60 Hz)에서 쟀습니다.
- 조건: 상세 모드에서 모든 차량이 매 프레임 움직이고, 워밍업 구간은 뺀 중앙값입니다.
- 결과: 10대 60 / 30대 59.5 / 50대 59.5 / 100대 57 / 200대 50.6 FPS
- 측정 조건 전체와 에뮬레이터 상태에 따른 편차(이전 측정 51/48/35/30)는 RESULTS.md 3절에 있습니다.
- 에뮬레이터 수치라 실차 SoC 성능을 보장하지 않습니다.

---

## 9. 시연 자료

| 파일 | 장면 |
|---|---|
| `docs/screenshots/01_normal_drive.png` | 정상 주행 — 모든 차량 중립 |
| `docs/screenshots/02_front_brake_drive.png` | 전방 급감속 — 앞 차량 경고, 후속 차량 변화 |
| `docs/screenshots/03_lane_change_drive.png` | 차선 변경 — 진입 예상 차량만 강조 + 차선 변경 경로 |
| `docs/screenshots/04a_chain_drive_early.png` | 연쇄 위험 전조 — 화면 밖 화물차(71m) → 우측 차량 → 내 경로 |
| `docs/screenshots/04b_chain_drive_cutin.png` | 연쇄 위험 — 끼어들기 경고 |
| `docs/screenshots/05_sensor_dropout_drive.png` | 센서 누락 — 판단 보류(점선 + ?), 경고 억제 |
| `docs/screenshots/06_chain_replay.png`, `07_dropout_replay.png` | 정차 후 리플레이 — 원인·등급 변화·타임라인 |
| `docs/screenshots/08_replay_locked_while_driving.png` | 주행 중 리플레이 잠금 |
| `docs/screenshots/tests/*.png` | 계측 테스트 캡처(해상도 5종, 등급별 레이더) |
| `docs/prism_chain_replay.mp4`, `docs/prism_chain_drive.mp4` | 재생 시연 영상(에뮬레이터 화면 녹화) |
| `docs/examples/` | 예제 입력(계약 프레임 3종)과 출력(Scene 요약) |
| `docs/HANDOFF_INTEGRATION.md` | 통합 담당자용 요약(연결 방법·필요한 값·검증 상태) |
| `docs/CONTRACT_CONFIRMATION_REQUEST.md` | 계약 필드별 확정·추정·제안 구분과 확인 요청 |

---

## 10. 제한 사항

- **PRISM 저장소에 아직 반영하지 않았습니다.**
  - 작업 시점에 저장소 URL이 없었고, GitHub CLI도 로그인되어 있지 않았습니다.
  - 그래서 `feature/visualization` 브랜치 커밋과 Pull Request를 만들지 않았습니다.
  - 이 폴더는 `C:\Users\jjssk\AndroidStudioProjects\prism-work\visualization-engine`에 있습니다. 저장소를 받으면 이 폴더만 옮겨 커밋합니다.
- 계약 JSON Schema를 받지 못해 5절의 가정 위에서 구현했습니다.
- Pleos Vehicle SDK(정차 판단)와 실제 엔진 출력은 연결하지 않았습니다. 이는 vehicle-integration 담당 범위입니다.
- 곡선 도로와 차선 형상은 지원하지 않습니다(직선 도로 가정).
- 성능 수치는 에뮬레이터 측정값입니다. 계측 테스트의 렌더링 비용은 debuggable APK의 CPU 래스터 기준이라 절대 성능 지표로 쓰지 않습니다.

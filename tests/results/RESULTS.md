# 테스트 결과 — visualization-engine

- 최종 실행: 2026-11-10 (KST)
- 원본 JUnit XML: `junit/` · FPS 원시 로그: `fps_stress_raw.txt` · 렌더링 비용: `render_benchmark.csv`

## 0. 요약

| 구분 | 테스트 수 | 성공 | 실패 |
|---|---|---|---|
| JVM 단위 — 라이브러리 (`tests/unit`) | 45 | 45 | 0 |
| JVM 단위 — 데모 (`demo/src/test`) | 3 | 3 | 0 |
| 계측(에뮬레이터) — Compose UI·해상도·성능 (`tests/android`) | 12 | 11 | 1 (환경) |
| **합계** | **60** | **59** | **1 (환경)** |

`TeamContractCompatibilityTest` 4개는 **팀 저장소 안에서만** 실행되고(다른 엔진의 실제 출력 파일을 읽기만 함), 독립 저장소에서는 건너뜁니다(skipped). 위 수치는 팀 저장소에서 실행한 결과입니다.

실행 명령:

```bash
./gradlew :testDebugUnitTest :demo:testDebugUnitTest   # 48개
./gradlew :connectedDebugAndroidTest                    # 12개, 에뮬레이터 필요
```

## 1. 테스트 환경

| 항목 | 값 |
|---|---|
| 호스트 | Windows 11 Home, JDK 17.0.20.1 |
| 빌드 | Gradle 9.6.0, AGP 9.5.1, Kotlin 2.2.10, Compose BOM 2026.02.01, compileSdk 37, minSdk 24 |
| 에뮬레이터 | AVD `Pleos_Connect_v2.1.0`, Android 14 (API 34), x86_64. 3.1~3.3절은 창 없이(`-no-window`), 3.4절과 최종 계측 테스트는 창 모드로 실행 |
| 화면 | 2560×1440, 160 dpi, 60 Hz. 앱은 Pleos 앱 영역 1682×1210(멀티 윈도)에 표시, 사용자 10 |
| GPU | 호스트 GPU(`-gpu host`, Intel Graphics). SwiftShader로는 측정하지 않음 |
| 가상 CPU / RAM | 4 vCPU / 4 GB. Pleos 시스템 앱(drivingview, 지도 등)이 함께 실행됨 |

## 2. 테스트별 결과 (60개)

### 2026-11-11 계약 반영 코드의 계측 테스트 기록

| 실행 | 결과 | 비고 |
|---|---|---|
| 1회차(창 없는 에뮬레이터) | **11/12 통과**, `renderCost_scalesWithVehicleCount` 실패(50대 평균 69.5ms > 기준 50ms) | 에뮬레이터 과부하(Pleos 지도 앱 CPU 125%). 같은 시점에 **이전 버전(2c45c52)도 67.9~130.4ms로 똑같이 기준 초과** → 코드 회귀 아님. 새 버전은 66.5~131.7ms |
| 2~4회차 | 무효 | 에뮬레이터 정지 또는 크래시(exit 139). "Compose 계층 없음", 상태 코드 -1 등 환경 오류 |

- 이 렌더링 비용 테스트가 정상 환경에서 다시 통과하는지는 **아직 확인하지 못했습니다.**
- 직전 커밋(278b1d3, 2026-11-10)에서는 계측 12/12가 통과했습니다.
- 이번 변경에서 렌더링 경로는 위험 영역 투명도 계산 한 줄만 바뀌었습니다.


### JVM — 라이브러리 (45)

| 스위트 | 테스트 | 검증 내용 |
|---|---|---|
| ContractJsonTest | parsesVehicleFrameV1 | VehicleFrame v1(팀 스키마: `lane_id` 문자열, `schema_version`, `coordinate_system`) 파싱 |
| | warnsOnWrongSchemaVersionOrCoordinateSystem | 지원하지 않는 버전·좌표계는 경고 |
| | parsesPredictionFrameFromPredictionEngineExample | PredictionFrame 1.0(`trajectories`/`mode`/`confidence`/`dt_ms`) |
| | parsesRiskFrameFromVehicleIntegrationSample | RiskFrame 1.0(`risk_objects`/`risk_level`/`reason_codes`/`ttc_s`/`risk_zones`) |
| | riskExportWithoutExtensionsHasOnlyContractFields | 계약 필드만 내보내기 옵션 |
| | skipsBrokenVehiclesInsteadOfCrashing | NaN·누락·ID 없음·객체 아님 → 해당 차량만 제외 + 경고 4건 |
| | unknownRiskLevelBecomesUnknownNotWarning | 모르는 등급 문자열 → UNKNOWN |
| | roundTripAllFrames | VehicleFrame·PredictionFrame 직렬화 → 역직렬화 동일 |
| GeometryTest | egoIsAnchoredAndAxesPointRightAndUp | x=오른쪽, y=전방=화면 위, 자차 고정 위치, 역변환 |
| | roadFitsAcrossLandscapeResolutions | 해상도 6종 × 패널 폭 2종 × 3/4/5차로에서 도로가 화면 안에 들어감 |
| | egoInOuterLaneStillCentersRoad | 자차가 바깥 차선에 있어도 도로는 가운데 |
| | cornersFollowHeading | heading 회전, 진행 방향 삼각형 위치 |
| | interpolatesById_andDoesNotInventVehicles | 트래킹 ID 기준 보간, 사라진·새 차량 처리 |
| | headingInterpolationTakesShortestArc | ±π 경계 각도 보간 |
| SceneBuilderTest | styleMapping_isNotColorOnly | 4등급의 색·아이콘·글자·선 모양이 모두 다름 |
| | colorTransition_followsRiskFrames | NORMAL→WATCH→WARNING→WATCH→NORMAL 전환 |
| | noRiskFrame_allNeutralWithNotice | RiskFrame 없음 → 전부 중립 + 안내 |
| | noVehicleFrame_showsEgoOnly | VehicleFrame 없음 → 자차만 + 안내 |
| | staleData_suppressesWarningsToUnknown | 데이터 지연 → WARNING/WATCH를 UNKNOWN으로 낮춤 |
| | riskOutOfSync_suppressesWarnings | RiskFrame 시각 불일치 → 판단 보류 |
| | riskForMissingVehicle_isGhostUnknown_neverWarning | 위치 모르는 차량 → UNKNOWN 영역만 |
| | invalidVehicles_droppedWithNotice | NaN·중복 ID 제거 + 안내 |
| | drivingMode_onlyHighlightsRiskVehiclesAndChosenCandidate | 주행 모드는 위험 근거 후보만, 상세 모드는 전체 |
| | danglingCandidateReference_stillShowsTrajectories | 끊긴 후보 참조 → 후보 전체 표시 |
| | drivingMode_capsNumberOfEmphasizedVehicles | 주행 모드 강조 상위 3대 제한 |
| | chainRelation_andSourceMarking | 원인 → 위험 차량 관계, 원인 차량 표시 |
| | missingEgoInFrame_addsDefaultEgo | 자차 누락 시 기본 자차 |
| SampleScenarioTest | allScenariosParse_andCoordinatesAreEgoRelative | 5개 샘플 JSON 파싱, 자차 원점, 10 Hz |
| | normal_everyVehicleNeutral | 정상 주행: 모든 프레임 중립, 궤적·영역 없음 |
| | frontBrake_leadThenFollowerChange | 앞 차량 주의 → 경고, 이후 후속 차량 변화 |
| | laneChange_onlyIntrudingVehicleHighlighted_withLaneChangePath | 침범 차량만 강조 + 차선 변경 경로 |
| | chain_truckIsSource_andRelationsFormChain | 화물차 → R1 → B1 순서와 관계 화살표 |
| | sensorDropout_unknownAndWarningSuppressed | 누락 구간은 UNKNOWN, Mock이 WATCH를 보내도 낮춤 |
| | replayEvents_explainCauses | 리플레이 이벤트의 원인·원인 차량 |
| | replayIndexAndInterpolation | 시간 → 프레임 인덱스, 보간 |
| VehicleShapesTest | classifyByDimensions | 치수 → 승용차·SUV·대형 화물차 분류 |
| | shapesStayInsideVehicleFootprint_soPositionAndSizeArePreserved | 모든 도형 점이 차량 길이·폭 안(미러만 폭 0.62까지), 차체가 길이·폭 전체 사용 → 크기·위치 유지 |
| | shapesAreLeftRightSymmetric | 좌우 대칭(곡선 제어점 포함) |
| | shapesDifferPerType | 차종별 도형이 서로 다름 |
| | sceneBuilderAssignsBodyType | Scene 차량에 차종이 지정됨 |
| TeamContractCompatibilityTest | vehicleIntegrationScenarios_parseWithoutWarnings_andRender | vehicle-integration 시나리오 6개(JSONL 3종)를 경고 0건으로 해석, WATCH·WARNING 183건이 화면 등급과 일치 |
| | dropoutScenario_unknownIsNeverShownAsWarning | 팀 `dropout` 시나리오의 UNKNOWN이 경고색으로 표시되지 않음 |
| | predictionEngineExample_parses | prediction-engine 예시 파싱, `dt_ms` 1~5000 |
| | simulationEngineSamples_parse | simulation-engine VehicleFrame 샘플 전 줄 파싱 |
| ExampleIoTest | writeExamples | `docs/examples/` 예제 입력·출력 생성 |

### JVM — 데모 (3)

| 스위트 | 테스트 | 검증 내용 |
|---|---|---|
| SampleJsonExportTest | exportAndRoundTrip | Mock → JSON → 파싱 경고 0 |
| | assetsAreUpToDate | assets 샘플 JSON이 생성기 결과와 같음 |
| MockScenarioSanityTest | noVehicleOverlapsInAnyScenario | 시연 데이터에서 차량끼리 겹치지 않음 |

### 계측 — 에뮬레이터 (12)

| 스위트 | 테스트 | 검증 내용 |
|---|---|---|
| RadarScreenUiTest | riskColorTransitions_NORMAL_WATCH_WARNING_UNKNOWN | **화면 픽셀**: 차체(보닛)는 회색 유지, 차체 외곽선이 WATCH 노란색 / WARNING 빨간색, UNKNOWN은 중립색. 상태 줄 텍스트 전환 |
| | missingVehicleData_showsNotice | "차량 데이터 없음" 안내 |
| | staleData_suppressesWarning | 지연 시 "판단 보류", 경고 문구 없음 |
| | riskFrameMissing_allNeutral | RiskFrame 없음 → 회색 픽셀 |
| | vehicleClick_onlyInDetailMode | 주행 모드에서는 탭 무시, 상세 모드에서만 선택 |
| ReplayScreenUiTest | playPause_and_progressAdvances | 재생 1초 → 위치 약 1초, 일시정지 후 정지 |
| | seekShowsCauseOfSelectedVehicle | 시점 이동 시 원인·원인 차량 패널 |
| | detailHiddenWhileDriving | 주행 중 상세 화면 잠금 |
| | lifecycle_stateSurvivesRecreation | 재생 위치·선택 차량·속도가 상태 복원 후 유지 |
| ResolutionAndPerformanceTest | landscapeResolutions_layoutAndRoadFit | 1920×720, 1280×720, 2560×1080, 1024×600, 800×480 비율로 실제 렌더링·캡처 |
| | drivingRadar_screenshots | 등급별 레이더 캡처 4장 |
| | renderCost_scalesWithVehicleCount | 차량 수별 프레임당 렌더링 비용(3.2절) |

## 3. 성능 측정

### 3.1 실제 화면 FPS — 측정 조건

| 조건 | 값 |
|---|---|
| 빌드 | 데모 **release** 변형: debuggable 아님, R8 꺼짐, 디버그 키 서명(배포용 아님) |
| 화면 | `StressScreen`: `RadarScreen`을 `DisplayMode.DETAIL`로 앱 영역 전체(1682×1210)에 표시 |
| 데이터 | 합성 차량 N대(3차로 배치). **모든 차량이 매 프레임 움직여** 매 vsync마다 Scene을 다시 만들고 다시 그림. 7대 중 1대씩 WATCH·WARNING·UNKNOWN, 상세 모드라 ID 라벨 포함 |
| 측정 | `Choreographer.FrameCallback`으로 2초 구간마다 프레임 수 집계(logcat `PRISM_FPS`). N마다 앱을 새로 시작해 약 13초 실행 |
| 집계 | 첫 구간(워밍업) 제외, 나머지 구간의 중앙값과 범위 |
| 상한 | 디스플레이 60 Hz이므로 최대 60 FPS |

### 3.2 실제 화면 FPS — 결과 (최종 코드, 에뮬레이터 재시작 직후)

| 차량 수 | 중앙값 FPS | 범위(워밍업 제외) |
|---|---|---|
| 10 | 60.0 | 58.0 – 60.0 |
| 30 | 59.5 | 59.0 – 60.0 |
| 50 | 59.5 | 52.6 – 60.0 |
| 100 | 57.0 | 49.5 – 59.0 |
| 200 | 50.6 | 47.2 – 52.1 |

같은 release 조건으로 앞서 측정한 값은 10/50/100/200대에서 약 51/48/35/30 FPS였습니다.
- 그 측정 직후 에뮬레이터가 응답하지 않아 재시작했습니다. 그래서 당시 에뮬레이터가 이미 느려진 상태였을 가능성이 있습니다(추정).
- 같은 조건에서도 **에뮬레이터 상태에 따라 큰 편차**가 있었다는 뜻이므로 두 값을 함께 남깁니다.
- debug(debuggable) 빌드는 10/50/100/200대에서 약 36/20/13/7 FPS였습니다(`fps_stress_raw.txt` 이전 기록).

참고로 실제 주행 화면은 데이터 주기(10 Hz)에 맞춰 100ms마다만 다시 그립니다. 위 stress 조건보다 부하가 훨씬 낮습니다.

### 3.3 프레임당 렌더링 비용 (계측 테스트)

- 조건: debuggable 테스트 APK, 960×720 비트맵에 **오프스크린 CPU 래스터**, 밀도 1.5, 상세 모드
- 방법: 워밍업 20회 후 100회 측정

| 차량 수 | 평균 ms | p95 ms |
|---|---|---|
| 5 | 10.83 | 17.75 |
| 20 | 22.24 | 34.64 |
| 50 | 33.53 | 45.84 |
| 100 | 56.63 | 78.20 |
| 200 | 90.50 | 122.91 |

탑뷰 차량 그래픽을 적용한 최종 코드의 값입니다(창 모드 에뮬레이터).
GPU 합성이 아니라 CPU로 그린 비용이고, ART 최적화가 꺼진 상태입니다. 실제 FPS와 비교하는 값이 아니라 **회귀 감지용**입니다(기준: 50대 평균 50ms 이하).

### 3.4 탑뷰 차량 그래픽 — 이전 사각형 그래픽과 성능 비교

같은 에뮬레이터(창 모드, `-gpu host`, 60 Hz)에서 이전 버전과 새 버전을 번갈아 측정했습니다.
- 이전 버전: 최초 커밋 `2c45c52`, 사각형 그래픽. `git archive`로 꺼내 빌드했습니다.
- 측정 조건은 3.1절과 같습니다(release, stress 모드, 2초 구간, 첫 구간 제외).
- 원시 데이터는 `vehicle_graphics_perf_raw.csv`에 있습니다.

| 구현 단계 | 10대 | 50대 | 100대 | 200대 |
|---|---|---|---|---|
| 1차: 차량마다 매 프레임 Path 12개 생성 | 58.3 (이전 58.8) | **35.2** (56.8) | **31.2** (50.4) | **17.7** (34.8) |
| 2차: 차종별 Path 캐시 + 행렬 변환 | 58.3 (59.3) | 51.1 (56.1) | 44.6 (48.2) | 32.2 (38.3) |
| 3차: 같은 색 부품 합치기 + LOD | 59.0 (59.3) | 51.6 (56.0) | 45.6 (50.4) | 34.4 (39.2) |
| **최종: 스프라이트 캐시** | **60.0** (59.5) | **59.5** (57.3) | **56.8** (45.6) | **45.9** (39.8) |

(괄호 = 같은 라운드의 이전 사각형 그래픽. 2차 이후는 2라운드 측정값 8개의 중앙값)

최종 구현:
- 차종·색·화면 크기별 차량 그림을 **한 번만** 비트맵으로 래스터화합니다(직접 만든 벡터 도형, 외부 이미지 아님).
- 차량마다 affine 행렬(위치·heading·축척) 하나와 그리기 호출 1회로 그립니다.
- 외곽선·테두리 띠는 위험 차량과 자차에만 화면 좌표 Path로 그립니다(선 두께 일정).
- 비교 결과 모든 차량 수에서 이전 사각형 그래픽보다 같거나 빠릅니다. CPU 렌더링 비용(3.3절)은 이전과 오차 범위 안에서 같습니다(50대 평균: 이전 33.6~38.0ms, 새 32.6~32.9ms).

## 4. 개발 중 발견한 실패와 조치

| 발견 | 원인 | 조치 |
|---|---|---|
| 일시정지 후 위치가 16ms 더 진행됨 | 재생 루프가 프레임 콜백 안에서 `playing` 상태를 다시 확인하지 않음 | `replay/ReplayScreen.kt` 수정 |
| 해상도·스크린샷 테스트가 `setContent`를 두 번 호출 | 테스트 코드 오류 | 상태 변경 방식으로 재작성 |
| 렌더링 비용 50대 평균 32ms > 16ms | debuggable APK에서 CPU로 래스터화함 | 절대 기준에서 회귀 감지 기준(50ms)으로 바꿈. 실제 FPS는 release로 별도 측정 |
| 연쇄 시나리오에서 후방 차량 위험이 생기지 않음 | Mock 수치 문제 | Mock 조정 + 충돌 없음 검사 테스트 추가 |
| 차선 점선이 상태 줄 위로 그려짐 | Canvas가 자기 영역을 잘라내지 않음 | `clipToBounds()` 추가 |
| 경고 차량인데 주행 모드에 궤적이 없음 | RiskFrame이 가리키는 후보가 PredictionFrame에 없음 | 참조가 끊기면 모든 후보 표시 + 테스트 추가 |
| 첫 화면까지 약 29초, 프레임당 0.5~1초 | 메인 스레드에서 JSON 1.5MB 파싱 + 에뮬레이터 SwiftShader | IO 스레드 지연 로딩 + `-gpu host` |
| 계측 실행 중 에뮬레이터 무응답 | 에뮬레이터 자체 정지(adb `dumpsys`도 시간 초과) | 재시작 후 전체 재실행 → 12/12 통과 |
| 루트 빌드에 넣으면 데모가 잘못된 프로젝트를 참조 (최종 점검에서 발견) | `project(":")`가 PRISM 루트를 가리킴 | `project(project.parent!!.path)`로 수정. 가짜 PRISM 루트 빌드에서 확인 |
| 탑뷰 그래픽 1차 구현에서 50대 이상 FPS 38~49% 하락 | 차량마다 매 프레임 Path 12개 생성·그리기 | Path 캐시 → 패스 병합 → 스프라이트 캐시 순으로 최적화(3.4절). 최종은 이전보다 같거나 빠름 |
| 차량 도형 대칭 테스트 실패 | 테스트가 곡선 끝점을 비교에서 빠뜨림(도형 문제 아님) | 곡선 끝점·제어점까지 비교하도록 테스트 수정 |
| 팀 저장소 계약과 필드명 불일치(Push 직전 발견) | PredictionFrame·RiskFrame을 추정 필드명으로 구현 | 팀 스키마·Contracts.kt 기준으로 파서·Mock·테스트 수정, 팀 실제 출력으로 호환성 테스트 추가 |
| `heading_rad` 왕복 변환 불일치 | 내보낼 때 소수 4자리로 반올림 | 6자리로 수정(팀 샘플과 같은 정밀도) |
| 렌더링 비용 벤치마크가 기준(50ms) 초과(50대 63~125ms) | 에뮬레이터 과부하(Pleos 지도 앱 CPU 125%, drivingview 116%). 같은 시점에 이전 버전도 67~130ms로 같았음 → 코드 회귀 아님 | 지도 앱을 에뮬레이터에서 강제 종료한 뒤 재실행 |

## 5. 측정·검증하지 않은 것

- 실차 하드웨어(Pleos 차량 SoC) 성능
- 장시간(수십 분) 메모리 누수
- 실제 엔진 출력과의 통합(Mock JSON만 사용)
- 에뮬레이터 이외 기기에서의 계측 테스트

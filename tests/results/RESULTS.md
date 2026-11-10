# 테스트 결과 — visualization-engine

- 최종 실행: 2026-11-10 (KST)
- 원본 JUnit XML: `junit/` · FPS 원시 로그: `fps_stress_raw.txt` · 렌더링 비용: `render_benchmark.csv`

## 0. 요약

| 구분 | 테스트 수 | 성공 | 실패 |
|---|---|---|---|
| JVM 단위 — 라이브러리 (`tests/unit`) | 32 | 32 | 0 |
| JVM 단위 — 데모 (`demo/src/test`) | 3 | 3 | 0 |
| 계측(에뮬레이터) — Compose UI·해상도·성능 (`tests/android`) | 12 | 12 | 0 |
| **합계** | **47** | **47** | **0** |

실행 명령:

```bash
./gradlew :testDebugUnitTest :demo:testDebugUnitTest   # 35개
./gradlew :connectedDebugAndroidTest                    # 12개, 에뮬레이터 필요
```

## 1. 테스트 환경

| 항목 | 값 |
|---|---|
| 호스트 | Windows 11 Home, JDK 17.0.20.1 |
| 빌드 | Gradle 9.6.0, AGP 9.5.1, Kotlin 2.2.10, Compose BOM 2026.02.01, compileSdk 37, minSdk 24 |
| 에뮬레이터 | AVD `Pleos_Connect_v2.1.0`, Android 14 (API 34), x86_64, 창 없이 실행(`-no-window`) |
| 화면 | 2560×1440, 160 dpi, 60 Hz. 앱은 Pleos 앱 영역 1682×1210(멀티 윈도)에 표시, 사용자 10 |
| GPU | 호스트 GPU(`-gpu host`, Intel Graphics). SwiftShader로는 측정하지 않음 |
| 가상 CPU / RAM | 4 vCPU / 4 GB. Pleos 시스템 앱(drivingview, 지도 등)이 함께 실행됨 |

## 2. 테스트별 결과 (47개, 모두 통과)

### JVM — 라이브러리 (32)

| 스위트 | 테스트 | 검증 내용 |
|---|---|---|
| ContractJsonTest | parsesVehicleFrameV1 | VehicleFrame v1 필드 파싱 |
| | skipsBrokenVehiclesInsteadOfCrashing | NaN·누락·ID 없음·객체 아님 → 해당 차량만 제외 + 경고 4건 |
| | unknownRiskLevelBecomesUnknownNotWarning | 모르는 등급 문자열 → UNKNOWN |
| | roundTripVehicleFrame | 직렬화 → 역직렬화 동일 |
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
| RadarScreenUiTest | riskColorTransitions_NORMAL_WATCH_WARNING_UNKNOWN | **화면 픽셀 색**과 상태 줄 텍스트가 등급에 따라 바뀜 |
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
| 5 | 9.66 | 17.92 |
| 20 | 24.02 | 36.86 |
| 50 | 30.22 | 39.58 |
| 100 | 48.97 | 62.87 |
| 200 | 101.47 | 196.97 |

GPU 합성이 아니라 CPU로 그린 비용이고, ART 최적화가 꺼진 상태입니다. 실제 FPS와 비교하는 값이 아니라 **회귀 감지용**입니다(기준: 50대 평균 50ms 이하).

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

## 5. 측정·검증하지 않은 것

- 실차 하드웨어(Pleos 차량 SoC) 성능
- 장시간(수십 분) 메모리 누수
- 실제 엔진 출력과의 통합(Mock JSON만 사용)
- 에뮬레이터 이외 기기에서의 계측 테스트

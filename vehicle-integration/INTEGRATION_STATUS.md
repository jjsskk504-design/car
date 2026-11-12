# INTEGRATION_STATUS — PART 05 vehicle-integration

기준: 2026-11-10, 브랜치 `feature/vehicle`. 상태 표기는 지시서 기준(DESIGNED / IMPLEMENTED / TESTED / INTEGRATED / VEHICLE-VERIFIED).

## 1. 빌드

| 항목 | 결과 |
|---|---|
| `gradlew test` (JVM 단위·통합) | **PASS** 40/40 (2026-11-12, 엔진 연결 후. JDK 17 `Microsoft 17.0.21`) |
| Compose UI 테스트 (`connectedDebugAndroidTest`, 7개) | 휴대폰 AVD `Medium_Phone_API_36.0` (Android 16): **PASS 7/7**. Pleos 에뮬레이터: 아래 4절 |
| `gradlew assembleDebug` | **성공** |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` (약 47MB) |
| 에뮬레이터 설치 | **성공** (`adb install -r`, Pleos_Connect_v2.1.0) |
| 에뮬레이터 실행·화면 동작 | **성공** — 스크린샷 `tests/reports/screenshots/` |
| Release APK | **미생성**. 서명 키를 저장소에 두지 않으므로 서명 없는 release 는 설치 불가. 제출·시연은 Debug APK 사용, Release 는 팀장이 서명 키로 별도 빌드 |

빌드 환경: Windows 11, JDK 17 (`ms-17.0.17`), Gradle 8.13, AGP 8.7.2, Kotlin 2.0.21, compileSdk 35, minSdk 28, targetSdk 34. Gradle 은 영문 정션 경로 `C:\dev\prism` 에서 실행(README 참고).

## 2. 실제 실행한 Android 환경

- AVD `Pleos_Connect_v2.1.0` — Android 14 (API 34), `pleos_car_emulator` x86_64, 2560×1440, user 10
- AVD `Medium_Phone_API_36.0` — Android 16, 1080×2400 세로 (Simulation Mode UI 테스트·좁은 화면 레이아웃 확인용)
- 실차: 미실행

## 3. 기능별 상태

| 기능 | 상태 | 근거 |
|---|---|---|
| 앱 실행 | TESTED | 에뮬레이터 실행, `01_select_simulation.png` |
| 시뮬레이션 선택 | TESTED | 시나리오 6종 목록, 선택 시 로드 |
| 차량 데이터 로딩 (JSON/JSONL) | TESTED | `AssetScenarioIntegrationTest`, `ContractParserTest` |
| 시뮬레이션 재생 | TESTED | `03_drive_chain_reaction_watch.png` (5.2s, WATCH) |
| 위험도 표시 | TESTED | NORMAL 회색 / WATCH 노랑 / WARNING 빨강 / UNKNOWN 점선+`?`, 텍스트 라벨 병기 |
| 정차 후 리플레이 (seek, 0.5·1·2배) | TESTED | `04_replay_seek_5_5s.png`, `ReplayClockTest`. 정차 전에는 버튼 비활성 확인 |
| 오류 상태 표시 | TESTED | `FixtureErrorHandlingTest` 12개 + UI 테스트 `unknownScenarioShowsErrorScreen` |
| 좁은 화면(분할·휴대폰) 레이아웃 | TESTED | 폭이 부족하면 위아래 배치. `05_phone_portrait_select.png` |
| 단일 JSON 결과 파일 | TESTED | `singleJsonFilesAreAccepted` |
| Simulation Mode | TESTED | SDK 미호출 확인 (상태 UNTESTED 유지) |
| Pleos Context Mode | TESTED (에뮬레이터) | 기어·위치·내비·TTS CONFIRMED (`SDK_COMPATIBILITY.md`) |
| ADAS Mode | 비활성 (설계대로) | 공개 SDK 없음 |
| 라이프사이클 리소스 해제 | TESTED | 백그라운드 전환 시 logcat `released all Pleos SDK sessions` |
| 앱 재시작 | TESTED | `am force-stop` 후 재실행, Pleos 세션 재연결 확인 |
| 04 UI 라이브러리 연결 | TESTED (휴대폰 AVD) | `ui/Radar.kt` 가 04 `RadarScreen` 사용, `PlaceholderRadar` 삭제. 휴대폰 AVD(Android 16, 세로)에서 주행 화면·정차 후 리플레이 확인: `06_sim_chain_drive_radar04.png`, `07_sim_chain_replay_5_3s_radar04.png` (01 예측 궤적, 02 UNKNOWN 표시). Pleos 에뮬레이터는 2026-11-12 시스템 앱 반복 crash 로 검은 화면이라 미확인 |
| 03 시나리오 7종 (`sim_*`) | TESTED (JVM) | Pleos 에뮬레이터 시나리오 목록에 13개(Mock 6 + sim 7) 표시 확인 |
| 실차 | 미진행 | VEHICLE-VERIFIED 아님 |

## 4. 통합 테스트 PASS/FAIL (지시문 7항)

| 검증 항목 | 테스트 | 결과 |
|---|---|---|
| JSON 파싱 | `ContractParserTest.parsesContractExamples`, `reportsLineNumberOfBrokenLine`, `missingRequiredFieldIsError` | PASS |
| 모든 Vehicle ID 연결 | `AssetScenarioIntegrationTest.everyScenarioParsesAndAllIdsLink`, `FixtureErrorHandlingTest.unknownVehicleRefFails` | PASS |
| 좌표계 일치 | `AssetScenarioIntegrationTest.egoIsOriginFacingForwardInEveryFrame`, `ScenarioTimelineTest.nonFiniteNumberIsError` | PASS |
| 시간 동기화 | `predictionAndRiskShareVehicleTimestamps`, `stalePredictionIsDroppedNotMixed`, `replayWalksWholeScenarioMonotonically` | PASS |
| 위험 등급 표시 | `riskScenariosActuallyRaiseRisk`, `unknownLevelOutranksNormalButNotWatch`, 에뮬레이터 화면 | PASS |
| 시나리오 전환 | 에뮬레이터에서 목록 → 시나리오 → 목록 | PASS (수동) |
| 데이터 누락 | `dropoutScenarioHasStaleWindowAndUnknown`, `gapIsPlayableButStale`, `missingVehicleFileFails`, `missingPredictionFileStillPlaysWithWarning` | PASS |
| 앱 재시작 | 에뮬레이터 force-stop → 재실행 | PASS (수동) |
| 리플레이 재생 | `ReplayClockTest` 4개, 에뮬레이터 seek | PASS |
| SDK 연결 실패 처리 | 권한 없는 상태에서 4개 SDK `SecurityException` → FAILED 표시, 앱 crash 없음 | PASS (수동) |
| 라이프사이클 리소스 해제 | onStop → `stop()` 로그 | PASS (수동) |

FAIL: 없음 (JVM). UI 테스트 결과는 아래.

### Compose UI 테스트 (`app/src/androidTest/.../PrismAppUiTest.kt`)

| 테스트 | 휴대폰 AVD (Android 16) |
|---|---|
| listsAllScenarios | PASS |
| playAdvancesTimeAndPauses | PASS |
| riskLevelChangesDuringPlayback | PASS |
| replayOnlyAfterParking | PASS |
| dropoutShowsMissingData | PASS |
| unknownScenarioShowsErrorScreen | PASS |
| adasModeIsDisabled | PASS |

Pleos 에뮬레이터 (`am instrument --user 10`, 콜드 부팅 직후): **5/7 PASS**. 실패 2개(`playAdvancesTimeAndPauses`, `riskLevelChangesDuringPlayback`)는 모두 `No compose hierarchies found` — 차량 홈(`ai.umos.homescreen`)의 TaskView 가 테스트가 띄운 Activity 를 숨겨 화면이 없는 상태. 같은 테스트가 휴대폰 AVD 에서 통과하므로 앱 로직 문제는 아닌 것으로 판단. 실패 테스트는 실행할 때마다 바뀐다(불안정).
- Gradle `connectedDebugAndroidTest` 는 Pleos 에뮬레이터에서 진행되지 않음(테스트를 user 0 에 띄움). Pleos 에서는 APK 2개 설치 후 `adb shell am instrument -w -r --user 10 com.prism.vehicle.app.test/androidx.test.runner.AndroidJUnitRunner` 로 실행.

UI 테스트로 찾아 고친 앱 버그 2건:
1. 레이더 라벨 crash — 표시 범위(-20~60m) 밖 차량의 라벨 좌표에서 `drawText` 가 음수 높이 제약을 만들어 `IllegalArgumentException` 으로 앱 종료 (`sudden_brake`·`dropout`·`rear_approach` 재생 시). 범위 밖 라벨은 그리지 않도록 수정.
2. 좁은 창 레이아웃 — 우측 패널 고정 폭(520~640dp)이 창보다 넓으면 시나리오 목록·레이더 폭이 0. 폭이 패널의 2배 미만이면 위아래 배치로 수정.

## 5. 통합된 엔진 버전

| 엔진 | 버전 | 비고 |
|---|---|---|
| 01 prediction | **INTEGRATED** (main `654516f` 소스) | `sim_*` 시나리오에서 앱 안 실행. 직전 3초 기록 + 03 ego_pose 를 자차 자세로 사용 |
| 02 risk | **INTEGRATED** (main `654516f` 소스, `engine02-kotlin`) | `sim_*` 시나리오에서 앱 안 실행. 03 ego_pose + 직선 도로(차선 폭 4.0m 고정)로 도로 좌표를 만든다. 결과는 대부분 NORMAL, 차선 변경 시나리오에서만 WATCH. 급감속·후방 접근은 02 가 다루지 않아 NORMAL |
| 03 simulation | **INTEGRATED** (`simulation-engine/samples`, v0.2.0) | `:adapters:importSim03` 으로 가져옴 |
| 04 visualization | **INTEGRATED** (main `654516f` 소스) | 휴대폰 AVD 화면 확인 |
| Mock 6종 | 유지 | 기존 테스트·UI 테스트용. 예측·위험 파일이 있으면 엔진 대신 파일을 쓴다 |
| 05 vehicle-integration | `feature/vehicle` 최신 커밋 (태그 미생성) | 이 문서 |

## 6. 오픈소스 조사 (실험 폴더 `현대 공모전/opensource-lab/`, PRISM 저장소 밖)

| 저장소 | commit | 라이선스 | 원본 실행 결과 | 판정 | 가져온 방식과 이유 |
|---|---|---|---|---|---|
| Pleos-Playground/pleos-sample-vehicle-app | 959981d | **없음** | 그대로는 빌드 실패: `Could not find ai.pleos.playground:Vehicle:2.3.1` (Maven 에 2.0.3/2.3.2 만 존재). 실험 폴더에서 2.3.2 로 고치면 `assembleDebug` 성공. 단위 테스트 소스 없음(NO-SOURCE) | reference only | 라이선스가 없어 코드 복사 불가 → SDK 는 Maven 패키지로 설치, 수명주기만 참고해 자체 어댑터 작성 |
| androidx/androidx | 73a8b7b | Apache-2.0 | 전용 빌드 시스템이 필요해 원본 빌드 생략(sparse clone 으로 Compose/Lifecycle 경로만 확인) | adopt (패키지) | Google Maven 공식 배포본이 같은 소스의 릴리스 → 패키지 설치가 가장 안전 |
| Kotlin/kotlinx.serialization | 6f6a2e6 | Apache-2.0 | `:kotlinx-serialization-json:jvmTest` 실패: 빌드가 `IBM_SEMERU` JDK 툴체인을 요구, 이 PC 에 없음 | adopt (패키지) | Maven Central 1.7.3 (Kotlin 2.0.21 호환). 원본 빌드 실패는 툴체인 문제로 라이브러리 품질과 무관 |
| android/architecture-samples | ee66e15 | Apache-2.0 | `testDebugUnitTest assembleDebug` 성공, 단위 테스트 46개 PASS | reference only | ViewModel+StateFlow 구조만 참고. 코드 복사 없음 |

submodule / vendoring 은 쓰지 않았다. 네 저장소 모두 필요한 부분이 공식 배포 패키지로 제공되거나(androidx, serialization, Pleos SDK) 참고만 하면 되기 때문이다.

## 7. Git

- 공유 저장소: https://github.com/qwon4466/PRISM — 이 모듈은 `vehicle-integration/` 만 수정. 작업 브랜치 `feature/vehicle` → `main` PR (main 직접 push 없음)
- 개인 백업 저장소: https://github.com/ftre235/prism-android-app (private, 같은 커밋 내용)

## 8. 남은 문제

1. 01~04 연결 완료(2026-11-12). 남은 것: Pleos 에뮬레이터 화면 확인, Compose UI 테스트 재실행, 휴대폰 가로 화면에서 시나리오 목록이 SDK 패널에 밀려 거의 안 보임(기존 레이아웃 문제), 02 가 급감속·후방 접근을 위험으로 판단하지 않는 점은 02 담당 범위.
2. `FUSED_LOCATION` 런타임 동의 창이 차량 홈 화면에서 앞에 보이지 않음 → 실차/심사 환경 확인 필요.
3. ADAS 객체 권한(`ADAS_TRACKED_OBJECT`)이 에뮬레이터에 있으나 공개 SDK 없음 → 주최 측 문의 필요.
4. 에뮬레이터 Pleos HAL 서비스 반복 crash (앱과 무관).
5. 공통 `contracts/` JSON Schema 미배포 → 계약 v1 은 지시서 예시 기준. 배포되면 `replay/Contracts.kt` 와 대조.
6. 예측·위험 파일 형식을 JSONL(프레임당 한 줄)로 가정. 01/02 가 단일 JSON 으로 주면 어댑터 추가 필요.
7. Release 서명·GitHub Releases 미진행.
8. Pleos 차량 홈 화면이 테스트용 Activity 창을 숨기거나 터치 주입을 막는 경우가 있어 Pleos 에뮬레이터 UI 테스트는 불안정.

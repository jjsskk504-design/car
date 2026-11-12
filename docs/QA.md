# PRISM 품질보증(QA) 문서

| 항목 | 내용 |
|---|---|
| 프로젝트 | PRISM: 주변 차량 연쇄 위험 시각화 차량용 앱 |
| 제출처 | 현대자동차그룹 차량용 앱 개발 공모전 |
| 문서 버전 | 1.0 |
| 시험 대상 | `com.prism.vehicle.app` 0.1.0 (Debug APK), 소스 `develop` 커밋 `3abc62b` |
| 기준 요구사항 | `docs/SRS.md` v1.0 (FR-01~FR-24, NFR-01~NFR-44) |

> 이 문서의 **실제 결과**와 **Pass/Fail** 칸은 시험 수행자가 직접 채웁니다. 예상 결과는 소스 코드의 구현 동작을 기준으로 적었습니다. SRS에서 [미구현] 또는 [목표치]인 요구사항을 검증하는 케이스는 "비고"에 그렇게 표시했습니다.

---

## 1. 테스트 계획

### 1.1 목적
SRS에 정의된 기능·비기능 요구사항이 구현대로 동작하는지 확인합니다. 특히 다음 네 가지를 중점적으로 검증합니다.
- 주행 중 운전자 주의 분산을 최소화하는 화면 제한
- 데이터 누락·오류 상황에서의 안전한 표시
- Pleos SDK 실패 상황에서 앱이 종료되지 않는지
- 오프라인 동작

### 1.2 범위

| 포함 | 제외 |
|---|---|
| 05 앱 전 화면(선택·주행·리플레이·오류)과 모드 전환 | 실차 주행 시험(실차 환경 없음) |
| 데이터 계약 검증(정상·오류 fixture) | ADAS 실센서 연동(미구현, 공개 SDK 없음) |
| 01·02 엔진의 앱 내 실행 결과 연결 | 01·02 알고리즘 자체의 정확도 평가(각 엔진 문서 범위) |
| Pleos SDK 4종(기어·위치·내비·TTS)과 권한 | 서버·네트워크 API(존재하지 않음) |
| 주행 중 / 정차 중 상태별 동작 | 음성 명령(STT, 미구현) |
| 성능(실행·로딩 시간), 안정성, 개인정보, 호환성 | Release 서명 빌드(미진행) |
| 03 시나리오 생성·가져오기 재현성(개발 도구) | |

### 1.3 테스트 환경

| ID | 환경 | 사양 | 용도 |
|---|---|---|---|
| ENV-1 | Pleos Connect 에뮬레이터 | AVD `Pleos_Connect_v2.1.0`, Android 14(API 34), x86_64, **2560×1440** 가로. 차량 홈 런처와 함께 다중 창(앱 영역은 런처 패널 제외) | 기본 시험 환경, Pleos SDK 시험 |
| ENV-2 | 휴대폰 에뮬레이터 | AVD `Medium_Phone_API_36.0`, Android 16, 1080×2400 세로 | 좁은 화면 레이아웃, Pleos 서비스가 없는 환경의 SDK 실패 처리 |
| ENV-3 | 개발 PC (JVM) | Windows 11, JDK 17, Gradle 8.13, **영문 경로**에서 실행 | 단위·통합 테스트, 03 생성 도구 |
| ENV-4 | 실차 | 해당 없음 | 미진행 (향후 과제) |

**도구**
- `adb`: 설치, 실행, logcat, 권한 부여·회수, 시간 측정 `am start -W`
- Android Studio Layout Inspector: 글자 크기와 터치 영역 측정
- Gradle: `test`, `connectedDebugAndroidTest`

> Pleos 에뮬레이터에서 UI 자동 테스트를 돌릴 때는 Gradle 대신 `adb shell am instrument -w -r --user 10 com.prism.vehicle.app.test/androidx.test.runner.AndroidJUnitRunner`를 씁니다.

### 1.4 테스트 방법

| 유형 | 방법 | 대상 |
|---|---|---|
| 기능 시험 | 화면 조작으로 입력·처리·출력 확인. 정상과 예외(빈 값, 잘못된 입력, 권한 거부, 서비스 없음, 네트워크 끊김)를 함께 시험 | FR-01~24 |
| UI 시험 | 화면 배치, 글자 크기, 색 외 구분 요소, 해상도별 겹침·잘림 확인 | FR-07·08·23, NFR-04~05·31~35·38 |
| 예외 시험 | 오류 fixture를 assets에 넣은 시험용 빌드, 권한 회수, Pleos 서비스가 없는 기기 | FR-04·15~19, NFR-20~22 |
| 성능 시험 | `am start -W` 실행 시간, 화면 전환 시간 측정(5회 평균) | NFR-12~17 |
| 주행 중 시나리오 시험 | **주행 상태**와 **정차 상태**를 나눠 같은 기능을 시험 | NFR-01~11 |
| 자동 시험 | JVM 38개, Compose UI 7개 (각 TC 비고에 해당 자동 시험 이름 표기) | 회귀 확인 |

**상태 정의**
- **주행 중**
  - Simulation Mode: 주행 화면의 "정차(P) 가정" 스위치 OFF
  - Pleos Context Mode: 기어 D. 에뮬레이터 Extended Controls → Car data → Gear를 D로 설정
- **정차 중**
  - Simulation Mode: "정차(P) 가정" 스위치 ON
  - Pleos Context Mode: 기어 P

### 1.5 진입·종료 기준
- **진입:** Debug APK를 빌드·설치했고, JVM 단위 테스트(`gradlew test`)가 모두 통과한 상태
- **종료:**
  - 우선순위 **상(High)** TC가 모두 Pass일 것
  - 미해결 결함 중 심각도 **치명(Critical)**·**높음(Major)**이 0건일 것
  - [미구현] 항목을 검증하는 TC의 Fail은 알려진 제약으로 기록하고 종료 판정에서 따로 표시합니다.

### 1.6 시험용 데이터

| 데이터 | 위치 | 용도 |
|---|---|---|
| Mock 6종 | `app/src/main/assets/scenarios/{normal, rear_approach, sudden_brake, lane_change, chain_reaction, dropout}` | 기능·UI 시험 (예측·위험 파일 포함) |
| 03 시뮬레이션 7종 | `app/src/main/assets/scenarios/sim_*` | 01·02 엔진 앱 내 실행 시험 |
| 오류 fixture 11종 | `tests/fixtures/scenarios/{valid, broken_json, bad_schema_version, bad_risk_level, ego_missing, unknown_vehicle_ref, missing_vehicle_file, missing_prediction_file, stale_prediction, gap, single_json}` | 예외 시험. 시험용 빌드의 assets로 복사하고 `index.json`에 추가 |

---

## 2. 테스트 케이스

표기:
- **우선순위**: 상 / 중 / 하
- **자동**: 같은 내용을 검증하는 자동 테스트. 없으면 수동입니다.
- 별도 표시가 없으면 환경은 ENV-1입니다.

### 2.1 앱 실행·모드 (공통)

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-001 | FR-01, FR-03, FR-22 | 앱 첫 실행 (정상) · 우선순위 상 · 자동 `listsAllScenarios` | APK 설치, 앱 미실행 | 1. 런처에서 PRISM 실행 | — | 선택 화면이 표시된다. 모드는 "Simulation Mode"로 선택돼 있다. 시나리오 13개가 보인다. 상단에 "시뮬레이션 결과 재생 · 실시간 감지 아님" 배지가 있다. SDK 패널에 "Simulation Mode 에서는 SDK 를 호출하지 않습니다"가 표시된다. | | |
| TC-002 | FR-01 | 시나리오 목록 없음 (예외: 빈 값) · 상 | `index.json`이 `[]`인 시험용 빌드 설치 | 1. 앱 실행 | `index.json = []` | "assets/scenarios/index.json 에 시나리오가 없습니다"가 빨간 글씨로 표시되고, 앱은 종료되지 않는다. | | |
| TC-003 | FR-01 | 목록 파일 형식 오류 (예외: 잘못된 입력) · 중 | `index.json` 내용이 `{bad` 인 시험용 빌드 | 1. 앱 실행 | `{bad` | TC-002와 같이 빈 목록 안내가 표시되고 crash가 없다. | | |
| TC-004 | FR-02, FR-22 | Pleos Context Mode 전환 · 상 | 선택 화면 | 1. "Pleos Context Mode" 터치 | — | 버튼이 선택 상태로 바뀐다. 상단에 "차량 상태·위치만 실제 SDK, 주변 차량 위험은 시뮬레이션" 배지가 추가된다. SDK 패널 항목이 PENDING을 거쳐 CONFIRMED/FAILED로 바뀐다. | | |
| TC-005 | FR-02, FR-20 | Simulation Mode 복귀 시 SDK 해제 · 중 | Pleos Context Mode | 1. "Simulation Mode" 터치<br>2. `adb logcat -s PrismPleosSdk` 확인 | — | 로그에 "released all Pleos SDK sessions"가 찍히고, Pleos 배지가 사라진다. | | |
| TC-006 | FR-02, NFR-11 | ADAS Mode 비활성 · 상 · 자동 `adasModeIsDisabled` | 선택 화면 | 1. "ADAS Mode" 버튼 터치 시도 | — | 버튼이 비활성이라 모드가 바뀌지 않는다. "ADAS Mode: 공식 접근 권한 확인 전 비활성" 문구가 표시된다. | | |
| TC-007 | FR-22 | MOCK 배지 표시 구분 · 중 | 선택 화면 | 1. `chain_reaction` 열기 → 배지 확인<br>2. 목록으로 돌아가 `sim_chain_reaction` 열기 → 배지 확인 | — | Mock 시나리오에서는 "MOCK 데이터" 배지가 보이고, `sim_*`에서는 보이지 않는다. 두 경우 모두 "실시간 감지 아님" 배지는 유지된다. | | |

### 2.2 시나리오 로딩·계약 검증

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-010 | FR-03, FR-04 | Mock 시나리오 로드 (정상) · 상 | 선택 화면 | 1. `chain_reaction` 터치 | `chain_reaction` | "불러오는 중…" 뒤에 주행 화면으로 이동한다. 제목 "화물차 급감속 연쇄 위험", 시간 "0.0 / 10.0 s", 레이더가 표시된다. | | |
| TC-011 | FR-04, FR-05, FR-06 | 03 시나리오 로드 (엔진 앱 내 계산) · 상 · 자동 `sim03ScenariosAreComputedByEngines` | 선택 화면 | 1. `sim_chain_reaction` 터치<br>2. 재생 | `sim_chain_reaction` | 주행 화면이 표시된다. 레이더에 01 예측 궤적이 그려진다. 배너가 "위험 정보 없음"이 아니라 등급 문구로 표시된다(02 결과). 제목은 "[03] 선두 화물차 급감속 …"이다. | | |
| TC-012 | FR-04 | 전 시나리오 로드 · 상 · 자동 `everyScenarioParsesAndAllIdsLink` | — | 1. 13개 시나리오를 차례로 열고 닫기 | 13종 | 모두 주행 화면으로 열리고 오류 화면이 0건이다. | | |
| TC-013 | FR-04, FR-15 | 없는 시나리오 (예외) · 상 · 자동 `unknownScenarioShowsErrorScreen`, `unknownScenarioFails` | `index.json`에 폴더가 없는 ID를 넣은 시험용 빌드 | 1. 해당 항목 터치 | `no_such_id` | 오류 화면에 "시나리오 'no_such_id' 를 재생할 수 없습니다"와 "[ERROR] … 파일 없음"이 표시된다. | | |
| TC-014 | FR-04, FR-15 | VehicleFrame 파일 누락 · 상 · 자동 `missingVehicleFileFails` | fixture `missing_vehicle_file` | 1. 시나리오 터치 | — | 오류 화면에 "vehicle_frames.jsonl: 파일 없음"이 표시된다. | | |
| TC-015 | FR-04, NFR-33 | 깨진 JSON 줄 (잘못된 입력) · 상 · 자동 `brokenJsonFailsWithLine` | fixture `broken_json` | 1. 시나리오 터치 | 깨진 JSONL 줄 | 오류 화면에 `파일명:줄번호`와 "JSON 파싱 실패"가 표시되고, 앱은 종료되지 않는다. | | |
| TC-016 | FR-04, NFR-39 | 지원하지 않는 스키마 버전 · 상 · 자동 `badSchemaVersionFails` | fixture `bad_schema_version` | 1. 시나리오 터치 | `schema_version: "2.0"` | 오류 "지원하지 않는 schema_version '2.0' (v1.x 만 지원)"이 표시된다. | | |
| TC-017 | FR-04 | 자차 누락 · 중 · 자동 `egoMissingFails` | fixture `ego_missing` | 1. 시나리오 터치 | — | 오류 "ego 차량 'ego' 누락"이 표시된다. | | |
| TC-018 | FR-04 | 존재하지 않는 차량 참조 · 중 · 자동 `unknownVehicleRefFails` | fixture `unknown_vehicle_ref` | 1. 시나리오 터치 | — | 오류 "vehicle_id '…' 가 VehicleFrame 에 없음"이 표시된다. | | |
| TC-019 | FR-04 | 잘못된 위험 등급 값 · 중 · 자동 `badRiskLevelFails` | fixture `bad_risk_level` | 1. 시나리오 터치 | `risk_level: "DANGER"` | 오류 화면에 파싱 실패가 표시된다. | | |
| TC-020 | FR-04 | 예측 파일 누락 (부분 데이터) · 중 · 자동 `missingPredictionFileStillPlaysWithWarning` | fixture `missing_prediction_file` | 1. 시나리오 터치<br>2. 정차 후 리플레이에서 검증 경고 확인 | — | 주행 화면으로 열려 재생된다. 리플레이의 "데이터 검증 경고"에 예측 관련 WARNING이 나온다. | | |
| TC-021 | FR-04 | 시각이 맞지 않는 예측 프레임 · 중 · 자동 `stalePredictionIsDroppedNotMixed` | fixture `stale_prediction` | 1. 시나리오 터치 → 리플레이 경고 확인 | — | 재생은 가능하다. "같은 timestamp 의 VehicleFrame 이 없어 표시하지 않음" 경고가 나오고, 다른 시각의 궤적이 섞여 그려지지 않는다. | | |
| TC-022 | FR-04 | 단일 JSON 결과 파일 수용 · 하 · 자동 `singleJsonFilesAreAccepted` | fixture `single_json` | 1. 시나리오 터치 | `prediction_frame.json`, `risk_frames.json` | 주행 화면으로 정상 재생된다. | | |
| TC-023 | FR-15 | 오류 화면에서 복귀 · 중 | TC-014 수행 후 | 1. "시나리오 목록" 터치 | — | 선택 화면으로 돌아가고, 다른 시나리오를 정상적으로 열 수 있다. | | |
| TC-024 | FR-06 | ego_pose 없는 시나리오의 02 생략 · 중 | `ego_pose.jsonl`·위험 파일이 없는 시험용 시나리오 | 1. 시나리오 터치 → 재생<br>2. 리플레이 경고 확인 | — | 배너에 "위험 정보 없음 (RiskFrame 없음)"이 표시된다. 경고 "ego_pose.jsonl: 파일 없음: 02 위험도를 계산하지 않음"이 나온다. 위험 등급을 추측해 표시하지 않는다. | | |
| TC-025 | FR-03 | 시나리오 연속 빠른 터치 (중복 입력) · 하 | 선택 화면 | 1. 서로 다른 두 시나리오를 0.2초 간격으로 연속 터치 | — | 앱이 종료되지 않고 주행 화면이 하나 표시된다. 표시된 제목과 레이더 데이터가 서로 일치한다. | | |

### 2.3 주행 중 상태

사전조건(공통): Simulation Mode에서 "정차(P) 가정"을 OFF로 둡니다. Pleos 케이스는 기어 D입니다.

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-030 | FR-07 | 레이더 표시 · 상 | `chain_reaction` 주행 화면 | 1. 재생<br>2. 3초 시점 관찰 | — | 자차(파란색, "자차" 라벨)가 고정 위치에 있고, 주변 차량이 차선 위에 탑뷰로 표시된다. 예측 궤적과 위험 영역이 차량을 가리지 않는다. | | |
| TC-031 | FR-09 | 재생·일시정지 · 상 · 자동 `playAdvancesTimeAndPauses` | 주행 화면, 0.0 s | 1. "재생" 터치, 2초 대기<br>2. "일시정지" 터치, 2초 대기 | — | 1단계에서 시간이 약 2초 증가한다. 2단계에서는 시간이 멈춰 있다. | | |
| TC-032 | FR-09 | 끝까지 재생 (경계값) · 중 · 자동 `advancesBySpeedAndStopsAtEnd` | 주행 화면 | 1. 재생 후 끝까지 대기<br>2. "처음부터" 터치 | — | 10.0 / 10.0 s에서 자동으로 멈추고 버튼이 "처음부터"로 바뀐다. 다시 누르면 0.0 s부터 재생된다. | | |
| TC-033 | FR-08 | 위험 등급 변화 · 상 · 자동 `riskLevelChangesDuringPlayback` | `chain_reaction` | 1. 재생하며 배너 문구 기록 | — | 배너가 "NORMAL"에서 "WATCH · 주의 관찰"을 거쳐 "WARNING · 주변 차량 위험"으로 바뀐다. 레이더 위험 차량 표시도 같은 시점에 바뀐다. | | |
| TC-034 | FR-08, NFR-05 | 색 외 구분 요소 · 상 | `chain_reaction`, WARNING 시점 | 1. 배너와 레이더 위험 차량을 확인 | — | 배너에 등급 텍스트가 있다. 레이더 위험 차량에 경고 아이콘(빨간 삼각형)과 "경고" 칩이 있다. 색을 빼도 등급을 구분할 수 있다. | | |
| TC-035 | FR-14, NFR-22 | 데이터 누락 구간 · 상 · 자동 `dropoutShowsMissingData` | `dropout` | 1. 재생해 누락 구간까지 진행 | — | 배너에 "데이터 누락 — 현재 상태 알 수 없음"이 표시된다. 해당 구간에서 "NORMAL"은 표시되지 않는다. | | |
| TC-036 | FR-07, FR-14 | 판단 불가 차량 표시 · 중 | `sim_sensor_dropout` 또는 `dropout` | 1. 관측 누락 차량이 생기는 시점 관찰 | — | 해당 차량이 파란 점선과 "?"(판단 보류)로 표시되고, 정상 회색으로 표시되지 않는다. | | |
| TC-037 | NFR-01 | 주행 화면 정보 제한 · 상 | 임의 시나리오 주행 화면 | 1. 화면 전체를 점검 | — | 원인 코드, TTC, 시간 슬라이더, 배속 버튼, 분석 패널이 **없다**. 레이더, 제목, 배너 한 줄, 시간, 재생/목록 버튼만 있다. | | |
| TC-038 | FR-10, NFR-02 | 주행 중 리플레이 차단 (Simulation) · 상 · 자동 `replayOnlyAfterParking` | 정차 가정 OFF | 1. "정차 후 리플레이" 터치 시도 | — | 버튼이 비활성이다. "정차(P) 상태에서만 리플레이를 열 수 있습니다. '정차 가정' 을 켜세요."가 표시된다. | | |
| TC-039 | FR-10, FR-16 | 주행 중 리플레이 차단 (Pleos, 기어 D) · 상 | Pleos Context Mode, 기어 D, 주행 화면 | 1. 버튼 상태와 문구 확인 | 기어 D | 버튼이 비활성이고 "현재 기어 DRIVE. P단에서만 리플레이를 열 수 있습니다."가 표시된다. | | |
| TC-040 | FR-19, NFR-09 | WARNING 진입 음성 안내 · 상 | Pleos Context Mode, TTS CONFIRMED, 기어 D, `chain_reaction` | 1. 재생해 WARNING 진입 시점까지 진행 | — | "주변 차량 위험 상황입니다. 시뮬레이션 안내입니다."가 **1회** 발화된다. | | |
| TC-041 | FR-19 | WARNING 유지 중 반복 발화 없음 · 중 | TC-040 직후 | 1. WARNING 상태가 지속되는 동안 관찰 | — | 추가 발화가 없다. WATCH로 내려갔다가 다시 WARNING이 되면 1회 더 발화한다. | | |
| TC-042 | FR-19 | Simulation Mode 음성 미발화 · 중 | Simulation Mode, `chain_reaction` | 1. WARNING 시점까지 재생 | — | 음성 안내가 발화되지 않는다. | | |
| TC-043 | NFR-04 | 주행 화면 글자 크기 · 중 | 주행 화면 | 1. Layout Inspector로 배너·제목·시간 글자 크기 측정 | — | 배너 24sp, 제목 20sp, 시간 16sp | | |
| TC-044 | NFR-04a | 주행 화면 보조 문구 최소 크기 · 하 | 주행 화면, 정차 가정 OFF | 1. 차단 사유 문구 글자 크기 측정 | — | 16sp 이상 (비고: [목표치], 현재 구현은 13sp) | | |
| TC-045 | NFR-08 | 주행 중 터치 조작 제한 · 중 | Pleos Context Mode, 기어 D | 1. 주행 화면에서 "일시정지", "시나리오 목록" 터치 | — | 요구사항(NFR-08): 시스템 주행 제한에 따라 불필요한 조작이 제한된다. (비고: [미구현], 현재 구현은 조작 가능) | | |
| TC-046 | FR-09 | 목록 복귀 시 재생 정지 · 중 | 재생 중 | 1. "시나리오 목록" 터치<br>2. 같은 시나리오 다시 열기 | — | 다시 열면 0.0 s, 일시정지 상태로 시작한다. | | |
| TC-047 | FR-07 | 표시 범위 밖 차량 (회귀) · 상 | — | 1. `sudden_brake`, `dropout`, `rear_approach`를 각각 끝까지 재생 | — | 앱이 종료되지 않는다. 범위 밖 차량 라벨 때문에 발생했던 `IllegalArgumentException` 회귀가 없다. | | |

### 2.4 정차 중 상태

사전조건(공통): Simulation Mode에서 "정차(P) 가정"을 ON으로 둡니다. Pleos 케이스는 기어 P입니다.

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-050 | FR-10, FR-11 | 정차 후 리플레이 진입 (Simulation) · 상 · 자동 `replayOnlyAfterParking` | 주행 화면 | 1. "정차(P) 가정" ON<br>2. "정차 후 리플레이" 터치 | — | 버튼이 활성화되고, 리플레이 화면 "리플레이 · <제목>"으로 이동한다. 재생은 일시정지 상태다. | | |
| TC-051 | FR-10, FR-16 | 정차 후 리플레이 진입 (Pleos, 기어 P) · 상 | Pleos Context Mode, 기어 P | 1. 주행 화면에서 버튼 터치 | 기어 P | 버튼이 활성이고 리플레이로 이동한다. | | |
| TC-052 | FR-10 | 기어를 읽지 못함 (예외) · 중 | Pleos Context Mode, Vehicle SDK FAILED (ENV-2 또는 권한 회수) | 1. 주행 화면에서 버튼 확인 | — | 버튼이 비활성이고 "기어 상태를 읽지 못해 정차 여부를 확인할 수 없습니다 (<사유>)"가 표시된다. | | |
| TC-053 | FR-11 | 시간 이동 · 상 | 리플레이, `chain_reaction` | 1. 슬라이더를 5.5 s로 이동 | 5.5 s | 시간 표시가 5.5 s로 바뀌고, 레이더·배너·분석 패널이 5.5 s 시점 데이터로 바뀐다. | | |
| TC-054 | FR-11 | 시간 이동 경계값 · 중 · 자동 `seekIsClamped` | 리플레이 | 1. 슬라이더를 맨 왼쪽, 맨 오른쪽으로 이동 | 0 / 끝 | 0.0 s와 전체 길이에서 각각 멈춘다. 범위를 넘는 값은 없다. | | |
| TC-055 | FR-11 | 배속 · 중 · 자동 `advancesBySpeedAndStopsAtEnd` | 리플레이, 0.0 s | 1. 0.5x로 4초 재생<br>2. 2x로 4초 재생 | 0.5x / 2x | 0.5x는 약 2초, 2x는 약 8초 진행한다. 선택한 배속 버튼이 강조된다. | | |
| TC-056 | FR-11 | 프레임 나이 표시 · 하 | 리플레이 | 1. 시간 문구 확인 | — | "x.x / y.y s · 프레임 나이 Nms"가 표시된다. 누락 구간에서는 N이 `step_ms × 2`(200)보다 크다. | | |
| TC-057 | FR-12 | 분석 패널 집계 일치 · 상 | 리플레이, `chain_reaction`, WARNING 시점 | 1. 레이더의 경고·주의·판단 보류 차량 수 세기<br>2. 패널 대수와 비교 | — | "전체 위험 수준"과 경고/주의/판단 보류 대수가 레이더와 일치한다. 주요 위험 카드에 원인 문구와 상대 위치가 표시된다. | | |
| TC-058 | FR-12 | 위험 없음 상태 · 중 | 리플레이, `normal` | 1. 패널 확인 | — | "현재 주의가 필요한 차량이 없습니다", 경고·주의·판단 보류 모두 0대, 센서 상태 "정상 수신" | | |
| TC-059 | FR-12 | 연쇄 위험 분석 표시 · 중 | 리플레이, `chain_reaction` | 1. "연쇄 위험 분석" 영역 확인 | — | 현재 구현에서는 "확인된 연쇄 관계 없음"이 표시된다. (비고: SRS FR-12 제약. RiskFrame에 `source_vehicle_ids`가 없음) | | |
| TC-060 | FR-12, FR-22 | 데이터 출처 문구 · 중 | 리플레이 | 1. `chain_reaction` 패널 하단 확인<br>2. `sim_chain_reaction` 패널 하단 확인 | — | 1은 "MOCK 데이터 · 실제 차량 안전 시스템이 아닙니다", 2는 "03 시뮬레이션 결과 · 실제 차량 안전 시스템이 아닙니다"가 표시된다. | | |
| TC-061 | FR-12, FR-14 | 정차 중 관측 누락 분석 · 상 | 리플레이, `sim_sensor_dropout`, 누락 구간 | 1. 패널의 센서·데이터 상태 확인 | — | "관측 누락 N대 · 해당 차량 판단 보류"(또는 데이터 지연 문구)와 판단 한계 안내가 표시된다. 해당 차량은 판단 보류로 집계된다. | | |
| TC-062 | FR-13 | 위험 근거 목록 · 중 | 리플레이, `chain_reaction` 4 s | 1. "위험 근거 (02 RiskFrame)" 목록 확인 | — | 차량별 "ID 등급 원인코드 TTC x.xs"가 표시된다. TTC가 없는 차량은 `TTC null`이다. | | |
| TC-063 | FR-13 | 이벤트 주석 · 하 | 리플레이, `chain_reaction` | 1. "이벤트 주석 (03 manifest)" 확인 | — | "2.5s truck 급감속", "3.2s car_01 추종 감속", "4.5s car_03 회피 차선 변경" | | |
| TC-064 | FR-13 | 검증 경고 목록 · 하 | 리플레이, `dropout` | 1. "데이터 검증 경고 N건" 확인 | — | 누락 구간 경고("데이터 누락 구간 N개 …")가 1건 이상 나온다. | | |
| TC-065 | FR-11 | 리플레이 닫기 · 중 | 리플레이 | 1. "닫기" 터치 | — | 주행 화면으로 돌아가고, 재생은 일시정지 상태다. | | |
| TC-066 | FR-10, NFR-03 | 정차 해제 시 리플레이 차단 (Simulation) · 상 | 주행 화면, 정차 가정 ON | 1. 정차 가정 OFF | — | "정차 후 리플레이" 버튼이 즉시 비활성으로 바뀐다. | | |
| TC-067 | FR-10, NFR-03a | 리플레이 중 기어 P→D (Pleos) · 상 | Pleos Context Mode, 기어 P, 리플레이 화면 | 1. 에뮬레이터에서 기어를 D로 변경<br>2. 1초 대기 | 기어 D | 요구사항(NFR-03a): 리플레이가 닫히고 주행 화면으로 돌아간다. (비고: [미구현], 현재 구현은 리플레이 유지. 결함으로 기록할 것) | | |

### 2.5 Pleos SDK·권한·수명주기

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-070 | FR-16~18, FR-20, NFR-37 | SDK 4종 연결 (정상) · 상 | ENV-1, `FUSED_LOCATION` 승인 | 1. Pleos Context Mode 선택<br>2. SDK 패널 확인 | — | Vehicle 연결, 기어, 위치, 내비, TTS가 CONFIRMED이다. 기어 값(예: PARK)과 위치 좌표가 표시된다. ADAS 객체는 UNSUPPORTED이다. | | |
| TC-071 | FR-17, NFR-28 | 위치 권한 요청 창 · 상 | 권한 미승인 (`pm revoke --user 10 com.prism.vehicle.app pleos.car.permission.FUSED_LOCATION`) | 1. Pleos Context Mode 선택 | — | 위치 권한 동의 창이 앞에 표시된다. (비고: Pleos 홈 화면에서 창이 가려지는 사례가 기록돼 있음. 확인 필요) | | |
| TC-072 | FR-17, NFR-20 | 위치 권한 거부 (예외) · 상 | TC-071 동의 창 | 1. "거부" 선택 | 거부 | 위치는 FAILED(`SecurityException …`)로 표시되고, 기어·내비·TTS는 CONFIRMED를 유지한다. 앱이 종료되지 않는다. | | |
| TC-073 | FR-17 | 위치 권한 승인 후 재시작 · 중 | TC-071 동의 창 | 1. "허용" 선택 | 허용 | SDK 세션이 다시 시작되고 위치가 CONFIRMED로 바뀌며 좌표가 표시된다. | | |
| TC-074 | FR-19 | TTS 테스트 버튼 · 중 | Pleos Context Mode, TTS CONFIRMED | 1. "TTS 테스트" 터치 | — | "PRISM 음성 안내 테스트입니다"가 발화된다. | | |
| TC-075 | FR-16~19, NFR-20 | Pleos 서비스가 없는 기기 (예외) · 상 | ENV-2 (휴대폰 AVD) | 1. Pleos Context Mode 선택<br>2. 시나리오 재생 | — | SDK 항목이 FAILED와 사유로 표시되고, 앱은 종료되지 않는다. 재생 기능은 정상이다. 정차 판정 불가 문구가 표시된다. | | |
| TC-076 | FR-21, NFR-24 | 백그라운드 전환 · 상 | Pleos Context Mode, 재생 중 | 1. 홈 버튼으로 앱을 백그라운드로 전환<br>2. logcat 확인 | — | 재생이 일시정지된다. 로그에 "released all Pleos SDK sessions"가 찍힌다. | | |
| TC-077 | FR-21 | 포그라운드 복귀 · 상 | TC-076 이후 | 1. 앱으로 복귀 | — | SDK 항목이 다시 CONFIRMED로 연결된다. 재생 위치는 유지되고 일시정지 상태다. | | |
| TC-078 | FR-01, NFR-24 | 강제 종료 후 재실행 · 중 | 임의 상태 | 1. `adb shell am force-stop com.prism.vehicle.app`<br>2. 재실행 | — | 선택 화면과 Simulation Mode로 시작한다(상태 미보존이 사양). crash가 없다. | | |
| TC-079 | FR-20 | 내비 상태 수신 · 하 | Pleos Context Mode, 내비 경로 안내 중 (가능한 경우) | 1. SDK 패널 내비 항목 확인 | — | "onNavigationStatus=…"가 표시된다. 경로 안내가 없으면 "initialize 성공, 상태 응답 대기"가 표시된다. | | |

### 2.6 비기능 (성능·안정성·보안·호환성)

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-080 | NFR-19 | 네트워크 차단 (터널·음영 지역) · 상 | ENV-1, 비행기 모드 ON (`adb shell cmd connectivity airplane-mode enable`) | 1. 앱 실행<br>2. 시나리오 재생, 리플레이, Pleos Context Mode, TTS 테스트 | — | 모든 기능이 네트워크 연결 시와 같게 동작한다. | | |
| TC-081 | NFR-19 | 재생 중 네트워크 끊김 · 중 | `chain_reaction` 재생 중 | 1. 재생 중 비행기 모드 ON | — | 재생, 위험 표시, 음성 안내에 영향이 없다. | | |
| TC-082 | NFR-15 | 콜드 스타트 시간 · 중 | 앱 종료 상태 | 1. `adb shell am start -W -n com.prism.vehicle.app/.MainActivity` 5회 반복 | — | TotalTime 평균 3,000 ms 이하 (비고: [목표치]) | | |
| TC-083 | NFR-16 | 시나리오 로딩 시간 · 중 | 선택 화면 | 1. `sim_chain_reaction` 터치부터 주행 화면 표시까지 측정(화면 녹화) 5회 | — | 평균 2초 이하 (비고: [목표치], 엔진 계산 포함) | | |
| TC-084 | NFR-14 | 로딩 중 UI 응답 · 하 | 선택 화면 | 1. 시나리오 터치 직후 목록 스크롤 | — | 로딩 중에도 스크롤이 동작한다. ANR이 없다. | | |
| TC-085 | NFR-12, NFR-17 | 재생 부드러움 · 하 | `chain_reaction` | 1. `adb shell dumpsys gfxinfo com.prism.vehicle.app` 수집 후 10초 재생 | — | Janky frames 비율 10% 이하 (비고: [목표치]) | | |
| TC-086 | NFR-20 | 장시간 반복 재생 · 중 | `chain_reaction` | 1. 30분간 재생→끝→처음부터를 반복 | — | crash와 ANR이 없고, 메모리(`dumpsys meminfo`)가 계속 증가하지 않는다. (비고: [목표치]) | | |
| TC-087 | NFR-23 | 재현성 · 중 · 자동 `replayWalksWholeScenarioMonotonically` | — | 1. `sim_chain_reaction`을 6.2 s로 이동해 배너·패널 기록<br>2. 앱 재시작 후 반복 | 6.2 s | 두 번의 결과(등급, 대수, 주요 위험 차량)가 같다. | | |
| TC-088 | NFR-25 | 개인정보: 저장 없음 · 상 | Pleos Context Mode에서 위치 수신 후 | 1. `adb shell run-as com.prism.vehicle.app ls -R files shared_prefs databases` | — | 앱이 만든 데이터 파일이 없다(디렉터리가 없거나 비어 있음). | | |
| TC-089 | NFR-19, NFR-25 | 개인정보: 외부 전송 없음 · 상 | — | 1. `adb shell dumpsys package com.prism.vehicle.app \| grep permission` | — | `android.permission.INTERNET`이 없다. | | |
| TC-090 | NFR-11, NFR-27 | 권한 최소화 · 상 | — | 1. APK 권한 목록 확인 (`aapt dump permissions`) | — | Pleos 권한은 `CAR_DRIVE`, `FUSED_LOCATION`, `NAVI_ROUTE`, `TTS_SERVICE` 4개다. `CONTROL_*`과 `PRIVATE_*`는 0개다. | | |
| TC-091 | NFR-26 | 백업 차단 · 하 | — | 1. 매니페스트 `allowBackup` 확인 | — | `allowBackup=false` | | |
| TC-092 | FR-23, NFR-38 | 차량 화면 레이아웃 · 상 | ENV-1, 차량 홈 런처와 다중 창 | 1. 선택·주행·리플레이·오류 화면을 각각 캡처 | — | 겹침·잘림·폭 0인 영역이 없다. 주행·리플레이는 좌우 배치다. | | |
| TC-093 | FR-23, NFR-38 | 좁은 화면 레이아웃 · 중 | ENV-2 세로 | 1. 같은 4개 화면 캡처 | — | 위아래 배치로 바뀌고, 시나리오 목록과 레이더 폭이 0이 아니다. 리플레이 분석 패널은 380 dp 높이로 스크롤된다. | | |
| TC-094 | NFR-38 | 화면 회전·구성 변경 · 중 | ENV-2, 리플레이 5 s 위치 | 1. 화면을 가로로 회전 | — | 리플레이 화면과 재생 위치가 유지된다(ViewModel). | | |
| TC-095 | NFR-34, NFR-35 | 용어·목록 표기 일관성 · 하 | — | 1. 배너 등급 표기와 분석 패널 표기 비교<br>2. 목록 표기 확인 | — | 요구사항: 같은 용어로 표기하고, 목록에 제목을 표시한다. (비고: [목표치], 현재는 영문/한글 혼용과 ID 표시) | | |

### 2.7 개발 도구 (03 시나리오 생성·가져오기)

| TC-ID | 관련 FR/NFR | 테스트 항목 | 사전조건 | 테스트 절차 | 입력값 | 예상 결과 | 실제 결과 | Pass/Fail |
|---|---|---|---|---|---|---|---|---|
| TC-096 | FR-24, NFR-23 | 시나리오 생성 재현성 · 중 · 자동 03 `SeedTest` | ENV-3, `simulation-engine/` | 1. `gradlew generate`<br>2. `git status samples/` | YAML 7종 | `samples/` 변경이 0건이다(커밋된 결과와 같음). | | |
| TC-097 | FR-24 | 03 결과 가져오기 · 중 | ENV-3, `vehicle-integration/` | 1. `gradlew :adapters:importSim03`<br>2. `gradlew test` | — | `assets/scenarios/sim_*` 7개와 `index.json`이 갱신되고, JVM 테스트가 모두 통과한다. | | |
| TC-098 | NFR-42 | JVM 회귀 테스트 · 상 | ENV-3 | 1. `vehicle-integration`에서 `gradlew test` | — | 38개 모두 통과 | | |
| TC-099 | NFR-42 | Compose UI 회귀 테스트 · 상 | ENV-2 (또는 ENV-1 `am instrument --user 10`) | 1. `gradlew connectedDebugAndroidTest` | — | 7개 모두 통과 | | |

---

## 3. 요구사항 추적표

### 3.1 기능 요구사항 → 테스트 케이스

| 요구사항 | 내용 | 테스트 케이스 |
|---|---|---|
| FR-01 | 앱 실행과 초기화 | TC-001, TC-002, TC-003, TC-078 |
| FR-02 | 앱 모드 선택 | TC-004, TC-005, TC-006 |
| FR-03 | 시나리오 목록·선택 | TC-001, TC-010, TC-025 |
| FR-04 | 로딩·계약 검증 | TC-010~TC-022 |
| FR-05 | 01 예측 앱 내 실행 | TC-011 |
| FR-06 | 02 위험 판정 앱 내 실행 | TC-011, TC-024 |
| FR-07 | 주행 화면 레이더 | TC-030, TC-036, TC-047 |
| FR-08 | 위험 등급 배너 | TC-033, TC-034 |
| FR-09 | 재생 제어 | TC-031, TC-032, TC-046 |
| FR-10 | 정차(P) 판정 | TC-038, TC-039, TC-050, TC-051, TC-052, TC-066, TC-067 |
| FR-11 | 정차 후 리플레이 | TC-050, TC-053, TC-054, TC-055, TC-056, TC-065 |
| FR-12 | 위험 분석 패널 | TC-057, TC-058, TC-059, TC-060, TC-061 |
| FR-13 | 근거·이벤트·경고 목록 | TC-062, TC-063, TC-064 |
| FR-14 | 데이터 누락 처리 | TC-035, TC-036, TC-061 |
| FR-15 | 오류 화면 | TC-013, TC-014, TC-023 |
| FR-16 | SDK 기어 | TC-039, TC-051, TC-070, TC-075 |
| FR-17 | SDK 위치 | TC-070, TC-071, TC-072, TC-073 |
| FR-18 | SDK 내비 | TC-070, TC-079 |
| FR-19 | SDK 음성 안내 | TC-040, TC-041, TC-042, TC-074 |
| FR-20 | SDK 상태 패널 | TC-005, TC-070, TC-079 |
| FR-21 | 수명주기 자원 관리 | TC-076, TC-077 |
| FR-22 | 데이터 출처 표시 | TC-001, TC-004, TC-007, TC-060 |
| FR-23 | 적응형 레이아웃 | TC-092, TC-093 |
| FR-24 | 시나리오 생성·가져오기 | TC-096, TC-097 |

### 3.2 비기능 요구사항 → 테스트 케이스

| 요구사항 | 내용 | 테스트 케이스 |
|---|---|---|
| NFR-01 | 주행 중 정보 제한 | TC-037 |
| NFR-02 | 정차 중에만 상세 조작 | TC-038, TC-039, TC-050 |
| NFR-03 / 03a | 정차 해제 시 리플레이 종료 | TC-066 / TC-067 [미구현] |
| NFR-04 / 04a | 글자 크기 | TC-043 / TC-044 [목표치] |
| NFR-05 | 색 외 구분 | TC-034 |
| NFR-06, NFR-07 | 터치 횟수·조작 시간 | TC-037 (화면 구성), 수동 측정 [목표치] |
| NFR-08 | 시스템 주행 제한 연동 | TC-045 [미구현] |
| NFR-09 | 음성 안내 1회 | TC-040, TC-041 |
| NFR-10 | 음성 명령 | 시험 대상 아님 [미구현] |
| NFR-11 | 차량 제어 금지 | TC-006, TC-090 |
| NFR-12, NFR-13 | 재생 주기·데이터 해상도 | TC-031, TC-085 |
| NFR-14 | 비동기 로딩 | TC-084 |
| NFR-15 | 콜드 스타트 | TC-082 [목표치] |
| NFR-16 | 로딩 시간 | TC-083 [목표치] |
| NFR-17 | 렌더링 | TC-085 [목표치] |
| NFR-18 | 탐색 성능 | TC-053 (기능), 자동 `snapshotPicksLatestFrameAndMatchingRisk` |
| NFR-19 | 네트워크 독립 | TC-080, TC-081, TC-089 |
| NFR-20 | SDK 실패 격리 | TC-072, TC-075, TC-086 |
| NFR-21 | 데이터 오류 격리 | TC-013~TC-019 |
| NFR-22 | 누락 시 안전 측 표시 | TC-035 |
| NFR-23 | 재현성 | TC-087, TC-096 |
| NFR-24 | 백그라운드 자원 해제 | TC-076, TC-077, TC-078 |
| NFR-25 | 위치 정보 최소 처리 | TC-088, TC-089 |
| NFR-26 | 백업 차단 | TC-091 |
| NFR-27 | 최소 권한 | TC-090 |
| NFR-28 | 런타임 동의 | TC-071, TC-072, TC-073 |
| NFR-29 | 비밀 정보 미포함 | TC-074 (ON_DEVICE TTS 키 없이 동작), 소스 검토 |
| NFR-30 | 차량 데이터 범위 | TC-070, TC-090 |
| NFR-31~33 | 테마·터치 크기·오류 메시지 | TC-001, TC-043, TC-015 |
| NFR-34, NFR-35 | 용어 통일·목록 표기 | TC-095 [목표치] |
| NFR-36, NFR-37 | OS·Pleos 호환 | TC-070, TC-075 |
| NFR-38 | 해상도 | TC-092, TC-093, TC-094 |
| NFR-39 | 계약 호환 | TC-016, TC-022 |
| NFR-40 | 실차 호환 | 시험 대상 아님 (ENV-4 없음) |
| NFR-41 | 모듈 경계 | TC-097, 소스 검토 |
| NFR-42 | 자동 테스트 | TC-098, TC-099 |
| NFR-43 | 라이선스 고지 | 문서 검토 (`THIRD_PARTY_NOTICES.md`) |
| NFR-44 | Release 서명 | 시험 대상 아님 [미구현] |

---

## 4. 결함(버그) 리포트 양식

```markdown
### BUG-<번호>: <한 줄 요약>

| 항목 | 내용 |
|---|---|
| 보고일 / 보고자 | YYYY-MM-DD / 이름 |
| 관련 TC / 요구사항 | TC-xxx / FR-xx, NFR-xx |
| 빌드 | com.prism.vehicle.app 0.1.0 (debug), 커밋 <hash> |
| 환경 | ENV-1 Pleos_Connect_v2.1.0 (Android 14, 2560×1440) / ENV-2 / 기타 |
| 앱 모드 / 차량 상태 | Simulation · Pleos Context / 주행 중(기어 D·정차 가정 OFF) · 정차 중(P·ON) |
| 시나리오 / 재생 시각 | 예: sim_chain_reaction / 6.2 s |
| 심각도 | 치명(Critical) / 높음(Major) / 보통(Minor) / 낮음(Trivial) |
| 우선순위 | 상 / 중 / 하 |
| 재현율 | 항상 / 간헐(n/10) / 1회 |

**재현 절차**
1.
2.
3.

**예상 결과**
(SRS 요구사항 또는 TC의 예상 결과)

**실제 결과**

**첨부**
- 스크린샷 / 화면 녹화:
- 로그: `adb logcat -d -s PrismPleosSdk AndroidRuntime` 결과
- crash 로그: `adb logcat -b crash -d`

**원인 분석 (개발자 작성)**

**조치 / 확인**
| 조치 커밋 | 확인자 | 확인일 | 재시험 TC | 결과 |
|---|---|---|---|---|
| | | | | |
```

**심각도 기준**

| 심각도 | 기준 | 예 |
|---|---|---|
| 치명 (Critical) | 앱 종료·멈춤, 또는 위험 상태를 정상으로 잘못 표시 | 데이터 누락 구간에 "NORMAL" 표시, 재생 중 crash |
| 높음 (Major) | 주요 기능 불가, 또는 주행 중 화면 제한 위반 | 정차가 아닌데 리플레이가 열림, SDK 실패 시 앱 종료 |
| 보통 (Minor) | 기능은 되지만 표시·동작이 사양과 다름 | 분석 패널 대수와 레이더 불일치, 문구 오류 |
| 낮음 (Trivial) | 외관·오탈자 | 정렬, 여백, 맞춤법 |

---

## 5. 시험 결과 요약 (수행 후 작성)

| 구분 | 전체 | Pass | Fail | 미수행 | 비고 |
|---|---|---|---|---|---|
| 기능 (2.1~2.5) | 69 | | | | |
| 비기능 (2.6) | 16 | | | | |
| 개발 도구 (2.7) | 4 | | | | |
| **합계** | **89** | | | | |

| 미해결 결함 | 심각도 | 관련 요구사항 | 처리 계획 |
|---|---|---|---|
| | | | |

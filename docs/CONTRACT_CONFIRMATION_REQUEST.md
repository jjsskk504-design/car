# [확인 요청] visualization-engine이 사용하는 데이터 계약 필드

- 받는 사람: 통합 담당자(vehicle-integration), 예측 엔진·위험 판정 엔진 담당자
- 보내는 사람: 시각화·UI/UX 엔진 담당 (`visualization-engine/`, 브랜치 `feature/visualization`)
- 작성일: 2026-11-10

## 배경

PRISM 저장소의 `contracts/` JSON Schema를 받지 못한 상태에서 **팀 공지의 필드 목록만으로** 시각화 엔진을 구현했습니다.
- 공통 계약은 바꾸지 않았습니다.
- 시각화 엔진의 해석이 스키마와 다르면 `visualization-engine/model/ContractJson.kt` 한 파일만 맞추면 됩니다.
- 아래 표의 **확인 필요** 항목에 답을 주시거나, 실제 스키마 파일 경로를 알려 주세요.

상태 표기:
- **공지 확정**: 팀 공지에 필드명까지 명시된 것
- **추정**: 공지에 의미만 있고 필드명·형식은 시각화 엔진이 정한 것
- **제안**: 공지에 없지만 시각화에 필요해서 추가를 제안하는 것

---

## 1. VehicleFrame v1

필드명은 모두 공지 그대로 사용합니다. **값의 해석**만 확인이 필요합니다.

| 필드 | 상태 | 시각화 엔진의 해석 | 확인 필요 |
|---|---|---|---|
| `timestamp_ms` | 공지 확정 | 프레임 시각(ms). 같은 시계로 `nowMs`를 받아 **500ms 이상 지나면 데이터 지연으로 보고 경고를 보류** | ① 시계 종류(epoch인지 monotonic인지) ② `nowMs`를 통합 측이 같은 시계로 줄 수 있는지 ③ 프레임 주기(10 Hz로 가정) |
| `ego_id` | 공지 확정 | `vehicles[]` 안의 자차 ID | 자차가 `vehicles[]`에 **항상 포함되는지**(없으면 원점에 기본 크기 자차를 그림) |
| `vehicles[].id` | 공지 확정 | 문자열 트래킹 ID. 프레임 간 같은 ID = 같은 차량 | 문자열인지 숫자인지, 센서 재획득 시 ID가 바뀌는지 |
| `vehicles[].x`, `y` | 공지 확정 | 자차 기준, x = 오른쪽, y = 전방(m), 자차 중심 원점 | 원점이 자차 **중심**인지 앞범퍼·후륜축인지 |
| `vehicles[].vx`, `vy` | 공지 확정 | **지면 속도**를 자차 축으로 표현한 값(m/s). 자차 항목의 `vy` ≈ 자차 속도 | **지면 속도인지 자차 대비 상대 속도인지** — 예측 궤적을 상대 운동으로 바꿀 때 사용 |
| `vehicles[].heading_rad` | 공지 확정 | 자차 좌표계에서 **+x(오른쪽) 기준 반시계**, 전방 = π/2 | **0 = 전방인지, 0 = 오른쪽인지**, 회전 방향 |
| `vehicles[].length_m`, `width_m` | 공지 확정 | 차체 치수 | 없을 때 기본값을 줄 것인지(지금은 값이 없으면 해당 차량을 제외) |
| `vehicles[].lane_id` | 공지 확정 | 표시·디버그용으로만 사용. null 허용 | 번호 규칙(왼쪽부터 1인지, 자차 차선이 0인지), 차선 밖·미확정 값 |

**계약에 없어서 호출 측이 따로 주는 값**

| 값 | 시각화 엔진에서의 처리 | 요청 |
|---|---|---|
| 차선 수·차선 폭·자차 차선 | `RoadConfig(laneCount, laneWidthM, egoLaneIndex)` 파라미터로 받음. 직선 도로 가정 | 통합 측이 Pleos NaviHelper 등으로 줄 수 있는지, 아니면 계약에 넣을지 |
| 정차 여부 | `ReplayScreen(isVehicleStopped)`, `RadarScreen(mode)` | 통합 측이 Pleos Vehicle SDK(기어·속도)로 판단해 전달 |

---

## 2. PredictionFrame

공지 내용은 "차량 ID, 예측 시점, 미래 위치·방향, 여러 후보 궤적"입니다. **필드명과 구조는 전부 추정**입니다.

| 추정 필드 | 형식 | 의미 | 확인 필요 |
|---|---|---|---|
| `timestamp_ms` | long | 예측 기준 시각(= 입력 VehicleFrame 시각) | 필드명 |
| `predictions[]` | array | 차량별 예측 | 필드명 |
| `predictions[].vehicle_id` | string | 대상 차량 | 필드명 |
| `predictions[].candidates[]` | array | 후보 궤적 목록 | 후보 최대 개수 |
| `candidates[].candidate_id` | string | 후보 ID(RiskFrame이 참조) | 필드 존재 여부 |
| `candidates[].maneuver` | string | `KEEP_LANE` / `LANE_CHANGE_LEFT` / `LANE_CHANGE_RIGHT` / `BRAKE` (모르는 값은 원문 표시) | 행동 종류 목록 |
| `candidates[].points[]` | array | 미래 위치 | 간격(0.5초로 가정)과 길이(3~5초) |
| `points[].t_offset_ms` | long | 기준 시각부터의 경과 시간 | **상대 시간인지 절대 시각인지** |
| `points[].x`, `y` | float | 미래 위치 | **좌표 기준**: 예측 시점의 자차 기준 지면 좌표로 가정. 화면에는 자차 이동량(현재 속도 유지 가정)을 빼서 상대 운동으로 그림 |
| `points[].heading_rad` | float, 선택 | 미래 방향 | 1절과 같은 규약인지 |
| (없음) | — | 후보별 확률 | 확률을 보낼 경우 **보정된 값인지**. 현재 화면에는 확률을 표시하지 않음 |

---

## 3. RiskFrame

공지 내용은 "차량 ID, 위험 등급, 원인 코드, 위험 영역 및 궤적"이고, 등급은 `NORMAL / WATCH / WARNING / UNKNOWN`입니다.

| 필드 | 상태 | 형식 | 의미 | 확인 필요 |
|---|---|---|---|---|
| `timestamp_ms` | 추정 | long | 판정 기준 시각. VehicleFrame과 **300ms 넘게 차이 나면 경고를 보류** | 필드명, VehicleFrame과 같은 시각인지 |
| `risks[]` | 추정 | array | 차량별 판정 | 필드명 |
| `risks[].vehicle_id` | 추정 | string | 대상 차량 | 필드명 |
| `risks[].level` | 공지 확정(값) / 추정(필드명) | string | 4개 등급. **모르는 문자열은 UNKNOWN 처리** | 필드명, 대소문자 |
| `risks[].cause_codes[]` | 추정 | string 배열 | 원인 코드(한 차량에 여러 개 가능) | **배열인지 단일 값인지**, 코드 목록(4절) |
| `risks[].risk_region[]` | 추정 | `{x, y}` 다각형 | 위험 점유 영역 | 다각형인지 원·박스인지, **좌표 기준**(지면인지 자차 상대 운동인지) |
| `risks[].trajectory_candidate_id` | 추정 | string, 선택 | "위험 궤적" = 판단 근거가 된 PredictionFrame 후보 ID. 주행 모드에서는 이 후보만 강조 | 궤적을 ID 참조로 줄지, 점 목록을 직접 줄지 |
| `risks[].source_vehicle_ids[]` | **제안** | string 배열, 선택 | 이 위험을 유발한 차량(연쇄 위험 화살표). 없으면 화살표를 그리지 않음 | 계약에 추가 가능 여부 |
| `risks[].message` | **제안** | string, 선택 | 사람이 읽는 설명. 없으면 원인 코드로 문구 생성 | 추가 여부 |

**의미 확인이 필요한 경우**

| 상황 | 현재 시각화 동작 | 확인 필요 |
|---|---|---|
| VehicleFrame에는 있고 RiskFrame에는 없는 차량 | NORMAL(회색)로 표시 | "평가하지 않음"과 "정상"을 구분해야 하는지 |
| RiskFrame에는 있고 VehicleFrame에는 없는 차량 | 등급과 관계없이 **UNKNOWN**(점선 영역 + ?), 빨간색 사용 안 함 | 센서 누락 차량을 위험 엔진이 계속 보내는지 |
| `UNKNOWN`의 의미 | "판단 보류"로 표시하고 경고 억제 | 위험 엔진이 UNKNOWN을 언제 보내는지 |
| RiskFrame 자체가 없음 | 모든 차량 회색 + "위험 정보 없음" 안내 | — |

---

## 4. 원인 코드 (위험 판정 엔진과 합의 필요)

시각화 엔진에 잠정 등록한 코드입니다(`model/CauseCatalog.kt`). 목록에 없는 코드는 원문을 그대로 표시합니다.

| 코드 | 화면 문구 |
|---|---|
| `LEAD_HARD_BRAKE` | 앞 차량 급감속 |
| `TTC_LOW` | 충돌 여유 시간 짧음 |
| `HEADWAY_SHORT` | 차간 거리 부족 |
| `REAR_CLOSING` | 후방 차량 접근 |
| `PREDICTED_CUT_IN` | 내 차로 진입 예상 |
| `LANE_INTRUSION` | 차선 침범 |
| `CHAIN_DECELERATION` | 연쇄 감속 |
| `SENSOR_DROPOUT` | 관측 끊김 |
| `LOW_CONFIDENCE` | 예측 신뢰도 낮음 |

---

## 5. 시각화 엔진 자체 포맷 (공통 계약 아님)

- 리플레이 파일 `prism.visualization.replay/0.1`은 시각화 엔진 내부 포맷입니다. `frames[]`마다 `vehicle_frame`, `prediction_frame`, `risk_frame`을 계약 형식 그대로 담습니다.
- 공통 계약으로 올릴 필요는 없습니다. 다만 통합 측이 주행 기록을 저장할 포맷이 따로 정해져 있다면 알려 주세요.

## 6. 예시

`visualization-engine/docs/examples/`에 연쇄 위험 시나리오 4.5초 시점의 실제 입력 예시가 있습니다(`input_*.json`).
- 시각화 엔진의 해석 결과는 `output_scene_summary.json`에 있습니다.
- 이 값은 Mock 데이터이고, 실제 위험 판정 결과가 아닙니다.

## 요청 정리

1. `contracts/` 스키마 파일 경로를 알려 주시거나, 위 **확인 필요** 열에 답을 주세요.
2. `source_vehicle_ids`, `message` 필드를 RiskFrame에 추가해도 되는지 검토해 주세요.
3. 원인 코드 목록을 위험 판정 엔진 담당자와 함께 확정해 주세요.
4. 루트 Gradle 파일 변경(모듈 포함과 플러그인 버전 선언, 별도 문서 참고)을 승인해 주세요.

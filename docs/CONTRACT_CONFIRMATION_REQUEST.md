# [확인 요청] visualization-engine 데이터 계약 — 남은 질문과 제안

- 받는 사람: 통합 담당자(vehicle-integration), 예측 엔진·위험 판정 엔진 담당자
- 보내는 사람: 시각화·UI/UX 엔진 담당 (`visualization-engine/`, 브랜치 `feature/visualization`)
- 작성일: 2026-11-11 (팀 저장소 `qwon4466/PRISM` main `e3f4b5a` 기준으로 갱신)

## 1. 반영 완료

팀 저장소의 실제 계약에 맞춰 파서를 고쳤습니다. 다른 엔진 폴더와 공통 계약은 바꾸지 않았습니다.

| 프레임 | 기준 | 상태 |
|---|---|---|
| VehicleFrame v1 | `simulation-engine/schema/vehicle_frame.v1.schema.json` | 반영. `lane_id` 문자열, `schema_version`, `coordinate_system` 포함 |
| PredictionFrame 1.0 | `prediction-engine/src/prism_predict/schema/prediction_frame.schema.json` | 반영. `trajectories[{mode, confidence, points[{dt_ms, x, y, heading_rad}]}]` |
| RiskFrame 1.0 | `vehicle-integration/replay/.../Contracts.kt` | 반영. `risk_objects[{vehicle_id, risk_level, reason_codes, ttc_s, risk_zones[{dt_ms, polygon}]}]` |

**검증**: `TeamContractCompatibilityTest`로 다른 엔진의 실제 출력을 읽기만 해서 확인했습니다.
- vehicle-integration 시나리오 6개(`chain_reaction`, `dropout`, `lane_change`, `normal`, `rear_approach`, `sudden_brake`): 경고 0건으로 해석됐습니다.
- 위의 6개 시나리오에서 WATCH·WARNING 차량 183건의 등급이 화면에 그대로 표시됐습니다.
- `dropout` 시나리오의 UNKNOWN은 경고색으로 표시되지 않았습니다.
- prediction-engine 예시와 simulation-engine 샘플도 경고 0건으로 해석됐습니다.

규약도 샘플과 일치하는 것을 확인했습니다.
- 전방 주행 `heading_rad` = π/2
- `vx`/`vy` = 지면 속도
- 예측 좌표 = 기준 시점 자차 좌표계에 고정

## 2. 확인이 필요한 질문

| # | 질문 | 시각화 엔진의 현재 처리 |
|---|---|---|
| 1 | **RiskFrame JSON Schema 파일**이 없습니다(Kotlin 데이터 클래스만 있음). 공통 `contracts/`에 올라올 예정인가요? | vehicle-integration의 `Contracts.kt` 필드명을 기준으로 삼았습니다 |
| 2 | `risk_zones[].polygon` 좌표는 예측과 같이 **기준 시점 자차 좌표계(지면)**인가요? | 그렇다고 보고, 화면에는 `dt_ms` × 자차 속도를 빼서 상대 운동으로 그립니다 |
| 3 | `dt_ms = 0`인 risk_zone(현재 시점 영역)을 허용하나요? 예측 계약은 1~5000ms입니다 | 허용합니다. 센서 누락 차량의 "현재 위치 추정 영역"을 Mock에서 `dt_ms = 0`으로 보냅니다 |
| 4 | 센서 누락 차량은 VehicleFrame에서 빠지고 RiskFrame에만 `UNKNOWN`으로 남나요? 이때 위치를 알 방법이 있나요? | 위치를 모르면 그리지 않고 상태 줄의 "판단 보류 N대"에만 집계합니다. `risk_zones`가 있으면 그 영역을 점선으로 표시합니다 |
| 5 | VehicleFrame에 있는데 RiskFrame에 없는 차량은 "정상"인가요, "평가하지 않음"인가요? | 정상(회색)으로 표시합니다 |
| 6 | 공통 `contracts/` 확정 시기 | 확정되면 `model/ContractJson.kt`만 맞추면 됩니다 |

## 3. 계약 추가 제안 (모두 선택 필드 — 없어도 동작함)

| 필드 | 위치 | 용도 | 없을 때 |
|---|---|---|---|
| `source_vehicle_ids[]` | RiskFrame `risk_objects[]` | 이 위험을 유발한 차량. 연쇄 위험 화살표(화물차 → 끼어드는 차량)를 그림 | 화살표를 그리지 않음 |
| `trajectory_mode` | RiskFrame `risk_objects[]` | 위험 판단에 쓰인 예측 궤적의 `mode`. 주행 모드에서 그 궤적만 강조 | 해당 차량의 궤적을 모두 표시 |
| `message` | RiskFrame `risk_objects[]` | 사람이 읽는 설명 | `reason_codes`로 문구를 만듦 |
| `vehicle_class` | VehicleFrame `vehicles[]` | 탑뷰 그래픽(승용차·SUV·화물차) 선택 | `length_m`·`width_m`으로 추정(7m 이상 화물차, 5.15m 이상 또는 폭 2.05m 이상 SUV) |

vehicle-integration 파서는 `ignoreUnknownKeys = true`라서, 이 필드를 보내도 기존 파싱은 깨지지 않습니다.
시각화 엔진은 `ContractJson.toJson(riskFrame, includeExtensions = false)`로 계약 필드만 내보낼 수도 있습니다.

## 4. 원인 코드 (팀 샘플 기준 등록)

| 코드 | 화면 문구 |
|---|---|
| `HARD_BRAKING` | 급감속 |
| `FRONT_CLOSING` | 앞 차량과 간격 좁아짐 |
| `REAR_CLOSING` | 후방 차량 접근 |
| `CHAIN_DECEL` | 연쇄 감속 |
| `LANE_CHANGE_INTRUSION` | 내 차로 진입 |
| `PATH_OVERLAP` | 예상 경로 겹침 |
| `SENSOR_DROPOUT` | 관측 끊김 |

새 코드가 생기면 알려 주세요. 등록 전에는 코드 원문이 그대로 표시됩니다.

## 5. 시각화 엔진 자체 포맷 (공통 계약 아님)

- 리플레이 파일 `prism.visualization.replay/0.1`은 시각화 엔진 데모용 포맷입니다. 프레임마다 계약 프레임 3개를 그대로 담습니다.
- 팀 시나리오 형식(`vehicle_frames.jsonl` / `prediction_frames.jsonl` / `risk_frames.jsonl`)은 `ReplayJson.fromJsonl(...)`로 바로 읽습니다.

## 요청 정리

1. 2절의 질문 1~5에 답해 주세요.
2. 3절의 선택 필드 4개를 계약에 넣을지 검토해 주세요.
3. 루트 Gradle 설정이 필요하면 승인해 주세요. 지금 팀 저장소는 엔진마다 독립 빌드라서, 이 모듈도 `visualization-engine/` 안에서 단독으로 빌드됩니다.

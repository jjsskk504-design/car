# PRISM Part 01 — Prediction Engine

VehicleFrame의 과거 2~5초 기록을 받아 각 주변 차량의 향후 1~5초 후보 궤적을 만드는 독립 엔진입니다. 이 폴더는 Part 01만 담당하며 위험도, TTC, 시뮬레이터, UI, Pleos 연동은 포함하지 않습니다.

## 1분 이해

- 입력: 시간순 VehicleFrame JSONL. 단위는 `m`, `m/s`, `rad`, `ms`입니다.
- 처리: 과거 관측을 최신 자차 좌표계(`x=오른쪽`, `y=전방`)로 맞춘 뒤 CV/CA 기준선을 계산합니다.
- 출력: 차량별 `lane_keep`, `braking`, `lane_change_left`, `lane_change_right` 후보가 담긴 PredictionFrame JSON입니다.
- 신뢰도: 학습·보정 근거가 없으므로 `confidence`는 거짓 숫자를 만들지 않고 `null`입니다.
- 안전 한계: 연구·공모전용 예측 보조 모듈이며 차량 안전 시스템이나 실시간 충돌 방지 장치를 대체하지 않습니다.

처음 보는 분은 [EASY_GUIDE_KO.md](EASY_GUIDE_KO.md)를 먼저 읽으면 됩니다.

## 설치와 재현 명령 3개

PowerShell, Python 3.10 기준입니다.

```powershell
py -3.10 -m venv .venv; .\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\prism-predict.exe export --input examples\vehicle_frames.jsonl --output build\prediction_frame.json
```

설치 버전을 완전히 고정하려면 가상환경에서 먼저 `pip install -r requirements-lock.txt`를 실행한 뒤 `pip install --no-deps -e .`을 사용합니다.

## 입력

한 줄에 VehicleFrame 하나인 JSONL입니다. 실제 공통 저장소의 JSON Schema가 추가되면 그 스키마가 최종 기준이며, 이 엔진 내부 사본을 임의로 공통 계약으로 승격하지 않습니다.

```json
{"timestamp_ms":2000,"ego_id":"ego","vehicles":[{"id":"car_01","x":1.0,"y":10.0,"vx":0.0,"vy":5.0,"heading_rad":1.5708,"length_m":4.5,"width_m":1.8,"lane_id":"L1"}]}
```

필수 검사는 다음과 같습니다.

- 타임스탬프 정렬, 중복 거부, 유한한 숫자 여부
- 같은 시점의 차량 ID 중복과 `ego_id` 누락 거부
- 같은 ID의 길이·폭이 0.5m 넘게 바뀌면 ID 재할당 의심 오류
- 관측 누락은 0으로 채우지 않고 사용 가능한 관측만 연결
- 출력 차량 ID가 최신 입력에 실제 존재하는지 확인

## 출력

[examples/prediction_frame.json](examples/prediction_frame.json)은 실제 CLI로 만든 예입니다. 모든 `dt_ms`는 원본 `timestamp_ms`에 대한 상대 시간이며, 점 좌표는 원본 시점의 자차 좌표계에 고정됩니다. 내부 JSON Schema는 `src/prism_predict/schema/prediction_frame.schema.json`에 있습니다.

## 알고리즘

- CV(Constant Velocity): 현재 속도가 그대로 유지된다고 가정합니다.
- CA(Constant Acceleration): 최근 두 유효 관측의 속도 차이로 가속도를 구합니다. 관측이 하나뿐이면 CV로 안전하게 폴백합니다.
- 속도·가속도 상한: 기본 `55m/s`, `8m/s²`로 비정상 폭주를 막습니다.
- 감속 후보: 기본 `4m/s²`로 감속하며 정지 뒤 뒤로 이동하지 않습니다.
- 차선 변경 후보: 기본 차선 폭 3.5m, 4초 smoothstep으로 급격한 횡이동을 피합니다.
- 곡률: `road_curvature_per_m`을 주면 원호 위치와 방향을 함께 계산합니다.

## 좌표계 변환의 중요한 한계

VehicleFrame v1에는 절대 자차 위치와 yaw rate가 없습니다. 기본 CLI는 자차 속도와 첫 프레임 대비 `heading_rad` 변화로 자차 포즈를 추정합니다. 상위 시스템에 측정된 자차 world pose가 있다면 Python API의 `normalize_history_to_current_ego(frames, ego_poses=...)`에 `Pose2D` 목록을 전달하는 편이 정확합니다. 이 한계를 숨기지 않고 [DELIVERY_REPORT.md](DELIVERY_REPORT.md)에 기록했습니다.

## 다른 엔진과 연결

1. 시뮬레이터/수집기가 VehicleFrame을 JSONL로 전달합니다.
2. 이 엔진의 CLI 또는 `build_prediction_frame()`을 호출합니다.
3. 생성된 PredictionFrame JSON을 위험도 엔진 입력으로 전달합니다.

다른 엔진이 없어도 `examples/`의 Mock JSON으로 독립 실행할 수 있습니다. 공통 계약 변경이 필요하면 이 폴더에서 고치지 말고 팀장에게 승인 요청 후 공통 Schema와 비교해야 합니다.

## 관련 문서

- [EASY_GUIDE_KO.md](EASY_GUIDE_KO.md): 비개발자도 읽을 수 있는 설명
- [DATA_CONTRACT.md](DATA_CONTRACT.md): 입력·출력과 단위
- [OPEN_SOURCE_REVIEW.md](OPEN_SOURCE_REVIEW.md): 외부 저장소 사전 검증
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): 출처·라이선스
- [DELIVERY_REPORT.md](DELIVERY_REPORT.md): 테스트, 한계, 인수인계


# PRISM Part 01 인수보고서

작성일: 2026-11-11

## 완료 범위

- VehicleFrame JSONL 로드, 검증, 타임스탬프 정렬
- ID 매칭, 누락 관측 skip, 크기 급변을 이용한 ID 재할당 방어
- 이동·회전 자차 좌표계를 최신 시점으로 변환
- CV/CA, 속도·가속도 상한, 1~5초 horizon
- 차선 유지, 감속, 좌/우 차선 변경 후보
- 차선 폭, 변경 시간, 도로 곡률 설정
- PredictionFrame 생성과 JSON Schema 검증
- Mock 입력 4종과 실제 출력 예제
- 쉬운 설명, 데이터 계약, 오픈소스 검토, 라이선스 고지

포함하지 않은 범위: TTC, 위험 등급, 위험 영역, 시뮬레이터, UI, Pleos SDK, 차량 제어, 신경망 학습/추론.

## 실제 테스트 결과

### 성공

| 명령/대상 | 결과 |
|---|---|
| `python -m pytest -q` | `18 passed` |
| 기본 CLI 예제 | PredictionFrame 생성 및 Schema 검사 통과 |
| `straight.jsonl` | 4개 후보 생성 성공 |
| `missing.jsonl` | 누락 관측 0 채움 없이 생성 성공 |
| `braking.jsonl` | CA 감속 관측과 braking 후보 생성 성공 |
| `lane_change.jsonl` | 횡속도 입력과 smooth lane-change 후보 생성 성공 |
| 좌표 고정 예제 | 평행이동, 90도 회전, 이동+회전 3종 통과 |

### 실패 또는 미실행

- Trajectron++ 현재 Python 3.10 의존성 설치 확인 실패: 구형 NumPy 고정 문제. 제품에 채택하지 않음.
- PyTorch 전체 소스 빌드 미실행: P0/P1 범위에 불필요한 대형 네이티브 빌드.
- 실제 차량 센서·실도로·Pleos 환경 테스트 미실행: 이 엔진 범위 밖이며 해당 하드웨어/SDK 연결이 없음.

## 알려진 한계

1. VehicleFrame v1만으로는 절대 자차 pose/yaw rate를 정확히 알 수 없습니다. 기본값은 dead reckoning 추정이며, 가능하면 측정 pose를 Python API로 주입해야 합니다.
2. 차선 중심선·지도·곡률이 입력 계약에 없어서 기본 곡률은 0입니다. 설정으로 곡률을 줄 수 있지만 실제 지도 기반은 아닙니다.
3. 후보의 확률은 보정되지 않았으므로 `confidence=null`입니다.
4. CV/CA는 급격한 운전자 의도 변화, 충돌 후 운동, 미끄러짐을 모델링하지 않습니다.
5. 내부 PredictionFrame Schema는 제공 문서를 구현한 로컬 검사본입니다. 실제 공통 Schema와 다르면 공통 파일을 임의 수정하지 말고 이슈를 등록해야 합니다.

## 변경 파일 목록

```text
.gitignore
pyproject.toml
requirements-lock.txt
README.md
EASY_GUIDE_KO.md
DATA_CONTRACT.md
OPEN_SOURCE_REVIEW.md
THIRD_PARTY_NOTICES.md
DELIVERY_REPORT.md
src/prism_predict/__init__.py
src/prism_predict/history.py
src/prism_predict/coordinates.py
src/prism_predict/baseline.py
src/prism_predict/modes.py
src/prism_predict/export.py
src/prism_predict/schema/prediction_frame.schema.json
tests/test_cv.py
tests/test_ca.py
tests/test_transform.py
tests/test_missing.py
tests/test_history.py
tests/test_modes.py
tests/test_export.py
examples/vehicle_frames.jsonl
examples/prediction_frame.json
examples/cases/straight.jsonl
examples/cases/missing.jsonl
examples/cases/braking.jsonl
examples/cases/lane_change.jsonl
```

## 다음 엔진에 연결하는 방법

- 파일 연결: CLI 출력 JSON을 Risk Engine 입력으로 전달
- Python 연결: `build_prediction_frame(normalized_frames, config)` 결과 dict 전달
- 메시지 연결: dict를 JSON 직렬화하여 같은 필드 구조를 유지

어떤 방식이든 `timestamp_ms`, `dt_ms`, 좌표축, 단위를 바꾸지 않아야 합니다. Risk Engine이 준비되지 않아도 `examples/prediction_frame.json`을 Mock으로 사용할 수 있습니다.

## Git 인계

통합 PRISM 저장소의 `feature/prediction` 개인 브랜치에 `prediction-engine/` 폴더로 제출합니다. `main`에 직접 Push하지 않으며, 최종 병합 상태는 원격 브랜치와 Pull Request 기록을 기준으로 확인합니다.


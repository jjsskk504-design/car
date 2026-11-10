# DELIVERY_REPORT — PART 03 simulation-engine

- 작성일: 2026-11-10
- 브랜치: `feature/simulation`
- 상태: **TESTED** (로컬에서 실제로 테스트를 실행함. 다른 엔진과 연결한 INTEGRATED 단계는 아님)

## 1. 프롬프트 항목별 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 1. HighwayEnv·INTERACTION·CARLA 조사, HighwayEnv를 기본으로 선정 | DONE | 3절 |
| 2. HighwayEnv 별도 clone, LICENSE 확인, venv 설치, highway·merge·intersection 실행 | DONE | roundabout까지 4종 PASS. `docs/opensource_lab/highway_env_run.log` |
| (추가) INTERACTION·CARLA clone 및 실행 시도 | INTERACTION은 DONE, CARLA는 BLOCKED | 3절, `docs/opensource_lab/` |
| 3. 내부 상태(ID·위치·속도·heading·차선·크기·시간)를 VehicleFrame v1로 바꾸는 어댑터, 좌표 변환과 테스트 | DONE | adapters/, test_transform.py, test_units.py |
| 4. 시나리오 7종, 결정적 이벤트 생성기, 강제·자연 행동 구분 | DONE | scenarios/, manifest의 `origin: "forced"`와 `provenance.natural_behavior` |
| 5. 지정한 파일명 7개와 메타데이터 | DONE | samples/, test_expected_output_files, test_manifest_metadata_complete |
| 6. 같은 seed면 같은 결과, ID·단위·좌표계·시간·크기·heading 검증 | DONE | 4절 |
| 7. 2D 재생 스크립트와 영상 | GIF·PNG는 DONE, MP4는 SKIP | ffmpeg가 설치되어 있지 않음. ffmpeg가 있으면 스크립트가 MP4도 자동으로 만듦 |
| 8. INTERACTION 약관 확인, 원본 데이터 업로드 금지 | PARTIAL | 도구 코드는 BSD-3. 데이터는 별도 신청과 약관이 필요한데 **약관 원문은 확인하지 못함**. 그래서 데이터는 쓰지 않았고 업로드하지 않음 |
| 9. 지정한 폴더 구조, README, THIRD_PARTY_NOTICES | DONE | |
| Git Pull Request | **BLOCKED** | 팀 GitHub 원격 저장소가 없음. 로컬 브랜치에 커밋만 함. main에 push하지 않음 |

## 2. 실행 환경

- Windows 11 Pro (10.0.26200), Python 3.13.2
- highway-env 1.12.1 (PyPI). 실험용 clone은 1.12.2.dev0, 커밋 37c84a3
- ffmpeg 없음

## 3. 오픈소스 검토 (실험 clone 위치: PRISM 밖 `opensource-lab/`, 로그는 `docs/opensource_lab/`)

| 저장소 | 라이선스 | 유지보수 | clone · 실행 | 판정 | 방식 · 이유 |
|---|---|---|---|---|---|
| HighwayEnv | MIT | 활발(2026-10-30) | clone 37c84a3. highway·merge·intersection·roundabout **PASS** | **adopt** | **pip 패키지 설치**(버전 고정). 고칠 코드가 없어서 submodule이나 vendoring은 필요 없음. gym 액션 루프 대신 `Road`를 직접 구성해 차량 배치와 이벤트 시각을 결정적으로 통제 |
| INTERACTION (도구 코드) | BSD-3 (FZI, KIT, UC Berkeley) | 정체(2022-07, 053213d) | clone 후 venv에 requirements 설치. 결과는 아래 표 | **reference only** | 코드는 데이터를 읽고 그리는 도구일 뿐이라 PRISM에 넣을 필요가 없음. 실제 데이터는 약관을 확인하기 전까지 재배포 불가로 간주 |
| CARLA | MIT (에셋은 별도) | 활발(18ffa06, ue58-dev) | clone 644MB. 클라이언트 설치 **FAIL**, 서버 실행 **BLOCKED** | **선택 도구, v0.1에는 쓰지 않음** | 클라이언트 휠은 Python 3.10~3.12용만 있음(이 PC는 3.13). 서버는 RAM 32GB와 VRAM 16GB 이상이 필요한데 이 PC는 RAM 15.3GB, RTX 4060 Laptop, C: 여유 5GB |

**INTERACTION 원본 실행 결과** (데이터 없이, 저장소에 포함된 `.TestScenarioForScripts` 샘플 사용)

| 항목 | 결과 | 비고 |
|---|---|---|
| `utils/check_imports.py` | PASS | lanelet2가 없어서 대체 경로(map_vis_without_lanelet)를 사용 |
| `python -m unittest` | README대로 `python/` 폴더에서 실행하면 FAIL, **저장소 루트에서 실행하면 PASS** | import 경로가 `python.utils...`라서 생기는 문제. 문서와 실제가 다름 |
| `main_load_track_file.py` | PASS | 2개 트랙, 9.9초 |
| `main_visualize_data.py` | 그대로 실행하면 FAIL, shim을 쓰면 PASS | matplotlib 3.6 이상에서 `canvas.set_window_title`이 없어져 AttributeError. 원본을 고치지 않고 `interaction_vis_shim.py`로 보완해 `interaction_test_scenario.png`를 렌더링 |

**참고:** INTERACTION 궤적 CSV는 `x, y, vx, vy, psi_rad, length, width, time_stamp_ms` 형식입니다. 데이터 이용 허가를 받으면 `adapters/`에 변환기를 추가해 VehicleFrame v1로 바꿀 수 있습니다.

## 4. 테스트

`pytest -v`로 실제 실행했고 로그는 `docs/test_log.txt`에 있습니다. **PASS 80 / FAIL 0 / SKIP 0**

| 검증 대상 | 테스트 |
|---|---|
| 좌표 변환(자차 이동·회전 포함 고정 예시 3종, 자차 원점) | test_transform.py |
| JSON Schema, 유한값, 시간 100ms 등간격, 길이 10초 이상, 자차 heading π/2 | test_units.py |
| 속도 단위(m/s)와 위치 변화(m)·시간(ms)의 일치 | test_speed_units_are_m_per_s |
| ego_pose 보조 파일로 월드 운동 복원 | test_ego_pose_sidecar_reconstructs_motion |
| 차량 ID가 유일하고 안정적인지, 누락이 의도된 dropout 구간에서만 생기는지, lane_id | test_identity.py |
| 메타데이터 7항목, 출력 파일명 7개 | test_manifest_metadata_complete, test_expected_output_files |
| 같은 seed면 같은 출력, seed가 바뀌면 노이즈가 바뀜, 커밋된 samples가 그대로 재생성되는지 | test_seed.py |

**개발 중 발견하고 고친 결함**

HighwayEnv의 `Vehicle.velocity`는 slip angle β를 빠뜨립니다(원본 코드에도 TODO로 남아 있음). 그래서 차선 변경 중에는 횡속도가 실제 이동량보다 작게 나왔습니다(예: 4.63 m/s여야 할 값이 약 3.1 m/s).
지금은 어댑터가 `step()`과 같은 heading+β 방향으로 속도를 계산합니다. 같은 버그가 다시 생기면 테스트가 잡아냅니다.

## 5. 재현 명령 (simulation-engine/ 안에서)

```bash
python -m venv .venv && .venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/python -m generators scenarios/*.yaml -o out
.venv/Scripts/python -m pytest -q
```

## 6. 팀장에게 올리는 이슈 (공통 계약은 임의로 수정하지 않음)

1. 공통 `contracts/` JSON Schema가 아직 없어서 `schema/`의 로컬 사본으로 검증했습니다. 공식 스키마가 나오면 그것으로 다시 검증하겠습니다.
2. VehicleFrame v1에는 자차의 월드 자세가 없습니다. 지금은 계약 밖 보조 파일 `*.ego_pose.jsonl`로 제공합니다. v1.1에 `ego_pose_world`를 추가하는 안의 승인을 요청합니다.
3. 팀 공통 프롬프트의 필드 목록에는 `coordinate_system`이 없지만, 지시서 예시에는 있습니다. 예시를 따라 `"ego_local_m"`을 포함했습니다.
4. 속도는 절대속도를 자차 축으로 회전한 값입니다. 02번은 상대속도를 직접 계산해야 합니다.
5. merge는 직선 3차선에서의 근사입니다(실제 진입로 형상이 아님).
6. 저장소 구조: 지시서 PDF는 엔진마다 개인 저장소(`prism-simulation-engine`)를 쓰라고 하고, 03 프롬프트는 `simulation-engine/` 폴더를 쓰라고 합니다. 그래서 저장소 안에 `simulation-engine/` 폴더를 두어 두 방식 모두에 맞췄습니다. 팀 모노레포를 쓰게 되면 이 폴더를 그대로 옮기면 됩니다.
7. 모든 데이터는 **시뮬레이션**입니다. 실차 ADAS나 Pleos 데이터가 아닙니다.

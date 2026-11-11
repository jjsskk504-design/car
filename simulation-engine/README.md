# simulation-engine (PRISM PART 03 / 교통 시뮬레이션·데이터 엔진)

**Kotlin/JVM** 엔진입니다. HighwayEnv(MIT)의 도로·차량 물리(IDM, 차선 추종 조향, 자전거 모델)를 Kotlin으로 옮겨
**재현 가능한 다중 차량 교통 시나리오**를 만들고, PRISM 공통 계약 **VehicleFrame v1 JSONL**로 내보냅니다.

> 이 엔진은 **합성 시뮬레이션 데이터 생성기**입니다. 위험도 판정, 궤적 예측, UI, Pleos 연동은 하지 않습니다.
> 실제 차량의 안전 시스템이나 실시간 충돌 방지 장치가 아닙니다.

## 실행 (`simulation-engine/` 폴더 안에서, JDK 17 이상, 검증 환경은 Windows 11 + JDK 21 + Gradle 8.14)

```bash
./gradlew generate      # scenarios/*.yaml 7종 -> samples/
./gradlew test          # 테스트
./gradlew replay        # samples/ -> docs/replay/*.png, *.gif
./gradlew run --args="scenarios -o out"   # 다른 폴더로 출력
```

Windows에서는 `gradlew.bat`을 씁니다. **한글 경로에서는 `gradlew test`가 `ClassNotFoundException`으로 실패**합니다(05와 같은 문제).
영문 정션을 만들어 그 경로에서 실행합니다: `cmd /c mklink /J C:\dev\prism "<PRISM 폴더>"` 후 `C:\dev\prism\simulation-engine`에서 실행.

### Python 버전에서 바꾼 것 (v0.1.0 → v0.2.0)

- 언어를 Python에서 Kotlin으로 바꿨습니다. HighwayEnv는 Python 전용이라 쓰는 부분(직선 도로, IDM, 차선 추종 조향, 자전거 모델)만 `adapters/HighwaySim.kt`로 옮겼습니다.
- 옮긴 결과가 원본과 같은지 확인했습니다: 시나리오 7종의 모든 프레임·차량·필드가 Python + HighwayEnv 출력과 **차이 0**입니다(`docs/kotlin_port_parity.log`).
- 다른 점은 하나입니다: `sensor_dropout`의 car_05 위치 노이즈는 난수 생성기가 numpy에서 `java.util.Random`으로 바뀌어 값이 다릅니다(seed 107 고정, 재현 가능).

## 폴더

| 폴더 | 역할 |
|---|---|
| `adapters/` | `HighwaySim.kt`: HighwayEnv 차량 모델의 Kotlin 이식(직선 도로, IDM, 차선 추종 조향, 자전거 모델, 충돌 판정)<br>`EgoTransform.kt`: 월드 좌표를 자차 좌표로 변환 |
| `generators/` | `Scenario.kt`: YAML 읽기·검증<br>`ScenarioRunner.kt`: 결정적 이벤트 주입, VehicleFrame과 매니페스트 생성<br>`Main.kt`: 명령줄 실행 |
| `exporters/` | `Jsonl.kt`: JSONL과 매니페스트 저장(NaN/Inf 금지) |
| `visualization/` | `Replay.kt`: 2D 재생 GIF, 시간별 PNG 스냅샷(Java2D) |
| `scenarios/` | 시나리오 7종 YAML |
| `samples/` | 생성 결과(커밋됨, 테스트로 재현성 검증) |
| `schema/` | VehicleFrame v1 JSON Schema 로컬 사본(공통 `contracts/`가 생기면 그것이 기준) |
| `tests/` | 좌표 변환, 단위, ID, 재현성, 메타데이터 테스트(JUnit 5, kotlin-test) |
| `docs/` | `replay/*.gif, *.png`, `test_log.txt`, `kotlin_port_parity.log`(Python 원본과 대조), 원본 오픈소스 실행 로그 |

## 출력

| 파일 | 내용 |
|---|---|
| `<name>.jsonl` | 100ms 간격 **VehicleFrame v1**, 한 줄에 한 프레임. 121프레임 = 12초 |
| `scenario_manifest.json` | duration_ms, step_ms, sim_substep_ms, seed, vehicle_count, ego_id, event_annotation(이벤트 시점), observed_collisions, provenance(데이터 출처), lane_id_map |
| `<name>.ego_pose.jsonl` | 프레임별 자차의 월드 자세. **계약 외 보조 파일**(아래 01 연결 참고) |

**좌표·단위**
- `ego_local_m` 좌표계에서 x는 오른쪽+, y는 전방+이고 단위는 m · m/s · rad · ms입니다.
- heading은 +x에서 반시계 방향이며, 자차는 항상 (0, 0, π/2)에 있습니다.
- 속도는 상대속도가 아니라 **절대속도를 자차 축으로 회전한 값**입니다.
- `lane_id`는 가장 왼쪽 차선부터 L1, L2, L3입니다.

## 시나리오 7종

자차는 L2에서 25 m/s로 출발하고, 주변 차량은 5대이며 길이는 12초입니다.

| 출력 파일 | seed | 강제 이벤트 | 결과(생성값) |
|---|---|---|---|
| normal | 101 | 없음 | 최소 범퍼 간격 40m |
| sudden_braking | 102 | t=4s, 전방 30m 화물차가 25→5 m/s로 급감속 | 자차가 5.2 m/s까지 감속, 최소 9.0m |
| rear_approach | 103 | 후방 35m 차량이 35 m/s로 접근 | 최소 19.5m |
| lane_change | 105 | t=5.5s, 왼쪽 추월 차량이 L1→L2로 끼어듦 | 최소 2.3m |
| merge | 104 | 오른쪽 저속 차량이 t=2s에 가속, t=3s에 L3→L2 합류 | 최소 16.4m |
| chain_reaction | 106 | t=4s 선두 화물차 급감속, t=5s 앞차가 L3로 회피 | 자차·후속차 연쇄 감속, 최소 12.8m |
| sensor_dropout | 107 | car_01 3~5s, car_03 7~7.5s 관측 누락 + car_05 위치 노이즈 σ=0.3m | 누락 프레임 25개 |

**강제 행동과 자연 행동의 구분**
- manifest의 `event_annotation`에 적힌 이벤트만 강제(`origin: "forced"`)입니다.
- 그 밖의 모든 움직임은 시뮬레이터의 자연 행동입니다. 예를 들어 앞차가 급감속하면 자차와 후속차가 IDM에 따라 스스로 감속합니다.
- 자동 차선변경(MOBIL)은 결정성을 위해 꺼 두었습니다.

`observed_collisions`는 시뮬레이터가 판정한 실제 충돌 여부(정답 라벨)입니다. 7종 모두 0건입니다.

## 다른 엔진과 연결

- **01 PREDICT:** `samples/<name>.jsonl`을 한 줄씩 읽습니다. 프레임마다 그 시점의 자차 좌표이므로, 과거 프레임은 `<name>.ego_pose.jsonl`로 현재 좌표로 옮깁니다.
  pose = (X, Y, θ)이고, R(a)는 반시계 a 회전입니다.
  - 로컬 → 월드: `P = (X,Y) + R(θ-π/2)·(x,y)`, `h_w = h + θ - π/2`
  - 월드 → 현재 로컬: `(x',y') = R(-(θ_now-π/2))·(P - (X_now,Y_now))`, `h' = h_w - θ_now + π/2`

  이 식은 `test_ego_pose_sidecar_reconstructs_motion`으로 검증했습니다.
- **02 ASSESS:** VehicleFrame(+01 결과)을 입력으로 씁니다. 상대속도는 직접 계산해야 합니다(속도가 절대값이므로). `event_annotation`과 `observed_collisions`는 평가 라벨로 쓸 수 있습니다.
- **05 INTEGRATE:** `samples/*.jsonl`과 `scenario_manifest.json`을 앱 assets로 복사합니다.

## 원본 오픈소스 실행 확인

실험용 clone은 PRISM 밖의 `opensource-lab/`에 있고, 로그는 `docs/opensource_lab/`에 있습니다.

| 오픈소스 | 실행 결과 |
|---|---|
| HighwayEnv | `highway-v0`, `merge-v0`, `intersection-v0`, `roundabout-v0` 헤드리스 실행 PASS. v0.1.0(Python)은 pip 패키지로 직접 사용, v0.2.0(Kotlin)은 알고리즘을 이식하고 출력이 같은지 대조 |
| INTERACTION | 저장소의 테스트 샘플로 unittest, track 로드, 시각화 PASS(시각화는 matplotlib 호환 shim 사용) |
| CARLA | clone만 완료. 클라이언트 설치 FAIL(Python 3.13용 패키지 없음), 서버는 하드웨어·디스크 부족으로 BLOCKED |

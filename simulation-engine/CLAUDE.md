# PRISM PART 03 — simulation-engine

현대자동차그룹 차량용 앱 공모전 PRISM, 5인 병렬 개발 중 **03 교통 시뮬레이션·데이터 엔진**.
**Kotlin/JVM**(v0.2.0~). HighwayEnv(MIT) 차량 모델을 Kotlin으로 이식해 재현 가능한 시나리오를 만들고 **VehicleFrame v1 JSONL**로 내보낸다. 답변은 한국어로.

## 담당 범위
- 한다: 시나리오 생성, HighwayEnv 어댑터, 좌표 변환, JSONL/manifest 출력, 2D 재생
- 하지 않는다: 위험도·TTC 판정(02), 궤적 예측(01), Android UI(04), 앱·Pleos(05)
- 실차 ADAS·실시간 충돌 방지라고 주장하지 않는다. 모든 데이터는 합성 시뮬레이션.

## 명령 (영문 정션 `C:\dev\prism\simulation-engine` 에서, JDK 17+ — 이 PC는 `C:\Program Files\Java\jdk-21`)
```bash
gradlew.bat generate   # scenarios/*.yaml 7종 -> samples/
gradlew.bat test       # 테스트 (현재 20 PASS, 각 테스트가 7종 전부 검사)
gradlew.bat replay     # samples -> docs/replay GIF·PNG
```
- 한글 경로에서 `gradlew test`는 `ClassNotFoundException`으로 실패한다. 반드시 `C:\dev\prism`(PRISM 정션)에서 실행.
- `javaHome ... Android Studio1\jbr` 오류가 나면 낡은 데몬 문제: `gradlew.bat --stop` 후 다시.
시나리오나 생성 코드를 바꾸면 `samples/`를 다시 생성해서 함께 커밋한다(`SeedTest.committedSamplesAreReproducible`가 검사).

## 데이터 계약 (VehicleFrame v1)
- `schema_version "1.0"`, `timestamp_ms`(시나리오 시작 기준 ms), `coordinate_system "ego_local_m"`, `ego_id`, `vehicles[]`
- vehicle: `id, x, y, vx, vy, heading_rad, length_m, width_m, lane_id`
- 자차 기준: x=오른쪽+, y=전방+, heading은 +x에서 반시계(전방=π/2). 단위 m, m/s, rad, ms
- 속도는 **절대속도를 자차 축으로 회전**한 값(상대속도 아님). lane_id L1=가장 왼쪽
- 공통 `contracts/` 스키마가 생기면 그것이 최종 기준. `schema/`는 로컬 사본
- 계약 필드를 임의로 추가하거나 바꾸지 않는다. 필요하면 팀장에게 이슈로 보고(현재 이슈 목록은 DELIVERY_REPORT.md 6절)
- 누락 값을 0으로 채우지 않는다. NaN/Inf 금지(exporter `requireFinite`가 막음)

## 구조
`adapters/`(HighwayEnv → 월드 상태, 좌표 변환) · `generators/`(YAML 실행, 강제 이벤트 주입, manifest) · `exporters/` · `visualization/` · `scenarios/` · `samples/` · `tests/` · `docs/`

- 강제 이벤트는 manifest `event_annotation`에 `origin: "forced"`로 기록한다. 그 밖의 모든 움직임은 시뮬레이터의 자연 행동(IDM)이다.
- HighwayEnv의 `vehicle.velocity`는 slip angle β를 빠뜨린다. 어댑터는 heading+β로 속도를 계산한다. 되돌리지 않는다.
- `adapters/HighwaySim.kt`는 HighwayEnv 1.12.1 이식본이다. 물리 식·상수를 바꾸면 원본과 달라지므로 `docs/kotlin_port_parity.log`를 다시 만들고 이유를 기록한다.
- Gradle 소스 폴더는 `src/`가 아니라 지시서 폴더(adapters/generators/exporters/visualization, 테스트는 tests/)다(`build.gradle.kts` sourceSets).

## Git 규칙
- 공유 저장소 github.com/qwon4466/PRISM (**공개**)에서 `simulation-engine/`만 수정한다. 브랜치 `feature/simulation`
- **main에 직접 push 금지**. 변경은 PR로. 루트 README, 다른 엔진 폴더, `contracts/`는 수정하지 않는다(필요하면 팀장 승인)
- 커밋 전에 `git status`를 확인한다. 다른 사람의 변경을 지우지 않는다.
- `git reset --hard`, `git clean -fd`, force push 금지

## 오픈소스 규칙
- 외부 저장소는 PRISM 밖 `명관 바이브코딩/opensource-lab/`에 clone해서 실험한다. PRISM 안으로 통째로 복사하지 않는다.
- 새 의존성은 LICENSE와 유지보수 상태를 확인한 뒤 `THIRD_PARTY_NOTICES.md`와 `build.gradle.kts`에 기록한다.
- INTERACTION 실제 데이터는 약관을 확인하기 전까지 사용·업로드 금지. CARLA는 이 PC에서 BLOCKED(RAM·디스크 부족)
- 실행하지 않은 것을 완료라고 보고하지 않는다. 결과는 PASS/FAIL/SKIP으로 구분한다.

## 환경 주의 (Windows, 한글 경로)
- 로그 파일은 UTF-8로 저장하고, 로컬 사용자 경로(C:\Users\...)는 남기지 않는다.
- C: 여유 공간이 약 5GB라서 대용량 다운로드 전에는 확인을 받는다.

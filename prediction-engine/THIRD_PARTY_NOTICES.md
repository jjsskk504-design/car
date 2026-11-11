# Third-Party Notices

외부 저장소의 소스, 데이터셋, 모델 가중치는 이 프로젝트에 복사하지 않았습니다.

## Runtime dependency

- **Kotlin 2.0.21** — Apache License 2.0 — https://github.com/JetBrains/kotlin
- **kotlinx.serialization-json 1.7.3** — Apache License 2.0 — https://github.com/Kotlin/kotlinx.serialization
  - 사용 목적: VehicleFrame JSONL 역직렬화와 PredictionFrame JSON 직렬화
  - 포함 방식: Maven Central 패키지 설치, vendoring 없음

## Build and test dependencies

- **Android Gradle Plugin 8.13.2** — Android SDK License / Apache License 2.0 구성요소 — https://developer.android.com/build/releases/gradle-plugin
- **Gradle Wrapper 8.14.3** — Apache License 2.0 — https://github.com/gradle/gradle
- **JUnit 4.13.2** — Eclipse Public License 1.0 — https://github.com/junit-team/junit4

## Evaluated but not included

- **Trajectron++** — MIT — Copyright (c) 2020 Stanford Autonomous Systems Lab — https://github.com/StanfordASL/Trajectron-plus-plus
- **INTERACTION dataset scripts** — BSD-3-Clause — https://github.com/interaction-dataset/interaction-dataset
- **PyTorch** — BSD-style and bundled third-party notices — https://github.com/pytorch/pytorch
- **Pleos Vehicle SDK** — Part 01에는 직접 의존하지 않음. 차량 연동 파트에서만 관리.

선택 이유와 외부 실행 결과는 `OPEN_SOURCE_REVIEW.md`에 기록했습니다.


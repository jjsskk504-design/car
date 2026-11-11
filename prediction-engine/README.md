# PRISM Part 01 — Kotlin Prediction Engine

VehicleFrame의 과거 2~5초 기록을 받아 주변 차량의 향후 1~5초 후보 궤적을 만드는 **Kotlin Android Library**입니다. Part 01만 담당하며 위험도, TTC, UI, 차량 제어와 Pleos SDK 호출은 포함하지 않습니다.

## 개발 환경

| 항목 | 설정 |
|---|---|
| 개발 언어 | Kotlin 2.0.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.14.3 |
| Minimum Android SDK | API 28 |
| Compile SDK | API 36 |
| Java/Kotlin bytecode target | Java 17 |
| 빌드 JDK | Temurin 21 |

`minSdk = 28`은 `build.gradle.kts`의 Android Library 설정에 명시되어 있습니다. 이 모듈을 사용하는 최종 앱도 API 28 이상이어야 합니다.

## 첨부 화면의 SDK와 Part 01 관계

첨부 화면은 Pleos 개발자 콘솔의 **Vehicle SDK → Vehicle State** 페이지입니다. 차량 제원, 주행 상태, 도어, 공조, 라이트, 안전 상태 등을 조회하는 기능입니다.

Part 01은 이 SDK를 직접 사용하지 않습니다. Part 01의 입력은 공통 `VehicleFrame`이고, 출력은 `PredictionFrame`입니다. 실제 Vehicle SDK 연결은 `vehicle-integration/`이 담당합니다. 공개 Vehicle State API는 차량 자체 상태 조회 중심이며 주변 차량의 `x/y/vx/vy` 목록을 직접 제공하는 API로 확인되지 않았으므로, 예측 엔진에 불필요한 SDK 의존성을 넣지 않았습니다.

## 빌드와 테스트

PowerShell에서 다음 명령으로 재현합니다.

```powershell
.\gradlew.bat --version
.\gradlew.bat clean testDebugUnitTest
.\gradlew.bat assembleDebug
```

고정 Mock 5종을 포함한 전체 검증 진입점은 다음과 같습니다.

```powershell
.\gradlew.bat verifyMocks
```

성공하면 Android Library AAR은 `build\outputs\aar\prism-prediction-engine-debug.aar`에 생성됩니다.

## Kotlin 사용 예

```kotlin
val jsonl = vehicleFramesFile.readText()
val predictionJson = PredictionEngine.predictJsonl(jsonl)
```

또는 DTO를 직접 전달할 수 있습니다.

```kotlin
val prediction: PredictionFrame = PredictionEngine.predict(frames)
```

## 입출력

- 입력: VehicleFrame JSONL, 단위 `m`, `m/s`, `rad`, `ms`
- 좌표: 자차 기준 `x=오른쪽`, `y=전방`
- 출력: `lane_keep`, `braking`, `lane_change_left`, `lane_change_right`
- 시간: 기준 `timestamp_ms`에서 1~5초 상대 `dt_ms`
- 신뢰도: 학습·보정하지 않았으므로 `confidence=null`

입력 검사는 타임스탬프 정렬·중복 거부, 유한 숫자, ego 존재, 차량 ID 중복, 차체 크기 급변에 의한 ID 재할당 의심까지 포함합니다. 관측 누락은 0으로 채우지 않습니다.

## 알고리즘

- CV: 현재 속도 유지
- CA: 최근 두 유효 관측의 속도 변화로 가속도 계산
- CA 관측 부족 시 CV 폴백
- 속도 55m/s, 가속도 8m/s² 기본 상한
- 4m/s² 감속 후보
- 차선 폭 3.5m, 4초 smoothstep 차선 변경 후보
- 설정된 도로 곡률에 따른 원호 위치·방향 계산

## 좌표 변환의 한계

VehicleFrame v1에는 절대 자차 pose와 yaw rate가 없습니다. 기본 구현은 자차 속도와 첫 프레임 대비 방향 변화로 pose를 추정합니다. 상위 시스템에 측정 pose가 있으면 `History.normalizeToCurrentEgo(frames, egoPoses)`에 `Pose2D` 목록을 전달하는 편이 정확합니다.

## 문서

- [EASY_GUIDE_KO.md](EASY_GUIDE_KO.md): 쉬운 설명과 실행 방법
- [DATA_CONTRACT.md](DATA_CONTRACT.md): 데이터 계약과 단위
- [OPEN_SOURCE_REVIEW.md](OPEN_SOURCE_REVIEW.md): 외부 저장소 점검
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): Kotlin 의존성 및 라이선스
- [DELIVERY_REPORT.md](DELIVERY_REPORT.md): 테스트·환경·한계·인수인계

이 모듈은 공모전용 예측 보조 코드이며 실제 차량 안전 시스템이나 실시간 충돌 방지 장치를 대체하지 않습니다.


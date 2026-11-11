# PRISM Part 01 Kotlin 전환 인수보고서

작성일: 2026-11-11

## 개발 환경

| 항목 | 실제 설정 |
|---|---|
| 언어 | Kotlin 2.0.21 |
| Android Library | `com.android.library` / AGP 8.13.2 |
| Gradle | Wrapper 8.14.3 |
| Minimum Android SDK | API 28 |
| Compile SDK | API 36 |
| JVM bytecode target | Java 17 |
| 검증 호스트 JDK | Temurin 21.0.12.1 |
| 설치 Android 플랫폼 | API 36, API 37 |
| 설치 Build Tools | 35.0.0, 36.0.0 |

## 완료 범위

- Python 소스·pytest·PyPI 의존성 제거
- Kotlin Android Library로 전체 기능 이식
- VehicleFrame JSONL 직렬화와 계약 검증
- 과거 좌표를 최신 자차 좌표계로 이동·회전
- CV/CA, 속도·가속도 상한, 1~5초 horizon
- 차선 유지, 감속, 좌/우 차선 변경 후보
- 누락 관측, 중복 시간, ID 재할당 의심 처리
- Mock 5종과 Kotlin/JUnit 테스트
- Android debug AAR 빌드

포함하지 않은 범위: TTC, 위험 등급, 위험 영역, 시뮬레이터, UI, Pleos SDK 직접 호출, 차량 제어, 신경망 학습/추론.

## SDK 사용 여부

- Part 01 직접 사용 SDK: **없음**
- JSON 라이브러리: `kotlinx.serialization-json:1.7.3`
- 첨부 화면: Pleos `Vehicle SDK → Vehicle State`
- 실제 Pleos SDK 연결 위치: `vehicle-integration/sdk/`

Vehicle SDK의 차량 자체 상태를 주변 차량 궤적으로 오해하지 않도록 Part 01은 공통 VehicleFrame만 소비합니다.

## 테스트 결과

최종 검증 명령은 `gradlew clean verifyMocks assembleDebug --console=plain --no-daemon`입니다.

| 검증 | 결과 |
|---|---|
| `gradlew testDebugUnitTest` | 성공 — 20개, 실패 0, 오류 0 |
| `gradlew verifyMocks` | 성공 |
| `gradlew assembleDebug` | 성공 |
| Mock 기본·직진·누락·감속·차선 변경 | 5종 모두 성공 |
| AAR Manifest | `minSdkVersion=28` 확인 |

산출물은 `build/outputs/aar/prism-prediction-engine-debug.aar`이며 SHA-256은 `C7E4881EBA9825EC939314B375B15EF371C72C5E9CD25E70E9B297F77C777D3C`입니다.

빌드는 성공했지만 로컬 Android SDK 명령줄 도구와 AGP의 SDK XML 파서 버전 차이로 `SDK XML version 4` 경고가 1회 표시됩니다. 컴파일·테스트·AAR 생성에는 영향을 주지 않았으며 실패로 숨기지 않고 기록합니다.

## 알려진 한계

1. VehicleFrame v1만으로 절대 자차 pose/yaw rate를 정확히 알 수 없어 기본값은 dead reckoning 추정입니다.
2. 차선 중심선·지도·곡률이 입력 계약에 없어 기본 곡률은 0입니다.
3. 확률은 보정되지 않았으므로 `confidence=null`입니다.
4. CV/CA는 급격한 의도 변화, 충돌 후 운동, 미끄러짐을 모델링하지 않습니다.
5. 내부 Schema는 로컬 검사본입니다. 실제 공통 Schema와 다르면 공통 파일을 임의 수정하지 않고 이슈를 등록해야 합니다.

## 연결 방법

- Kotlin: `PredictionEngine.predict(frames)`
- JSONL: `PredictionEngine.predictJsonl(vehicleFramesJsonl)`
- Android 앱: `prism-prediction-engine-debug.aar` 또는 Gradle 모듈 의존성

Risk Engine에는 반환된 PredictionFrame을 그대로 전달하며 시간·좌표·단위를 변경하지 않습니다.

## Git 인계

통합 PRISM 저장소의 `feature/prediction` 브랜치와 PR #3을 갱신합니다. 수정 범위는 `prediction-engine/`뿐이며 `main`에 직접 Push하지 않습니다.

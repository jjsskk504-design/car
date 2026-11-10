# THIRD_PARTY_NOTICES — visualization-engine

이 모듈은 **외부 오픈소스 코드를 복사(vendoring)하거나 submodule로 포함하지 않습니다.**
아래 라이브러리는 모두 Google Maven / Maven Central의 **공개 바이너리를 Gradle 의존성으로 사용**합니다.
소스 코드에 외부 저장소의 코드 조각은 들어 있지 않습니다. 모든 렌더링·기하·리플레이 로직은 직접 구현했습니다.
탑뷰 차량 그래픽(승용차·SUV·대형 화물차)도 외부 이미지·아이콘을 쓰지 않고 `rendering/VehicleShapes.kt`에 좌표로 직접 정의한 벡터 도형입니다.

## 런타임 의존성 (라이브러리 + 데모 앱)

| 구성요소 | 좌표 / 버전 | 라이선스 | 출처 | 사용 목적 |
|---|---|---|---|---|
| Jetpack Compose UI, Foundation, Material3 (AndroidX) | `androidx.compose:compose-bom:2026.02.01` | Apache-2.0 | https://github.com/androidx/androidx | Canvas 2D 렌더링, 레이아웃, 제스처, 텍스트 측정 |
| Compose UI Tooling Preview | BOM 관리 | Apache-2.0 | 위와 같음 | 미리보기 어노테이션 |
| AndroidX Activity Compose (데모 앱만) | `androidx.activity:activity-compose:1.8.0` | Apache-2.0 | 위와 같음 | `setContent` |
| Kotlin 표준 라이브러리 | Kotlin 2.2.10 | Apache-2.0 | https://github.com/JetBrains/kotlin | 언어 런타임 |
| kotlinx.coroutines (Compose 전이 의존성) | Compose BOM이 결정 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines | 재생 루프·비동기 로딩 |
| org.json | Android 플랫폼 내장 | Android 플랫폼 일부 | Android SDK | 계약 JSON 파싱(런타임에서는 별도 의존성 없음) |

## 테스트 전용 의존성 (배포물에 포함되지 않음)

| 구성요소 | 좌표 / 버전 | 라이선스 | 출처 |
|---|---|---|---|
| JUnit 4 | `junit:junit:4.13.2` | EPL-1.0 | https://github.com/junit-team/junit4 |
| JSON-java (JVM 단위 테스트에서 Android 내장 org.json 대체) | `org.json:json:20240303` | Public Domain | https://github.com/stleary/JSON-java |
| Compose UI Test (junit4, manifest) | BOM 관리 | Apache-2.0 | https://github.com/androidx/androidx |
| AndroidX Test (ext-junit 1.1.5, runner 1.5.2) | 표기 버전 | Apache-2.0 | https://github.com/android/android-test |

## 조사했지만 사용하지 않은 오픈소스

| 저장소 | 라이선스(LICENSE 파일 확인) | 결정 | 이유 |
|---|---|---|---|
| Vico (patrykandpatrick/vico) | Apache-2.0 | 미사용 | 필요한 차트가 위험 등급 타임라인 하나뿐이라 Canvas로 직접 그림. 최신 버전이 Kotlin 2.4.21로 빌드되어 현재 Kotlin 2.2.10 프로젝트와 호환되지 않을 가능성이 있음(검증하지 않음) |
| Filament (google/filament) | Apache-2.0 | 미사용 (P2 검토만) | 3D·NDK 네이티브 의존성이 커서 P0 시연과 맞지 않음. 2D 탑다운으로 충분 |

조사는 PRISM 저장소 밖의 실험 폴더(`C:\Users\jjssk\prism-oss-lab`)에서 shallow·sparse clone으로 했습니다.
PRISM 저장소에는 아무것도 복사하지 않았습니다.

## 라이선스 고지 원문

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
Eclipse Public License 1.0 (JUnit, 테스트 전용): https://www.eclipse.org/legal/epl-v10.html

이 모듈을 APK로 배포할 때는 Apache-2.0 의존성의 NOTICE를 앱의 오픈소스 고지 화면에 포함해야 합니다.
NOTICE 화면은 통합 앱(vehicle-integration) 쪽에서 만들어야 하며, 이 모듈은 화면을 제공하지 않습니다.

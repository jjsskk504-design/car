# Third-party notices

이 모듈은 외부 소스 코드를 복사(vendoring)하지 않는다. 모든 외부 코드는 Gradle 의존성(공식 배포 바이너리)으로만 사용한다.

| 구성요소 | 출처 | 버전 | 라이선스 | 사용 방식 |
|---|---|---|---|---|
| AndroidX (Compose UI, Material3, Activity, Lifecycle) | https://github.com/androidx/androidx · Google Maven | Compose BOM 2024.10.01, activity-compose 1.9.3, lifecycle 2.8.7 | Apache-2.0 | Gradle 의존성 |
| kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization · Maven Central | 1.7.3 | Apache-2.0 | Gradle 의존성 |
| kotlinx.coroutines | https://github.com/Kotlin/kotlinx.coroutines · Maven Central | 1.9.0 | Apache-2.0 | Gradle 의존성 |
| JUnit 4 | https://github.com/junit-team/junit4 | 4.13.2 | EPL-1.0 | 테스트 전용 |
| Pleos Vehicle SDK | `ai.pleos.playground:Vehicle` · nexus-playground.pleos.ai | 2.3.2 | 라이선스 파일 없음 (Pleos Playground 이용 조건 따름) | Gradle 의존성 |
| Pleos FusedLocation SDK | `ai.pleos.playground:FusedLocation` | 1.1.3 | 〃 | Gradle 의존성 |
| Pleos NaviHelper SDK | `ai.pleos.playground:NaviHelper` | 3.0.5 | 〃 | Gradle 의존성 |
| Pleos TextToSpeech SDK | `ai.pleos.playground:TextToSpeech` | 3.3.1 | 〃 | Gradle 의존성 |
| Gradle Wrapper | https://github.com/gradle/gradle | 8.13 | Apache-2.0 | `gradlew`, `gradle/wrapper/` |

참고만 하고 코드를 가져오지 않은 저장소:

- https://github.com/Pleos-Playground/pleos-sample-vehicle-app (commit 959981d, **LICENSE 없음**) — README 의 SDK 수명주기·capability 확인 순서만 참고. `PleosVehicleSdkProvider` 는 AAR 공개 시그니처를 보고 새로 작성했다.
- https://github.com/android/architecture-samples (Apache-2.0) — ViewModel + StateFlow 단방향 상태 구조 참고.

Pleos SDK 와 서비스는 Pleos Playground 이용 조건이 적용된다. 재배포(APK 포함) 조건은 공모전 주최 측 확인이 필요하다.

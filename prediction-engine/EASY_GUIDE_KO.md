# 쉽게 이해하는 Kotlin 궤적 예측 엔진

## 무엇을 하나요?

주변 차량의 최근 위치와 속도를 보고 1~5초 뒤 위치 후보를 계산합니다. 위험 여부는 판단하지 않고 다음 위험도 엔진이 사용할 미래 궤적만 만듭니다.

```text
                 y+ (앞)
                    ↑
       x- (왼쪽) ← 내 차 → x+ (오른쪽)
                    ↓
                 y- (뒤)
```

- `x=3, y=20`: 내 차 오른쪽 3m, 앞쪽 20m
- `vx=0, vy=5`: 초당 5m씩 전진
- `heading_rad≈1.57`: 앞쪽을 향함
- `timestamp_ms=2000`: 시나리오 시작 후 2초

## 왜 Kotlin인가요?

공모전의 Android 개발 언어 조건에 맞추기 위해 Python 구현을 Kotlin Android Library로 전환했습니다. `minSdk=28`, Gradle Wrapper `8.14.3`, Android Gradle Plugin `8.13.2`를 사용합니다.

## 네 가지 후보

1. `lane_keep`: 현재 움직임으로 차선 유지
2. `braking`: 감속하다 정지
3. `lane_change_left`: 부드럽게 왼쪽 차선 이동
4. `lane_change_right`: 부드럽게 오른쪽 차선 이동

`confidence=null`은 오류가 아닙니다. 실제 데이터로 보정하지 않은 확률을 꾸며내지 않았다는 뜻입니다.

## 직접 검증하기

```powershell
.\gradlew.bat clean testDebugUnitTest
.\gradlew.bat assembleDebug
```

테스트는 기본·직진·관측 누락·감속·차선 변경 Mock 5종을 포함합니다. 빌드 결과는 `build\outputs\aar\prism-prediction-engine-debug.aar`입니다.

## 첨부 화면은 무엇인가요?

첨부 화면은 Pleos **Vehicle SDK의 Vehicle State** 기능입니다. 차량 제원, 주행 상태, 문, 공조, 라이트 등의 차량 자체 상태를 조회합니다.

이 Part 01 엔진은 Vehicle SDK를 직접 호출하지 않습니다. 다른 파트가 차량·시뮬레이션 정보를 공통 VehicleFrame으로 바꾸면 Part 01은 그 JSON을 받아 예측합니다. 이 분리는 SDK가 없어도 Mock JSON으로 테스트할 수 있게 해줍니다.

## 한계

CV/CA는 급격한 끼어들기, 미끄러짐, 충돌 후 운동, 운전자 의도를 완전히 알 수 없습니다. 실제 차량 제어 또는 충돌 방지 장치로 사용하면 안 됩니다.


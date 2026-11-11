# THIRD_PARTY_NOTICES

v0.2.0부터 이 엔진은 **Kotlin/JVM**으로 작성되었습니다. 외부 저장소를 submodule로 넣거나 통째로 복사하지 않았습니다.
다만 HighwayEnv의 차량 모델 **알고리즘 일부를 Kotlin으로 옮겼으므로(포팅)** 아래에 MIT 라이선스 원문을 함께 적습니다.

| 구성요소 | 버전 | 라이선스 | 출처 | 사용 방식 · 위치 |
|---|---|---|---|---|
| HighwayEnv (highway-env) | 1.12.1 | MIT | https://github.com/Farama-Foundation/HighwayEnv | **Kotlin 포팅**: `adapters/HighwaySim.kt`. 원본 파일은 `road/lane.py`(StraightLane), `road/road.py`(Road, straight_road_network), `vehicle/kinematics.py`(Vehicle), `vehicle/controller.py`(ControlledVehicle), `vehicle/behavior.py`(IDMVehicle). 상수·식을 그대로 옮겼고, 원본과 출력이 같은지 `docs/kotlin_port_parity.log`로 확인함 |
| SnakeYAML | 2.3 | Apache-2.0 | https://bitbucket.org/snakeyaml/snakeyaml | Maven 의존성. `generators/Scenario.kt`(시나리오 YAML 읽기) |
| kotlinx.serialization (json) | 1.7.3 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization | Maven 의존성. JSONL·매니페스트 읽기·쓰기 |
| Kotlin 표준 라이브러리 · kotlin-test | 2.0.21 | Apache-2.0 | https://github.com/JetBrains/kotlin | 언어 런타임, `tests/` |
| Gradle Wrapper | 8.14 | Apache-2.0 | https://github.com/gradle/gradle | `gradlew`, `gradle/wrapper/` (05 vehicle-integration 과 같은 wrapper 파일) |

- 시각화(`visualization/Replay.kt`)는 JDK 표준 Java2D·ImageIO만 사용합니다.
- INTERACTION 데이터셋, CARLA 에셋, 모델 가중치는 포함하지 않습니다. `samples/`는 모두 이 엔진이 생성한 합성 데이터입니다.
- `docs/opensource_lab/`의 `.py` 파일은 원본 오픈소스를 실행해 본 **실험 기록**이며 엔진 코드가 아닙니다.

## HighwayEnv 라이선스 원문 (MIT)

```
MIT License

Copyright (c) 2018 Edouard Leurent
Copyright (c) 2023 Farama Foundation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

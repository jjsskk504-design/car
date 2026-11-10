# THIRD_PARTY_NOTICES

이 엔진은 외부 소스 코드를 **복사(vendoring)하거나 submodule로 넣지 않았습니다.** 아래 항목은 모두 pip 패키지 의존성으로만 사용합니다.

| 구성요소 | 버전 | 라이선스 | 출처 | 사용 위치 |
|---|---|---|---|---|
| highway-env | 1.12.1 | MIT — Copyright (c) 2018 Edouard Leurent, (c) 2023 Farama Foundation | https://github.com/Farama-Foundation/HighwayEnv | adapters/: `Road`, `RoadNetwork.straight_road_network`, `IDMVehicle` API 호출. 속도 계산 식(slip angle β)은 highway-env `Vehicle.step()`의 bicycle model 식을 따름 |
| gymnasium | 1.4.0 | MIT | https://github.com/Farama-Foundation/Gymnasium | highway-env 전이 의존성 |
| numpy | 2.5.4 | BSD-3-Clause | https://github.com/numpy/numpy | adapters/, generators/ |
| PyYAML | 6.0.3 | MIT | https://github.com/yaml/pyyaml | generators/ (시나리오 파일) |
| matplotlib | 3.11.2 | matplotlib License (PSF 기반) | https://github.com/matplotlib/matplotlib | visualization/ |
| Pillow | 13.0.0 | MIT-CMU (HPND) | https://github.com/python-pillow/Pillow | visualization/ (GIF 저장) |
| pytest / jsonschema | 9.2.0 / 4.26.0 | MIT / MIT | pytest-dev/pytest, python-jsonschema/jsonschema | tests/ |

- 전체 고정 버전은 `requirements.lock.txt`에 있습니다.
- INTERACTION 데이터셋, CARLA 에셋, 모델 가중치는 포함하지 않습니다. `samples/`는 모두 이 엔진이 생성한 합성 데이터입니다.

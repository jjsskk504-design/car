# Third-Party Notices

이 파일은 출처와 라이선스 추적을 위한 고지입니다. 외부 저장소의 소스, 데이터셋, 모델 가중치는 이 프로젝트에 복사하지 않았습니다.

## Runtime dependency

- **jsonschema 4.25.1** — MIT License — https://github.com/python-jsonschema/jsonschema
  - 사용 목적: PredictionFrame JSON Schema 검증
  - 포함 방식: PyPI 패키지 설치, vendoring 없음
  - 주요 전이 의존성: `attrs` (MIT), `jsonschema-specifications` (MIT), `referencing` (MIT), `rpds-py` (MIT), `typing_extensions` (PSF-2.0)

## Development-only dependencies

- **pytest 8.4.2** — MIT License — https://github.com/pytest-dev/pytest
  - 사용 목적: 단위·통합 테스트
  - 포함 방식: optional `dev` 패키지 설치
  - 전이 의존성: `colorama` (BSD-3-Clause), `exceptiongroup` (MIT), `iniconfig` (MIT), `packaging` (Apache-2.0 또는 BSD-2-Clause), `pluggy` (MIT), `Pygments` (BSD-2-Clause), `tomli` (MIT)

정확한 설치 버전은 `requirements-lock.txt`에 고정했습니다. 각 배포 패키지의 원문 라이선스는 설치된 wheel의 `.dist-info/licenses` 및 위 공식 저장소를 기준으로 확인해야 합니다.

## Evaluated but not included

- **Trajectron++** — MIT — Copyright (c) 2020 Stanford Autonomous Systems Lab — https://github.com/StanfordASL/Trajectron-plus-plus
- **INTERACTION dataset scripts** — BSD-3-Clause — https://github.com/interaction-dataset/interaction-dataset
- **PyTorch** — BSD-style and bundled third-party notices — https://github.com/pytorch/pytorch

위 세 저장소는 외부 실험 폴더에서만 점검했습니다. 참고 결과는 `OPEN_SOURCE_REVIEW.md`에 있으며 코드·데이터·가중치는 제품 폴더에 포함하지 않았습니다.


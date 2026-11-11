# 오픈소스 사전점검 기록

점검일: 2026-11-10~11 (Asia/Seoul)

모든 외부 저장소는 PRISM 작업 폴더 밖의 `C:\Users\82108\AndroidStudioProjects\opensource-lab\prism-part01`에 shallow clone했습니다. 외부 저장소 전체, 모델, 데이터셋, 가중치는 이 엔진에 복사하지 않았습니다.

## 후보 저장소 점검

| 후보 | URL / 확인 SHA | 라이선스 | 유지보수·실행 확인 | 결정 |
|---|---|---|---|---|
| Trajectron++ | https://github.com/StanfordASL/Trajectron-plus-plus / `1031c7bd1a444273af378c1ec1dcca907ba59830` | MIT, Stanford Autonomous Systems Lab | 마지막 확인 커밋 2023-03-11. 태그 없음. README는 Python 3.6 커널을 안내하고 `numpy==1.16.4`, `torch==1.4.0` 등 오래된 버전을 고정함. Python 3.10 격리 환경에서 의존성 해석을 시도했으나 NumPy 1.16.4 metadata 생성에서 실패. 원본 평가에는 별도 데이터·모델 필요. | P0/P1 미채택. 코드 복사 없음. P2 신경망 비교 후보로만 보류. |
| INTERACTION scripts | https://github.com/interaction-dataset/interaction-dataset / `3e45d7c02dc475173efba887804fb921f4c187e1` | BSD-3-Clause | 마지막 확인 커밋 2022-07-26. 태그 없음. Python 3.10 격리 환경에서 요구 패키지 설치, import 검사 통과(선택 Lanelet2 제외), 원본 테스트 `1 passed`. 데이터는 별도 사이트에서 별도 조건으로 받아야 함. | 지도/데이터 형식 참고만 함. 코드·데이터 복사 없음. |
| PyTorch | https://github.com/pytorch/pytorch / `595e53abbc99f57dd8a042310a296b4f84c73186` | BSD-style LICENSE 및 다수 제3자 고지 | 확인 커밋 2026-11-10. README의 소스 빌드는 Python 3.11+, Visual Studio/CMake 등 대형 도구체인을 요구함. Part 01 P0에는 신경망 런타임이 필요 없어 소스 빌드하지 않음. | P0/P1 미채택. P2가 승인되면 공식 wheel 방식으로 별도 검증. |

## 실행 결과 구분

### 성공

- INTERACTION `python/utils/check_imports.py`: 필수 fallback 경로 import 성공. Lanelet2는 선택 구성이라 미설치 메시지 확인.
- INTERACTION `python -m pytest -q`: `1 passed in 0.17s`.

### 실패

- Trajectron++ `pip install --dry-run -r requirements.txt`: Python 3.10에서 고정된 NumPy 1.16.4의 metadata 생성 실패. 이 상태를 실행 가능하다고 주장하지 않습니다.

### 의도적으로 미실행

- PyTorch 전체 소스 빌드: P0/P1에 필요하지 않고 30~60분 이상 대형 네이티브 빌드 도구체인을 요구하므로 범위에서 제외했습니다.
- Trajectron++ 학습/평가: 요구 데이터·가중치와 구형 Python 환경이 없으므로 수행하지 않았습니다.

## 재사용 방식 결정

- **직접 구현**: CV/CA, 좌표변환, 후보 궤적은 공개 수학식 수준에서 새로 작성했습니다. 외부 프로젝트 코드를 복사하지 않았습니다.
- **패키지 설치**: JSON 입출력은 `kotlinx.serialization-json:1.7.3`을 Maven Central에서 사용합니다. 테스트는 `junit:4.13.2`를 사용합니다.
- **submodule 미사용**: P0에 외부 저장소 런타임이 필요하지 않고, 구형·대형 저장소를 제품 트리에 결합하면 재현성과 공급망 부담이 커집니다.
- **vendoring 미사용**: 외부 코드를 복사할 필요가 없고 업데이트·라이선스 추적 부담을 피할 수 있습니다.
- **adapter 미사용**: 채택한 외부 추론 엔진이 없으므로 어댑터 대상이 없습니다. Pleos Vehicle SDK 연결은 `vehicle-integration/`에 격리합니다. P2에서 Trajectron 계열을 채택할 때 별도 어댑터 경계를 설계합니다.

## P2 신경망 진입 조건

Trajectron++ 또는 PyTorch 기반 모델은 다음을 모두 충족하기 전에는 추가하지 않습니다.

1. 현재 Python 버전에서 실제 데모 성공
2. 차량 데이터셋 사용권과 배포권 별도 확인
3. 모델 가중치의 라이선스·출처·재배포 가능 여부 확인
4. VehicleFrame/PredictionFrame 변환 어댑터와 단위 테스트
5. CV/CA보다 나은 정량 평가와 실패 시 폴백


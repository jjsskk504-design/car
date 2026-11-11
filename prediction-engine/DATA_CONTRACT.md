# Part 01 데이터 계약 메모

이 문서는 제공된 공통 규칙을 구현 관점에서 풀어쓴 메모입니다. 실제 PRISM 저장소의 공통 JSON Schema가 생기거나 다르면 그 Schema가 최종 기준입니다. 이 엔진은 `contracts/`를 수정하지 않습니다.

## 공통 단위와 축

| 항목 | 규칙 |
|---|---|
| 위치 | `x` 오른쪽, `y` 전방, 단위 m |
| 속도 | `vx`, `vy`, 단위 m/s |
| 방향 | +x축 기준 반시계 방향, 단위 rad |
| 시간 | 시나리오 경과 시간 `timestamp_ms`, 단위 ms |
| 예측 시간 | 기준 시점으로부터 상대 시간 `dt_ms`, 1~5000ms |

## VehicleFrame v1 입력

```text
timestamp_ms
ego_id
vehicles[]
  id, x, y, vx, vy, heading_rad
  length_m, width_m, lane_id
```

JSONL 한 줄에 하나의 완전한 VehicleFrame을 둡니다. 입력 줄 순서가 뒤섞여도 타임스탬프로 정렬합니다. 같은 타임스탬프가 두 번 있으면 어느 것이 진짜인지 임의 선택하지 않고 오류로 중단합니다.

누락 관측은 0이 아닙니다. 해당 시점 관측이 없다는 의미로 건너뜁니다. 동일 ID의 차체 크기가 0.5m 넘게 바뀌면 ID 재할당 가능성으로 오류를 냅니다.

## PredictionFrame 출력

```json
{
  "schema_version": "1.0",
  "timestamp_ms": 2000,
  "predictions": [
    {
      "vehicle_id": "car_01",
      "trajectories": [
        {
          "mode": "lane_keep",
          "confidence": null,
          "points": [
            {"dt_ms": 1000, "x": 1.0, "y": 15.0, "heading_rad": 1.5708}
          ]
        }
      ]
    }
  ]
}
```

- `timestamp_ms`: 입력의 최신 기준 시점
- `dt_ms`: 기준 시점에서 미래로 얼마나 떨어졌는지
- `x`, `y`, `heading_rad`: 기준 시점의 자차 좌표계에 고정
- `confidence`: 보정되지 않은 현재 버전은 반드시 `null`
- `vehicle_id`: 최신 VehicleFrame에 실제 존재하는 ID만 허용

내부 참고 Schema는 `src/main/resources/prediction_frame.schema.json`이며 Kotlin의 `PredictionEngine.validatePredictionFrame()`가 실제 런타임 검사를 수행합니다. 공통 Schema가 제공되면 CI에서 양쪽을 비교하고, 불일치가 있으면 임의 수정 대신 이슈를 등록해야 합니다.


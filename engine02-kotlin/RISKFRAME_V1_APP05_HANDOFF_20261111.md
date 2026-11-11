# Engine02 Kotlin �� App05 RiskFrame v1 handoff

**Source of truth:** `vehicle-integration/replay/src/main/kotlin/com/prism/vehicle/replay/Contracts.kt` and `ContractParser.kt`. These files remain unchanged.

## Exact app-visible data contract

Engine02 emits one JSON object per line (`risk_frames.jsonl`) from Kotlin `RiskFrameV1.toJsonLine()`:

```json
{"schema_version":"1.0","timestamp_ms":100,"risk_objects":[{"vehicle_id":"car_01","risk_level":"UNKNOWN","reason_codes":["ENGINE02_RESEARCH_ONLY","INSUFFICIENT_VERIFIED_OBSERVATION"],"ttc_s":null,"risk_zones":[]}]}
```

- `risk_level`: one of `NORMAL`, `WATCH`, `WARNING`, `UNKNOWN` (exact Part05 enum)
- `reason_codes`: list of stable uppercase research/geometry reason codes
- `ttc_s`: JSON null unless independently verified; no fictitious collision TTC
- `risk_zones`: empty unless a *verified road-tangent �� ego-local* transform is provided; nonempty values are positive `dt_ms` and 4-point polygons `{x,y}` in ego-local metres
- `vehicle_id`: must exist in the same-timestamp `VehicleFrame`
- `schema_version`: `1.0` and `timestamp_ms` identical to source `VehicleFrame`; never mix future/other-time frames

## Implementation

Kotlin only:
`engine02-kotlin/src/main/kotlin/ai/riskradar/engine02/RiskFrameV1.kt`

`Engine02RiskFrameAdapter.convert(timestampMs, vehicleIdsInVehicleFrame, items, verifiedEgoLocalTransform)`

`items` are pairs of a fully verified road-frame `Observation` and the same-track/same-time internal `IntentFrame`. Caller must provide the vehicle IDs observed in the matching `VehicleFrame`. Export with `RiskFrameV1.toJsonLine()`. The published app interface is RiskFrame v1, **not** the internal `BehaviorIntentFrame`.

**Risk zone safety:** `Observation` is in a lane/road tangent reference, which is NOT automatically the ego-local frame expected by 05. By default `verifiedEgoLocalTransform=null`, so risk zones remain empty even if a CV trajectory hypothesis exists. If an independently validated transform is provided with matching source timestamp and lane reference, target vehicle CV footprints may be exported for a research-only visual replay, clearly tagged `RISK_ZONE_CV_FOOTPRINT_ONLY`. No continuous-time impact prediction or on-road alert is established.

**Part05 integration:** 05 reads `app/src/main/assets/scenarios/<scenario_id>/risk_frames.jsonl` or its single/array JSON fallback. Its actual replay `ContractParser.parseRiskFrames` and `ScenarioTimeline.build` are included **read-only into the Engine02 test sourceSets**, so no 05 production code is modified.

## Status and limits

- Synthetic Kotlin/JVM tests inspect actual 05 parser and timeline, plus invalid IDs, mismatched timestamps, malformed polygons and unknown measurements.
- No Android/Pleos app UI / emulator / vehicle SDK test has been conducted in this adapter check.
- No real-world ADE/FDE, intent precision/recall, lead time or false alerts/hour has been measured.
- No Python is needed in the Engine02 runtime. Legacy Python research references remain untouched.
- This is a research/demonstration risk frame format, **not** a validated ADAS risk estimator.

Team repository: `qwon4466/PRISM`; isolated branch:
`feature/engine02-preintegration-20261111`. No main merge or frozen RiskFrame v1 source changes.

# RISK RADAR Engine02 ? Kotlin-first migration checkpoint

Date: 2026-11-11 KST. Scope: team repository qwon4466/PRISM independent
feature/engine02-preintegration-20261111 branch, **without** modifying team main,
PART01, vehicle-integration, visualization-engine, or frozen RiskFrame v1.

## Confirmed execution on notebook

- Authorized laptop: BOOK-R28NIKA2LB
- Workspace: Documents/RiskRadarEngine02_Kotlin_20261111/engine02-kotlin
- Kotlin source and Kotlin tests only; no Python runtime in this module.
- Kotlin Gradle plugin: 2.2.10; installed Gradle: 9.6.0;
  build JDK: openjdk-27.0.1.0.3; Java/Kotlin bytecode target: JVM 17.
- Command: `gradle test --offline --no-daemon --console=plain`.
- **BUILD SUCCESSFUL** on the notebook in the latest synthetic simulation run.
- Gradle JUnit XML: 5 test suites, **59 tests, 0 failures, 0 errors**.
- Log: `engine02-kotlin/kotlin_test_20261111_physics.log` (local only; ignored by Git).
- SHA256 of log: `C0D862CBC99A05A15E8287E06A1EDAA88C7777924385F0FA1E8C4F0E28FDC328`.

## Ported Kotlin capability

1. `Contracts.kt` and `IntentEngine.kt`: causal experimental V3
   KEEP/PREPARE/CHANGE/ABORT/BRAKE/YIELD/UNKNOWN states, track clock and
   identity epochs, missing signals and lane gating.
2. `Geometry.kt`: oriented body overlap and constant-velocity projected
   overlap **reported separately**; no collision probability.
3. `EventEvaluation.kt`: one-to-one episode/event matching, lead time on
   labeled synthetic fixtures only, leakage-resistant split validator.
4. `Av2Guard.kt`: AV2 per-scenario composite identity, nanoseconds,
   box center vs front center, observed-only prefix and verified map/lane
   provenance. UNKNOWN signal default.
5. `DatasetGuards.kt`: CARLA ego signal cannot be copied to other cars,
   SHA-256 duplicate-group manifest checks, NGSIM feet->meters,
   front-center origin, site/recording/vehicle composite identity and
   Peachtree quarantine.
6. `PhysicalSimulation.kt` and `PhysicalAudit.kt`: standalone
   deterministic 50ms physical synthetic world, 4 scenes
   (lane_change/yield_merge/shockwave/dense), actual sampled body-overlap
   all-pairs QA and jerk/acceleration/road boundary tests.

13 Kotlin source/test files; 59 verified tests. This **does not** mean
the old Python scripts were deleted or that Python-to-Kotlin parity is
formally proven across all historic dataset inputs.

## Excluded from claim

- Existing `engine02_preintegration_20261111` Python files remain
  **historical research reference**, not runtime input. They must be
  retired only after executable Kotlin parity and team interface review.
- New Kotlin JVM module has **not** been integrated into the Android
  app or produced an APK.
- Real-time hardware/SDK CAN/Pleos integration, event annotation protocol,
  valid real held-out ADE/FDE/intent precision-recall/lead-time/false alerts
  per track-hour and real-world vehicle safety: **NOT MEASURED / NOT APPROVED**.
- Synthetic 50ms geometry checks are not continuous-time collision proofs.
- No raw CARLA/NGSIM/AV2/Waymo data, tokens, restricted assets or weights in Git.

## Next integration steps (requires team review)

1. Add an explicit Kotlin adapter for the existing `VehicleFrame` and
   `PredictionFrame` contracts, preserving right+/forward+ axes and
   timestamp units, without editing `RiskFrame v1`.
2. Add Android Kotlin library target / app Gradle integration and verify
   release/runtime dependencies on the target Android SDK.
3. Build a cross-module compatibility/behavior replay test suite with
   independently reviewed synthetic fixtures before using any data labels.
4. Freeze scope after verified parity; deprecate Python research scripts
   in app packaging, not by deleting evidence from Git history.

## Status

PASS: Kotlin JVM build + 59/59 unit tests on notebook.
PARTIAL: Migration of engine algorithms to standalone Kotlin.
BLOCKED/PENDING: Android cross-module product integration and real-world validation.

## VehicleFrame and parity follow-up

- The independent Engine02 JVM build passed 83/83 tests across seven suites.
- The independent PRISM contract build passed 4/4 tests using the existing team
  replay DTOs and parser. The contract sources are compiled as test sources.
- Kotlin matched the Python V3 oracle over 49 synthetic frames in nine
  default-parameter scenarios. The fixture generator runs offline and the
  Kotlin builds run without Python.
- The adapter requires synchronized body-center road projections and
  frame-aligned signal evidence. Offline evaluation includes temporal splits,
  miss rate, and the physical QA gate.
- Android/Pleos integration and live road projection are pending.
  Real-world metrics: NOT MEASURED.

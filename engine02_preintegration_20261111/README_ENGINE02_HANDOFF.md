# RISK RADAR Engine02 — isolated preintegration research snapshot

This folder contains the completed *research-side* Engine02 code and tests from the owner's personal repository. It is intentionally isolated on branch `feature/engine02-preintegration-20261111` and **does not modify or replace PRISM's existing PART01, frozen RiskFrame v1, app code, or production interfaces.**

## Included

- `scripts/behavior_v3/` — causal, past-only experimental behavior-intent logic, evaluation contract, smoke runner and inventory
- `scripts/behavior_v3_v4/test_adversarial_unittest.py` — 16 independent Python standard-library synthetic adversarial checks
- `scripts/sim_v2/` — synthetic physical simulation and QA, with test module
- `scripts/data_quality/` — NGSIM data quality analysis utilities (raw recordings **not included**)
- `prism-risk-engine/tests/` — independent V3 behavior/data quality tests
- `docs/` — implementation notes, research, data safety gates, adversarial coverage
- `artifacts/overnight_rdc_main/` — selected textual QA summaries; NO raw CSV, dataset archive, model weights or private assets

## Reproducing checks

From this directory:

```sh
python -m unittest scripts.behavior_v3_v4.test_adversarial_unittest -v
python -m pytest -q prism-risk-engine/tests/test_behavior_v3.py
```

The first test suite uses the Python standard library. The second requires pytest in a compatible Python environment. Test results recorded on DO, 2026-11-11: **16/16 new stdlib adversarial PASS** (after fixing an incorrect test fixture expectation), earlier **72/72 V3 focused PASS** and **35/35 physics-risk focused PASS**. Historical passes were **not rerun as part of the team Git push**.

## Restrictions and validity

- These heuristic outputs are not calibrated probabilities and are **not approved for real-world vehicle warning/control**.
- Independent real-data ADE/FDE, intent precision/recall, event lead-time and false alerts per track-hour: **NOT MEASURED**. The synthetic smoke numbers must not be called real-world performance.
- NGSIM Peachtree recording remains **QUARANTINED** due to duplicate vehicle/time and vehicle/frame keys. Row counts and lane-ID transitions are not independently labeled event counts.
- NGSIM coordinates require feet-to-meters conversion (0.3048), front-center origin, and per-site/per-recording identity keys. CARLA turn indicators apply to the instrumented ego only; surrounding signals stay UNKNOWN when not separately observed.
- DATA LICENSE (third-party research traces) is separate from CODE LICENSE; no third-party data rights are granted by this commit.

**Review before integration. No automatic merge into main, no force push.**

Origin: personal backup `Ukdongs/Practice`, branch `feature/engine02-research-snapshot-20261111`. Content copied via explicit allowlist; only independent research code, synthetic tests, documentation and textual QC evidence.

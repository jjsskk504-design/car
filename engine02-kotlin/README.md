# RISK RADAR Engine02 ? Kotlin-first research engine

This independent Kotlin/JVM module is intended to replace the Python Engine02
**runtime**, not to modify PRISM PART01 or frozen RiskFrame v1 contracts.

## Staged migration
1. Port causal Behavior V3 heuristics, risk geometry and offline event/holdout metrics to Kotlin.
2. Port AV2/NGSIM/CARLA adapters with strict timestamps, coordinates, provenance and uncertainty.
3. Port V2 physical simulator and data QA after Kotlin V3 unit tests pass.
4. Integrate with PRISM Kotlin prediction and vehicle modules through a reviewed adapter;
   then remove legacy Python snapshots from any final Android deployment.

The historical Python files in the separate engine02_preintegration_20261111
folder are **reference-only** and not packaged by this module.

## Run

From the module directory, with JDK 17+ and Gradle:

```powershell
gradle test --no-daemon
```

Target JVM bytecode level 17. This standalone JVM build is not an Android
APK or an in-vehicle runtime validation.

No raw CARLA, NGSIM, AV2 or Waymo files are included. This project makes no
real-world safety, calibrated probability, ADE/FDE, precision/recall, lead
time or false alerts/hour claim. These require independent authorized
ground-truth data, measured exposure and applicable licenses.

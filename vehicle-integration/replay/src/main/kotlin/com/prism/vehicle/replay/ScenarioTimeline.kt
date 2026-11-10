package com.prism.vehicle.replay

/**
 * 03(VehicleFrame) + 01(PredictionFrame) + 02(RiskFrame) 결과를 timestamp 로 묶은 재생용 타임라인.
 * 예측·위험은 같은 timestamp 의 VehicleFrame 좌표계에서만 함께 표시한다(계약: source timestamp 좌표계 고정).
 */
class ScenarioTimeline private constructor(
    val manifest: ScenarioManifest,
    val vehicleFrames: List<VehicleFrame>,
    private val predictionByTs: Map<Long, PredictionFrame>,
    private val riskByTs: Map<Long, RiskFrame>,
    val issues: List<Issue>,
) {
    val isPlayable: Boolean get() = issues.none { it.severity == Severity.ERROR }
    val durationMs: Long get() = maxOf(manifest.durationMs, vehicleFrames.lastOrNull()?.timestampMs ?: 0)

    /** 재생 시각 tMs 에 보여줄 데이터. 해당 시각 직전 프레임이 2 step 보다 오래되면 stale(누락 구간). */
    fun snapshotAt(tMs: Long): Snapshot {
        val frame = latestAtOrBefore(tMs)
        val age = frame?.let { tMs - it.timestampMs }
        val stale = frame == null || age!! > manifest.stepMs * 2
        return Snapshot(
            tMs = tMs,
            vehicle = frame,
            prediction = frame?.let { predictionByTs[it.timestampMs] },
            risk = frame?.let { riskByTs[it.timestampMs] },
            stale = stale,
            frameAgeMs = age,
        )
    }

    private fun latestAtOrBefore(tMs: Long): VehicleFrame? {
        var lo = 0
        var hi = vehicleFrames.lastIndex
        var found: VehicleFrame? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (vehicleFrames[mid].timestampMs <= tMs) { found = vehicleFrames[mid]; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    companion object {
        fun build(
            manifest: ScenarioManifest,
            vehicles: List<VehicleFrame>,
            predictions: List<PredictionFrame>,
            risks: List<RiskFrame>,
            parseIssues: List<Issue> = emptyList(),
        ): ScenarioTimeline {
            val issues = parseIssues.toMutableList()
            fun err(where: String, msg: String) { issues += Issue(Severity.ERROR, where, msg) }
            fun warn(where: String, msg: String) { issues += Issue(Severity.WARNING, where, msg) }

            if (manifest.stepMs <= 0) err("manifest", "step_ms 는 0보다 커야 합니다")
            if (vehicles.isEmpty()) err("vehicle_frames", "VehicleFrame 이 없습니다")

            checkSchema("vehicle_frames", vehicles.map { it.schemaVersion }, ::err)
            checkSchema("prediction_frames", predictions.map { it.schemaVersion }, ::err)
            checkSchema("risk_frames", risks.map { it.schemaVersion }, ::err)

            val sorted = vehicles.sortedBy { it.timestampMs }
            val idsByTs = HashMap<Long, Set<String>>()
            for (f in sorted) {
                val at = "vehicle_frames@${f.timestampMs}"
                if (idsByTs.containsKey(f.timestampMs)) err(at, "timestamp 중복")
                if (f.timestampMs < 0) err(at, "timestamp_ms 음수")
                if (f.coordinateSystem != null && f.coordinateSystem != "ego_local_m") err(at, "지원하지 않는 coordinate_system '${f.coordinateSystem}'")
                if (f.egoId != manifest.egoId) err(at, "ego_id '${f.egoId}' 가 manifest '${manifest.egoId}' 와 다름")
                val ids = f.vehicles.map { it.id }
                if (ids.size != ids.toSet().size) err(at, "vehicle id 중복")
                if (f.egoId !in ids) err(at, "ego 차량 '${f.egoId}' 누락")
                f.vehicles.forEach { v ->
                    if (!allFinite(v.x, v.y, v.vx, v.vy, v.headingRad, v.lengthM, v.widthM)) err(at, "${v.id}: 유한하지 않은 수치")
                    if (v.lengthM <= 0 || v.widthM <= 0) err(at, "${v.id}: 차량 크기 <= 0")
                }
                idsByTs[f.timestampMs] = ids.toSet()
            }
            if (sorted.isNotEmpty() && manifest.stepMs > 0) {
                val gaps = sorted.zipWithNext().count { (a, b) -> b.timestampMs - a.timestampMs > manifest.stepMs * 2 }
                if (gaps > 0) warn("vehicle_frames", "데이터 누락 구간 ${gaps}개 (step_ms ${manifest.stepMs} 의 2배 초과 간격)")
                if (sorted.last().timestampMs > manifest.durationMs) warn("vehicle_frames", "마지막 timestamp 가 duration_ms 를 넘음")
            }

            val predictionByTs = linkedMapOf<Long, PredictionFrame>()
            for (p in predictions) {
                val at = "prediction_frames@${p.timestampMs}"
                val ids = idsByTs[p.timestampMs]
                if (ids == null) { warn(at, "같은 timestamp 의 VehicleFrame 이 없어 표시하지 않음"); continue }
                if (predictionByTs.put(p.timestampMs, p) != null) err(at, "timestamp 중복")
                p.predictions.forEach { vp ->
                    if (vp.vehicleId !in ids) err(at, "vehicle_id '${vp.vehicleId}' 가 VehicleFrame 에 없음")
                    vp.trajectories.forEach { t ->
                        t.confidence?.let { if (!it.isFinite() || it < 0 || it > 1) err(at, "${vp.vehicleId}: confidence 범위 오류") }
                        t.points.forEach { pt ->
                            if (pt.dtMs <= 0) err(at, "${vp.vehicleId}: dt_ms 는 양수여야 함")
                            if (!allFinite(pt.x, pt.y, pt.headingRad ?: 0.0)) err(at, "${vp.vehicleId}: 유한하지 않은 좌표")
                        }
                    }
                }
            }

            val riskByTs = linkedMapOf<Long, RiskFrame>()
            for (r in risks) {
                val at = "risk_frames@${r.timestampMs}"
                val ids = idsByTs[r.timestampMs]
                if (ids == null) { warn(at, "같은 timestamp 의 VehicleFrame 이 없어 표시하지 않음"); continue }
                if (riskByTs.put(r.timestampMs, r) != null) err(at, "timestamp 중복")
                r.riskObjects.forEach { o ->
                    if (o.vehicleId !in ids) err(at, "vehicle_id '${o.vehicleId}' 가 VehicleFrame 에 없음")
                    o.ttcS?.let { if (!it.isFinite() || it < 0) err(at, "${o.vehicleId}: ttc_s 오류") }
                    o.riskZones.forEach { z ->
                        if (z.polygon.size < 3) err(at, "${o.vehicleId}: polygon 꼭짓점 3개 미만")
                        if (z.polygon.any { !allFinite(it.x, it.y) }) err(at, "${o.vehicleId}: 유한하지 않은 polygon")
                    }
                }
            }

            return ScenarioTimeline(manifest, sorted, predictionByTs, riskByTs, issues)
        }

        private fun checkSchema(source: String, versions: List<String>, err: (String, String) -> Unit) {
            versions.filter { it.substringBefore('.') != SUPPORTED_SCHEMA_MAJOR }.distinct().forEach {
                err(source, "지원하지 않는 schema_version '$it' (v$SUPPORTED_SCHEMA_MAJOR.x 만 지원)")
            }
        }

        private fun allFinite(vararg values: Double) = values.all { it.isFinite() }
    }
}

data class Snapshot(
    val tMs: Long,
    val vehicle: VehicleFrame?,
    val prediction: PredictionFrame?,
    val risk: RiskFrame?,
    /** 직전 VehicleFrame 이 너무 오래되어 현재 상태로 볼 수 없음 → 화면에 '데이터 누락' 표시. */
    val stale: Boolean,
    val frameAgeMs: Long?,
) {
    fun riskOf(vehicleId: String): RiskObject? = risk?.riskObjects?.firstOrNull { it.vehicleId == vehicleId }

    /** 가장 높은 위험 등급. UNKNOWN 은 WATCH 보다 낮게 두지 않고 별도 표시용으로 NORMAL 위에 둔다. */
    val topLevel: RiskLevel
        get() {
            if (stale) return RiskLevel.UNKNOWN
            val levels = risk?.riskObjects?.map { it.riskLevel } ?: return RiskLevel.UNKNOWN
            return listOf(RiskLevel.WARNING, RiskLevel.WATCH, RiskLevel.UNKNOWN).firstOrNull { it in levels } ?: RiskLevel.NORMAL
        }
}

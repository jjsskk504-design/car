package ai.prism.visualization.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 계약 JSON <-> Kotlin 모델 변환 (팀 저장소 계약 필드명 기준, Contracts.kt 머리말 참고).
 *
 * 원칙: 잘못된 항목 하나 때문에 화면 전체가 죽지 않도록, 항목 단위로 건너뛰고
 * [ParseResult.warnings] 에 기록한다. 최상위 구조가 깨진 경우에만 예외를 던진다.
 * 알 수 없는 필드는 무시한다(다른 엔진의 확장 필드 허용).
 */
object ContractJson {

    data class ParseResult<T>(val value: T, val warnings: List<String>)

    private fun checkVersion(o: JSONObject, warnings: MutableList<String>): String {
        val v = o.optString("schema_version", "")
        if (v.isEmpty()) warnings += "schema_version 없음"
        else if (v != SCHEMA_VERSION) warnings += "schema_version '$v' (지원: $SCHEMA_VERSION)"
        return v.ifEmpty { SCHEMA_VERSION }
    }

    // ---------------------------------------------------------------- VehicleFrame v1

    fun parseVehicleFrame(json: String): ParseResult<VehicleFrame> = parseVehicleFrame(JSONObject(json))

    fun parseVehicleFrame(o: JSONObject): ParseResult<VehicleFrame> {
        val warnings = ArrayList<String>()
        val version = checkVersion(o, warnings)
        val cs = o.optString("coordinate_system", "")
        if (cs.isNotEmpty() && cs != COORDINATE_SYSTEM) warnings += "coordinate_system '$cs' (지원: $COORDINATE_SYSTEM)"
        if (cs.isEmpty()) warnings += "coordinate_system 없음"
        val arr = o.optJSONArray("vehicles") ?: JSONArray().also { warnings += "vehicles 필드 없음" }
        val vehicles = ArrayList<VehicleState>()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i)
            if (v == null) { warnings += "vehicles[$i] 객체 아님"; continue }
            val id = v.optString("id", "")
            if (id.isEmpty()) { warnings += "vehicles[$i] id 없음"; continue }
            val nums = listOf("x", "y", "vx", "vy", "heading_rad", "length_m", "width_m")
                .associateWith { k -> v.optDouble(k, Double.NaN) }
            val bad = nums.filterValues { !it.isFinite() }.keys
            if (bad.isNotEmpty()) { warnings += "vehicles[$i]($id) 값 누락/비정상: $bad"; continue }
            vehicles += VehicleState(
                id = id,
                x = nums.getValue("x").toFloat(),
                y = nums.getValue("y").toFloat(),
                vx = nums.getValue("vx").toFloat(),
                vy = nums.getValue("vy").toFloat(),
                headingRad = nums.getValue("heading_rad").toFloat(),
                lengthM = nums.getValue("length_m").toFloat(),
                widthM = nums.getValue("width_m").toFloat(),
                laneId = if (v.has("lane_id") && !v.isNull("lane_id")) v.get("lane_id").toString() else null,
            )
        }
        return ParseResult(
            VehicleFrame(
                timestampMs = o.getLong("timestamp_ms"),
                egoId = o.optString("ego_id", "ego"),
                vehicles = vehicles,
                schemaVersion = version,
                coordinateSystem = cs.ifEmpty { COORDINATE_SYSTEM },
            ),
            warnings,
        )
    }

    fun toJson(f: VehicleFrame): JSONObject = JSONObject().apply {
        put("schema_version", f.schemaVersion)
        put("timestamp_ms", f.timestampMs)
        put("coordinate_system", f.coordinateSystem)
        put("ego_id", f.egoId)
        put("vehicles", JSONArray().apply {
            f.vehicles.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("x", rnd(v.x)); put("y", rnd(v.y))
                    put("vx", rnd(v.vx)); put("vy", rnd(v.vy))
                    put("heading_rad", rnd(v.headingRad, 6))
                    put("length_m", rnd(v.lengthM)); put("width_m", rnd(v.widthM))
                    put("lane_id", v.laneId ?: JSONObject.NULL)
                })
            }
        })
    }

    // ---------------------------------------------------------------- PredictionFrame 1.0

    fun parsePredictionFrame(json: String): ParseResult<PredictionFrame> = parsePredictionFrame(JSONObject(json))

    fun parsePredictionFrame(o: JSONObject): ParseResult<PredictionFrame> {
        val warnings = ArrayList<String>()
        val version = checkVersion(o, warnings)
        val preds = ArrayList<VehiclePrediction>()
        val arr = o.optJSONArray("predictions") ?: JSONArray().also { warnings += "predictions 필드 없음" }
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val vid = p.optString("vehicle_id", "")
            if (vid.isEmpty()) { warnings += "predictions[$i] vehicle_id 없음"; continue }
            val trajs = ArrayList<Trajectory>()
            val ta = p.optJSONArray("trajectories") ?: JSONArray().also { warnings += "predictions[$i]($vid) trajectories 없음" }
            for (j in 0 until ta.length()) {
                val c = ta.optJSONObject(j) ?: continue
                val pts = ArrayList<TrajectoryPoint>()
                val pa = c.optJSONArray("points") ?: JSONArray()
                for (k in 0 until pa.length()) {
                    val q = pa.optJSONObject(k) ?: continue
                    val x = q.optDouble("x", Double.NaN)
                    val y = q.optDouble("y", Double.NaN)
                    if (!x.isFinite() || !y.isFinite()) { warnings += "predictions[$i]($vid) 궤적 점 좌표 비정상"; continue }
                    val h = q.optDouble("heading_rad", Double.NaN)
                    pts += TrajectoryPoint(q.optLong("dt_ms", 0), x.toFloat(), y.toFloat(), if (h.isFinite()) h.toFloat() else null)
                }
                val conf = c.optDouble("confidence", Double.NaN)
                trajs += Trajectory(c.optString("mode", "unknown"), if (conf.isFinite()) conf.toFloat() else null, pts)
            }
            preds += VehiclePrediction(vid, trajs)
        }
        return ParseResult(PredictionFrame(o.getLong("timestamp_ms"), preds, version), warnings)
    }

    fun toJson(f: PredictionFrame): JSONObject = JSONObject().apply {
        put("schema_version", f.schemaVersion)
        put("timestamp_ms", f.timestampMs)
        put("predictions", JSONArray().apply {
            f.predictions.forEach { p ->
                put(JSONObject().apply {
                    put("vehicle_id", p.vehicleId)
                    put("trajectories", JSONArray().apply {
                        p.trajectories.forEach { c ->
                            put(JSONObject().apply {
                                put("mode", c.mode)
                                put("confidence", c.confidence?.let { rnd(it, 3) } ?: JSONObject.NULL)
                                put("points", JSONArray().apply {
                                    c.points.forEach { q ->
                                        put(JSONObject().apply {
                                            put("dt_ms", q.dtMs)
                                            put("x", rnd(q.x)); put("y", rnd(q.y))
                                            put("heading_rad", rnd(q.headingRad ?: (Math.PI / 2).toFloat(), 6))
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    // ---------------------------------------------------------------- RiskFrame 1.0

    fun parseRiskFrame(json: String): ParseResult<RiskFrame> = parseRiskFrame(JSONObject(json))

    fun parseRiskFrame(o: JSONObject): ParseResult<RiskFrame> {
        val warnings = ArrayList<String>()
        val version = checkVersion(o, warnings)
        val risks = ArrayList<VehicleRisk>()
        val arr = o.optJSONArray("risk_objects") ?: JSONArray().also { warnings += "risk_objects 필드 없음" }
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val vid = r.optString("vehicle_id", "")
            if (vid.isEmpty()) { warnings += "risk_objects[$i] vehicle_id 없음"; continue }
            val levelStr = r.optString("risk_level", "")
            val level = RiskLevel.parse(levelStr)
            if (level == RiskLevel.UNKNOWN && !levelStr.equals("UNKNOWN", true)) {
                warnings += "risk_objects[$i]($vid) 알 수 없는 등급 '$levelStr' → UNKNOWN"
            }
            val zones = ArrayList<RiskZone>()
            val za = r.optJSONArray("risk_zones") ?: JSONArray()
            for (k in 0 until za.length()) {
                val z = za.optJSONObject(k) ?: continue
                val poly = ArrayList<Point2>()
                val pa = z.optJSONArray("polygon") ?: JSONArray()
                for (m in 0 until pa.length()) {
                    val q = pa.optJSONObject(m) ?: continue
                    val x = q.optDouble("x", Double.NaN); val y = q.optDouble("y", Double.NaN)
                    if (x.isFinite() && y.isFinite()) poly += Point2(x.toFloat(), y.toFloat())
                }
                if (poly.size >= 3) zones += RiskZone(z.optLong("dt_ms", 0), poly)
                else warnings += "risk_objects[$i]($vid) risk_zones[$k] 꼭짓점 3개 미만"
            }
            val ttc = r.optDouble("ttc_s", Double.NaN)
            risks += VehicleRisk(
                vehicleId = vid,
                level = level,
                reasonCodes = strings(r.optJSONArray("reason_codes")),
                ttcS = if (ttc.isFinite()) ttc.toFloat() else null,
                riskZones = zones,
                sourceVehicleIds = strings(r.optJSONArray("source_vehicle_ids")),
                trajectoryMode = r.optString("trajectory_mode", "").ifEmpty { null },
                message = r.optString("message", "").ifEmpty { null },
            )
        }
        return ParseResult(RiskFrame(o.getLong("timestamp_ms"), risks, version), warnings)
    }

    /**
     * @param includeExtensions false 면 계약 필드만 내보낸다. true 면 시각화 엔진이 제안한 선택 필드
     *   (source_vehicle_ids, trajectory_mode, message)를 함께 내보낸다 — 다른 엔진은 무시한다(ignoreUnknownKeys).
     */
    fun toJson(f: RiskFrame, includeExtensions: Boolean = true): JSONObject = JSONObject().apply {
        put("schema_version", f.schemaVersion)
        put("timestamp_ms", f.timestampMs)
        put("risk_objects", JSONArray().apply {
            f.risks.forEach { r ->
                put(JSONObject().apply {
                    put("vehicle_id", r.vehicleId)
                    put("risk_level", r.level.name)
                    put("reason_codes", JSONArray(r.reasonCodes))
                    put("ttc_s", r.ttcS?.let { rnd(it) } ?: JSONObject.NULL)
                    put("risk_zones", JSONArray().apply {
                        r.riskZones.forEach { z ->
                            put(JSONObject().apply {
                                put("dt_ms", z.dtMs)
                                put("polygon", JSONArray().apply {
                                    z.polygon.forEach { p -> put(JSONObject().apply { put("x", rnd(p.x)); put("y", rnd(p.y)) }) }
                                })
                            })
                        }
                    })
                    if (includeExtensions) {
                        if (r.sourceVehicleIds.isNotEmpty()) put("source_vehicle_ids", JSONArray(r.sourceVehicleIds))
                        r.trajectoryMode?.let { put("trajectory_mode", it) }
                        r.message?.let { put("message", it) }
                    }
                })
            }
        })
    }

    fun parseRoad(o: JSONObject?): RoadConfig = if (o == null) RoadConfig() else RoadConfig(
        laneCount = o.optInt("lane_count", 3),
        laneWidthM = o.optDouble("lane_width_m", 3.5).toFloat(),
        egoLaneIndex = o.optInt("ego_lane_index", 1),
    )

    fun toJson(r: RoadConfig): JSONObject = JSONObject().apply {
        put("lane_count", r.laneCount)
        put("lane_width_m", rnd(r.laneWidthM))
        put("ego_lane_index", r.egoLaneIndex)
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it, "").ifEmpty { null } }

    private fun rnd(v: Float, digits: Int = 2): Double {
        var m = 1.0
        repeat(digits) { m *= 10 }
        return Math.round(v * m) / m
    }
}

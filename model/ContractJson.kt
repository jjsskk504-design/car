package ai.prism.visualization.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 계약 JSON <-> Kotlin 모델 변환.
 *
 * 원칙: 잘못된 항목 하나 때문에 화면 전체가 죽지 않도록, 차량 단위로 건너뛰고
 * [ParseResult.warnings] 에 기록한다. 최상위 구조가 깨진 경우에만 예외를 던진다.
 */
object ContractJson {

    data class ParseResult<T>(val value: T, val warnings: List<String>)

    // ---------------------------------------------------------------- VehicleFrame v1

    fun parseVehicleFrame(json: String): ParseResult<VehicleFrame> = parseVehicleFrame(JSONObject(json))

    fun parseVehicleFrame(o: JSONObject): ParseResult<VehicleFrame> {
        val warnings = ArrayList<String>()
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
                laneId = if (v.has("lane_id") && !v.isNull("lane_id")) v.optInt("lane_id") else null,
            )
        }
        return ParseResult(
            VehicleFrame(
                timestampMs = o.getLong("timestamp_ms"),
                egoId = o.optString("ego_id", "ego"),
                vehicles = vehicles,
            ),
            warnings,
        )
    }

    fun toJson(f: VehicleFrame): JSONObject = JSONObject().apply {
        put("timestamp_ms", f.timestampMs)
        put("ego_id", f.egoId)
        put("vehicles", JSONArray().apply {
            f.vehicles.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("x", rnd(v.x)); put("y", rnd(v.y))
                    put("vx", rnd(v.vx)); put("vy", rnd(v.vy))
                    put("heading_rad", rnd(v.headingRad, 4))
                    put("length_m", rnd(v.lengthM)); put("width_m", rnd(v.widthM))
                    put("lane_id", v.laneId ?: JSONObject.NULL)
                })
            }
        })
    }

    // ---------------------------------------------------------------- PredictionFrame (잠정)

    fun parsePredictionFrame(o: JSONObject): ParseResult<PredictionFrame> {
        val warnings = ArrayList<String>()
        val preds = ArrayList<VehiclePrediction>()
        val arr = o.optJSONArray("predictions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val vid = p.optString("vehicle_id", "")
            if (vid.isEmpty()) { warnings += "predictions[$i] vehicle_id 없음"; continue }
            val cands = ArrayList<TrajectoryCandidate>()
            val ca = p.optJSONArray("candidates") ?: JSONArray()
            for (j in 0 until ca.length()) {
                val c = ca.optJSONObject(j) ?: continue
                val pts = ArrayList<TrajectoryPoint>()
                val pa = c.optJSONArray("points") ?: JSONArray()
                for (k in 0 until pa.length()) {
                    val q = pa.optJSONObject(k) ?: continue
                    val x = q.optDouble("x", Double.NaN)
                    val y = q.optDouble("y", Double.NaN)
                    if (!x.isFinite() || !y.isFinite()) continue
                    val h = q.optDouble("heading_rad", Double.NaN)
                    pts += TrajectoryPoint(q.optLong("t_offset_ms", 0), x.toFloat(), y.toFloat(),
                        if (h.isFinite()) h.toFloat() else null)
                }
                cands += TrajectoryCandidate(c.optString("candidate_id", "$vid-$j"), c.optString("maneuver", "UNKNOWN"), pts)
            }
            preds += VehiclePrediction(vid, cands)
        }
        return ParseResult(PredictionFrame(o.getLong("timestamp_ms"), preds), warnings)
    }

    fun toJson(f: PredictionFrame): JSONObject = JSONObject().apply {
        put("timestamp_ms", f.timestampMs)
        put("predictions", JSONArray().apply {
            f.predictions.forEach { p ->
                put(JSONObject().apply {
                    put("vehicle_id", p.vehicleId)
                    put("candidates", JSONArray().apply {
                        p.candidates.forEach { c ->
                            put(JSONObject().apply {
                                put("candidate_id", c.candidateId)
                                put("maneuver", c.maneuver)
                                put("points", JSONArray().apply {
                                    c.points.forEach { q ->
                                        put(JSONObject().apply {
                                            put("t_offset_ms", q.tOffsetMs)
                                            put("x", rnd(q.x)); put("y", rnd(q.y))
                                            q.headingRad?.let { put("heading_rad", rnd(it, 4)) }
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

    // ---------------------------------------------------------------- RiskFrame (잠정)

    fun parseRiskFrame(o: JSONObject): ParseResult<RiskFrame> {
        val warnings = ArrayList<String>()
        val risks = ArrayList<VehicleRisk>()
        val arr = o.optJSONArray("risks") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val vid = r.optString("vehicle_id", "")
            if (vid.isEmpty()) { warnings += "risks[$i] vehicle_id 없음"; continue }
            val levelStr = r.optString("level", "")
            val level = RiskLevel.parse(levelStr)
            if (level == RiskLevel.UNKNOWN && !levelStr.equals("UNKNOWN", true)) {
                warnings += "risks[$i]($vid) 알 수 없는 등급 '$levelStr' → UNKNOWN"
            }
            val region = ArrayList<Point2>()
            val ra = r.optJSONArray("risk_region") ?: JSONArray()
            for (k in 0 until ra.length()) {
                val q = ra.optJSONObject(k) ?: continue
                val x = q.optDouble("x", Double.NaN); val y = q.optDouble("y", Double.NaN)
                if (x.isFinite() && y.isFinite()) region += Point2(x.toFloat(), y.toFloat())
            }
            risks += VehicleRisk(
                vehicleId = vid,
                level = level,
                causeCodes = strings(r.optJSONArray("cause_codes")),
                sourceVehicleIds = strings(r.optJSONArray("source_vehicle_ids")),
                riskRegion = region,
                trajectoryCandidateId = r.optString("trajectory_candidate_id", "").ifEmpty { null },
                message = r.optString("message", "").ifEmpty { null },
            )
        }
        return ParseResult(RiskFrame(o.getLong("timestamp_ms"), risks), warnings)
    }

    fun toJson(f: RiskFrame): JSONObject = JSONObject().apply {
        put("timestamp_ms", f.timestampMs)
        put("risks", JSONArray().apply {
            f.risks.forEach { r ->
                put(JSONObject().apply {
                    put("vehicle_id", r.vehicleId)
                    put("level", r.level.name)
                    put("cause_codes", JSONArray(r.causeCodes))
                    if (r.sourceVehicleIds.isNotEmpty()) put("source_vehicle_ids", JSONArray(r.sourceVehicleIds))
                    if (r.riskRegion.isNotEmpty()) put("risk_region", JSONArray().apply {
                        r.riskRegion.forEach { p -> put(JSONObject().apply { put("x", rnd(p.x)); put("y", rnd(p.y)) }) }
                    })
                    r.trajectoryCandidateId?.let { put("trajectory_candidate_id", it) }
                    r.message?.let { put("message", it) }
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

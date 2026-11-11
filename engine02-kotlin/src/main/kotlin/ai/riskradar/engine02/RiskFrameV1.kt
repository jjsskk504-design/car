package ai.riskradar.engine02

import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Engine02-owned implementation of the Part05 RiskFrame v1 wire contract.
 * Reference: vehicle-integration/replay/.../Contracts.kt (READ ONLY).
 * The output coordinate frame is ego-local: x=right+, y=forward+, metres.
 * Risk and VehicleFrame must refer to the same source timestamp.
 */
enum class RiskLevelV1 { NORMAL, WATCH, WARNING, UNKNOWN }

data class PointV1(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) }
}
data class RiskZoneV1(val dtMs: Long, val polygon: List<PointV1>) {
    init { require(dtMs > 0 && polygon.size >= 3) }
}
data class RiskObjectV1(
    val vehicleId: String,
    val riskLevel: RiskLevelV1,
    val reasonCodes: List<String>,
    val ttcS: Double? = null,
    val riskZones: List<RiskZoneV1> = emptyList(),
) {
    init {
        require(vehicleId.isNotBlank())
        require(ttcS == null || (ttcS.isFinite() && ttcS >= 0.0))
    }
}
/**
 * Caller-provided VERIFIED local tangent-to-ego transform. Never infer this
 * from lane_id or Frenet d/s alone. In absence of this, risk_zones is [].
 */
data class EgoLocalTransformV1(
    val timestampMs: Long,
    val laneReference: String,
    val originDM: Double,
    val originSM: Double,
    val roadYawInEgoRad: Double,
    val verified: Boolean,
) {
    fun point(dM: Double, sM: Double): PointV1 {
        val dx = dM - originDM
        val dy = sM - originSM
        val c = cos(roadYawInEgoRad)
        val s = sin(roadYawInEgoRad)
        return PointV1(dx * c - dy * s, dx * s + dy * c)
    }
}

data class RiskFrameV1(
    val schemaVersion: String = "1.0",
    val timestampMs: Long,
    val riskObjects: List<RiskObjectV1>,
) {
    init {
        require(schemaVersion == "1.0" && timestampMs >= 0)
        require(riskObjects.map { it.vehicleId }.distinct().size == riskObjects.size)
    }

    /** Valid one-line JSON for Part05's parseRiskFrames() and risk_frames.jsonl. */
    fun toJsonLine(): String {
        val objects = riskObjects.joinToString(",") { o ->
            val reasons = o.reasonCodes.joinToString(",") { jsonString(it) }
            val zones = o.riskZones.joinToString(",") { z ->
                val polygon = z.polygon.joinToString(",") { point ->
                    "{\"x\":" + point.x + ",\"y\":" + point.y + "}"
                }
                "{\"dt_ms\":" + z.dtMs + ",\"polygon\":[" + polygon + "]}"
            }
            "{\"vehicle_id\":" + jsonString(o.vehicleId) +
                ",\"risk_level\":\"" + o.riskLevel.name +
                "\",\"reason_codes\":[" + reasons + "],\"ttc_s\":" +
                (o.ttcS?.toString() ?: "null") +
                ",\"risk_zones\":[" + zones + "]}"
        }
        return "{\"schema_version\":\"1.0\",\"timestamp_ms\":" + timestampMs +
            ",\"risk_objects\":[" + objects + "]}"
    }
}
private fun jsonString(text: String): String {
    val out = StringBuilder("\"")
    for (c in text) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c.code < 0x20) {
                out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else out.append(c)
        }
    }
    return out.append('"').toString()
}

/**
 * Converts Part02 intent evidence to an app-readable RiskFrame v1 object.
 * Unknown lane/provenance and lack of neighbor coverage fail closed.
 * CV projected shapes are hypotheses, NEVER calibrated collision probabilities.
 * TTC stays null until its source and road-frame semantics are independently
 * validated; raw signed longitudinal gaps do not imply collision TTC.
 */
object Engine02RiskFrameAdapter {
    fun convert(
        timestampMs: Long,
        vehicleIdsInVehicleFrame: Set<String>,
        items: List<Pair<Observation, IntentFrame>>,
        verifiedEgoLocalTransform: EgoLocalTransformV1? = null,
    ): RiskFrameV1 {
        require(timestampMs >= 0 && vehicleIdsInVehicleFrame.isNotEmpty())
        require(items.map { it.first.trackId }.distinct().size == items.size)
        val objects = items.map { (observation, intent) ->
            require(observation.trackId in vehicleIdsInVehicleFrame) {
                "vehicle_id must exist in matching VehicleFrame"
            }
            require(observation.trackId == intent.trackId &&
                observation.timestampMs == timestampMs &&
                intent.timestampMs == timestampMs) {
                "RiskFrame and VehicleFrame identity/timestamp mismatch"
            }
            val verified = observation.reliable && observation.laneGeometryReliable &&
                observation.laneReference.isNotBlank() &&
                listOf(observation.dM, observation.sM, observation.vdMps,
                    observation.vsMps, observation.yawRad, observation.lengthM,
                    observation.widthM).all { it.isFinite() } &&
                observation.lengthM in 0.5..30.0 && observation.widthM in 0.3..5.0
            val geometry = intent.riskGeometry
            val observedOverlap = geometry.status == "AVAILABLE" &&
                geometry.observedBodyOverlapIds.orEmpty().isNotEmpty()
            val cvProjected = geometry.status == "AVAILABLE" &&
                geometry.cvProjectedOverlap.orEmpty().isNotEmpty()
            val missingCoverage = "neighbor_coverage_unknown" in intent.uncertainty
            val uncertain = intent.motionIntention == "UNKNOWN" || !verified ||
                intent.features == null || geometry.status != "AVAILABLE" ||
                (missingCoverage && !observedOverlap && !cvProjected)

            val level = when {
                uncertain -> RiskLevelV1.UNKNOWN
                observedOverlap -> RiskLevelV1.WARNING
                cvProjected || intent.motionIntention.startsWith("CHANGE_") ||
                    intent.motionIntention == "YIELD" -> RiskLevelV1.WATCH
                else -> RiskLevelV1.NORMAL
            }
            val reasons = linkedSetOf("ENGINE02_RESEARCH_ONLY", "UNCALIBRATED_HEURISTIC")
            when (level) {
                RiskLevelV1.UNKNOWN -> reasons.add("INSUFFICIENT_VERIFIED_OBSERVATION")
                RiskLevelV1.WARNING -> reasons.add("OBSERVED_SAMPLED_BODY_OVERLAP")
                RiskLevelV1.WATCH -> {
                    if (cvProjected) reasons.add("CV_PROJECTED_BODY_OVERLAP_HYPOTHESIS")
                    if (intent.motionIntention.startsWith("CHANGE_"))
                        reasons.add("MOTION_CHANGE_HEURISTIC")
                    if (intent.motionIntention == "YIELD") reasons.add("YIELD_HEURISTIC")
                }
                RiskLevelV1.NORMAL -> reasons.add("NO_RISK_TRIGGER_IN_VERIFIED_SAMPLE")
            }
            if (observation.indicatorProvenance == Provenance.UNKNOWN)
                reasons.add("INDICATOR_UNKNOWN")

            // The first CV projected hit selects a hypothesis timestamp, not
            // the time of a measured crash. The polygon is the target vehicle's
            // predicted footprint in the SAME ego-local coordinate frame.
            val first = if (level == RiskLevelV1.WATCH && cvProjected) {
                geometry.cvProjectedOverlap.orEmpty().minByOrNull { it.firstSampleS }
            } else null
            val transform = verifiedEgoLocalTransform?.takeIf {
                it.verified && it.timestampMs == timestampMs &&
                    it.laneReference == observation.laneReference &&
                    listOf(it.originDM, it.originSM, it.roadYawInEgoRad).all { value ->
                        value.isFinite()
                    }
            }
            val zone = first?.takeIf {
                transform != null && it.firstSampleS.isFinite() &&
                    it.firstSampleS > 0.0 &&
                    it.firstSampleS <= (geometry.horizonS ?: 0.0)
            }?.let {
                val t = it.firstSampleS
                val w = observation.widthM / 2.0
                val l = observation.lengthM / 2.0
                val c = cos(observation.yawRad)
                val s = sin(observation.yawRad)
                val x = observation.dM + observation.vdMps * t
                val y = observation.sM + observation.vsMps * t
                val vertices = listOf(-w to -l, w to -l, w to l, -w to l).map { (u,v) ->
                    transform!!.point(x + u*c + v*s, y - u*s + v*c)
                }
                RiskZoneV1((t * 1000).roundToLong().coerceAtLeast(1L), vertices)
            }
            if (zone != null) reasons.add("RISK_ZONE_CV_FOOTPRINT_ONLY")
            if (first != null && zone == null)
                reasons.add("RISK_ZONE_OMITTED_UNVERIFIED_EGO_COORDINATES")
            RiskObjectV1(
                vehicleId = observation.trackId,
                riskLevel = level,
                reasonCodes = reasons.toList(),
                ttcS = null,
                riskZones = if (zone != null) listOf(zone) else emptyList(),
            )
        }
        return RiskFrameV1(timestampMs = timestampMs, riskObjects = objects)
    }
}

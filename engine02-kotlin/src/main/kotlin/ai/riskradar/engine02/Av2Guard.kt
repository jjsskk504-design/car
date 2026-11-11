package ai.riskradar.engine02

import kotlin.math.cos
import kotlin.math.sin

/**
 * Fail-closed input provenance gate for AV2 ObjectState-like objects.
 * The external map conversion and vehicle length must be separately verified.
 * AV2 track category is NOT a behavioral-event label.
 */
data class Av2Input(
    val recordingId: String,
    val scenarioId: String,
    val trackId: String,
    val timestep: Int,
    val asOfTimestep: Int,
    val timestampsNs: List<Long>,
    val observed: Boolean,
    val category: String,
    val centerXM: Double,
    val centerYM: Double,
    val headingRad: Double,
    val velocityXMps: Double,
    val velocityYMps: Double,
    val verifiedLengthM: Double? = null,
    val verifiedRoadSM: Double? = null,
    val verifiedRoadDM: Double? = null,
    val laneReference: String = "",
    val laneGeometryReliable: Boolean = false,
)

data class Av2GateResult(
    val status: String,
    val reason: String,
    val actorKey: Triple<String, String, String>? = null,
    val elapsedS: Double? = null,
    val frontCenterXM: Double? = null,
    val frontCenterYM: Double? = null,
    val roadSM: Double? = null,
    val roadDM: Double? = null,
    val indicator: Indicator = Indicator.UNKNOWN,
    val indicatorProvenance: Provenance = Provenance.UNKNOWN,
    val groundTruthManeuver: String? = null,
    val eligibleForInference: Boolean = false,
)

/** Verified road-frame values are caller's responsibility, never inferred from map XY. */
object Av2Guard {
    fun check(input: Av2Input): Av2GateResult {
        fun blocked(reason: String) = Av2GateResult("BLOCKED", reason)
        if (listOf(input.recordingId, input.scenarioId, input.trackId).any { it.isBlank() }) {
            return blocked("MISSING_COMPOSITE_ID")
        }
        if (input.timestep < 0 || input.asOfTimestep < 0 || input.timestep > input.asOfTimestep) {
            return blocked("FUTURE_OR_INVALID_TIMESTEP")
        }
        if (!input.observed) return blocked("UNOBSERVED_OR_INVALID_VISIBILITY")
        if (input.asOfTimestep >= input.timestampsNs.size) {
            return blocked("MISSING_TIMESTAMP")
        }
        // Do not inspect future timestamp suffix, or allow it to affect current output.
        val prefix = input.timestampsNs.take(input.asOfTimestep + 1)
        if (prefix.any { it < 0 }) return blocked("INVALID_TIMESTAMP")
        if (prefix.zipWithNext().any { (a, b) -> b <= a }) {
            return blocked("NONMONOTONIC_TIMESTAMP")
        }
        if (!listOf(input.centerXM, input.centerYM, input.headingRad,
                input.velocityXMps, input.velocityYMps).all { it.isFinite() }) {
            return blocked("INVALID_MAP_FRAME_KINEMATICS")
        }
        val length = input.verifiedLengthM
            ?: return blocked("UNVERIFIED_VEHICLE_LENGTH")
        if (!length.isFinite() || length !in 0.5..30.0) {
            return blocked("INVALID_VEHICLE_LENGTH")
        }
        val s = input.verifiedRoadSM
        val d = input.verifiedRoadDM
        if (!input.laneGeometryReliable || input.laneReference.isBlank()
            || s == null || d == null || !s.isFinite() || !d.isFinite()) {
            return blocked("UNVERIFIED_ROAD_GEOMETRY")
        }
        val half = length / 2.0
        val frontX = input.centerXM + half * cos(input.headingRad)
        val frontY = input.centerYM + half * sin(input.headingRad)
        val elapsed = (prefix[input.timestep].toDouble() - prefix.first()) / 1_000_000_000.0
        return Av2GateResult(
            status = "PASS",
            reason = "VERIFIED_INPUT_CONTRACT_ONLY",
            actorKey = Triple(input.recordingId, input.scenarioId, input.trackId),
            elapsedS = elapsed,
            frontCenterXM = frontX,
            frontCenterYM = frontY,
            roadSM = s,
            roadDM = d,
            indicator = Indicator.UNKNOWN,
            indicatorProvenance = Provenance.UNKNOWN,
            groundTruthManeuver = null,
            eligibleForInference = true,
        )
    }
}

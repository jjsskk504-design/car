package ai.riskradar.engine02

import kotlin.math.abs

/**
 * Engine02-owned mirror of the existing team VehicleFrame v1 values.
 * No dependency on, or edit to, PART01, PART05, or frozen RiskFrame v1.
 * The independent contract check maps actual team DTOs to this boundary.
 */
data class PrismVehicle(
    val id: String, val xM: Double, val yM: Double,
    val vxMps: Double, val vyMps: Double,
    val headingRad: Double, val lengthM: Double, val widthM: Double,
    val laneId: String? = null,
)

data class PrismVehicleFrame(
    val schemaVersion: String,
    val timestampMs: Long,
    val coordinateSystem: String?,
    val egoId: String,
    val vehicles: List<PrismVehicle>,
)

/**
 * Explicit externally verified road map projection for a *body-center*
 * (not NGSIM front-center) actor. Never infer road-relative d/s from ego
 * local x/y or from laneId alone.
 */
data class VerifiedRoadProjection(
    val trackId: String, val timestampMs: Long,
    val dBodyCenterM: Double, val sBodyCenterM: Double,
    val vdMps: Double, val vsMps: Double,
    val yawRelativeRoadRad: Double,
    val laneCenterDM: Double, val laneWidthM: Double,
    val laneReference: String,
    val identityEpoch: String,
    val origin: String = "BODY_CENTER",
    val geometryReliable: Boolean = true,
)

enum class SignalSourceScope { EGO_ONLY, DIRECT_ACTOR_OBSERVATION, UNKNOWN }
data class SignalEvidence(
    val actorTrackId: String,
    val timestampMs: Long,
    val indicator: Indicator,
    val observed: Boolean,
    val sourceScope: SignalSourceScope,
)

data class PrismAdapterResult(
    val status: String,
    val reason: String,
    val observation: Observation? = null,
)

/**
 * Causal gateway into IntentEngine. This adapter does NOT read or generate
 * any PredictionFrame future trajectories, calibrated risk levels or vehicle
 * actuation commands. Failed inputs never become fictitious observed data.
 */
object PrismInputAdapter {
    // Heading bound from the team VehicleFrame v1 JSON schema.
    private const val MAX_FRAME_HEADING_RAD = 3.14159266

    fun convert(
        frame: PrismVehicleFrame,
        targetId: String,
        verifiedRoadByTrack: Map<String, VerifiedRoadProjection>,
        evidence: SignalEvidence? = null,
        parameters: EngineParameters = EngineParameters(),
    ): PrismAdapterResult {
        fun blocked(reason: String) = PrismAdapterResult("BLOCKED", reason)
        if (frame.schemaVersion != "1.0" || frame.timestampMs < 0 ||
            frame.coordinateSystem != "ego_local_m" ||
            frame.egoId.isBlank() || targetId.isBlank()) {
            return blocked("UNVERIFIED_FRAME_CONTRACT")
        }
        val ids = frame.vehicles.map { it.id }
        if (ids.isEmpty() || ids.distinct().size != ids.size ||
            ids.any { it.isBlank() } || frame.egoId !in ids || targetId !in ids) {
            return blocked("INVALID_EGO_TARGET_OR_DUPLICATE_IDS")
        }
        for (v in frame.vehicles) {
            if (!listOf(v.xM, v.yM, v.vxMps, v.vyMps, v.headingRad,
                    v.lengthM, v.widthM).all { it.isFinite() } ||
                abs(v.headingRad) > MAX_FRAME_HEADING_RAD ||
                v.lengthM !in 0.5..30.0 || v.widthM !in 0.3..5.0) {
                return blocked("INVALID_VEHICLE_GEOMETRY")
            }
        }

        val projections = mutableMapOf<String, VerifiedRoadProjection>()
        for (id in ids) {
            val p = verifiedRoadByTrack[id] ?: return blocked("MISSING_ACTOR_ROAD_PROJECTION")
            if (p.trackId != id || p.timestampMs != frame.timestampMs) {
                return blocked("UNSYNCHRONIZED_ROAD_PROJECTION")
            }
            if (!p.geometryReliable || p.origin != "BODY_CENTER" ||
                p.laneReference.isBlank() || p.identityEpoch.isBlank() ||
                p.laneWidthM !in 2.0..6.0 ||
                abs(p.vdMps) > parameters.maxLateralMps ||
                abs(p.vsMps) > parameters.maxSpeedMps ||
                !listOf(p.dBodyCenterM, p.sBodyCenterM, p.vdMps, p.vsMps,
                    p.yawRelativeRoadRad, p.laneCenterDM, p.laneWidthM).all { it.isFinite() }) {
                return blocked("UNVERIFIED_BODY_CENTER_OR_ROAD_GEOMETRY")
            }
            projections[id] = p
        }
        val targetProjection = projections.getValue(targetId)
        if (projections.values.any { it.laneReference != targetProjection.laneReference }) {
            return blocked("MISMATCHED_ROAD_REFERENCE")
        }
        val vehicle = frame.vehicles.first { it.id == targetId }
        val observedSignal = evidence?.takeIf {
            it.actorTrackId == targetId && it.timestampMs == frame.timestampMs &&
                it.observed && it.indicator != Indicator.UNKNOWN &&
                (it.sourceScope == SignalSourceScope.DIRECT_ACTOR_OBSERVATION ||
                    (it.sourceScope == SignalSourceScope.EGO_ONLY && targetId == frame.egoId))
        }
        val neighbors = frame.vehicles.filter { it.id != targetId }.map { other ->
            val p = projections.getValue(other.id)
            Neighbor(
                trackId = other.id, timestampMs = frame.timestampMs,
                dM = p.dBodyCenterM, sM = p.sBodyCenterM,
                vdMps = p.vdMps, vsMps = p.vsMps,
                lengthM = other.lengthM, widthM = other.widthM,
                yawRad = p.yawRelativeRoadRad,
            )
        }
        return PrismAdapterResult(
            status = "PASS",
            reason = "VERIFIED_BODY_CENTER_ROAD_FRAME_ONLY",
            observation = Observation(
                trackId = targetId, timestampMs = frame.timestampMs,
                dM = targetProjection.dBodyCenterM,
                sM = targetProjection.sBodyCenterM,
                vdMps = targetProjection.vdMps,
                vsMps = targetProjection.vsMps,
                yawRad = targetProjection.yawRelativeRoadRad,
                laneCenterDM = targetProjection.laneCenterDM,
                laneWidthM = targetProjection.laneWidthM,
                lengthM = vehicle.lengthM,
                widthM = vehicle.widthM,
                indicator = observedSignal?.indicator ?: Indicator.UNKNOWN,
                indicatorProvenance = if (observedSignal == null) Provenance.UNKNOWN else Provenance.OBSERVED,
                identityEpoch = targetProjection.identityEpoch,
                laneReference = targetProjection.laneReference,
                laneGeometryReliable = true,
                neighbors = neighbors,
            ),
        )
    }
}

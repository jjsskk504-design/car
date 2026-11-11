package ai.riskradar.engine02

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Research-only causal behavior intent baseline ported from isolated Python V3.
 * Always uses observed past/current state, not future ground-truth trajectories.
 * The score is a heuristic, not a probability and not an in-vehicle safety signal.
 */
class IntentEngine(val parameters: EngineParameters = EngineParameters()) {
    private data class TrackState(
        val observation: Observation,
        val firstMs: Long,
        val previousDirection: Direction? = null,
    )

    private val states = mutableMapOf<String, TrackState>()
    private var watermarkMs = -1L

    fun reset() {
        states.clear()
        watermarkMs = -1L
    }

    private fun unknown(id: String, time: Long, reason: String) = IntentFrame(
        trackId = id,
        timestampMs = time,
        motionIntention = "UNKNOWN",
        uncertainty = listOf(reason),
        reasons = listOf(reason),
    )

    fun observe(observation: Observation?, nowMs: Long, missingTrackId: String = ""): IntentFrame {
        val id = observation?.trackId ?: missingTrackId
        if (nowMs < 0 || nowMs < watermarkMs) {
            return unknown(id, nowMs, "nonmonotonic_clock")
        }
        watermarkMs = nowMs
        val p = parameters
        val expired = states.filterValues { nowMs - it.observation.timestampMs > p.expiryMs }.keys.toList()
        for (key in expired) states.remove(key)
        val o = observation ?: return unknown(id, nowMs, "missing_observation")
        fun invalid(reason: String) = unknown(o.trackId, o.timestampMs, reason)

        if (o.trackId.isBlank() || o.identityEpoch.isBlank() || o.laneReference.isBlank()
            || o.timestampMs < 0
        ) return invalid("invalid_identity_or_timestamp")

        if (o.timestampMs > nowMs || nowMs - o.timestampMs > p.maxLatencyMs) {
            return invalid("future_or_stale_frame")
        }
        if (!o.reliable || !o.laneGeometryReliable) {
            states.remove(o.trackId)
            return invalid("unreliable_observation_or_lane")
        }
        if (!listOf(o.dM, o.sM, o.vdMps, o.vsMps, o.yawRad, o.laneCenterDM,
                o.laneWidthM, o.lengthM, o.widthM).all { it.isFinite() }
            || o.lengthM !in 0.5..30.0 || o.widthM !in 0.3..5.0
            || o.laneWidthM !in 2.0..6.0
            || abs(o.vsMps) > p.maxSpeedMps || abs(o.vdMps) > p.maxLateralMps
        ) {
            states.remove(o.trackId)
            return invalid("invalid_kinematics_or_dimensions")
        }

        val previous = states[o.trackId]
        if (previous != null && o.timestampMs <= previous.observation.timestampMs) {
            return invalid("duplicate_or_out_of_order")
        }
        if (previous != null
            && (o.identityEpoch != previous.observation.identityEpoch
                || o.laneReference != previous.observation.laneReference
                || o.laneCenterDM != previous.observation.laneCenterDM
                || o.laneWidthM != previous.observation.laneWidthM)
        ) {
            states[o.trackId] = TrackState(o, o.timestampMs)
            return invalid("identity_or_lane_reference_reset")
        }

        var lateralAccel: Double? = null
        var longitudinalAccel: Double? = null
        val state: TrackState
        if (previous != null) {
            val old = previous.observation
            val dt = (o.timestampMs - old.timestampMs) / 1000.0
            if (dt * 1000 < p.minDtMs) return invalid("sampling_interval_too_short")
            val lateralResidual = o.dM - old.dM - (old.vdMps + o.vdMps) * dt / 2
            val longitudinalResidual = o.sM - old.sM - (old.vsMps + o.vsMps) * dt / 2
            if (hypot(lateralResidual, longitudinalResidual) > p.maxResidualM) {
                states[o.trackId] = TrackState(o, o.timestampMs)
                return invalid("track_discontinuity")
            }
            lateralAccel = (o.vdMps - old.vdMps) / dt
            longitudinalAccel = (o.vsMps - old.vsMps) / dt
            if (hypot(lateralAccel, longitudinalAccel) > p.maxAccelerationMps2) {
                states.remove(o.trackId)
                return invalid("implausible_acceleration")
            }
            state = previous
        } else {
            state = TrackState(o, o.timestampMs)
        }

        val ageMs = o.timestampMs - state.firstMs
        val uncertainty = mutableListOf<String>()
        val provenance = if (o.indicatorProvenance == Provenance.OBSERVED
            || o.indicatorProvenance == Provenance.SYNTHETIC) o.indicatorProvenance else Provenance.UNKNOWN
        val indicator = if (provenance == Provenance.UNKNOWN || o.indicator == Indicator.UNKNOWN)
            Indicator.UNKNOWN else o.indicator
        val effectiveProvenance = if (indicator == Indicator.UNKNOWN) Provenance.UNKNOWN else provenance
        if (indicator == Indicator.UNKNOWN) uncertainty.add("indicator_unobserved")
        if (effectiveProvenance == Provenance.SYNTHETIC) {
            uncertainty.add("synthetic_indicator_research_only")
        }

        val neighborIds = o.neighbors.groupingBy { it.trackId }.eachCount()
        val neighbors = o.neighbors.filter {
            neighborIds[it.trackId] == 1 && RoadGeometry.valid(it, o, p)
        }
        if (neighbors.size != o.neighbors.size) {
            uncertainty.add("invalid_or_unsynchronized_neighbors_omitted")
        }
        if (neighbors.isEmpty()) uncertainty.add("neighbor_coverage_unknown")

        val offset = o.dM - o.laneCenterDM
        val extent = abs(cos(o.yawRad)) * o.widthM / 2 + abs(sin(o.yawRad)) * o.lengthM / 2
        val distances = mapOf(
            Direction.LEFT to pDistance(o.laneWidthM / 2 + offset - extent),
            Direction.RIGHT to pDistance(o.laneWidthM / 2 - offset - extent),
        )
        val ttl = mutableMapOf<Direction, Double?>()
        val scores = mutableMapOf<Direction, Double>()
        for (direction in Direction.entries) {
            val sign = if (direction == Direction.LEFT) -1.0 else 1.0
            val speed = sign * o.vdMps
            ttl[direction] = if (speed > 0.05) max(0.0, distances.getValue(direction)) / speed else null
            val motion = ramp(speed, 0.08, 0.65)
            val near = 1 - ramp(max(0.0, distances.getValue(direction)), 0.1, 1.5)
            val yaw = ramp(sign * o.yawRad, 0.015, 0.12)
            val indicatorBonus = if (indicator.name == direction.name) 0.34 else 0.0
            scores[direction] = (0.72 * motion + 0.16 * near * motion
                + 0.12 * yaw * motion + indicatorBonus).coerceAtMost(1.0)
        }
        val chosen = scores.maxBy { it.value }.key
        val score = scores.getValue(chosen)
        val prior = state.previousDirection
        val returning = (prior == Direction.LEFT && o.vdMps > 0.08)
            || (prior == Direction.RIGHT && o.vdMps < -0.08)
        val reasons = mutableListOf<String>()
        var label = "KEEP"
        val geometry = RoadGeometry.calculate(o, neighbors, p)

        if (ageMs < p.warmupMs) {
            label = "UNKNOWN"
            uncertainty.add("track_warmup")
        } else if (returning && abs(offset) > 0.12) {
            label = "ABORT_" + prior!!.name
            reasons.add("motion_reversed_after_preparation")
        } else if (score >= p.changeScore && abs(o.vdMps) > 0.08) {
            label = "CHANGE_" + chosen.name
            reasons.add("lateral_motion_toward_boundary")
        } else if (longitudinalAccel != null && longitudinalAccel <= p.brakeMps2) {
            val interaction = geometry.neighborGaps.any { gap ->
                gap.side != "SAME" && gap.longitudinalTtcS != null && gap.longitudinalTtcS < 4.0
            }
            label = if (interaction && (prior != null || score >= p.prepareScore)) "YIELD" else "BRAKE"
            reasons.add(if (label == "YIELD") "observed_deceleration_with_interaction"
                else "observed_deceleration")
        } else if (score >= p.prepareScore) {
            label = "PREPARE_" + chosen.name
            reasons.add("weak_motion_or_optional_indicator")
        }

        val nextPrior = when {
            label.startsWith("PREPARE_") || label.startsWith("CHANGE_") -> chosen
            label.startsWith("ABORT_") || (abs(offset) < 0.12 && abs(o.vdMps) < 0.08) -> null
            else -> prior
        }
        states[o.trackId] = TrackState(o, state.firstMs, nextPrior)
        return IntentFrame(
            trackId = o.trackId,
            timestampMs = o.timestampMs,
            motionIntention = label,
            scores = scores,
            features = IntentFeatures(
                lateralOffsetM = offset,
                lateralVelocityMps = o.vdMps,
                lateralAccelerationMps2 = lateralAccel,
                longitudinalAccelerationMps2 = longitudinalAccel,
                yawRelativeRoadRad = o.yawRad,
                timeToLineS = ttl,
                bodyDistanceToLineM = distances,
                trackAgeS = ageMs / 1000.0,
                indicator = indicator,
                indicatorProvenance = effectiveProvenance,
                indicatorObserved = effectiveProvenance == Provenance.OBSERVED,
                frontEvent = o.frontEvent,
            ),
            riskGeometry = geometry,
            uncertainty = uncertainty,
            reasons = reasons,
        )
    }

    private fun pDistance(x: Double) = x

    private fun ramp(x: Double, low: Double, high: Double): Double {
        val u = ((x - low) / (high - low)).coerceIn(0.0, 1.0)
        return u * u * (3 - 2 * u)
    }
}

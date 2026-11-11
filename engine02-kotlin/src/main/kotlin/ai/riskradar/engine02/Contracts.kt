package ai.riskradar.engine02

enum class Indicator { LEFT, RIGHT, OFF, UNKNOWN }
enum class Provenance { OBSERVED, SYNTHETIC, UNKNOWN, INFERRED }
enum class Direction { LEFT, RIGHT }
enum class FrontEvent { BRAKING, NONE, UNKNOWN }

data class EngineParameters(
    val expiryMs: Long = 1000,
    val maxLatencyMs: Long = 250,
    val warmupMs: Long = 100,
    val minDtMs: Long = 20,
    val maxSpeedMps: Double = 80.0,
    val maxLateralMps: Double = 8.0,
    val maxAccelerationMps2: Double = 15.0,
    val maxResidualM: Double = 1.5,
    val horizonS: Double = 2.0,
    val projectionStepS: Double = 0.25,
    val prepareScore: Double = 0.32,
    val changeScore: Double = 0.60,
    val brakeMps2: Double = -1.5,
) {
    init {
        require(minDtMs > 0 && minDtMs <= warmupMs && warmupMs < expiryMs)
        require(maxLatencyMs >= 0)
        require(listOf(maxSpeedMps, maxLateralMps, maxAccelerationMps2,
            maxResidualM, horizonS, projectionStepS, prepareScore, changeScore,
            brakeMps2).all { it.isFinite() })
        require(maxSpeedMps > 0 && maxLateralMps > 0 && maxAccelerationMps2 > 0)
        require(maxResidualM > 0 && horizonS > 0 && projectionStepS > 0 && projectionStepS <= horizonS)
        require(prepareScore >= 0.0 && prepareScore < changeScore && changeScore <= 1.0)
    }
}

data class Neighbor(
    val trackId: String, val timestampMs: Long,
    val dM: Double, val sM: Double,
    val vdMps: Double, val vsMps: Double,
    val lengthM: Double = 4.6, val widthM: Double = 1.9,
    val yawRad: Double = 0.0,
)

data class Observation(
    val trackId: String, val timestampMs: Long,
    val dM: Double, val sM: Double,
    val vdMps: Double, val vsMps: Double,
    val yawRad: Double = 0.0,
    val laneCenterDM: Double = 0.0,
    val laneWidthM: Double = 3.6,
    val lengthM: Double = 4.6, val widthM: Double = 1.9,
    val indicator: Indicator = Indicator.UNKNOWN,
    val indicatorProvenance: Provenance = Provenance.UNKNOWN,
    val frontEvent: FrontEvent = FrontEvent.UNKNOWN,
    val reliable: Boolean = true,
    val laneGeometryReliable: Boolean = true,
    val identityEpoch: String = "0",
    val laneReference: String = "fixed-road-lane",
    val neighbors: List<Neighbor> = emptyList(),
)

data class NeighborGap(
    val trackId: String, val side: String, val position: String,
    val bumperGapM: Double, val closingMps: Double,
    val longitudinalTtcS: Double?,
)

data class ProjectedOverlap(val trackId: String, val firstSampleS: Double)

data class RiskGeometry(
    val status: String,
    val observedBodyOverlapIds: List<String>?,
    val cvProjectedOverlap: List<ProjectedOverlap>?,
    val neighborGaps: List<NeighborGap> = emptyList(),
    val horizonS: Double? = null,
    val stepS: Double? = null,
    val semantics: String = "local tangent OBB geometry; CV hypothesis, no collision probability",
)

data class IntentFeatures(
    val lateralOffsetM: Double,
    val lateralVelocityMps: Double,
    val lateralAccelerationMps2: Double?,
    val longitudinalAccelerationMps2: Double?,
    val yawRelativeRoadRad: Double,
    val timeToLineS: Map<Direction, Double?>,
    val bodyDistanceToLineM: Map<Direction, Double>,
    val trackAgeS: Double,
    val indicator: Indicator,
    val indicatorProvenance: Provenance,
    val indicatorObserved: Boolean,
    val frontEvent: FrontEvent,
)

data class IntentFrame(
    val trackId: String,
    val timestampMs: Long,
    val motionIntention: String,
    val scores: Map<Direction, Double> = emptyMap(),
    val features: IntentFeatures? = null,
    val riskGeometry: RiskGeometry = RiskGeometry(
        status = "UNAVAILABLE",
        observedBodyOverlapIds = null,
        cvProjectedOverlap = null,
    ),
    val uncertainty: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
    val schemaVersion: String = "BehaviorIntentFrame v0",
    val researchOnly: Boolean = true,
    val scoreSemantics: String = "heuristic score not calibrated",
    val parameterStatus: String = "EXPERIMENTAL",
)

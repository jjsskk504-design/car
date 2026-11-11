package ai.riskradar.engine02

import kotlin.math.abs

/** Metadata/row-only guard: never redistributes raw CARLA or NGSIM recordings. */
object CarlaSignalGuard {
    /** Ego indicator cannot be generalized to neighboring actors. */
    fun indicatorFor(
        egoTrackId: String, targetTrackId: String,
        egoIndicator: Indicator, isEgoObserved: Boolean,
    ): Pair<Indicator, Provenance> =
        if (egoTrackId == targetTrackId && isEgoObserved && egoIndicator != Indicator.UNKNOWN) {
            egoIndicator to Provenance.OBSERVED
        } else {
            Indicator.UNKNOWN to Provenance.UNKNOWN
        }

    /** Return filenames of exact duplicate SHA-256 digest groups from an authorized manifest. */
    fun duplicateHashGroups(shas: Map<String, String>): List<List<String>> {
        require(shas.keys.none { it.isBlank() })
        for (sha in shas.values) {
            require(sha.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256 manifest" }
        }
        return shas.entries.groupBy { it.value.lowercase() }
            .values.map { g -> g.map { it.key }.sorted() }
            .filter { it.size > 1 }.sortedBy { it.first() }
    }
}

data class NgSimRow(
    val site: String,
    val recording: String,
    val vehicleId: String,
    val timestampMs: Long,
    val frameId: Long,
    val localXFeet: Double,
    val localYFeet: Double,
    val lengthFeet: Double,
    val widthFeet: Double,
    val speedFeetPerS: Double,
    val accelerationFeetPerS2: Double,
)

data class NgSimGateResult(
    val status: String,
    val reason: String,
    val compositeTrackKey: Triple<String, String, String>? = null,
    val frontCenterXM: Double? = null,
    val frontCenterYM: Double? = null,
    val speedMps: Double? = null,
    val accelerationMps2: Double? = null,
    val indicator: Indicator = Indicator.UNKNOWN,
    val indicatorProvenance: Provenance = Provenance.UNKNOWN,
    val coordinateOrigin: String = "FRONT_CENTER",
)

/**
 * NGSIM input is feet/ft/s/ft/s2. This guard checks metadata and conversions.
 * Road tangent, center-of-body, heading and maneuver truth are NOT supplied.
 * Do not convert front-center to body-center without trusted heading.
 */
class NgSimGate {
    private data class UniqueAtTime(
        val site: String, val recording: String,
        val vehicle: String, val time: Long,
    )

    private val observed = mutableSetOf<UniqueAtTime>()
    private val lastTime = mutableMapOf<Triple<String, String, String>, Long>()

    fun check(row: NgSimRow): NgSimGateResult {
        fun block(reason: String) = NgSimGateResult("QUARANTINE", reason)
        if (listOf(row.site, row.recording, row.vehicleId).any { it.isBlank() }
            || row.timestampMs < 0 || row.frameId < 0) {
            return block("INVALID_SITE_RECORDING_VEHICLE_TIME")
        }
        val metrics = listOf(row.localXFeet, row.localYFeet,
            row.lengthFeet, row.widthFeet, row.speedFeetPerS, row.accelerationFeetPerS2)
        if (metrics.any { !it.isFinite() }) return block("NONFINITE_ROW")
        val converted = metrics.map { it * FT_TO_M }
        if (converted[2] !in 0.5..30.0 || converted[3] !in 0.3..5.0
            || converted[4] !in 0.0..70.0 || abs(converted[5]) > 15.0) {
            return block("INVALID_DIMENSIONS_OR_KINEMATICS")
        }
        val key = Triple(row.site, row.recording, row.vehicleId)
        val idTime = UniqueAtTime(row.site, row.recording, row.vehicleId, row.timestampMs)
        if (!observed.add(idTime)) return block("DUPLICATE_VEHICLE_TIME")
        val old = lastTime[key]
        if (old != null && row.timestampMs <= old) {
            return block("NONMONOTONIC_TRACK_TIME")
        }
        lastTime[key] = row.timestampMs

        // Historic Peachtree 60s raw tracks had repeated keys; held-out
        // evaluation cannot use the unsegmented site.
        if (row.site.contains("peachtree", ignoreCase = true)) {
            return block("PEACHTREE_SOURCE_QUARANTINE")
        }
        return NgSimGateResult(
            status = "PASS",
            reason = "ROW_CONTRACT_ONLY_NOT_EVENT_TRUTH",
            compositeTrackKey = key,
            frontCenterXM = converted[0],
            frontCenterYM = converted[1],
            speedMps = converted[4],
            accelerationMps2 = converted[5],
        )
    }

    companion object {
        const val FT_TO_M: Double = 0.3048
    }
}

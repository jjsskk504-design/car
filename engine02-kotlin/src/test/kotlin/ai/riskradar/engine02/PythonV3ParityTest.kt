package ai.riskradar.engine02

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class PythonV3ParityTest(private val scenario: String, private val samples: List<String>) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): List<Array<Any>> =
            checkNotNull(PythonV3ParityTest::class.java.getResourceAsStream("/behavior-v3-parity.tsv"))
                .bufferedReader().use { it.readLines() }
                .groupBy { it.substringBefore('\t') }
                .map { (scenario, samples) -> arrayOf(scenario, samples) }
    }

    @Test fun matchesPythonV3() {
        val engine = IntentEngine()
        for ((index, sample) in samples.withIndex()) {
            val (_, time, input, expected) = sample.split('\t')
            val frame = engine.observe(decodeObservation(input), time.toLong(), "missing")
            val actualFields = flatten(snapshot(frame)).toMap()
            val expectedFields = expected.split(";;").associate { it.substringBefore('=') to it.substringAfter('=') }
            val context = "$scenario sample $index at $time"
            assertEquals(expectedFields.keys, actualFields.keys, context)
            for ((key, value) in expectedFields) {
                val actual = actualFields[key]
                when (actual) {
                    is Double -> assertEquals(value.toDouble(), actual, 1e-10, "$context $key")
                    else -> assertEquals(value, actual?.toString() ?: "~", "$context $key")
                }
            }
        }
    }

    private fun decodeObservation(input: String): Observation? {
        if (input == "~") return null
        val actors = input.split('|').map { it.split(',') }
        val o = actors.first()
        return Observation(
            trackId = o[0], timestampMs = o[1].toLong(),
            dM = o[2].toDouble(), sM = o[3].toDouble(),
            vdMps = o[4].toDouble(), vsMps = o[5].toDouble(),
            yawRad = o[6].toDouble(), laneCenterDM = o[7].toDouble(),
            laneWidthM = o[8].toDouble(), lengthM = o[9].toDouble(), widthM = o[10].toDouble(),
            indicator = Indicator.valueOf(o[11]), indicatorProvenance = Provenance.valueOf(o[12]),
            frontEvent = FrontEvent.valueOf(o[13]), reliable = o[14].toBooleanStrict(),
            laneGeometryReliable = o[15].toBooleanStrict(), identityEpoch = o[16], laneReference = o[17],
            neighbors = actors.drop(1).map { n ->
                Neighbor(n[0], n[1].toLong(), n[2].toDouble(), n[3].toDouble(),
                    n[4].toDouble(), n[5].toDouble(), n[6].toDouble(), n[7].toDouble(), n[8].toDouble())
            },
        )
    }

    private fun flatten(value: Any?, prefix: String = ""): List<Pair<String, Any?>> = when (value) {
        is Map<*, *> -> value.flatMap { (key, item) -> flatten(item, if (prefix.isEmpty()) "$key" else "$prefix.$key") }
        is List<*> -> listOf("$prefix.size" to value.size) +
            value.flatMapIndexed { index, item -> flatten(item, "$prefix.$index") }
        else -> listOf(prefix to value)
    }

    private fun snapshot(frame: IntentFrame) = mapOf(
        "track_id" to frame.trackId,
        "timestamp_ms" to frame.timestampMs,
        "motion_intention" to frame.motionIntention,
        "scores" to frame.scores,
        "features" to frame.features?.let(::features).orEmpty(),
        "risk_geometry" to geometry(frame.riskGeometry),
        "uncertainty" to frame.uncertainty,
        "reasons" to frame.reasons,
        "schema_version" to frame.schemaVersion,
        "research_only" to frame.researchOnly,
        "score_semantics" to frame.scoreSemantics,
        "parameter_status" to frame.parameterStatus,
    )

    private fun features(f: IntentFeatures) = mapOf(
        "lateral_offset_m" to f.lateralOffsetM,
        "lateral_velocity_mps" to f.lateralVelocityMps,
        "lateral_acceleration_mps2" to f.lateralAccelerationMps2,
        "longitudinal_acceleration_mps2" to f.longitudinalAccelerationMps2,
        "yaw_relative_road_rad" to f.yawRelativeRoadRad,
        "time_to_line_s" to f.timeToLineS,
        "body_distance_to_line_m" to f.bodyDistanceToLineM,
        "track_age_s" to f.trackAgeS,
        "indicator" to f.indicator,
        "indicator_provenance" to f.indicatorProvenance,
        "indicator_observed" to f.indicatorObserved,
        "front_event" to f.frontEvent,
    )

    private fun geometry(g: RiskGeometry): Map<String, Any?> = buildMap {
        put("status", g.status)
        put("observed_body_overlap_ids", g.observedBodyOverlapIds)
        put("cv_projected_overlap", g.cvProjectedOverlap?.map {
            mapOf("track_id" to it.trackId, "first_sample_s" to it.firstSampleS)
        })
        if (g.status == "AVAILABLE") {
            put("neighbor_gaps", g.neighborGaps.map { gap ->
                mapOf(
                    "track_id" to gap.trackId, "side" to gap.side, "position" to gap.position,
                    "bumper_gap_m" to gap.bumperGapM, "closing_mps" to gap.closingMps,
                    "longitudinal_ttc_s" to gap.longitudinalTtcS,
                )
            })
            put("horizon_s", g.horizonS)
            put("step_s", g.stepS)
            put("semantics", g.semantics)
        }
    }
}

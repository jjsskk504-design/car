package ai.riskradar.engine02

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.math.abs

class IntentEngineTest {
    private fun obs(t: Long = 0L) = Observation(
        trackId = "target", timestampMs = t,
        dM = 0.0, sM = t * 0.02, vdMps = 0.0, vsMps = 20.0,
    )
    private fun warmed(current: Observation = obs(100L), first: Observation = obs(0L)): IntentFrame {
        val e = IntentEngine()
        e.observe(first, nowMs = 0)
        return e.observe(current, nowMs = 100)
    }

    @Test fun stableLaneIsKeep() {
        val r = warmed()
        assertEquals("KEEP", r.motionIntention)
        assertEquals(0.0, r.features?.lateralAccelerationMps2)
        assertTrue(r.researchOnly)
    }

    @Test fun signalAloneNeverImpliesCompletedLaneChange() {
        for (indicator in listOf(Indicator.LEFT, Indicator.RIGHT)) {
            val r = warmed(current = obs(100).copy(indicator = indicator,
                indicatorProvenance = Provenance.OBSERVED),
                first = obs().copy(indicator = indicator,
                    indicatorProvenance = Provenance.OBSERVED))
            assertEquals("PREPARE_" + indicator.name, r.motionIntention)
            assertTrue(r.riskGeometry.observedBodyOverlapIds!!.isEmpty())
        }
    }

    @Test fun noSignalRapidCutInBothDirections() {
        for (velocity in listOf(-0.8, 0.8)) {
            val e = IntentEngine()
            e.observe(obs().copy(vdMps = velocity), nowMs = 0)
            val r = e.observe(obs(100).copy(
                dM = velocity * 0.1, vdMps = velocity
            ), nowMs = 100)
            assertEquals(if (velocity < 0) "CHANGE_LEFT" else "CHANGE_RIGHT", r.motionIntention)
            assertEquals(Indicator.UNKNOWN, r.features?.indicator)
            assertEquals("heuristic score not calibrated", r.scoreSemantics)
        }
    }

    @Test fun noNeighbourSignalCanBeCopiedFromEgoOrInferred() {
        for (provenance in listOf(Provenance.INFERRED, Provenance.UNKNOWN)) {
            val a = obs().copy(indicator = Indicator.LEFT, indicatorProvenance = provenance)
            val b = obs(100).copy(indicator = Indicator.LEFT, indicatorProvenance = provenance)
            val r = warmed(b, a)
            assertEquals("KEEP", r.motionIntention)
            assertEquals(Indicator.UNKNOWN, r.features?.indicator)
            assertTrue("indicator_unobserved" in r.uncertainty)
        }
    }

    @Test fun observedOffIsDistinctFromMissing() {
        val r = warmed(obs(100).copy(indicator = Indicator.OFF,
                indicatorProvenance = Provenance.OBSERVED),
            obs().copy(indicator = Indicator.OFF,
                indicatorProvenance = Provenance.OBSERVED))
        assertEquals(Indicator.OFF, r.features?.indicator)
        assertTrue(r.features?.indicatorObserved == true)
    }

    @Test fun syntheticProvenanceRetainedAsResearchOnly() {
        val a = obs().copy(indicator = Indicator.LEFT, indicatorProvenance = Provenance.SYNTHETIC)
        val b = obs(100).copy(indicator = Indicator.LEFT, indicatorProvenance = Provenance.SYNTHETIC)
        val r = warmed(b, a)
        assertEquals("PREPARE_LEFT", r.motionIntention)
        assertFalse(r.features!!.indicatorObserved)
        assertTrue("synthetic_indicator_research_only" in r.uncertainty)
    }

    @Test fun abortFollowingPreparationIsNotCompletedChange() {
        val e = IntentEngine()
        e.observe(obs().copy(dM = -0.2, vdMps = -0.4), nowMs = 0)
        val r1 = e.observe(obs(100).copy(dM = -0.24, vdMps = -0.4), nowMs = 100)
        val r2 = e.observe(obs(200).copy(dM = -0.24, vdMps = 0.4), nowMs = 200)
        assertEquals("PREPARE_LEFT", r1.motionIntention)
        assertEquals("ABORT_LEFT", r2.motionIntention)
    }

    @Test fun futureOrExpiredPacketFailsConservatively() {
        val e = IntentEngine()
        assertEquals("UNKNOWN", e.observe(obs(100), nowMs = 0).motionIntention)
        e.observe(obs(), nowMs = 0)
        e.observe(obs(100), nowMs = 100)
        assertEquals("future_or_stale_frame",
            e.observe(obs(150), nowMs = 500).reasons.first())
        val fresh = e.observe(obs(500), nowMs = 500)
        assertEquals("KEEP", fresh.motionIntention)
        assertEquals(0.5, fresh.features?.trackAgeS)
    }

    @Test fun duplicateAndOutOfOrderAndShortDtDoNotAdvanceHistory() {
        val e = IntentEngine()
        e.observe(obs(), nowMs = 0)
        assertEquals("sampling_interval_too_short", e.observe(obs(1), nowMs = 1).reasons.first())
        assertEquals("KEEP", e.observe(obs(100), nowMs = 100).motionIntention)
        assertEquals("duplicate_or_out_of_order", e.observe(obs(100), nowMs = 100).reasons.first())
        assertEquals("duplicate_or_out_of_order", e.observe(obs(50), nowMs = 100).reasons.first())
    }

    @Test fun packetReceivedOutOfOrderClockUnknown() {
        val e = IntentEngine()
        e.observe(obs(100), nowMs = 100)
        val r = e.observe(obs(50), nowMs = 50)
        assertEquals("nonmonotonic_clock", r.reasons.first())
    }

    @Test fun identityEpochOrLaneReferenceChangesReset() {
        val e = IntentEngine()
        e.observe(obs(), nowMs = 0)
        e.observe(obs(100), nowMs = 100)
        assertEquals("identity_or_lane_reference_reset",
            e.observe(obs(200).copy(identityEpoch = "next"), nowMs = 200).reasons.first())
        assertEquals("KEEP",
            e.observe(obs(300).copy(identityEpoch = "next"), nowMs = 300).motionIntention)
        assertEquals("identity_or_lane_reference_reset",
            e.observe(obs(400).copy(identityEpoch = "next",
                laneCenterDM = 3.6), nowMs = 400).reasons.first())
    }

    @Test fun implausibleTrackAndDimensionsBlock() {
        val e = IntentEngine()
        e.observe(obs(), nowMs = 0)
        e.observe(obs(100), nowMs = 100)
        assertEquals("track_discontinuity",
            e.observe(obs(200).copy(dM = 15.0), nowMs = 200).reasons.first())
        assertEquals("invalid_kinematics_or_dimensions",
            e.observe(obs(300).copy(widthM = -1.0), nowMs = 300).reasons.first())
    }

    @Test fun unreliableLaneAndMissingObservationBlock() {
        val e = IntentEngine()
        assertEquals("missing_observation", e.observe(null, nowMs = 0).reasons.first())
        assertEquals("unreliable_observation_or_lane",
            e.observe(obs(100).copy(laneGeometryReliable = false), nowMs = 100).reasons.first())
    }

    @Test fun neighborCoverageValidationAndHypothesisDistinction() {
        val e = IntentEngine()
        e.observe(obs(), nowMs = 0)
        val bad = Neighbor("stale", 0, 0.0, 0.0, 0.0, 20.0)
        val rear = Neighbor("rear", 100, 0.0, -18.0, 0.0, 35.0)
        val r = e.observe(obs(100).copy(neighbors = listOf(bad, rear)), nowMs = 100)
        assertEquals("KEEP", r.motionIntention)
        assertEquals(emptyList<String>(), r.riskGeometry.observedBodyOverlapIds)
        assertTrue(r.riskGeometry.cvProjectedOverlap!!.any { it.trackId == "rear" })
        assertTrue("invalid_or_unsynchronized_neighbors_omitted" in r.uncertainty)
        assertTrue(r.riskGeometry.semantics.contains("no collision probability"))
    }

    @Test fun duplicateNeighborIdentityIsNotTrusted() {
        val n = Neighbor("repeat", 100, 0.0, 0.0, 0.0, 20.0)
        val r = warmed(obs(100).copy(neighbors = listOf(n, n)))
        assertTrue(r.riskGeometry.neighborGaps.isEmpty())
    }

    @Test fun brakingOnlyFromObservedDeceleration() {
        val e = IntentEngine()
        e.observe(obs(), nowMs = 0)
        val keep = e.observe(obs(100).copy(frontEvent = FrontEvent.BRAKING), nowMs = 100)
        assertEquals("KEEP", keep.motionIntention)
        val braking = e.observe(obs(200).copy(vsMps = 19.7), nowMs = 200)
        assertEquals("BRAKE", braking.motionIntention)
        assertTrue(abs(braking.features!!.longitudinalAccelerationMps2!! + 3.0) < 1e-9)
    }

    @Test fun adjacentInteractionWithPriorPreparationMayYield() {
        val e = IntentEngine()
        val a = obs().copy(indicator = Indicator.LEFT, indicatorProvenance = Provenance.OBSERVED)
        val b = obs(100).copy(indicator = Indicator.LEFT, indicatorProvenance = Provenance.OBSERVED)
        e.observe(a, nowMs = 0)
        assertEquals("PREPARE_LEFT", e.observe(b, nowMs = 100).motionIntention)
        val n = Neighbor("priority", 200, -3.6, 12.0, 0.0, 10.0)
        val r = e.observe(obs(200).copy(vsMps = 19.7,
            indicator = Indicator.LEFT, indicatorProvenance = Provenance.OBSERVED,
            neighbors = listOf(n)), nowMs = 200)
        assertEquals("YIELD", r.motionIntention)
    }

    @Test fun fastRearApproachIsNotCurrentBodyOverlap() {
        val e = IntentEngine()
        e.observe(obs(), nowMs = 0)
        val n = Neighbor("rear", 100, 3.6, -18.0, 0.0, 38.0)
        val r = e.observe(obs(100).copy(neighbors = listOf(n)), nowMs = 100)
        assertEquals(emptyList<String>(), r.riskGeometry.observedBodyOverlapIds)
        val gap = r.riskGeometry.neighborGaps.single()
        assertEquals("REAR", gap.position)
        assertEquals("RIGHT", gap.side)
        assertTrue(abs(gap.longitudinalTtcS!! - (20.0 - 4.6) / 18) < 1e-9)
    }

    @Test fun directionProcessingIsOrderInvariantForTwoTracks() {
        fun run(order: List<String>): Map<String, List<String>> {
            val e = IntentEngine()
            val data = mutableMapOf<String, MutableList<String>>()
            for (t in listOf(0L, 100L)) {
                for (id in order) {
                    val o = obs(t).copy(trackId = id)
                    data.getOrPut(id) { mutableListOf() }.add(e.observe(o, nowMs = t).motionIntention)
                }
            }
            return data
        }
        assertEquals(run(listOf("A", "B")), run(listOf("B", "A")))
    }

    @Test fun invalidExperimentalParametersAreRejected() {
        assertFailsWith<IllegalArgumentException> { EngineParameters(projectionStepS = 0.0) }
        assertFailsWith<IllegalArgumentException> { EngineParameters(horizonS = Double.POSITIVE_INFINITY) }
    }

    @Test fun noProbabilityFieldIsExposed() {
        val r = warmed()
        assertEquals("BehaviorIntentFrame v0", r.schemaVersion)
        assertTrue(r.scoreSemantics.contains("not calibrated"))
        assertTrue(r.researchOnly)
        assertNull(r.features?.longitudinalAccelerationMps2?.takeIf { it > 0.0 })
    }
}

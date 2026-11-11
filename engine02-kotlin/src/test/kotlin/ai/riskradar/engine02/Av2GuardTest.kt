package ai.riskradar.engine02

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Av2GuardTest {
    private fun sample() = Av2Input(
        recordingId = "record-A",
        scenarioId = "scenario-A",
        trackId = "vehicle-7",
        timestep = 1,
        asOfTimestep = 1,
        timestampsNs = listOf(0, 100_000_000, 200_000_000),
        observed = true,
        category = "FOCAL_TRACK",
        centerXM = 10.0,
        centerYM = 5.0,
        headingRad = 0.0,
        velocityXMps = 10.0,
        velocityYMps = 0.0,
        verifiedLengthM = 4.0,
        verifiedRoadSM = 10.0,
        verifiedRoadDM = 0.0,
        laneReference = "verified-road",
        laneGeometryReliable = true,
    )

    @Test fun compositeIdentityNotJustTrackId() {
        val a = Av2Guard.check(sample())
        val b = Av2Guard.check(sample().copy(scenarioId = "scenario-B"))
        val c = Av2Guard.check(sample().copy(recordingId = "record-B"))
        assertEquals("PASS", a.status)
        assertTrue(a.actorKey != b.actorKey && a.actorKey != c.actorKey)
    }

    @Test fun timeIsNanosecondsNotTimestepMilliseconds() {
        val r = Av2Guard.check(sample())
        assertEquals("PASS", r.status)
        assertEquals(0.1, r.elapsedS)
    }

    @Test fun scoringCategoryIsNotBehaviorTruth() {
        for (cat in listOf("FOCAL_TRACK", "SCORED_TRACK", "TRACK_FRAGMENT")) {
            val r = Av2Guard.check(sample().copy(category = cat))
            assertEquals("PASS", r.status)
            assertNull(r.groundTruthManeuver)
        }
    }

    @Test fun centerToFrontRequiresVerifiedLengthAndHeading() {
        val front = Av2Guard.check(sample())
        assertEquals(12.0, front.frontCenterXM)
        assertEquals(5.0, front.frontCenterYM)
        val north = Av2Guard.check(sample().copy(headingRad = PI / 2))
        assertTrue(kotlin.math.abs(north.frontCenterXM!! - 10.0) < 1e-9)
        assertTrue(kotlin.math.abs(north.frontCenterYM!! - 7.0) < 1e-9)
        assertEquals("UNVERIFIED_VEHICLE_LENGTH",
            Av2Guard.check(sample().copy(verifiedLengthM = null)).reason)
    }

    @Test fun futureAndUnobservedMustNotEnterInference() {
        assertEquals("FUTURE_OR_INVALID_TIMESTEP",
            Av2Guard.check(sample().copy(timestep = 2)).reason)
        assertEquals("UNOBSERVED_OR_INVALID_VISIBILITY",
            Av2Guard.check(sample().copy(observed = false)).reason)
    }

    @Test fun noSignalColumnMeansUnknownNotOff() {
        val r = Av2Guard.check(sample())
        assertEquals(Indicator.UNKNOWN, r.indicator)
        assertEquals(Provenance.UNKNOWN, r.indicatorProvenance)
        assertNull(r.groundTruthManeuver)
    }

    @Test fun failedRoadTransformFailsClosed() {
        for (a in listOf(
            sample().copy(laneGeometryReliable = false),
            sample().copy(laneReference = ""),
            sample().copy(verifiedRoadSM = null),
            sample().copy(verifiedRoadDM = null),
        )) {
            val r = Av2Guard.check(a)
            assertEquals("BLOCKED", r.status)
            assertFalse(r.eligibleForInference)
        }
    }

    @Test fun futureTimeSuffixCannotChangePastPrediction() {
        val a = Av2Guard.check(sample())
        val b = Av2Guard.check(sample().copy(
            timestampsNs = listOf(0, 100_000_000, -999_999_999)))
        assertEquals(a, b)
    }

    @Test fun duplicateTimeOrBadCoordinatesBlock() {
        assertEquals("NONMONOTONIC_TIMESTAMP",
            Av2Guard.check(sample().copy(timestampsNs = listOf(0, 0, 20))).reason)
        assertEquals("INVALID_MAP_FRAME_KINEMATICS",
            Av2Guard.check(sample().copy(centerXM = Double.NaN)).reason)
    }

    @Test fun missingIdentityOrLengthMustBlock() {
        assertEquals("MISSING_COMPOSITE_ID",
            Av2Guard.check(sample().copy(recordingId = "")).reason)
        assertEquals("INVALID_VEHICLE_LENGTH",
            Av2Guard.check(sample().copy(verifiedLengthM = -1.0)).reason)
    }

    @Test fun uncalibratedContractNoSafetyMetricFields() {
        val r = Av2Guard.check(sample())
        assertEquals("VERIFIED_INPUT_CONTRACT_ONLY", r.reason)
        assertTrue(r.eligibleForInference)
        assertNull(r.groundTruthManeuver)
    }
}

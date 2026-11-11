package ai.riskradar.engine02

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class DatasetGuardsTest {
    private fun sample() = NgSimRow(
        site = "i-80", recording = "08:05-08:20", vehicleId = "7",
        timestampMs = 100L, frameId = 1L, localXFeet = 100.0,
        localYFeet = 200.0, lengthFeet = 15.0, widthFeet = 6.0,
        speedFeetPerS = 50.0, accelerationFeetPerS2 = -3.0,
    )

    @Test fun neighborSignalMustNotBeCopiedFromCarlaEgo() {
        val self = CarlaSignalGuard.indicatorFor("ego", "ego", Indicator.LEFT, true)
        assertEquals(Indicator.LEFT to Provenance.OBSERVED, self)
        val neighbor = CarlaSignalGuard.indicatorFor("ego", "other", Indicator.LEFT, true)
        assertEquals(Indicator.UNKNOWN to Provenance.UNKNOWN, neighbor)
    }

    @Test fun unobservedEgoSignalMustRemainUnknown() {
        val result = CarlaSignalGuard.indicatorFor("ego", "ego", Indicator.OFF, false)
        assertEquals(Indicator.UNKNOWN, result.first)
    }

    @Test fun manifestShaDuplicatesOnlyByExactDigest() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        assertEquals(listOf(listOf("user25LC", "user9LC")),
            CarlaSignalGuard.duplicateHashGroups(
                mapOf("user9LC" to a, "user25LC" to a.uppercase(), "user12LC" to b)))
        assertFailsWith<IllegalArgumentException> {
            CarlaSignalGuard.duplicateHashGroups(mapOf("invalid" to "123"))
        }
    }

    @Test fun feetMustBecomeMetersWithFrontCenterOriginPreserved() {
        val r = NgSimGate().check(sample())
        assertEquals("PASS", r.status)
        assertEquals(30.48, r.frontCenterXM!!)
        assertEquals(60.96, r.frontCenterYM!!)
        assertEquals(15.24, r.speedMps!!)
        assertEquals("FRONT_CENTER", r.coordinateOrigin)
        assertEquals(Indicator.UNKNOWN, r.indicator)
    }

    @Test fun duplicateVehicleTimeIsQuarantined() {
        val g = NgSimGate()
        assertEquals("PASS", g.check(sample()).status)
        assertEquals("DUPLICATE_VEHICLE_TIME", g.check(sample()).reason)
    }

    @Test fun reusedVehicleIdsAcrossSitesAndRecordingsAreDistinct() {
        val g = NgSimGate()
        val a = g.check(sample())
        val b = g.check(sample().copy(site = "us-101"))
        val c = g.check(sample().copy(recording = "08:20-08:35"))
        assertEquals("PASS", b.status)
        assertEquals("PASS", c.status)
        assertTrue(a.compositeTrackKey != b.compositeTrackKey)
        assertTrue(a.compositeTrackKey != c.compositeTrackKey)
    }

    @Test fun peachtreeIsQuarantinedEvenWhenIndividualRowLooksValid() {
        val r = NgSimGate().check(sample().copy(site = "Peachtree"))
        assertEquals("QUARANTINE", r.status)
        assertEquals("PEACHTREE_SOURCE_QUARANTINE", r.reason)
        assertNull(r.frontCenterXM)
    }

    @Test fun nonmonotonicVehicleTimeCannotBecomeValidEvent() {
        val g = NgSimGate()
        g.check(sample().copy(timestampMs = 200L))
        assertEquals("NONMONOTONIC_TRACK_TIME",
            g.check(sample().copy(timestampMs = 100L)).reason)
    }

    @Test fun invalidDimensionsAndSpeedAreBlocked() {
        val g = NgSimGate()
        assertEquals("INVALID_DIMENSIONS_OR_KINEMATICS",
            g.check(sample().copy(lengthFeet = -1.0)).reason)
        assertEquals("INVALID_DIMENSIONS_OR_KINEMATICS",
            g.check(sample().copy(speedFeetPerS = 600.0)).reason)
    }

    @Test fun dataMissingProvenanceIsBlocked() {
        assertEquals("INVALID_SITE_RECORDING_VEHICLE_TIME",
            NgSimGate().check(sample().copy(recording = "")).reason)
        assertEquals("NONFINITE_ROW",
            NgSimGate().check(sample().copy(localXFeet = Double.NaN)).reason)
    }
}

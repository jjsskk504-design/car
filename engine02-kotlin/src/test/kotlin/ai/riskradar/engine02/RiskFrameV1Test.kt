package ai.riskradar.engine02

import com.prism.vehicle.replay.ContractParser
import com.prism.vehicle.replay.RiskLevel as AppRiskLevel
import com.prism.vehicle.replay.ScenarioManifest
import com.prism.vehicle.replay.ScenarioTimeline
import com.prism.vehicle.replay.VehicleFrame as AppVehicleFrame
import com.prism.vehicle.replay.VehicleState as AppVehicleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Actual Part05 (vehicle-integration/replay) Kotlin parser is included
 * read-only in test sourceSets; this is NOT a copied faux decoder.
 */
class RiskFrameV1Test {
    private fun obs(
        time: Long = 100L,
        neighbors: List<Neighbor> = listOf(
            Neighbor("lane_far", time, 0.0, 200.0, 0.0, 20.0)
        ),
    ): Observation = Observation(
        trackId = "car_01",
        timestampMs = time,
        dM = 0.0, sM = 20.0 * time / 1000.0,
        vdMps = 0.0, vsMps = 20.0,
        neighbors = neighbors,
    )

    private fun intent(o: Observation): IntentFrame {
        val e = IntentEngine()
        e.observe(obs(0, emptyList()), nowMs = 0)
        return e.observe(o, nowMs = o.timestampMs)
    }

    private fun toRisk(
        o: Observation,
        withVerifiedTransform: Boolean = false,
    ): RiskFrameV1 =
        Engine02RiskFrameAdapter.convert(
            timestampMs = o.timestampMs,
            vehicleIdsInVehicleFrame = setOf("ego", "car_01", "lane_far", "rear"),
            items = listOf(o to intent(o)),
            verifiedEgoLocalTransform = if (withVerifiedTransform) {
                EgoLocalTransformV1(
                    timestampMs = o.timestampMs, laneReference = o.laneReference,
                    originDM = 0.0, originSM = 0.0, roadYawInEgoRad = 0.0,
                    verified = true,
                )
            } else null,
        )

    private fun appVehicles(t: Long) = AppVehicleFrame(
        schemaVersion = "1.0",
        timestampMs = t,
        coordinateSystem = "ego_local_m",
        egoId = "ego",
        vehicles = listOf("ego", "car_01", "lane_far", "rear").map {
            AppVehicleState(it, 0.0, 0.0, 0.0, 20.0, 0.0, 4.6, 1.9)
        },
    )

    @Test fun normalRiskUsesAllRequiredPart05SnakeCaseFields() {
        val risk = toRisk(obs())
        assertEquals(RiskLevelV1.NORMAL, risk.riskObjects.single().riskLevel)
        val json = risk.toJsonLine()
        val result = ContractParser.parseRiskFrames(json)
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        val parsed = assertNotNull(result.value).single()
        assertEquals("1.0", parsed.schemaVersion)
        assertEquals(100L, parsed.timestampMs)
        val item = parsed.riskObjects.single()
        assertEquals("car_01", item.vehicleId)
        assertEquals(AppRiskLevel.NORMAL, item.riskLevel)
        assertTrue(item.reasonCodes.contains("ENGINE02_RESEARCH_ONLY"))
        assertNull(item.ttcS)
        assertTrue(item.riskZones.isEmpty())
        assertTrue(json.contains("\"risk_level\""))
        assertTrue(json.contains("\"reason_codes\""))
        assertTrue(json.contains("\"ttc_s\":null"))
        assertTrue(json.contains("\"risk_zones\":[]"))
        assertTrue(json.contains("\"risk_objects\""))
        assertTrue(json.contains("\"timestamp_ms\""))
        assertTrue(json.contains("\"schema_version\":\"1.0\""))
    }

    @Test fun projectedBodyHypothesisIsWatchAndPolygonDecodesByPart05() {
        val o = obs(neighbors = listOf(
            Neighbor("rear", 100L, 0.0, -18.0, 0.0, 38.0),
        ))
        val risk = toRisk(o, withVerifiedTransform = true)
        val item = risk.riskObjects.single()
        assertEquals(RiskLevelV1.WATCH, item.riskLevel)
        assertTrue("CV_PROJECTED_BODY_OVERLAP_HYPOTHESIS" in item.reasonCodes)
        assertNull(item.ttcS)
        val zone = item.riskZones.single()
        assertTrue(zone.dtMs > 0)
        assertEquals(4, zone.polygon.size)
        val result = ContractParser.parseRiskFrames(risk.toJsonLine())
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        val parsed = assertNotNull(result.value).single().riskObjects.single()
        assertEquals(AppRiskLevel.WATCH, parsed.riskLevel)
        assertEquals(4, parsed.riskZones.single().polygon.size)
        assertEquals(zone.dtMs, parsed.riskZones.single().dtMs)
    }

    @Test fun projectedRiskZoneIsOmittedWithoutVerifiedEgoCoordinates() {
        val o = obs(neighbors = listOf(
            Neighbor("rear", 100L, 0.0, -18.0, 0.0, 38.0),
        ))
        val risk = toRisk(o)
        val item = risk.riskObjects.single()
        assertEquals(RiskLevelV1.WATCH, item.riskLevel)
        assertTrue(item.riskZones.isEmpty())
        assertTrue("RISK_ZONE_OMITTED_UNVERIFIED_EGO_COORDINATES" in item.reasonCodes)
        assertTrue(ContractParser.parseRiskFrames(risk.toJsonLine()).issues.isEmpty())
    }

    @Test fun mismatchedTransformDoesNotCreateMisalignedZone() {
        val o = obs(neighbors = listOf(
            Neighbor("rear", 100L, 0.0, -18.0, 0.0, 38.0),
        ))
        val risk = Engine02RiskFrameAdapter.convert(
            timestampMs = 100L, vehicleIdsInVehicleFrame = setOf("car_01", "rear"),
            items = listOf(o to intent(o)),
            verifiedEgoLocalTransform = EgoLocalTransformV1(
                timestampMs = 99L, laneReference = "wrong-road",
                originDM = 0.0, originSM = 0.0, roadYawInEgoRad = 0.0,
                verified = true,
            ),
        )
        assertTrue(risk.riskObjects.single().riskZones.isEmpty())
    }

    @Test fun observedOverlapIsWarningButNotCollisionProbability() {
        val o = obs(neighbors = listOf(
            Neighbor("rear", 100L, 0.0, 2.0, 0.0, 20.0),
        ))
        val item = toRisk(o).riskObjects.single()
        assertEquals(RiskLevelV1.WARNING, item.riskLevel)
        assertTrue("OBSERVED_SAMPLED_BODY_OVERLAP" in item.reasonCodes)
        assertNull(item.ttcS)
        val result = ContractParser.parseRiskFrames(toRisk(o).toJsonLine())
        assertEquals(AppRiskLevel.WARNING, result.value!!.single().riskObjects.single().riskLevel)
    }

    @Test fun unknownNeighborCoverageNeverBecomesSafeNormal() {
        val o = obs(neighbors = emptyList())
        val risk = toRisk(o)
        assertEquals(RiskLevelV1.UNKNOWN, risk.riskObjects.single().riskLevel)
        assertTrue(risk.riskObjects.single().riskZones.isEmpty())
        assertEquals(AppRiskLevel.UNKNOWN,
            ContractParser.parseRiskFrames(risk.toJsonLine())
                .value!!.single().riskObjects.single().riskLevel)
    }

    @Test fun unusableLaneGeometryIsUnknown() {
        val o = obs().copy(laneGeometryReliable = false)
        val risk = toRisk(o)
        assertEquals(RiskLevelV1.UNKNOWN, risk.riskObjects.single().riskLevel)
        assertNull(risk.riskObjects.single().ttcS)
        assertTrue(risk.riskObjects.single().riskZones.isEmpty())
    }

    @Test fun mismatchedTimestampOrVehicleIdFailsClosed() {
        val o = obs()
        val frame = intent(o)
        assertFailsWith<IllegalArgumentException> {
            Engine02RiskFrameAdapter.convert(101L, setOf("car_01"),
                listOf(o to frame))
        }
        assertFailsWith<IllegalArgumentException> {
            Engine02RiskFrameAdapter.convert(100L, setOf("ghost"),
                listOf(o to frame))
        }
        assertFailsWith<IllegalArgumentException> {
            Engine02RiskFrameAdapter.convert(100L, setOf("car_01"),
                listOf(o to frame.copy(trackId = "other")))
        }
    }

    @Test fun duplicateOutputVehicleIsForbidden() {
        val o = obs()
        assertFailsWith<IllegalArgumentException> {
            Engine02RiskFrameAdapter.convert(100L, setOf("car_01"),
                listOf(o to intent(o), o to intent(o)))
        }
    }

    @Test fun JSONQuotedVehicleIdIsEscapedAndParsed() {
        val id = "vehicle \"one\" \\ second\n"
        val r = RiskFrameV1(
            timestampMs = 2L,
            riskObjects = listOf(
                RiskObjectV1(
                    vehicleId = id,
                    riskLevel = RiskLevelV1.UNKNOWN,
                    reasonCodes = listOf("line\nfeed", "quote\""),
                ),
            ),
        )
        val parsed = ContractParser.parseRiskFrames(r.toJsonLine())
        assertTrue(parsed.issues.isEmpty(), parsed.issues.toString())
        assertEquals(id, parsed.value!!.single().riskObjects.single().vehicleId)
        assertEquals(listOf("line\nfeed", "quote\""),
            parsed.value!!.single().riskObjects.single().reasonCodes)
    }

    @Test fun appTimelineAcceptsRiskOfMatchingVehicleAndTimestamp() {
        val risk = toRisk(obs(neighbors = listOf(
            Neighbor("rear", 100L, 0.0, -18.0, 0.0, 38.0),
        )))
        val appRisk = ContractParser.parseRiskFrames(risk.toJsonLine())
        val timeline = ScenarioTimeline.build(
            manifest = ScenarioManifest(
                scenarioId = "engine02_contract_test",
                durationMs = 1000L,
                stepMs = 100L,
                egoId = "ego",
                provenance = "SYNTHETIC_UNIT_TEST",
                mock = true,
            ),
            vehicles = listOf(appVehicles(100)),
            predictions = emptyList(),
            risks = assertNotNull(appRisk.value),
        )
        assertTrue(timeline.isPlayable, timeline.issues.toString())
        assertEquals(AppRiskLevel.WATCH, timeline.snapshotAt(100).topLevel)
        assertNotNull(timeline.snapshotAt(100).riskOf("car_01"))
    }

    @Test fun appTimelineRejectsRiskWithoutMatchingTimestamp() {
        val risk = RiskFrameV1(
            timestampMs = 200L,
            riskObjects = listOf(
                RiskObjectV1("car_01", RiskLevelV1.WATCH, listOf("SYNTHETIC")),
            ),
        )
        val appRisk = ContractParser.parseRiskFrames(risk.toJsonLine())
        val timeline = ScenarioTimeline.build(
            ScenarioManifest("test", durationMs = 1000L, stepMs = 100L,
                egoId = "ego", provenance = "TEST"),
            vehicles = listOf(appVehicles(100)),
            predictions = emptyList(),
            risks = assertNotNull(appRisk.value),
        )
        assertTrue(timeline.issues.isNotEmpty())
        assertNull(timeline.snapshotAt(100).risk)
    }

    @Test fun enumAndGeometryDoNotAcceptInvalidValues() {
        assertFailsWith<IllegalArgumentException> { PointV1(Double.NaN, 2.0) }
        assertFailsWith<IllegalArgumentException> {
            RiskZoneV1(0, listOf(PointV1(0.0, 0.0), PointV1(1.0, 0.0), PointV1(1.0, 1.0)))
        }
        assertFailsWith<IllegalArgumentException> {
            RiskObjectV1("id", RiskLevelV1.NORMAL, emptyList(), Double.POSITIVE_INFINITY)
        }
    }
}

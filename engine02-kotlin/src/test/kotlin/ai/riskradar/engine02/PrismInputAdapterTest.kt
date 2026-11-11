package ai.riskradar.engine02

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PrismInputAdapterTest {
    private val ego = PrismVehicle("ego", 0.0, 0.0, 0.0, 12.0, 1.57, 4.6, 1.9)
    private val target = ego.copy(id = "target", xM = 2.0, yM = 10.0, laneId = "unverified")
    private val frame = PrismVehicleFrame("1.0", 100, "ego_local_m", "ego", listOf(ego, target))
    private val road = mapOf(
        "ego" to VerifiedRoadProjection("ego", 100, 1.0, 40.0, 0.0, 12.0, 0.0, 0.0, 3.6, "road", "a"),
        "target" to VerifiedRoadProjection("target", 100, 3.0, 50.0, 0.2, 10.0, 0.1, 3.6, 3.6, "road", "b"),
    )
    private val signal = SignalEvidence("target", 100, Indicator.LEFT, true, SignalSourceScope.DIRECT_ACTOR_OBSERVATION)

    private fun assertBlocked(reason: String, result: PrismAdapterResult) {
        assertEquals("BLOCKED", result.status)
        assertEquals(reason, result.reason)
        assertNull(result.observation)
    }

    @Test fun usesOnlyVerifiedRoadValuesAndSynchronizedNeighbors() {
        val result = PrismInputAdapter.convert(frame, "target", road, signal)
        assertEquals("PASS", result.status)
        assertEquals(
            Observation(
                "target", 100, 3.0, 50.0, 0.2, 10.0, yawRad = 0.1,
                laneCenterDM = 3.6, indicator = Indicator.LEFT,
                indicatorProvenance = Provenance.OBSERVED, identityEpoch = "b", laneReference = "road",
                neighbors = listOf(Neighbor("ego", 100, 1.0, 40.0, 0.0, 12.0)),
            ),
            result.observation,
        )
    }

    @Test fun acceptsEgoSignalOnlyForEgo() {
        val egoSignal = signal.copy(actorTrackId = "ego", sourceScope = SignalSourceScope.EGO_ONLY)
        val egoObservation = assertNotNull(PrismInputAdapter.convert(frame, "ego", road, egoSignal).observation)
        assertEquals(Indicator.LEFT, egoObservation.indicator)
        assertEquals(Provenance.OBSERVED, egoObservation.indicatorProvenance)
        val targetObservation = assertNotNull(
            PrismInputAdapter.convert(frame, "target", road, egoSignal.copy(actorTrackId = "target")).observation,
        )
        assertEquals(Indicator.UNKNOWN, targetObservation.indicator)
    }

    @Test fun staleFutureUnobservedOrUnattributedSignalsStayUnknown() {
        val rejected = listOf(
            null, signal.copy(timestampMs = 99), signal.copy(timestampMs = 101),
            signal.copy(actorTrackId = "ego"), signal.copy(observed = false),
            signal.copy(sourceScope = SignalSourceScope.UNKNOWN), signal.copy(indicator = Indicator.UNKNOWN),
        )
        for (evidence in rejected) {
            val observation = assertNotNull(PrismInputAdapter.convert(frame, "target", road, evidence).observation)
            assertEquals(Indicator.UNKNOWN, observation.indicator, evidence.toString())
            assertEquals(Provenance.UNKNOWN, observation.indicatorProvenance, evidence.toString())
        }
    }

    @Test fun requiresExplicitVersionAndCoordinates() {
        for (invalid in listOf(frame.copy(schemaVersion = "1.1"), frame.copy(timestampMs = -1),
            frame.copy(coordinateSystem = null), frame.copy(coordinateSystem = "world_m"),
            frame.copy(egoId = " "))) {
            assertBlocked("UNVERIFIED_FRAME_CONTRACT", PrismInputAdapter.convert(invalid, "target", road))
        }
    }

    @Test fun rejectsMissingAndDuplicateIdentities() {
        for (invalid in listOf(frame.copy(egoId = "absent"), frame.copy(vehicles = listOf(ego)),
            frame.copy(vehicles = listOf(ego, target, target)), frame.copy(vehicles = emptyList()),
            frame.copy(vehicles = frame.vehicles + target.copy(id = " ")))) {
            assertBlocked("INVALID_EGO_TARGET_OR_DUPLICATE_IDS", PrismInputAdapter.convert(invalid, "target", road))
        }
    }

    @Test fun rejectsMalformedVehicleGeometryEvenOnNeighbor() {
        for (invalid in listOf(ego.copy(xM = Double.NaN), ego.copy(vyMps = Double.POSITIVE_INFINITY),
            ego.copy(headingRad = 4.0), ego.copy(widthM = 0.2), ego.copy(lengthM = 31.0))) {
            assertBlocked("INVALID_VEHICLE_GEOMETRY",
                PrismInputAdapter.convert(frame.copy(vehicles = listOf(invalid, target)), "target", road))
        }
    }

    @Test fun neverInfersProjectionFromLaneIdOrEgoCoordinates() {
        assertBlocked("MISSING_ACTOR_ROAD_PROJECTION", PrismInputAdapter.convert(frame, "target", emptyMap()))
        assertBlocked("MISSING_ACTOR_ROAD_PROJECTION", PrismInputAdapter.convert(frame, "target", road - "ego"))
    }

    @Test fun requiresMatchingActorAndTimestampForEveryProjection() {
        val egoRoad = road.getValue("ego")
        for (invalid in listOf(egoRoad.copy(trackId = "target"), egoRoad.copy(timestampMs = 99),
            egoRoad.copy(timestampMs = 101))) {
            assertBlocked("UNSYNCHRONIZED_ROAD_PROJECTION",
                PrismInputAdapter.convert(frame, "target", road + ("ego" to invalid)))
        }
    }

    @Test fun requiresReliableBodyCenterAndValidKinematics() {
        val projection = road.getValue("target")
        for (invalid in listOf(projection.copy(origin = "FRONT_CENTER"), projection.copy(geometryReliable = false),
            projection.copy(identityEpoch = ""), projection.copy(laneReference = ""),
            projection.copy(laneWidthM = 7.0), projection.copy(vsMps = 81.0),
            projection.copy(vdMps = 9.0), projection.copy(dBodyCenterM = Double.NaN))) {
            assertBlocked("UNVERIFIED_BODY_CENTER_OR_ROAD_GEOMETRY",
                PrismInputAdapter.convert(frame, "target", road + ("target" to invalid)))
        }
    }

    @Test fun requiresSharedRoadReferenceAndUsesConfiguredVelocityLimits() {
        assertBlocked("MISMATCHED_ROAD_REFERENCE", PrismInputAdapter.convert(
            frame, "target", road + ("ego" to road.getValue("ego").copy(laneReference = "another-road")),
        ))
        assertBlocked("UNVERIFIED_BODY_CENTER_OR_ROAD_GEOMETRY", PrismInputAdapter.convert(
            frame, "target", road, parameters = EngineParameters(maxSpeedMps = 11.0),
        ))
    }
}

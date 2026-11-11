package ai.riskradar.engine02

import com.prism.vehicle.replay.ContractParser
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.VehicleFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// This check compiles the team DTO and parser from the checkout, without patching them.
class PrismContractTest {
    private fun vehicleJson(time: Long, coordinateField: String = """"coordinate_system":"ego_local_m",""") = """
        {"schema_version":"1.0","timestamp_ms":$time,$coordinateField"ego_id":"ego","vehicles":[
          {"id":"ego","x":0,"y":0,"vx":0,"vy":10,"heading_rad":1.57079632679,"length_m":4.6,"width_m":1.9},
          {"id":"target","x":3,"y":14,"vx":0,"vy":10,"heading_rad":1.57079632679,"length_m":4.8,"width_m":2.0,
           "lane_id":"hint","future_trajectory":[{"dt_ms":200,"x":5,"y":40}]}
        ]}
    """.trimIndent().lineSequence().joinToString("")

    private fun parse(text: String): VehicleFrame {
        val parsed = ContractParser.parseVehicleFrames(text)
        assertTrue(parsed.issues.isEmpty(), parsed.issues.toString())
        return assertNotNull(parsed.value).single()
    }

    private fun VehicleFrame.toEngineInput() = PrismVehicleFrame(
        schemaVersion, timestampMs, coordinateSystem, egoId,
        vehicles.map { PrismVehicle(it.id, it.x, it.y, it.vx, it.vy, it.headingRad, it.lengthM, it.widthM, it.laneId) },
    )

    private fun projections(time: Long) = mapOf(
        "ego" to VerifiedRoadProjection("ego", time, 0.0, time / 100.0, 0.0, 10.0, 0.0, 0.0, 3.6, "map", "a"),
        "target" to VerifiedRoadProjection(
            "target", time, 3.6, 20.0 + time / 100.0, 0.0, 10.0, 0.0, 3.6, 3.6, "map", "b",
        ),
    )

    @Test fun teamJsonReplaysIntoEngineInMillisecondsWithVerifiedRoadGeometry() {
        val engine = IntentEngine()
        val times = listOf(100L, 200L)
        val parsed = ContractParser.parseVehicleFrames(times.joinToString("\n") { vehicleJson(it) })
        assertTrue(parsed.issues.isEmpty())
        val results = assertNotNull(parsed.value).map { frame ->
            assertEquals(3.0, frame.vehicles.last().x)
            val observation = assertNotNull(PrismInputAdapter.convert(
                frame.toEngineInput(), "target", projections(frame.timestampMs),
            ).observation)
            assertEquals(3.6, observation.dM)
            assertEquals(4.8, observation.lengthM)
            assertEquals(0.0, observation.yawRad)
            engine.observe(observation, frame.timestampMs)
        }
        assertEquals(times, results.map { it.timestampMs })
        assertEquals(listOf("UNKNOWN", "KEEP"), results.map { it.motionIntention })
        assertEquals(Indicator.UNKNOWN, results.last().features?.indicator)
        assertEquals("LEFT", results.last().riskGeometry.neighborGaps.single().side)
    }

    @Test fun parserOptionalCoordinateStillRequiresExplicitAdapterContract() {
        val input = parse(vehicleJson(100, "")).toEngineInput()
        assertNull(input.coordinateSystem)
        val result = PrismInputAdapter.convert(input, "target", projections(100))
        assertEquals("UNVERIFIED_FRAME_CONTRACT", result.reason)
        assertNull(result.observation)
    }

    @Test fun sameTrackLaneHintDoesNotAuthorizeRoadGeometry() {
        val input = parse(vehicleJson(100)).toEngineInput()
        assertEquals("hint", input.vehicles.last().laneId)
        val result = PrismInputAdapter.convert(input, "target", emptyMap())
        assertEquals("MISSING_ACTOR_ROAD_PROJECTION", result.reason)
        assertNull(result.observation)
    }

    @Test fun frozenRiskFrameAndPredictionFrameRemainSeparateTeamContracts() {
        val risk = ContractParser.parseRiskJson("""
            {"schema_version":"1.0","timestamp_ms":100,"risk_objects":[{"vehicle_id":"target","risk_level":"UNKNOWN"}]}
        """.trimIndent())
        val prediction = ContractParser.parsePredictionJson("""
            {"schema_version":"1.0","timestamp_ms":100,"predictions":[{"vehicle_id":"target","trajectories":[
                {"mode":"lane_keep","confidence":null,"points":[{"dt_ms":200,"x":1,"y":2}]}
            ]}]}
        """.trimIndent())
        assertTrue(risk.issues.isEmpty())
        assertTrue(prediction.issues.isEmpty())
        assertEquals(RiskLevel.UNKNOWN, assertNotNull(risk.value).single().riskObjects.single().riskLevel)
        val trajectory = assertNotNull(prediction.value).single().predictions.single().trajectories.single()
        assertNull(trajectory.confidence)
        assertEquals(200L, trajectory.points.single().dtMs)
    }
}

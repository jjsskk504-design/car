package com.prism.prediction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {
    private fun verifyMock(relativePath: String) {
        val frames = ContractJson.decodeVehicleFramesJsonl(File(relativePath).readText())
        val result = PredictionEngine.predict(frames)
        PredictionEngine.validatePredictionFrame(result, frames.last().vehicles.map { it.id }.toSet())
        assertEquals(1, result.predictions.size)
        val trajectories = result.predictions.single().trajectories
        assertEquals(
            listOf("lane_keep", "braking", "lane_change_left", "lane_change_right"),
            trajectories.map { it.mode },
        )
        trajectories.forEach {
            assertNull(it.confidence)
            assertEquals(5, it.points.size)
        }
        val roundTrip = ContractJson.decodePredictionFrame(ContractJson.encodePredictionFrame(result))
        assertEquals(result, roundTrip)
    }

    @Test fun basicMockPasses() = verifyMock("examples/vehicle_frames.jsonl")

    @Test fun straightMockPasses() = verifyMock("examples/cases/straight.jsonl")

    @Test fun missingObservationMockPasses() = verifyMock("examples/cases/missing.jsonl")

    @Test fun brakingMockPasses() = verifyMock("examples/cases/braking.jsonl")

    @Test fun laneChangeMockPasses() = verifyMock("examples/cases/lane_change.jsonl")

    @Test
    fun existingOutputExampleIsValid() {
        val output = ContractJson.decodePredictionFrame(File("examples/prediction_frame.json").readText())
        PredictionEngine.validatePredictionFrame(output, setOf("car_01"))
        assertEquals("1.0", output.schemaVersion)
        assertEquals(2000, output.timestampMs)
        assertTrue(output.predictions.isNotEmpty())
    }
}

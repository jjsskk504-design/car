package com.prism.prediction

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private fun vehicle(id: String, x: Double, y: Double, length: Double = 4.5) =
        VehicleState(id, x, y, 0.0, 0.0, PI / 2, length, 1.8, "L1")

    private fun frame(timestamp: Long, vararg vehicles: VehicleState) =
        VehicleFrame(timestamp, "ego", vehicles.toList())

    @Test
    fun regressiveInputOrderIsSorted() {
        val frames = listOf(frame(1000, vehicle("ego", 0.0, 0.0)), frame(0, vehicle("ego", 0.0, 0.0)))
        assertEquals(listOf(0L, 1000L), History.prepare(frames).map { it.timestampMs })
    }

    @Test
    fun duplicateTimestampIsRejected() {
        val result = runCatching {
            History.prepare(listOf(frame(0, vehicle("ego", 0.0, 0.0)), frame(0, vehicle("ego", 0.0, 0.0))))
        }
        assertTrue(result.exceptionOrNull() is ContractException)
    }

    @Test
    fun missingEgoIsRejected() {
        val result = runCatching { History.prepare(listOf(VehicleFrame(0, "ego", listOf(vehicle("other", 0.0, 0.0))))) }
        assertTrue(result.exceptionOrNull() is ContractException)
    }

    @Test
    fun possibleIdReassignmentIsRejected() {
        val frames = listOf(
            frame(0, vehicle("ego", 0.0, 0.0), vehicle("car", 0.0, 5.0)),
            frame(1000, vehicle("ego", 0.0, 0.0), vehicle("car", 0.0, 5.0, length = 7.0)),
        )
        assertTrue(runCatching { History.prepare(frames) }.exceptionOrNull() is ContractException)
    }

    @Test
    fun explicitEgoPosesNormalizeHistoryToLatestFrame() {
        val frames = listOf(
            frame(0, vehicle("ego", 0.0, 0.0), vehicle("car", 2.0, 12.0)),
            frame(1000, vehicle("ego", 0.0, 0.0), vehicle("car", 2.0, 4.0)),
        )
        val normalized = History.normalizeToCurrentEgo(frames, listOf(Pose2D(0.0, 0.0, 0.0), Pose2D(0.0, 10.0, 0.0)))
        val oldCar = History.observations(normalized, "car").first().vehicle
        assertEquals(2.0, oldCar.x, 1e-9)
        assertEquals(2.0, oldCar.y, 1e-9)
    }
}

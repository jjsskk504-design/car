package com.prism.prediction

import kotlin.math.PI
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {
    private fun vehicle(
        x: Double = 0.0,
        y: Double = 0.0,
        vx: Double = 0.0,
        vy: Double = 0.0,
        heading: Double = PI / 2,
    ) = VehicleState("car", x, y, vx, vy, heading, 4.5, 1.8, "L1")

    @Test
    fun constantVelocityUsesSecondsAndPrismAxes() {
        val points = Baseline.constantVelocity(
            vehicle(x = 1.0, y = 10.0, vx = 2.0, vy = 5.0),
            PredictionConfig(horizonsMs = listOf(1000, 3000)),
        )
        assertEquals(3.0, points[0].x, 1e-9)
        assertEquals(15.0, points[0].y, 1e-9)
        assertEquals(7.0, points[1].x, 1e-9)
        assertEquals(25.0, points[1].y, 1e-9)
        assertEquals(atan2(5.0, 2.0), points[0].headingRad, 1e-9)
    }

    @Test
    fun stationaryVehicleRetainsHeading() {
        val point = Baseline.constantVelocity(
            vehicle(vx = 0.01, vy = 0.01, heading = 1.2),
            PredictionConfig(horizonsMs = listOf(1000)),
        ).single()
        assertEquals(1.2, point.headingRad, 1e-9)
    }

    @Test
    fun constantAccelerationUsesVelocityDifference() {
        val observations = listOf(
            TimedVehicle(0, vehicle(y = 0.0, vy = 2.0)),
            TimedVehicle(1000, vehicle(y = 3.0, vy = 4.0)),
        )
        val point = Baseline.constantAcceleration(
            observations,
            PredictionConfig(horizonsMs = listOf(1000)),
        ).single()
        assertEquals(8.0, point.y, 1e-9)
    }

    @Test
    fun predictionPastFiveSecondsIsRejected() {
        val result = runCatching { PredictionConfig(horizonsMs = listOf(6000)) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}

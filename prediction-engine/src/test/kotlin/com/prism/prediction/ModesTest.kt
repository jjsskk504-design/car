package com.prism.prediction

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {
    private fun vehicle(vx: Double, vy: Double, heading: Double) =
        VehicleState("car", 0.0, 10.0, vx, vy, heading, 4.5, 1.8, "L1")

    @Test
    fun laneChangeIsSmoothAndBoundedByLaneWidth() {
        val trajectories = Modes.candidates(
            listOf(TimedVehicle(0, vehicle(0.0, 5.0, PI / 2))),
            PredictionConfig(horizonsMs = listOf(1000, 2000, 4000), laneWidthM = 3.5),
        )
        val keep = trajectories[0].points
        val left = trajectories[2].points
        val offsets = keep.zip(left).map { (base, shifted) -> base.x - shifted.x }
        assertTrue(offsets[0] in 0.0..offsets[1])
        assertTrue(offsets[1] < offsets[2])
        assertEquals(3.5, offsets.last(), 1e-9)
    }

    @Test
    fun curvatureChangesPositionAndHeading() {
        val point = Modes.candidates(
            listOf(TimedVehicle(0, vehicle(5.0, 0.0, 0.0).copy(y = 0.0))),
            PredictionConfig(horizonsMs = listOf(1000), roadCurvaturePerM = 0.1),
        ).first().points.single()
        assertTrue(point.y > 0.0)
        assertTrue(point.headingRad > 0.0)
    }
}

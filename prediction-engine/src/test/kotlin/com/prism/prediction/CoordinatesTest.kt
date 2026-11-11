package com.prism.prediction

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinatesTest {
    @Test
    fun translationIntoMovingEgoFrame() {
        val result = Coordinates.transformPoint(2.0, 12.0, Pose2D(0.0, 0.0, 0.0), Pose2D(0.0, 10.0, 0.0))
        assertEquals(2.0, result.first, 1e-9)
        assertEquals(2.0, result.second, 1e-9)
    }

    @Test
    fun ninetyDegreeSourceRotation() {
        val result = Coordinates.transformPoint(1.0, 0.0, Pose2D(0.0, 0.0, PI / 2), Pose2D(0.0, 0.0, 0.0))
        assertEquals(0.0, result.first, 1e-9)
        assertEquals(1.0, result.second, 1e-9)
    }

    @Test
    fun translationAndRotationTogether() {
        val result = Coordinates.transformPoint(
            2.0,
            1.0,
            Pose2D(10.0, 5.0, PI / 2),
            Pose2D(8.0, 8.0, -PI / 2),
        )
        assertEquals(1.0, result.first, 1e-9)
        assertEquals(1.0, result.second, 1e-9)
    }
}

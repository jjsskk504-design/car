package com.prism.vehicle.app.ui

import com.prism.vehicle.replay.VehicleFrame
import com.prism.vehicle.replay.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Test

class LaneLayoutTest {
    private fun v(id: String, x: Double, lane: String?) = VehicleState(id, x, 10.0, 0.0, 20.0, 1.57, 4.6, 1.8, lane)
    private fun frame(vararg vs: VehicleState) = VehicleFrame("1.0", 0, "ego_local_m", "ego", listOf(v("ego", 0.0, "L2"), *vs))

    @Test fun estimatesWidthAndEgoLaneFromLaneIds() {
        val f = frame(v("a", -4.0, "L1"), v("b", 4.0, "L3"), v("c", 0.3, "L2"))
        val road = LaneLayout.of(listOf(f)).roadAt(f)
        assertEquals(3, road.laneCount)
        assertEquals(4.0f, road.laneWidthM)
        assertEquals(1, road.egoLaneIndex)
    }

    @Test fun missingLaneIdsFallBackToDefaultRoad() {
        val f = VehicleFrame("1.0", 0, null, "ego", listOf(v("ego", 0.0, null)))
        val road = LaneLayout.of(listOf(f)).roadAt(f)
        assertEquals(3, road.laneCount)
        assertEquals(1, road.egoLaneIndex)
    }
}

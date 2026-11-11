package ai.riskradar.engine02

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PhysicalSimulationTest {
    @Test fun allFourSyntheticScenariosPassIndependentPhysicalGate() {
        for (name in PhysicalSimulator.scenes) {
            val report = requirePhysicalPass(PhysicalSimulator.simulate(name))
            assertEquals("PASS", report.status, name + ": " + report.failures.take(8))
            assertEquals(201, report.samples)
            assertTrue(report.pairChecks > 0)
            assertTrue(report.collisions.isEmpty())
            assertEquals(0, report.laneBoundaryViolations)
        }
    }

    @Test fun injectedPhysicalBodyOverlapMustFailEvenAtFinalSample() {
        val source = PhysicalSimulator.simulate("shockwave")
        for (index in listOf(0, 50, 100, 200)) {
            val modified = source.world.mapIndexed { i, frame ->
                if (i != index) frame else {
                    val a = frame.vehicles[0]
                    val b = frame.vehicles[1]
                    val bodies = frame.vehicles.toMutableList()
                    bodies[1] = b.copy(xM = a.xM, yM = a.yM)
                    frame.copy(vehicles = bodies)
                }
            }
            val invalid = source.copy(world = modified)
            assertFailsWith<IllegalArgumentException> { requirePhysicalPass(invalid) }
            val report = PhysicalAuditor.audit(invalid)
            assertEquals("FAIL", report.status)
            assertTrue("ACTUAL_BODY_COLLISION" in report.failures)
            assertTrue(report.collisions.any { it.timestampMs == index*50L })
        }
    }

    @Test fun allWorldTimestampsAndVehicleIdsAreStable() {
        for (name in PhysicalSimulator.scenes) {
            val scene = PhysicalSimulator.simulate(name)
            assertEquals(0, scene.world.first().timestampMs)
            assertEquals(10000, scene.world.last().timestampMs)
            val ids = scene.world.first().vehicles.map { it.id }
            assertEquals(ids.size, ids.distinct().size)
            assertTrue("ego" in ids)
            assertTrue(scene.world.withIndex().all { (i, frame) ->
                frame.timestampMs == i*50L && frame.vehicles.map { it.id } == ids
            })
        }
    }

    @Test fun physicalEventsAreNotFutureSensorObservations() {
        val scene = PhysicalSimulator.simulate("lane_change")
        assertTrue(scene.provenance.startsWith("SYNTHETIC"))
        assertTrue(scene.events.all {
            it.timestampMs >= 0 && it.timestampMs <= 10000
        })
    }

    @Test fun unexpectedVehicleIdOrTimeIsGateFailure() {
        val scene = PhysicalSimulator.simulate("shockwave")
        val f = scene.world[5]
        val overwritten = scene.world.toMutableList()
        overwritten[5] = f.copy(timestampMs = 999,
            vehicles = f.vehicles.mapIndexed { i, v ->
                if (i == 2) v.copy(id = "reassigned") else v
            })
        val r = PhysicalAuditor.audit(scene.copy(world = overwritten))
        assertEquals("FAIL", r.status)
        assertTrue(r.failures.any { it.startsWith("unstable_vehicle_ids") })
        assertTrue(r.failures.any { it.startsWith("invalid_timestamp") })
    }

    @Test fun openRoadBoundaryShouldFail() {
        val scene = PhysicalSimulator.simulate("shockwave")
        val altered = scene.world.toMutableList()
        val f = altered[20]
        altered[20] = f.copy(vehicles=f.vehicles.mapIndexed { i,v ->
            if (i == 1) v.copy(xM = 20.0) else v
        })
        val r = PhysicalAuditor.audit(scene.copy(world=altered))
        assertEquals("FAIL", r.status)
        assertTrue(r.failures.any { it.startsWith("road_boundary") })
    }
}

package ai.prism.visualization.demo

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 시연 데이터가 물리적으로 말이 되는지(차량끼리 겹치지 않는지) 확인한다. */
class MockScenarioSanityTest {
    @Test
    fun noVehicleOverlapsInAnyScenario() {
        MockScenarios.all().forEach { s ->
            s.frames.forEach { f ->
                val vs = f.vehicle.vehicles
                for (i in vs.indices) for (j in i + 1 until vs.size) {
                    val a = vs[i]; val b = vs[j]
                    val overlap = abs(a.x - b.x) < (a.widthM + b.widthM) / 2 &&
                        abs(a.y - b.y) < (a.lengthM + b.lengthM) / 2
                    assertTrue("${s.id}: ${a.id}-${b.id} overlap at ${f.vehicle.timestampMs}", !overlap)
                }
            }
        }
    }
}

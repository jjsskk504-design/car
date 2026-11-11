package ai.riskradar.engine02

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluationTest {
    @Test fun oneToOneEventMatchingWithEarliestOnset() {
        val a = listOf(Event("A", Direction.LEFT, 1000),
            Event("A", Direction.LEFT, 1500), Event("B", Direction.RIGHT, 2000))
        val e = listOf(Event("A", Direction.LEFT, 2500),
            Event("C", Direction.LEFT, 3000))
        val r = eventMetrics(a, e, exposureTrackHours = 0.5)
        assertEquals(1, r.truePositiveEvents)
        assertEquals(2, r.falseAlertEpisodes)
        assertEquals(1, r.missedEvents)
        assertEquals(1.0 / 3.0, r.precision)
        assertEquals(0.5, r.recall)
        assertEquals(4.0, r.falseAlertsPerTrackHour)
        assertEquals(listOf(1.5), r.leadTimeS)
    }

    @Test fun alertOnWindowBoundaryMatches() {
        val r = eventMetrics(listOf(Event("a", Direction.LEFT, 2000)),
            listOf(Event("a", Direction.LEFT, 5000)),
            exposureTrackHours = 1.0, leadWindowMs = 3000)
        assertEquals(1, r.truePositiveEvents)
        assertEquals(listOf(3.0), r.leadTimeS)
    }

    @Test fun oneAlertNeverMatchesTwoEvents() {
        val r = eventMetrics(listOf(Event("A", Direction.LEFT, 2500)),
            listOf(Event("A", Direction.LEFT, 3000),
                Event("A", Direction.LEFT, 3100)), 1.0)
        assertEquals(1, r.truePositiveEvents)
        assertEquals(1, r.missedEvents)
    }

    @Test fun lateOrWrongDirectionAlertCannotMatch() {
        val r = eventMetrics(listOf(Event("A", Direction.RIGHT, 900),
            Event("A", Direction.LEFT, 1100)),
            listOf(Event("A", Direction.LEFT, 1000)),
            exposureTrackHours = 1.0)
        assertEquals(0, r.truePositiveEvents)
        assertEquals(2, r.falseAlertEpisodes)
        assertEquals(1, r.missedEvents)
    }

    @Test fun noEventsDoesNotFabricatePrecisionOrRecall() {
        val r = eventMetrics(emptyList(), emptyList(), exposureTrackHours = 1.0)
        assertNull(r.precision)
        assertNull(r.recall)
        assertNull(r.meanLeadTimeS)
        assertFailsWith<IllegalArgumentException> {
            eventMetrics(emptyList(), emptyList(), 0.0)
        }
    }

    private fun windows() = listOf(
        EvaluationWindow("siteA", "recordingA", "driverA", 0, 1000, Partition.TRAIN),
        EvaluationWindow("siteB", "recordingB", "driverB", 2000, 3000, Partition.VALIDATION),
        EvaluationWindow("siteC", "recordingC", "driverC", 4000, 5000, Partition.TEST),
    )

    @Test fun verifiedNonoverlappingGroupsCanPass() {
        assertTrue(validateHoldout(windows(), embargoMs = 500))
    }

    @Test fun repeatedDriverAcrossPartitionsIsLeakage() {
        val w = windows().toMutableList()
        w[1] = w[1].copy(driver = "driverA")
        assertFailsWith<IllegalArgumentException> { validateHoldout(w) }
    }

    @Test fun repeatedSiteAcrossPartitionsIsLeakage() {
        val w = windows().toMutableList()
        w[1] = w[1].copy(site = "siteA")
        assertFailsWith<IllegalArgumentException> { validateHoldout(w) }
    }

    @Test fun unknownDriverMustFailClosed() {
        val w = windows().toMutableList()
        w[1] = w[1].copy(driver = "UNKNOWN")
        assertFailsWith<IllegalArgumentException> { validateHoldout(w) }
    }

    @Test fun overlappingOrTooCloseSameRecordingBlocks() {
        val a = EvaluationWindow("s", "r", "d1", 0, 1000, Partition.TRAIN)
        val b = EvaluationWindow("s", "r", "d2", 1500, 2000, Partition.TRAIN)
        assertTrue(validateHoldout(listOf(a, b), embargoMs = 500))
        assertFailsWith<IllegalArgumentException> {
            validateHoldout(listOf(a, b), embargoMs = 501)
        }
        assertFailsWith<IllegalArgumentException> {
            validateHoldout(listOf(a, b.copy(startMs = 900)), embargoMs = 0)
        }
    }

    @Test fun backwardsCrossPartitionTimeBlocks() {
        val w = windows().toMutableList()
        w[1] = w[1].copy(startMs = 100, endMs = 800)
        assertFailsWith<IllegalArgumentException> { validateHoldout(w) }
    }
}

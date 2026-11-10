package com.prism.vehicle.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractParserTest {
    // 지시서 02장 계약 예시 그대로
    private val vehicleExample = """{"schema_version":"1.0","timestamp_ms":1000,"coordinate_system":"ego_local_m","ego_id":"ego","vehicles":[{"id":"ego","x":0,"y":0,"vx":0,"vy":25,"heading_rad":1.570796,"length_m":4.6,"width_m":1.8,"lane_id":"L2"},{"id":"car_01","x":3.6,"y":20,"vx":-0.3,"vy":20,"heading_rad":1.59,"length_m":4.4,"width_m":1.8,"lane_id":"L3"}]}"""
    private val predictionExample = """{"schema_version":"1.0","timestamp_ms":1000,"predictions":[{"vehicle_id":"car_01","trajectories":[{"mode":"lane_keep","confidence":null,"points":[{"dt_ms":1000,"x":3.3,"y":40.0,"heading_rad":1.58}]}]}]}"""
    private val riskExample = """{"schema_version":"1.0","timestamp_ms":1000,"risk_objects":[{"vehicle_id":"car_01","risk_level":"WATCH","reason_codes":["PATH_OVERLAP"],"ttc_s":null,"risk_zones":[{"dt_ms":1000,"polygon":[{"x":1.5,"y":20},{"x":3.5,"y":20},{"x":3.5,"y":25},{"x":1.5,"y":25}]}]}]}"""

    @Test fun parsesContractExamples() {
        val v = ContractParser.parseVehicleFrames(vehicleExample)
        val p = ContractParser.parsePredictionFrames(predictionExample)
        val r = ContractParser.parseRiskFrames(riskExample)
        assertTrue(v.issues.isEmpty() && p.issues.isEmpty() && r.issues.isEmpty())
        assertEquals(25.0, v.value!!.single().vehicles[0].vy, 0.0)
        assertNull(p.value!!.single().predictions[0].trajectories[0].confidence)
        assertEquals(RiskLevel.WATCH, r.value!!.single().riskObjects[0].riskLevel)
        assertNull(r.value!!.single().riskObjects[0].ttcS)
    }

    @Test fun rejectsUnknownRiskLevel() {
        val r = ContractParser.parseRiskFrames(riskExample.replace("WATCH", "DANGER"))
        assertEquals(1, r.issues.size)
        assertEquals(Severity.ERROR, r.issues[0].severity)
    }

    @Test fun reportsLineNumberOfBrokenLine() {
        val r = ContractParser.parseVehicleFrames(vehicleExample + "\n{\"schema_version\":")
        assertEquals(1, r.value!!.size)
        assertEquals("vehicle_frames.jsonl:2", r.issues.single().where)
    }

    @Test fun missingRequiredFieldIsError() {
        val r = ContractParser.parseVehicleFrames(vehicleExample.replace("\"ego_id\":\"ego\",", ""))
        assertEquals(Severity.ERROR, r.issues.single().severity)
    }
}

class ScenarioTimelineTest {
    private val manifest = ScenarioManifest("t", durationMs = 1000, stepMs = 100, egoId = "ego", provenance = "test")
    private fun car(id: String, y: Double = 10.0) = VehicleState(id, 0.0, y, 0.0, 20.0, 1.5708, 4.6, 1.8, "L2")
    private fun vf(ts: Long, vararg ids: String) = VehicleFrame("1.0", ts, "ego_local_m", "ego", ids.map { car(it) })
    private fun rf(ts: Long, id: String, level: RiskLevel = RiskLevel.WATCH) = RiskFrame("1.0", ts, listOf(RiskObject(id, level)))

    @Test fun snapshotPicksLatestFrameAndMatchingRisk() {
        val t = ScenarioTimeline.build(manifest, listOf(vf(0, "ego", "a"), vf(100, "ego", "a")), emptyList(), listOf(rf(100, "a")))
        assertTrue(t.isPlayable)
        val s = t.snapshotAt(150)
        assertEquals(100L, s.vehicle!!.timestampMs)
        assertEquals(RiskLevel.WATCH, s.topLevel)
        assertFalse(s.stale)
        assertNull(t.snapshotAt(50).risk) // ts 0 에는 RiskFrame 없음 → 다른 시점 위험을 섞지 않는다
    }

    @Test fun gapBecomesStaleAndUnknown() {
        val t = ScenarioTimeline.build(manifest, listOf(vf(0, "ego"), vf(800, "ego")), emptyList(), emptyList())
        assertTrue(t.issues.any { it.severity == Severity.WARNING && "누락" in it.message })
        val s = t.snapshotAt(500)
        assertTrue(s.stale)
        assertEquals(RiskLevel.UNKNOWN, s.topLevel)
    }

    @Test fun riskReferencingUnknownVehicleIsError() {
        val t = ScenarioTimeline.build(manifest, listOf(vf(0, "ego")), emptyList(), listOf(rf(0, "ghost")))
        assertFalse(t.isPlayable)
    }

    @Test fun egoMissingIsError() {
        assertFalse(ScenarioTimeline.build(manifest, listOf(vf(0, "a")), emptyList(), emptyList()).isPlayable)
    }

    @Test fun unsupportedSchemaMajorIsError() {
        val frames = listOf(vf(0, "ego").copy(schemaVersion = "2.0"))
        assertFalse(ScenarioTimeline.build(manifest, frames, emptyList(), emptyList()).isPlayable)
    }

    @Test fun nonFiniteNumberIsError() {
        val frames = listOf(VehicleFrame("1.0", 0, null, "ego", listOf(car("ego").copy(x = Double.NaN))))
        assertFalse(ScenarioTimeline.build(manifest, frames, emptyList(), emptyList()).isPlayable)
    }

    @Test fun predictionWithoutVehicleFrameIsDroppedWithWarning() {
        val p = PredictionFrame("1.0", 50, emptyList())
        val t = ScenarioTimeline.build(manifest, listOf(vf(0, "ego")), listOf(p), emptyList())
        assertTrue(t.isPlayable)
        assertTrue(t.issues.any { it.severity == Severity.WARNING })
        assertNull(t.snapshotAt(50).prediction)
    }

    @Test fun unknownLevelOutranksNormalButNotWatch() {
        val r = RiskFrame("1.0", 0, listOf(RiskObject("a", RiskLevel.UNKNOWN), RiskObject("b", RiskLevel.NORMAL)))
        val t = ScenarioTimeline.build(manifest, listOf(vf(0, "ego", "a", "b")), emptyList(), listOf(r))
        assertEquals(RiskLevel.UNKNOWN, t.snapshotAt(0).topLevel)
    }
}

class ReplayClockTest {
    @Test fun advancesBySpeedAndStopsAtEnd() {
        var c = ReplayClock(durationMs = 1000).play().withSpeed(2f)
        c = c.advance(300)
        assertEquals(600L, c.positionMs)
        c = c.advance(1000)
        assertEquals(1000L, c.positionMs)
        assertFalse(c.playing)
        assertEquals(0L, c.play().positionMs) // 끝에서 재생하면 처음부터
    }

    @Test fun pausedClockDoesNotMove() {
        assertEquals(0L, ReplayClock(durationMs = 1000).advance(500).positionMs)
    }

    @Test fun seekIsClamped() {
        val c = ReplayClock(durationMs = 1000)
        assertEquals(1000L, c.seek(5000).positionMs)
        assertEquals(0L, c.seek(-5).positionMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSpeed() {
        ReplayClock(durationMs = 1000, speed = 3f)
    }
}

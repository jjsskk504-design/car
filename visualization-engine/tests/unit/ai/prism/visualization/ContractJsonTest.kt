package ai.prism.visualization

import ai.prism.visualization.model.ContractJson
import ai.prism.visualization.model.RiskLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 팀 저장소 계약(VehicleFrame v1 / PredictionFrame 1.0 / RiskFrame 1.0) 필드 기준 파싱 테스트. */
class ContractJsonTest {

    // simulation-engine 샘플과 같은 형태(lane_id 문자열, coordinate_system 포함)
    private val vehicleJson = """
        {"schema_version":"1.0","timestamp_ms":0,"coordinate_system":"ego_local_m","ego_id":"ego","vehicles":[
          {"id":"ego","x":0.0,"y":0.0,"vx":0.0,"vy":25.0,"heading_rad":1.570796,"length_m":5.0,"width_m":2.0,"lane_id":"L2"},
          {"id":"truck_01","x":0.0,"y":60.0,"vx":0.0,"vy":25.0,"heading_rad":1.570796,"length_m":10.0,"width_m":2.5,"lane_id":"L2"}
        ]}
    """.trimIndent()

    @Test
    fun parsesVehicleFrameV1() {
        val r = ContractJson.parseVehicleFrame(vehicleJson)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        val f = r.value
        assertEquals(0L, f.timestampMs)
        assertEquals("ego", f.egoId)
        assertEquals("1.0", f.schemaVersion)
        assertEquals("ego_local_m", f.coordinateSystem)
        val v = f.vehicles[1]
        assertEquals("truck_01", v.id); assertEquals(60f, v.y); assertEquals(10f, v.lengthM)
        assertEquals("L2", v.laneId)
    }

    @Test
    fun warnsOnWrongSchemaVersionOrCoordinateSystem() {
        val r = ContractJson.parseVehicleFrame("""{"schema_version":"2.0","timestamp_ms":1,"coordinate_system":"world","ego_id":"ego","vehicles":[]}""")
        assertEquals(2, r.warnings.size)
    }

    @Test
    fun skipsBrokenVehiclesInsteadOfCrashing() {
        val json = """
            {"schema_version":"1.0","timestamp_ms": 1,"coordinate_system":"ego_local_m","ego_id": "ego", "vehicles": [
              {"id":"ok","x":1,"y":2,"vx":0,"vy":0,"heading_rad":1.57,"length_m":4,"width_m":2},
              {"id":"nan","x":"NaN","y":2,"vx":0,"vy":0,"heading_rad":1.57,"length_m":4,"width_m":2},
              {"id":"missing_y","x":1,"vx":0,"vy":0,"heading_rad":1.57,"length_m":4,"width_m":2},
              {"x":1,"y":2,"vx":0,"vy":0,"heading_rad":1.57,"length_m":4,"width_m":2},
              "garbage"
            ]}
        """.trimIndent()
        val r = ContractJson.parseVehicleFrame(json)
        assertEquals(listOf("ok"), r.value.vehicles.map { it.id })
        assertEquals(4, r.warnings.size)
        assertNull(r.value.vehicles[0].laneId)
    }

    @Test
    fun parsesPredictionFrameFromPredictionEngineExample() {
        // prediction-engine/examples/prediction_frame.json 과 같은 구조
        val json = JSONObject("""{"schema_version":"1.0","timestamp_ms":2000,"predictions":[
            {"vehicle_id":"car_01","trajectories":[
              {"mode":"lane_keep","confidence":null,"points":[
                {"dt_ms":1000,"x":1.0,"y":15.0,"heading_rad":1.5707963267948966},
                {"dt_ms":2000,"x":1.0,"y":20.0,"heading_rad":1.5707963267948966}]},
              {"mode":"braking","confidence":0.4,"points":[{"dt_ms":1000,"x":1.0,"y":12.0,"heading_rad":1.57}]}]}]}""")
        val r = ContractJson.parsePredictionFrame(json)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        val p = r.value.predictions.single()
        assertEquals("car_01", p.vehicleId)
        assertEquals(listOf("lane_keep", "braking"), p.trajectories.map { it.mode })
        assertNull(p.trajectories[0].confidence)
        assertEquals(0.4f, p.trajectories[1].confidence!!, 1e-6f)
        assertEquals(2000L, p.trajectories[0].points[1].dtMs)
    }

    @Test
    fun parsesRiskFrameFromVehicleIntegrationSample() {
        // vehicle-integration assets 의 risk_frames.jsonl 한 줄과 같은 구조
        val json = JSONObject("""{"schema_version":"1.0","timestamp_ms":100,"risk_objects":[
            {"vehicle_id":"car_01","risk_level":"WARNING","reason_codes":["HARD_BRAKING"],"ttc_s":null,
             "risk_zones":[{"dt_ms":1000,"polygon":[{"x":-1.2,"y":44.8},{"x":1.2,"y":44.8},{"x":1.2,"y":51.8},{"x":-1.2,"y":51.8}]}]},
            {"vehicle_id":"car_03","risk_level":"UNKNOWN","reason_codes":["SENSOR_DROPOUT"],"ttc_s":2.5,"risk_zones":[]}]}""")
        val r = ContractJson.parseRiskFrame(json)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        val (a, b) = r.value.risks
        assertEquals(RiskLevel.WARNING, a.level)
        assertEquals(listOf("HARD_BRAKING"), a.reasonCodes)
        assertNull(a.ttcS)
        assertEquals(1000L, a.riskZones.single().dtMs)
        assertEquals(4, a.riskZones.single().polygon.size)
        assertEquals(RiskLevel.UNKNOWN, b.level)
        assertEquals(2.5f, b.ttcS!!, 1e-6f)
        assertTrue(a.sourceVehicleIds.isEmpty() && a.trajectoryMode == null) // 선택 필드 없음 = 정상
    }

    @Test
    fun unknownRiskLevelBecomesUnknownNotWarning() {
        val json = JSONObject("""{"schema_version":"1.0","timestamp_ms":1,"risk_objects":[
            {"vehicle_id":"A","risk_level":"DANGER","reason_codes":["X"]},
            {"vehicle_id":"B","risk_level":"warning","reason_codes":[]},
            {"vehicle_id":"C","risk_level":"UNKNOWN"}]}""")
        val r = ContractJson.parseRiskFrame(json)
        assertEquals(RiskLevel.UNKNOWN, r.value.risks[0].level)
        assertEquals(RiskLevel.WARNING, r.value.risks[1].level)
        assertEquals(RiskLevel.UNKNOWN, r.value.risks[2].level)
        assertEquals(1, r.warnings.size) // 'DANGER' 만 경고, 명시적 UNKNOWN 은 정상
    }

    @Test
    fun roundTripAllFrames() {
        val f = ContractJson.parseVehicleFrame(vehicleJson).value
        assertEquals(f, ContractJson.parseVehicleFrame(ContractJson.toJson(f)).value)
        val pf = ContractJson.parsePredictionFrame(JSONObject("""{"schema_version":"1.0","timestamp_ms":5,"predictions":[{"vehicle_id":"a","trajectories":[{"mode":"lane_keep","confidence":null,"points":[{"dt_ms":500,"x":1.0,"y":2.0,"heading_rad":1.5708}]}]}]}""")).value
        assertEquals(pf, ContractJson.parsePredictionFrame(ContractJson.toJson(pf)).value)
    }

    @Test
    fun riskExportWithoutExtensionsHasOnlyContractFields() {
        val r = ContractJson.parseRiskFrame(JSONObject("""{"schema_version":"1.0","timestamp_ms":1,"risk_objects":[
            {"vehicle_id":"A","risk_level":"WATCH","reason_codes":["PATH_OVERLAP"],"ttc_s":null,"risk_zones":[],
             "source_vehicle_ids":["T"],"trajectory_mode":"lane_change_left"}]}""")).value
        val obj = ContractJson.toJson(r, includeExtensions = false).getJSONArray("risk_objects").getJSONObject(0)
        assertEquals(setOf("vehicle_id", "risk_level", "reason_codes", "ttc_s", "risk_zones"), obj.keys().asSequence().toSet())
        val ext = ContractJson.toJson(r).getJSONArray("risk_objects").getJSONObject(0)
        assertTrue(ext.has("source_vehicle_ids") && ext.has("trajectory_mode"))
    }
}

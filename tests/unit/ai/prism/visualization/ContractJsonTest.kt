package ai.prism.visualization

import ai.prism.visualization.model.ContractJson
import ai.prism.visualization.model.RiskLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractJsonTest {

    private val vehicleJson = """
        {"timestamp_ms": 1700000000000, "ego_id": "ego", "vehicles": [
          {"id":"ego","x":0,"y":0,"vx":0,"vy":27,"heading_rad":1.5708,"length_m":4.6,"width_m":1.9,"lane_id":2},
          {"id":"R1","x":3.5,"y":12.0,"vx":-0.5,"vy":27.5,"heading_rad":1.589,"length_m":4.6,"width_m":1.9,"lane_id":3}
        ]}
    """.trimIndent()

    @Test
    fun parsesVehicleFrameV1() {
        val r = ContractJson.parseVehicleFrame(vehicleJson)
        assertTrue(r.warnings.isEmpty())
        val f = r.value
        assertEquals(1700000000000L, f.timestampMs)
        assertEquals("ego", f.egoId)
        assertEquals(2, f.vehicles.size)
        val v = f.vehicles[1]
        assertEquals("R1", v.id); assertEquals(3.5f, v.x); assertEquals(12f, v.y)
        assertEquals(3, v.laneId)
    }

    @Test
    fun skipsBrokenVehiclesInsteadOfCrashing() {
        val json = """
            {"timestamp_ms": 1, "ego_id": "ego", "vehicles": [
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
    fun unknownRiskLevelBecomesUnknownNotWarning() {
        val json = JSONObject("""{"timestamp_ms":1,"risks":[
            {"vehicle_id":"A","level":"DANGER","cause_codes":["X"]},
            {"vehicle_id":"B","level":"warning","cause_codes":[]},
            {"vehicle_id":"C","level":"UNKNOWN"}]}""")
        val r = ContractJson.parseRiskFrame(json)
        assertEquals(RiskLevel.UNKNOWN, r.value.risks[0].level)
        assertEquals(RiskLevel.WARNING, r.value.risks[1].level)
        assertEquals(RiskLevel.UNKNOWN, r.value.risks[2].level)
        assertEquals(1, r.warnings.size) // 'DANGER' 만 경고, 명시적 UNKNOWN 은 정상
    }

    @Test
    fun roundTripVehicleFrame() {
        val f = ContractJson.parseVehicleFrame(vehicleJson).value
        val back = ContractJson.parseVehicleFrame(ContractJson.toJson(f)).value
        assertEquals(f, back)
    }
}

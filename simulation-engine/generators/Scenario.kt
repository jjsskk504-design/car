package com.prism.simulation.generators

import com.prism.simulation.adapters.SIM_EVENTS
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path

val OUTPUT_EVENTS = setOf("sensor_dropout", "sensor_noise")

data class VehicleSpec(
    val id: String, val lane: Int, val x: Double, val speed: Double,
    val targetSpeed: Double? = null, val lengthM: Double? = null, val widthM: Double? = null,
)

/** value 는 YAML 에 적힌 숫자 그대로(Int 또는 Double) 둔다: 매니페스트에 원래 표기로 남기기 위함. */
data class EventSpec(
    val tS: Double, val tEndS: Double?, val type: String, val vehicle: String,
    val value: Number?, val note: String?, val headingStdRad: Double?,
)

data class Scenario(
    val name: String, val description: String, val seed: Long, val durationS: Double, val stepMs: Int,
    val lanes: Int, val egoId: String, val ego: VehicleSpec, val vehicles: List<VehicleSpec>, val events: List<EventSpec>,
) {
    val vehicleIds get() = listOf(egoId) + vehicles.map { it.id }
}

private val REQUIRED = listOf("name", "seed", "duration_s", "step_ms", "lanes", "ego")

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap() = this as Map<String, Any?>
private fun Map<String, Any?>.num(k: String) = (get(k) as Number?)?.toDouble()

private fun vehicleSpec(m: Map<String, Any?>, id: String) = VehicleSpec(
    id = id, lane = (m["lane"] as Number).toInt(), x = m.num("x")!!, speed = m.num("speed")!!,
    targetSpeed = m.num("target_speed"), lengthM = m.num("length_m"), widthM = m.num("width_m"),
)

/** 시나리오 YAML 을 읽고 검증한다(필수 키, id 중복, 이벤트 대상, 자차 누락 금지). */
fun load(path: Path): Scenario {
    val raw = Files.newBufferedReader(path, Charsets.UTF_8).use { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(it) }.asMap()
    val missing = REQUIRED.filter { it !in raw }
    require(missing.isEmpty()) { "$path: 필수 키 누락 $missing" }
    val egoId = raw["ego_id"] as String? ?: "ego"
    val vehicles = (raw["vehicles"] as List<*>? ?: emptyList<Any>()).map { it.asMap().let { m -> vehicleSpec(m, m["id"] as String) } }
    val ids = listOf(egoId) + vehicles.map { it.id }
    require(ids.toSet().size == ids.size) { "$path: 차량 id 중복" }
    val events = (raw["events"] as List<*>? ?: emptyList<Any>()).map { it.asMap() }.map { e ->
        val type = e["type"] as String
        val vehicle = e["vehicle"] as String
        require(type in SIM_EVENTS + OUTPUT_EVENTS) { "$path: 알 수 없는 이벤트 $type" }
        require(vehicle in ids) { "$path: 이벤트 대상 없음 $vehicle" }
        require(!(type == "sensor_dropout" && vehicle == egoId)) { "$path: 자차는 누락시킬 수 없음" }
        EventSpec(e.num("t_s")!!, e.num("t_end_s"), type, vehicle, e["value"] as Number?, e["note"]?.toString(), e.num("heading_std_rad"))
    }
    return Scenario(
        name = raw["name"] as String, description = raw["description"] as String? ?: "",
        seed = (raw["seed"] as Number).toLong(), durationS = raw.num("duration_s")!!,
        stepMs = (raw["step_ms"] as Number).toInt(), lanes = (raw["lanes"] as Number).toInt(),
        egoId = egoId, ego = vehicleSpec(raw["ego"].asMap(), egoId), vehicles = vehicles, events = events,
    )
}

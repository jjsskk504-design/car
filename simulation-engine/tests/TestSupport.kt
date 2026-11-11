package com.prism.simulation

import com.prism.simulation.generators.RunResult
import com.prism.simulation.generators.load
import com.prism.simulation.generators.run
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.test.assertTrue
import kotlin.test.fail

val ROOT: Path = Path.of("").toAbsolutePath()
val SCENARIOS: List<Path> = Files.list(ROOT.resolve("scenarios")).use { s -> s.filter { it.extension == "yaml" }.sorted().toList() }

/** 시나리오 7종 실행 결과 (테스트 사이에서 한 번만 계산). 키 = 파일 이름(확장자 제외) */
val RESULTS: Map<String, RunResult> by lazy { SCENARIOS.associate { it.nameWithoutExtension to run(load(it)) } }

fun JsonObject.vehicles() = getValue("vehicles").jsonArray.map { it.jsonObject }
fun JsonObject.d(k: String) = getValue(k).jsonPrimitive.double
fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int
fun JsonObject.s(k: String) = (getValue(k) as JsonPrimitive).content

/**
 * schema/vehicle_frame.v1.schema.json 의 규칙을 그대로 검사한다
 * (필수 필드, const, 타입, heading 범위, 크기 > 0). 공통 contracts/ 스키마가 생기면 그것으로 다시 검증.
 */
fun assertVehicleFrameV1(f: JsonObject) {
    fun str(e: JsonElement?) = e is JsonPrimitive && e.isString && e.content.isNotEmpty()
    fun num(e: JsonElement?) = e is JsonPrimitive && !e.isString && e.content.toDoubleOrNull() != null
    for (k in listOf("schema_version", "timestamp_ms", "coordinate_system", "ego_id", "vehicles")) assertTrue(k in f, "필수 필드 없음 $k")
    assertTrue(f.s("schema_version") == "1.0" && f.s("coordinate_system") == "ego_local_m")
    val t = (f["timestamp_ms"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: fail("timestamp_ms 는 정수")
    assertTrue(t >= 0 && str(f["ego_id"]))
    val vs = f.vehicles()
    assertTrue(vs.isNotEmpty())
    for (v in vs) {
        assertTrue(str(v["id"]) && str(v["lane_id"]), "$v")
        for (k in listOf("x", "y", "vx", "vy", "heading_rad", "length_m", "width_m")) assertTrue(num(v[k]), "$k 는 숫자: $v")
        assertTrue(v.d("heading_rad") in -3.14159266..3.14159266, "heading 범위: $v")
        assertTrue(v.d("length_m") > 0 && v.d("width_m") > 0, "크기 > 0: $v")
    }
}

package com.prism.simulation.exporters

import com.prism.simulation.generators.RunResult
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** NaN/Inf 가 파일에 들어가는 것을 막는다 (kotlinx 는 그대로 "NaN" 을 써 버리므로 직접 검사). */
private fun requireFinite(e: JsonElement) {
    when (e) {
        is JsonObject -> e.values.forEach(::requireFinite)
        is JsonArray -> e.forEach(::requireFinite)
        is JsonPrimitive -> if (!e.isString) e.doubleOrNull?.let { require(it.isFinite()) { "NaN/Inf 는 출력할 수 없음: $e" } }
    }
}

fun writeJsonl(path: Path, rows: List<JsonElement>) {
    rows.forEach(::requireFinite)
    Files.writeString(path, rows.joinToString("") { "$it\n" }, Charsets.UTF_8)
}

/** results -> outDir/<name>.jsonl, <name>.ego_pose.jsonl, scenario_manifest.json */
fun writeOutputs(outDir: Path, results: List<RunResult>): Path {
    Files.createDirectories(outDir)
    for (r in results) {
        writeJsonl(outDir.resolve(r.manifest.str("file")), r.frames)
        writeJsonl(outDir.resolve(r.manifest.str("ego_pose_file")), r.poses)
    }
    val manifest = buildJsonObject {
        put("schema_version", "1.0")
        putJsonArray("scenarios") { results.forEach { add(it.manifest) } }
    }
    requireFinite(manifest)
    Files.writeString(outDir.resolve("scenario_manifest.json"), pretty.encodeToString(JsonElement.serializer(), manifest), Charsets.UTF_8)
    return outDir
}

fun readJsonl(path: Path): List<JsonObject> =
    Files.readAllLines(path, Charsets.UTF_8).filter { it.isNotBlank() }.map { Json.parseToJsonElement(it) as JsonObject }

fun JsonObject.str(key: String) = (getValue(key) as JsonPrimitive).content

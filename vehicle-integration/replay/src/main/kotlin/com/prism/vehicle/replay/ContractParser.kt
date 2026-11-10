package com.prism.vehicle.replay

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** JSON / JSONL 파싱. 실패는 예외가 아니라 줄 번호가 붙은 오류 목록으로 돌려준다. */
object ContractParser {
    // v1.x 에서 필드가 추가되어도 읽을 수 있게 모르는 키는 무시. 누락된 필수 필드는 오류.
    val json = Json { ignoreUnknownKeys = true }

    fun parseManifest(text: String): Parsed<ScenarioManifest> =
        parseOne(text, ScenarioManifest.serializer(), "scenario_manifest.json")

    fun parseVehicleFrames(text: String, source: String = "vehicle_frames.jsonl") =
        parseLines(text, VehicleFrame.serializer(), source)

    fun parsePredictionFrames(text: String, source: String = "prediction_frames.jsonl") =
        parseLines(text, PredictionFrame.serializer(), source)

    fun parseRiskFrames(text: String, source: String = "risk_frames.jsonl") =
        parseLines(text, RiskFrame.serializer(), source)

    /** 단일 .json 파일: 프레임 하나({...}) 또는 프레임 배열([...]) 둘 다 받는다. (지시서 예: prediction_frame.json) */
    fun parsePredictionJson(text: String, source: String = "prediction_frames.json") =
        parseJsonFile(text, PredictionFrame.serializer(), source)

    fun parseRiskJson(text: String, source: String = "risk_frames.json") =
        parseJsonFile(text, RiskFrame.serializer(), source)

    private fun <T> parseJsonFile(text: String, serializer: KSerializer<T>, source: String): Parsed<List<T>> =
        if (text.trimStart().startsWith("[")) parseOne(text, ListSerializer(serializer), source)
        else parseOne(text, serializer, source).let { Parsed(it.value?.let(::listOf), it.issues) }

    private fun <T> parseOne(text: String, serializer: KSerializer<T>, source: String): Parsed<T> =
        try {
            Parsed(json.decodeFromString(serializer, text), emptyList())
        } catch (e: Exception) {
            Parsed(null, listOf(Issue(Severity.ERROR, source, "JSON 파싱 실패: ${e.message?.lineSequence()?.first()}")))
        }

    private fun <T> parseLines(text: String, serializer: KSerializer<T>, source: String): Parsed<List<T>> {
        val items = mutableListOf<T>()
        val issues = mutableListOf<Issue>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            try {
                items += json.decodeFromString(serializer, line)
            } catch (e: Exception) {
                issues += Issue(Severity.ERROR, "$source:${index + 1}", "JSON 파싱 실패: ${e.message?.lineSequence()?.first()}")
            }
        }
        return Parsed(items, issues)
    }
}

data class Parsed<T>(val value: T?, val issues: List<Issue>)

enum class Severity { ERROR, WARNING }

data class Issue(val severity: Severity, val where: String, val message: String) {
    override fun toString() = "[$severity] $where: $message"
}

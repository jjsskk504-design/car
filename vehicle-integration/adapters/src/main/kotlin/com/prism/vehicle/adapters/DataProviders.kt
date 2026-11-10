package com.prism.vehicle.adapters

import com.prism.vehicle.replay.ContractParser
import com.prism.vehicle.replay.Issue
import com.prism.vehicle.replay.Parsed
import com.prism.vehicle.replay.PredictionFrame
import com.prism.vehicle.replay.RiskFrame
import com.prism.vehicle.replay.ScenarioManifest
import com.prism.vehicle.replay.ScenarioTimeline
import com.prism.vehicle.replay.Severity
import com.prism.vehicle.replay.VehicleFrame

/*
 * 엔진별 데이터 경계. Python 엔진(01·02·03)은 Android 에서 실행하지 않고, 미리 생성된 JSON 파일로만 연결한다.
 *   03 prism-simulation-engine → ScenarioDataProvider  (scenario_manifest.json, vehicle_frames.jsonl)
 *   01 prism-prediction-engine → PredictionDataProvider (prediction_frames.jsonl)
 *   02 prism-risk-engine       → RiskDataProvider       (risk_frames.jsonl)
 */

interface ScenarioDataProvider {
    fun scenarioIds(): List<String>
    fun manifest(scenarioId: String): Parsed<ScenarioManifest>
    fun vehicleFrames(scenarioId: String): Parsed<List<VehicleFrame>>
}

interface PredictionDataProvider {
    fun predictionFrames(scenarioId: String): Parsed<List<PredictionFrame>>
}

interface RiskDataProvider {
    fun riskFrames(scenarioId: String): Parsed<List<RiskFrame>>
}

/** 파일 시스템/assets 어디서든 읽을 수 있게 읽기 함수만 받는다. 파일이 없으면 null. */
fun interface TextSource {
    fun read(path: String): String?
}

/**
 * scenarios/<id>/{scenario_manifest.json, vehicle_frames.jsonl, prediction_frames.jsonl, risk_frames.jsonl}
 * 배치를 읽는 세 Provider 의 기본 구현. 시나리오 목록은 scenarios/index.json(문자열 배열).
 */
class JsonFileProviders(private val source: TextSource, private val root: String = "scenarios") :
    ScenarioDataProvider, PredictionDataProvider, RiskDataProvider {

    override fun scenarioIds(): List<String> {
        val text = source.read("$root/index.json") ?: return emptyList()
        return runCatching { ContractParser.json.decodeFromString<List<String>>(text) }.getOrDefault(emptyList())
    }

    override fun manifest(scenarioId: String) =
        read(scenarioId, "scenario_manifest.json", required = true) { ContractParser.parseManifest(it) }

    override fun vehicleFrames(scenarioId: String) =
        read(scenarioId, "vehicle_frames.jsonl", required = true) { ContractParser.parseVehicleFrames(it) }

    // 예측·위험 파일은 없어도 차량 재생은 가능하다(해당 엔진 미도착). 경고로만 남긴다.
    override fun predictionFrames(scenarioId: String) =
        read(scenarioId, "prediction_frames.jsonl", required = false) { ContractParser.parsePredictionFrames(it) }

    override fun riskFrames(scenarioId: String) =
        read(scenarioId, "risk_frames.jsonl", required = false) { ContractParser.parseRiskFrames(it) }

    private fun <T> read(id: String, file: String, required: Boolean, parse: (String) -> Parsed<T>): Parsed<T> {
        val text = source.read("$root/$id/$file")
            ?: return Parsed(null, listOf(Issue(if (required) Severity.ERROR else Severity.WARNING, "$id/$file", "파일 없음")))
        return parse(text)
    }
}

/** 세 Provider 결과를 하나의 타임라인으로 합친다. 실패해도 예외 대신 issues 로 보고. */
class ScenarioLoader(
    private val scenarios: ScenarioDataProvider,
    private val predictions: PredictionDataProvider,
    private val risks: RiskDataProvider,
) {
    fun load(scenarioId: String): LoadResult {
        val manifest = scenarios.manifest(scenarioId)
        val vehicles = scenarios.vehicleFrames(scenarioId)
        val prediction = predictions.predictionFrames(scenarioId)
        val risk = risks.riskFrames(scenarioId)
        val parseIssues = manifest.issues + vehicles.issues + prediction.issues + risk.issues
        val m = manifest.value ?: return LoadResult.Failed(scenarioId, parseIssues)
        val timeline = ScenarioTimeline.build(
            m, vehicles.value.orEmpty(), prediction.value.orEmpty(), risk.value.orEmpty(), parseIssues,
        )
        return if (timeline.isPlayable) LoadResult.Ready(timeline) else LoadResult.Failed(scenarioId, timeline.issues)
    }
}

sealed interface LoadResult {
    data class Ready(val timeline: ScenarioTimeline) : LoadResult
    data class Failed(val scenarioId: String, val issues: List<Issue>) : LoadResult
}

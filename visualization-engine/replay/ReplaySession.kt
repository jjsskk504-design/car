package ai.prism.visualization.replay

import ai.prism.visualization.model.CauseCatalog
import ai.prism.visualization.model.ContractJson
import ai.prism.visualization.model.PredictionFrame
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.rendering.FrameInterpolator
import org.json.JSONArray
import org.json.JSONObject

/** 한 시점의 세 계약 프레임 묶음. prediction / risk 는 누락될 수 있다. */
data class ReplayFrame(
    val vehicle: VehicleFrame,
    val prediction: PredictionFrame?,
    val risk: RiskFrame?,
)

/** 차량별 위험 등급 변화 이벤트(리플레이 "원인 확인"용). tMs 는 세션 시작 기준 상대 시간. */
data class RiskEvent(
    val tMs: Long,
    val vehicleId: String,
    val from: RiskLevel,
    val to: RiskLevel,
    val cause: String,
    val sourceVehicleIds: List<String>,
)

/** 정차 후 리플레이할 기록. frames 는 timestamp 오름차순이어야 한다. */
data class ReplaySession(
    val id: String,
    val title: String,
    val description: String,
    val road: RoadConfig,
    val frames: List<ReplayFrame>,
) {
    init {
        require(frames.isNotEmpty()) { "frames is empty" }
        require(frames.zipWithNext().all { (a, b) -> a.vehicle.timestampMs < b.vehicle.timestampMs }) {
            "frames must be strictly increasing by timestamp"
        }
    }

    val startMs: Long get() = frames.first().vehicle.timestampMs
    val durationMs: Long get() = frames.last().vehicle.timestampMs - startMs

    /** 상대 시간 tMs 이하의 마지막 프레임 인덱스. */
    fun indexAt(tMs: Long): Int {
        val abs = startMs + tMs.coerceIn(0, durationMs)
        var lo = 0; var hi = frames.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (frames[mid].vehicle.timestampMs <= abs) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** 표시용 프레임: 차량은 보간, 예측·위험은 직전 프레임 값(새 정보를 만들어내지 않음). */
    fun frameAt(tMs: Long, interpolate: Boolean = true): ReplayFrame {
        val i = indexAt(tMs)
        val a = frames[i]
        if (!interpolate || i == frames.lastIndex) return a
        val b = frames[i + 1]
        val abs = startMs + tMs.coerceIn(0, durationMs)
        val alpha = (abs - a.vehicle.timestampMs).toFloat() / (b.vehicle.timestampMs - a.vehicle.timestampMs)
        return a.copy(vehicle = FrameInterpolator.lerp(a.vehicle, b.vehicle, alpha).copy(timestampMs = a.vehicle.timestampMs))
    }

    /** 프레임별 최고 등급(UNKNOWN 은 WATCH 보다 낮은 우선순위). */
    fun overallLevels(): List<RiskLevel> = frames.map { f ->
        f.risk?.risks?.map { it.level }?.maxByOrNull { severity(it) } ?: RiskLevel.NORMAL
    }

    /** 위험이 한 번이라도 NORMAL 이 아니었던 차량(타임라인 행). */
    fun flaggedVehicleIds(): List<String> = frames.flatMap { f ->
        f.risk?.risks?.filter { it.level != RiskLevel.NORMAL }?.map { it.vehicleId }.orEmpty()
    }.distinct()

    fun levelOf(frameIndex: Int, vehicleId: String): RiskLevel =
        frames[frameIndex].risk?.risks?.firstOrNull { it.vehicleId == vehicleId }?.level ?: RiskLevel.NORMAL

    fun events(): List<RiskEvent> {
        val out = ArrayList<RiskEvent>()
        val last = HashMap<String, RiskLevel>()
        frames.forEach { f ->
            val t = f.vehicle.timestampMs - startMs
            val current = f.risk?.risks?.associateBy { it.vehicleId }.orEmpty()
            (last.keys + current.keys).forEach { id ->
                val before = last[id] ?: RiskLevel.NORMAL
                val r = current[id]
                val now = r?.level ?: RiskLevel.NORMAL
                if (now != before) {
                    out += RiskEvent(t, id, before, now,
                        r?.let { CauseCatalog.describe(it) } ?: "위험 해소", r?.sourceVehicleIds.orEmpty())
                }
                last[id] = now
            }
        }
        return out
    }

    companion object {
        fun severity(l: RiskLevel) = when (l) {
            RiskLevel.NORMAL -> 0; RiskLevel.UNKNOWN -> 1; RiskLevel.WATCH -> 2; RiskLevel.WARNING -> 3
        }
    }
}

/**
 * 리플레이 파일 포맷(시각화 엔진 데모·테스트용 잠정 포맷).
 * frames[] 의 각 원소가 계약 프레임 세 개를 그대로 담으므로, 통합 측은 실제 엔진 출력을 그대로 기록하면 된다.
 */
object ReplayJson {
    data class Parsed(val session: ReplaySession, val warnings: List<String>)

    fun parse(json: String): Parsed {
        val o = JSONObject(json)
        val warnings = ArrayList<String>()
        val meta = o.optJSONObject("scenario") ?: JSONObject()
        val arr = o.getJSONArray("frames")
        val frames = ArrayList<ReplayFrame>()
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            val v = ContractJson.parseVehicleFrame(f.getJSONObject("vehicle_frame"))
            warnings += v.warnings.map { "frame[$i] $it" }
            val p = f.optJSONObject("prediction_frame")?.let { ContractJson.parsePredictionFrame(it) }
            val r = f.optJSONObject("risk_frame")?.let { ContractJson.parseRiskFrame(it) }
            p?.warnings?.let { w -> warnings += w.map { "frame[$i] $it" } }
            r?.warnings?.let { w -> warnings += w.map { "frame[$i] $it" } }
            frames += ReplayFrame(v.value, p?.value, r?.value)
        }
        return Parsed(
            ReplaySession(
                id = meta.optString("id", "session"),
                title = meta.optString("title", "리플레이"),
                description = meta.optString("description", ""),
                road = ContractJson.parseRoad(o.optJSONObject("road")),
                frames = frames,
            ),
            warnings,
        )
    }

    /**
     * 팀 저장소 시나리오 형식을 읽는다: vehicle_frames.jsonl(필수) + prediction_frames.jsonl + risk_frames.jsonl.
     * 한 줄 = 계약 프레임 하나. timestamp_ms 가 같은 프레임끼리 묶고, 깨진 줄은 건너뛰며 경고에 남긴다.
     */
    fun fromJsonl(
        id: String,
        title: String,
        vehicleJsonl: String,
        predictionJsonl: String? = null,
        riskJsonl: String? = null,
        description: String = "",
        road: RoadConfig = RoadConfig(),
    ): Parsed {
        val warnings = ArrayList<String>()
        fun <T> lines(text: String?, name: String, parse: (JSONObject) -> ContractJson.ParseResult<T>): List<T> =
            text.orEmpty().lines().mapIndexedNotNull { i, line ->
                if (line.isBlank()) return@mapIndexedNotNull null
                runCatching { parse(JSONObject(line)) }.fold(
                    onSuccess = { r -> warnings += r.warnings.map { "$name:${i + 1} $it" }; r.value },
                    onFailure = { e -> warnings += "$name:${i + 1} 파싱 실패: ${e.message}"; null },
                )
            }
        val vehicles = lines(vehicleJsonl, "vehicle_frames", ContractJson::parseVehicleFrame)
        val predictions = lines(predictionJsonl, "prediction_frames", ContractJson::parsePredictionFrame).associateBy { it.timestampMs }
        val risks = lines(riskJsonl, "risk_frames", ContractJson::parseRiskFrame).associateBy { it.timestampMs }
        val frames = vehicles.sortedBy { it.timestampMs }.distinctBy { it.timestampMs }
            .map { v -> ReplayFrame(v, predictions[v.timestampMs], risks[v.timestampMs]) }
        return Parsed(ReplaySession(id, title, description, road, frames), warnings)
    }

    fun toJson(s: ReplaySession): String = JSONObject().apply {
        put("format", "prism.visualization.replay/0.1")
        put("scenario", JSONObject().apply {
            put("id", s.id); put("title", s.title); put("description", s.description)
        })
        put("road", ContractJson.toJson(s.road))
        put("frames", JSONArray().apply {
            s.frames.forEach { f ->
                put(JSONObject().apply {
                    put("vehicle_frame", ContractJson.toJson(f.vehicle))
                    f.prediction?.let { put("prediction_frame", ContractJson.toJson(it)) }
                    f.risk?.let { put("risk_frame", ContractJson.toJson(it)) }
                })
            }
        })
    }.toString()
}

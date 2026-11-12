package ai.prism.visualization.rendering

import ai.prism.visualization.model.CauseCatalog
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RoadConfig
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 위험 분석 패널용 요약(그리기와 무관한 순수 로직 → JVM 단위 테스트 가능).
 *
 * 원칙: 입력 프레임에 있는 값만 쓴다. 거리·위치는 VehicleFrame 좌표에서 계산한 값이고,
 * TTC 는 RiskFrame 이 ttc_s 를 준 경우에만 보여 준다. 확률·속도 추정치를 만들어 내지 않는다.
 */

/** 데이터 수신 상태. 위에서부터 우선순위가 높다. */
enum class DataHealth(val label: String) {
    NO_VEHICLE_DATA("차량 데이터 없음"),
    STALE("데이터 지연"),
    OUT_OF_SYNC("위험 정보 시각 불일치"),
    NO_RISK_DATA("위험 정보 없음"),
    PARTIAL("일부 차량 관측 누락"),
    OK("정상 수신"),
}

data class SensorStatus(
    val health: DataHealth,
    /** VehicleFrame 나이(ms). 현재 시각을 모르면 null. */
    val dataAgeMs: Long?,
    /** 위치를 모르는(VehicleFrame 에 없는) 위험 대상 차량. */
    val missingVehicleIds: List<String>,
    /** 깨진 데이터로 제외된 차량 수. */
    val invalidVehicleCount: Int,
) {
    /** 운전자에게 보여 줄 한 줄 요약(판단의 한계를 숨기지 않는다). */
    val summary: String
        get() = when (health) {
            DataHealth.OK -> "정상 수신"
            DataHealth.PARTIAL -> "관측 누락 ${missingVehicleIds.size}대 · 해당 차량 판단 보류"
            DataHealth.STALE -> "데이터 지연 %.1f초 · 위험 표시 보류".format((dataAgeMs ?: 0L) / 1000f)
            DataHealth.OUT_OF_SYNC -> "위험 정보 시각 불일치 · 판단 보류"
            DataHealth.NO_RISK_DATA -> "위험 정보 없음 · 위험 판단 불가"
            DataHealth.NO_VEHICLE_DATA -> "차량 데이터 없음 · 판단 불가"
        }
}

/** 위험 차량 한 대의 요약. */
data class RiskItem(
    val vehicleId: String,
    val level: DisplayLevel,
    /** 원인 문구(reason_codes → 한국어). */
    val reasonText: String,
    val reasonCodes: List<String>,
    /** 이 위험을 유발한 차량(확인된 관계만). */
    val sourceVehicleIds: List<String>,
    /** 자차 기준 상대 위치 문구(VehicleFrame 좌표 기준). 위치를 모르면 null. */
    val relativePosition: String?,
    /** 자차 기준 앞뒤 거리(m, 좌표 차이). 위치를 모르면 null. */
    val longitudinalGapM: Float?,
    /** RiskFrame 이 준 TTC(초). 없으면 null(만들지 않음). */
    val ttcS: Float?,
    /** 자차와 겹치는 충돌 위험 영역이 있는지. */
    val conflictsWithEgo: Boolean,
    /** 위치 관측이 안 된 차량인지. */
    val unobserved: Boolean,
)

data class RiskAnalysis(
    val overall: DisplayLevel,
    val warningCount: Int,
    val watchCount: Int,
    val unknownCount: Int,
    /** 우선순위 순(경고 > 주의 > 판단 보류, 같은 등급이면 충돌 영역 있음 > 가까운 차량). */
    val items: List<RiskItem>,
    /** 연쇄 위험 경로. 각 원소는 원인 → … → 영향받는 차량 순서의 차량 ID 목록. */
    val chains: List<List<String>>,
    val sensor: SensorStatus,
) {
    val primary: RiskItem? get() = items.firstOrNull()
}

object RiskAnalyzer {

    fun analyze(scene: Scene, riskFrame: RiskFrame?, road: RoadConfig = RoadConfig()): RiskAnalysis {
        val others = scene.vehicles.filter { !it.isEgo }
        val risks = riskFrame?.risks?.associateBy { it.vehicleId }.orEmpty()
        val conflictIds = scene.regions.filter { it.kind == RegionKind.CONFLICT }.map { it.vehicleId }.toSet()

        val items = ArrayList<RiskItem>()
        for (v in others) {
            if (v.level == DisplayLevel.NORMAL) continue
            val r = risks[v.id]
            items += RiskItem(
                vehicleId = v.id, level = v.level,
                reasonText = r?.let { CauseCatalog.describe(it.copy(ttcS = null)) } ?: "원인 정보 없음",
                reasonCodes = r?.reasonCodes.orEmpty(),
                sourceVehicleIds = scene.relations.filter { it.toId == v.id }.map { it.fromId },
                relativePosition = relativePosition(v.center.x, v.center.y, road),
                longitudinalGapM = v.center.y,
                ttcS = r?.ttcS,
                conflictsWithEgo = v.id in conflictIds,
                unobserved = false,
            )
        }
        for (g in scene.ghosts) {
            val r = risks[g.vehicleId]
            items += RiskItem(
                vehicleId = g.vehicleId, level = DisplayLevel.UNKNOWN,
                reasonText = g.causeText, reasonCodes = r?.reasonCodes.orEmpty(),
                sourceVehicleIds = emptyList(),
                relativePosition = g.anchor?.let { "추정 " + relativePosition(it.x, it.y, road) },
                longitudinalGapM = null, ttcS = null, conflictsWithEgo = false, unobserved = true,
            )
        }
        items.sortWith(
            compareByDescending<RiskItem> { it.level.severity }
                .thenByDescending { it.conflictsWithEgo }
                .thenBy { it.longitudinalGapM?.let { g -> abs(g) } ?: Float.MAX_VALUE },
        )

        return RiskAnalysis(
            overall = scene.highestLevel,
            warningCount = scene.count(DisplayLevel.WARNING),
            watchCount = scene.count(DisplayLevel.WATCH),
            unknownCount = scene.count(DisplayLevel.UNKNOWN),
            items = items,
            chains = chains(scene.relations),
            sensor = sensorStatus(scene),
        )
    }

    /** 관계 그래프에서 원인(들어오는 간선 없음)부터 끝(나가는 간선 없음)까지의 경로. 순환은 끊는다. */
    fun chains(relations: List<SceneRelation>): List<List<String>> {
        if (relations.isEmpty()) return emptyList()
        val next = relations.groupBy({ it.fromId }, { it.toId })
        val targets = relations.map { it.toId }.toSet()
        val roots = relations.map { it.fromId }.distinct().filter { it !in targets }
            .ifEmpty { listOf(relations.first().fromId) }
        val out = ArrayList<List<String>>()
        fun walk(path: List<String>) {
            val nexts = next[path.last()].orEmpty().filter { it !in path }
            if (nexts.isEmpty()) { if (path.size >= 2) out += path; return }
            nexts.forEach { walk(path + it) }
        }
        roots.forEach { walk(listOf(it)) }
        return out
    }

    fun sensorStatus(scene: Scene): SensorStatus {
        val kinds = scene.notices.map { it.kind }.toSet()
        val invalid = scene.notices.firstOrNull { it.kind == NoticeKind.INVALID_VEHICLE_DATA }
            ?.text?.let { Regex("(\\d+)건").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        val missing = scene.ghosts.map { it.vehicleId } +
            scene.vehicles.filter { !it.isEgo && it.level == DisplayLevel.UNKNOWN && !it.stale && "SENSOR_DROPOUT" in it.reasonCodes }.map { it.id }
        val health = when {
            NoticeKind.NO_VEHICLE_DATA in kinds -> DataHealth.NO_VEHICLE_DATA
            NoticeKind.DATA_STALE in kinds -> DataHealth.STALE
            NoticeKind.RISK_OUT_OF_SYNC in kinds -> DataHealth.OUT_OF_SYNC
            NoticeKind.NO_RISK_DATA in kinds -> DataHealth.NO_RISK_DATA
            missing.isNotEmpty() -> DataHealth.PARTIAL
            else -> DataHealth.OK
        }
        return SensorStatus(health, scene.dataAgeMs, missing.distinct(), invalid)
    }

    /** 자차 기준 상대 위치 문구. 차로는 횡방향 좌표 ÷ 차선 폭으로 반올림, 거리는 좌표 차이(m). */
    fun relativePosition(x: Float, y: Float, road: RoadConfig = RoadConfig()): String {
        val laneOffset = (x / road.laneWidthM).roundToInt()
        val lane = when {
            laneOffset == 0 -> "같은 차로"
            laneOffset < 0 -> "왼쪽 ${-laneOffset}차로"
            else -> "오른쪽 ${laneOffset}차로"
        }
        val d = abs(y).roundToInt()
        val dir = when {
            d <= 2 -> "나란히"
            y > 0 -> "전방 ${d}m"
            else -> "후방 ${d}m"
        }
        return "$lane · $dir"
    }
}

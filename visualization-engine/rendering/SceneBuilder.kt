package ai.prism.visualization.rendering

import ai.prism.visualization.model.CauseCatalog
import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.PredictionFrame
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleRisk
import kotlin.math.abs

/**
 * DRIVING: 주행 중. 위험 객체만 강조, 상세 원인·ID·전체 후보 궤적은 숨긴다.
 * DETAIL: 정차 후(리플레이). 모든 후보 궤적, 차량 ID, 원인 문구를 보여준다.
 */
enum class DisplayMode { DRIVING, DETAIL }

enum class NoticeKind { NO_VEHICLE_DATA, INVALID_VEHICLE_DATA, NO_RISK_DATA, DATA_STALE, RISK_OUT_OF_SYNC, NO_PREDICTION_DATA }

data class SceneNotice(val kind: NoticeKind, val text: String)

data class SceneVehicle(
    val id: String,
    val isEgo: Boolean,
    val center: Point2,
    val headingRad: Float,
    val lengthM: Float,
    val widthM: Float,
    /** 탑뷰 그래픽 종류(치수로 추정). */
    val bodyType: VehicleBodyType,
    /** 실제 치수 기준 꼭짓점(화면 표시는 VehicleRenderer 가 폭을 보정해 다시 계산). */
    val corners: List<Point2>,
    val nose: List<Point2>,
    val level: DisplayLevel,
    /** 위험 판정 엔진이 보낸 원래 등급(표시 등급과 다를 수 있음: 지연·동기화 실패 시 판단 보류). */
    val reportedLevel: RiskLevel?,
    val laneId: String?,
    val causeText: String?,
    val reasonCodes: List<String>,
    /** 자차 위험과 연결된 연쇄 관계에서 위험을 시작한 차량(원인). */
    val isSource: Boolean,
    /** 데이터 지연·시각 불일치로 위치·위험 정보를 믿을 수 없는 상태(UNKNOWN 과 다르게 회색으로 흐리게 표시). */
    val stale: Boolean = false,
)

/** VehicleFrame 에는 없지만 RiskFrame 에 등장하는 차량(예: 센서 누락). 항상 판단 보류로 표시한다. risk_zones 가 없으면 위치를 그리지 않고 상태 줄에만 집계한다. */
data class SceneGhost(val vehicleId: String, val region: List<Point2>, val anchor: Point2?, val causeText: String)

data class SceneTrajectory(
    val vehicleId: String,
    /** PredictionFrame trajectories[].mode (예: lane_keep, braking) */
    val mode: String,
    val points: List<Point2>,
    /** 위험 판단에 쓰인 궤적(굵게) 여부 */
    val emphasized: Boolean,
    val level: DisplayLevel,
)

/** RiskFrame risk_zones[] 하나(dtMs 시점 영역, 표시 기준으로 변환된 좌표). */
/**
 * 위험 영역 종류.
 * - OCCUPANCY: 미래 점유 영역(risk_zones 그대로). 옅은 채움 + 점선.
 * - CONFLICT : 같은 시점 자차 점유 영역과 겹치는 영역 = 충돌 위험 영역. 진한 채움 + 실선.
 */
enum class RegionKind { OCCUPANCY, CONFLICT }

/** RiskFrame risk_zones[] 하나(dtMs 시점 영역, 표시 기준으로 변환된 좌표). */
data class SceneRegion(
    val vehicleId: String,
    val polygon: List<Point2>,
    val level: DisplayLevel,
    val dtMs: Long = 0,
    val kind: RegionKind = RegionKind.OCCUPANCY,
)

data class SceneRelation(val fromId: String, val toId: String, val level: DisplayLevel)

data class Scene(
    val timestampMs: Long?,
    val mode: DisplayMode,
    val vehicles: List<SceneVehicle>,
    val ghosts: List<SceneGhost>,
    val trajectories: List<SceneTrajectory>,
    val regions: List<SceneRegion>,
    val relations: List<SceneRelation>,
    val notices: List<SceneNotice>,
    /** 현재 시각(nowMs) 기준 VehicleFrame 의 나이(ms). nowMs 를 주지 않으면 null. */
    val dataAgeMs: Long? = null,
    /** 자차 전방 속도(m/s, VehicleFrame 자차 vy). 값이 없거나 비정상이면 null. 도로 스크롤에만 쓴다. */
    val egoSpeedMps: Float? = null,
) {
    /** 데이터 지연 또는 RiskFrame 시각 불일치로 위험 표시를 보류 중인지. */
    val stale: Boolean
        get() = notices.any { it.kind == NoticeKind.DATA_STALE || it.kind == NoticeKind.RISK_OUT_OF_SYNC }

    val highestLevel: DisplayLevel
        get() = (vehicles.filter { !it.isEgo }.map { it.level } +
            ghosts.map { DisplayLevel.UNKNOWN }).maxByOrNull { it.severity } ?: DisplayLevel.NORMAL

    fun count(level: DisplayLevel) = vehicles.count { !it.isEgo && it.level == level } +
        if (level == DisplayLevel.UNKNOWN) ghosts.size else 0

    /** 접근성·테스트용 요약 문장. 예: "경고 1대(R), 주의 1대(T)" */
    fun summary(): String {
        val parts = listOf(DisplayLevel.WARNING, DisplayLevel.WATCH, DisplayLevel.UNKNOWN).mapNotNull { lv ->
            val ids = vehicles.filter { !it.isEgo && it.level == lv }.map { it.id } +
                (if (lv == DisplayLevel.UNKNOWN) ghosts.map { it.vehicleId } else emptyList())
            if (ids.isEmpty()) null else "${RiskStyle.of(lv).label} ${ids.size}대(${ids.joinToString(",")})"
        }
        return if (parts.isEmpty()) "강조할 위험 없음" else parts.joinToString(", ")
    }
}

/**
 * 예측 궤적 표시 기준.
 * - AS_GIVEN(기본): PredictionFrame 좌표(현재 시각 자차 좌표계에 고정된 지면 기준 미래 위치)를 그대로 그린다.
 *   도로 표식이 자차 주행거리만큼 흐르는 화면([ViewportConfig.scrollRoadWithEgo])과 같은 기준이라
 *   직진 차량의 경로는 차선을 따라 앞으로, 차선 변경 차량의 경로는 실제 진행 방향으로 그려진다.
 * - RELATIVE_TO_EGO: 자차 이동량(자차 속도 × dt)을 빼서 "나와의 상대 위치 변화"로 그린다.
 *   도로를 고정해 그릴 때만 쓴다. 도로가 흐르는 화면에서 쓰면 자차보다 느린 차량의 경로가 진행 방향과 반대(뒤)나
 *   옆으로 누워 그려져, 차량이 여러 차선을 가로질러 파고드는 것처럼 보인다.
 * 위험 영역(risk_zones)도 각 dt_ms 에 맞춰 같은 기준으로 변환하고, 충돌 영역 판정은 어느 기준이든
 * 같은 시점의 자차 위치와 겹치는지로 계산한다(결과 동일).
 */
enum class TrajectoryReference { RELATIVE_TO_EGO, AS_GIVEN }

data class SceneOptions(
    val trajectoryReference: TrajectoryReference = TrajectoryReference.AS_GIVEN,
    /** VehicleFrame 이 이 시간보다 오래되면 '데이터 지연'으로 보고 위험 강조를 보류한다. */
    val staleAfterMs: Long = 500,
    /** VehicleFrame 과 RiskFrame 시각 차이 허용치. */
    val riskSyncToleranceMs: Long = 300,
    /** 주행 모드에서 궤적·영역을 그릴 최대 차량 수(필요한 위험 객체만 강조). */
    val maxEmphasizedInDriving: Int = 3,
)

/**
 * 계약 프레임 → 그리기 독립적인 Scene. Android 의존성이 없어 JVM 단위 테스트가 가능하다.
 *
 * 안전 관련 표시 규칙
 * 1. 데이터가 지연되었거나 RiskFrame 시각이 맞지 않으면 WATCH/WARNING 을 UNKNOWN(판단 보류)으로 낮춘다.
 * 2. 위치를 모르는 차량(VehicleFrame 에 없음)은 등급과 무관하게 UNKNOWN 으로만 표시한다.
 * 3. RiskFrame 이 없으면 모든 차량을 중립으로 표시하고 안내 문구를 띄운다.
 */
object SceneBuilder {

    fun build(
        vehicleFrame: VehicleFrame?,
        predictionFrame: PredictionFrame?,
        riskFrame: RiskFrame?,
        mode: DisplayMode = DisplayMode.DRIVING,
        nowMs: Long? = null,
        options: SceneOptions = SceneOptions(),
        invalidVehicleCount: Int = 0,
    ): Scene {
        val notices = ArrayList<SceneNotice>()
        if (invalidVehicleCount > 0) notices += SceneNotice(NoticeKind.INVALID_VEHICLE_DATA, "일부 차량 데이터 오류 ${invalidVehicleCount}건 제외")

        if (vehicleFrame == null) {
            notices += SceneNotice(NoticeKind.NO_VEHICLE_DATA, "차량 데이터 없음")
            return Scene(null, mode, listOf(defaultEgo()), emptyList(), emptyList(), emptyList(), emptyList(), notices)
        }

        // 1) 차량 정리: 비정상 값·중복 ID 제거
        val seen = HashSet<String>()
        val vehicles = vehicleFrame.vehicles.filter { v ->
            val finite = listOf(v.x, v.y, v.headingRad, v.lengthM, v.widthM).all { it.isFinite() } && v.lengthM > 0 && v.widthM > 0
            finite && seen.add(v.id)
        }
        val dropped = vehicleFrame.vehicles.size - vehicles.size
        if (dropped > 0 && invalidVehicleCount == 0) notices += SceneNotice(NoticeKind.INVALID_VEHICLE_DATA, "일부 차량 데이터 오류 ${dropped}건 제외")

        // 2) 위험 신뢰 가능 여부
        val stale = nowMs != null && nowMs - vehicleFrame.timestampMs > options.staleAfterMs
        val dataAgeMs = nowMs?.let { it - vehicleFrame.timestampMs }
        if (stale) notices += SceneNotice(NoticeKind.DATA_STALE, "데이터 지연 %.1f초 · 위험 표시 보류".format((dataAgeMs ?: 0L) / 1000f))
        val outOfSync = riskFrame != null && abs(riskFrame.timestampMs - vehicleFrame.timestampMs) > options.riskSyncToleranceMs
        if (outOfSync) notices += SceneNotice(NoticeKind.RISK_OUT_OF_SYNC, "위험 정보 시각 불일치 · 판단 보류")
        if (riskFrame == null) notices += SceneNotice(NoticeKind.NO_RISK_DATA, "위험 정보 없음")
        val trustRisk = !stale && !outOfSync

        val risks: Map<String, VehicleRisk> = riskFrame?.risks?.associateBy { it.vehicleId }.orEmpty()

        fun displayOf(r: VehicleRisk?): DisplayLevel {
            if (r == null) return DisplayLevel.NORMAL
            val lv = r.level.toDisplay()
            return if (!trustRisk && (lv == DisplayLevel.WATCH || lv == DisplayLevel.WARNING)) DisplayLevel.UNKNOWN else lv
        }

        val egoId = vehicleFrame.egoId
        val sceneVehicles = vehicles.map { v ->
            val isEgo = v.id == egoId
            val r = if (isEgo) null else risks[v.id]
            val lv = if (isEgo) DisplayLevel.EGO else displayOf(r)
            SceneVehicle(
                id = v.id, isEgo = isEgo,
                center = Point2(v.x, v.y), headingRad = v.headingRad,
                lengthM = v.lengthM, widthM = v.widthM,
                bodyType = VehicleBodyType.classify(v.lengthM, v.widthM),
                corners = VehicleGeometry.corners(v.x, v.y, v.headingRad, v.lengthM, v.widthM),
                nose = VehicleGeometry.nose(v.x, v.y, v.headingRad, v.lengthM, v.widthM),
                level = lv, reportedLevel = r?.level, laneId = v.laneId,
                causeText = r?.let { if (lv == DisplayLevel.NORMAL) null else CauseCatalog.describe(it) },
                reasonCodes = r?.reasonCodes.orEmpty(),
                isSource = false, // 관계(relations)가 정해진 뒤 아래에서 지정
                stale = !trustRisk && !isEgo,
            )
        }.let { list -> if (list.none { it.isEgo }) list + defaultEgo(egoId) else list }

        // 예측 궤적·위험 영역 표시 기준(자차 상대 운동): 기준 시점 좌표에서 자차 이동량(dt × 자차 속도)을 뺀다
        val egoState = vehicles.firstOrNull { it.id == egoId }
        val (egoVx, egoVy) = if (options.trajectoryReference == TrajectoryReference.RELATIVE_TO_EGO && egoState != null &&
            egoState.vx.isFinite() && egoState.vy.isFinite()) egoState.vx to egoState.vy else 0f to 0f
        fun shift(p: Point2, dtMs: Long): Point2 { val s = dtMs / 1000f; return Point2(p.x - egoVx * s, p.y - egoVy * s) }
        fun shiftZone(z: ai.prism.visualization.model.RiskZone) = z.polygon.map { shift(it, z.dtMs) }

        val present = sceneVehicles.map { it.id }.toSet()
        val ghosts = risks.values.filter { it.vehicleId !in present }.map { r ->
            val zone = r.riskZones.minByOrNull { it.dtMs }
            val region = zone?.let { shiftZone(it) }.orEmpty()
            SceneGhost(
                vehicleId = r.vehicleId, region = region,
                anchor = region.takeIf { it.isNotEmpty() }?.let { centroid(it) },
                causeText = CauseCatalog.describe(r),
            )
        }

        // 3) 강조 대상 선정: 주행 모드에서는 심각도 순 상위 N 대만
        val ranked = sceneVehicles.filter { !it.isEgo && it.level.severity > 0 }
            .sortedByDescending { it.level.severity }
        val emphasized = (if (mode == DisplayMode.DRIVING) ranked.take(options.maxEmphasizedInDriving) else ranked)
            .map { it.id }.toSet()
        val levelById = sceneVehicles.associate { it.id to it.level }

        // 4) 위험 영역
        // 자차의 같은 시점 위치(표시 좌표): 상대 운동 표시면 항상 원점, 지면 표시면 자차 속도 × dt
        val egoLen = egoState?.lengthM ?: 4.6f
        val egoWid = egoState?.widthM ?: 1.9f
        val (trueVx, trueVy) = if (egoState != null && egoState.vx.isFinite() && egoState.vy.isFinite()) egoState.vx to egoState.vy else 0f to 0f
        fun egoFootprint(dtMs: Long): List<Point2> {
            val s = dtMs / 1000f
            val c = Point2((trueVx - egoVx) * s, (trueVy - egoVy) * s)
            return VehicleGeometry.corners(c.x, c.y, egoState?.headingRad ?: (Math.PI / 2).toFloat(), egoLen + 0.6f, egoWid + 0.3f)
        }
        val regions = if (!trustRisk) emptyList() else risks.values.filter { it.vehicleId in emphasized }
            .flatMap { r ->
                r.riskZones.sortedBy { it.dtMs }.map { z ->
                    val poly = shiftZone(z)
                    val kind = if (ConvexOverlap.overlaps(poly, egoFootprint(z.dtMs))) RegionKind.CONFLICT else RegionKind.OCCUPANCY
                    SceneRegion(r.vehicleId, poly, levelById.getValue(r.vehicleId), z.dtMs, kind)
                }
            }

        // 5) 궤적
        if (predictionFrame == null && mode == DisplayMode.DETAIL) {
            notices += SceneNotice(NoticeKind.NO_PREDICTION_DATA, "예측 궤적 없음")
        }
        val trajectories = ArrayList<SceneTrajectory>()
        predictionFrame?.predictions?.forEach { p ->
            val lv = levelById[p.vehicleId] ?: return@forEach
            if (lv == DisplayLevel.EGO) return@forEach
            // 위험 판단에 쓰인 궤적 mode(선택 필드 trajectory_mode)가 없거나 PredictionFrame 에 없으면 모든 궤적을 표시
            val chosen = risks[p.vehicleId]?.trajectoryMode?.takeIf { m -> p.trajectories.any { it.mode == m } }
            val show = when (mode) {
                DisplayMode.DRIVING -> p.vehicleId in emphasized
                DisplayMode.DETAIL -> true
            }
            if (!show) return@forEach
            p.trajectories.forEach { c ->
                if (c.points.isEmpty()) return@forEach
                val isChosen = chosen == null || chosen == c.mode
                // 주행 모드: 위험 판단에 쓰인 후보만 표시해 화면을 단순하게 유지
                if (mode == DisplayMode.DRIVING && chosen != null && !isChosen) return@forEach
                val from = sceneVehicles.first { it.id == p.vehicleId }.center
                trajectories += SceneTrajectory(
                    vehicleId = p.vehicleId, mode = c.mode,
                    points = listOf(from) + c.points.sortedBy { it.dtMs }.map { shift(Point2(it.x, it.y), it.dtMs) },
                    emphasized = isChosen && p.vehicleId in emphasized,
                    level = lv,
                )
            }
        }

        // 6) 연쇄 위험 관계(원인 → 위험 차량)
        val relations = if (!trustRisk) emptyList() else risks.values
            .filter { it.vehicleId in emphasized }
            .flatMap { r -> r.sourceVehicleIds.filter { it in present }.map { s -> SceneRelation(s, r.vehicleId, levelById.getValue(r.vehicleId)) } }

        // 원인 차량 표시는 자차 위험(강조 대상)과 실제로 연결된 관계의 시작 차량에만
        val sourceIds = relations.map { it.fromId }.toSet()
        val finalVehicles = if (sourceIds.isEmpty()) sceneVehicles else sceneVehicles.map { if (it.id in sourceIds) it.copy(isSource = true) else it }
        val egoSpeed = egoState?.vy?.takeIf { it.isFinite() }
        return Scene(vehicleFrame.timestampMs, mode, finalVehicles, ghosts, trajectories, regions, relations, notices, dataAgeMs, egoSpeed)
    }

    private fun defaultEgo(id: String = "ego"): SceneVehicle {
        val h = (Math.PI / 2).toFloat()
        return SceneVehicle(
            id, true, Point2(0f, 0f), h, 4.6f, 1.9f, VehicleBodyType.SEDAN,
            VehicleGeometry.corners(0f, 0f, h, 4.6f, 1.9f), VehicleGeometry.nose(0f, 0f, h, 4.6f, 1.9f),
            DisplayLevel.EGO, null, null, null, emptyList(), false,
        )
    }

    private fun centroid(ps: List<Point2>) = Point2(ps.map { it.x }.average().toFloat(), ps.map { it.y }.average().toFloat())
}

package ai.prism.visualization.demo

import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.PredictionFrame
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.RiskZone
import ai.prism.visualization.model.Trajectory
import ai.prism.visualization.model.TrajectoryPoint
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehiclePrediction
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.replay.ReplayFrame
import ai.prism.visualization.replay.ReplaySession
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 시연용 Mock 데이터 생성기.
 *
 * 다른 엔진(예측·위험 판정·vehicle-integration)이 없어도 시각화 엔진을 돌리기 위한 가짜 데이터다.
 * 위험 등급은 실제 위험 판정 알고리즘이 아니라 시나리오별로 손으로 정한 규칙(스크립트)이며,
 * 어떤 성능도 의미하지 않는다.
 *
 * 출력은 팀 저장소 계약(VehicleFrame v1 / PredictionFrame 1.0 / RiskFrame 1.0)과 같은 필드·단위를 쓴다.
 * 좌표: 자차 기준 x=오른쪽, y=전방(m·m/s·rad·ms). 예측 궤적·위험 영역은 기준 시점 자차 좌표계에 고정.
 * 원인 코드·예측 mode 는 팀 샘플에서 쓰는 어휘(HARD_BRAKING, lane_keep 등)를 따른다.
 */
object MockScenarios {
    const val DT_MS = 100L
    private const val LANE_W = 3.5f
    private const val LAT_SPEED = 1.2f
    private val ROAD = RoadConfig(laneCount = 3, laneWidthM = LANE_W, egoLaneIndex = 1)

    // ------------------------------------------------------------------ 간단 시뮬레이터(월드 좌표)

    data class Cmd(val at: Float, val accel: Float? = null, val minSpeed: Float? = null, val lane: Int? = null, val visible: Boolean? = null)
    data class Spec(val id: String, val truck: Boolean, val y0: Float, val lane: Int, val v0: Float, val cmds: List<Cmd> = emptyList(), val suv: Boolean = false)

    /** 월드 좌표: X = 오른쪽(자차 차선 중심 0), Y = 전방. */
    data class WorldState(
        val id: String, val truck: Boolean, val suv: Boolean, val X: Float, val Y: Float, val vX: Float, val vY: Float, val aY: Float,
        val lane: Int, val targetLane: Int, val visible: Boolean,
    ) {
        val length get() = if (truck) 12f else if (suv) 5.2f else 4.6f
        val width get() = if (truck) 2.5f else if (suv) 2.1f else 1.9f
    }

    private fun laneX(lane: Int) = (lane - ROAD.egoLaneIndex) * LANE_W

    private fun simulate(specs: List<Spec>, durationS: Float): List<List<WorldState>> {
        class A(val s: Spec) {
            var X = laneX(s.lane); var Y = s.y0; var v = s.v0; var a = 0f; var minV = 0f
            var target = s.lane; var visible = true; var next = 0
            val cmds = s.cmds.sortedBy { it.at }
        }
        val agents = specs.map { A(it) }
        val dt = DT_MS / 1000f
        val steps = (durationS / dt).roundToInt()
        val out = ArrayList<List<WorldState>>()
        for (i in 0..steps) {
            val t = i * dt
            val snap = agents.map { ag ->
                while (ag.next < ag.cmds.size && ag.cmds[ag.next].at <= t + 1e-4f) {
                    val c = ag.cmds[ag.next++]
                    c.accel?.let { ag.a = it }; c.minSpeed?.let { ag.minV = it }
                    c.lane?.let { ag.target = it }; c.visible?.let { ag.visible = it }
                }
                val effA = if (ag.a < 0 && ag.v <= ag.minV + 1e-3f) 0f else ag.a
                val dX = laneX(ag.target) - ag.X
                val vX = if (abs(dX) < 1e-3f) 0f else sign(dX) * min(LAT_SPEED, abs(dX) / dt)
                WorldState(ag.s.id, ag.s.truck, ag.s.suv, ag.X, ag.Y, vX, ag.v, effA,
                    ((ag.X / LANE_W) + ROAD.egoLaneIndex).roundToInt(), ag.target, ag.visible)
                    .also {
                        val v1 = (ag.v + effA * dt).let { nv -> if (effA < 0 && nv < ag.minV) ag.minV else nv }
                        ag.Y += (ag.v + v1) / 2 * dt; ag.v = v1; ag.X += vX * dt
                    }
            }
            out += snap
        }
        return out
    }

    // ------------------------------------------------------------------ 계약 프레임으로 변환

    private fun toVehicle(w: WorldState, ego: WorldState) = VehicleState(
        id = w.id, x = w.X - ego.X, y = w.Y - ego.Y, vx = w.vX, vy = w.vY,
        headingRad = atan2(w.vY, w.vX), lengthM = w.length, widthM = w.width,
        laneId = "L${w.lane + 1}", // 팀 시뮬레이션과 같은 표기: 왼쪽부터 L1, L2, L3
    )

    /** 등가속(최저속도 고려 X) 직선 + 선택적 차선 변경 예측. 좌표는 기준 시점 자차 기준(지면 위치). */
    private fun trajectory(w: WorldState, ego: WorldState, mode: String, targetLane: Int?, accel: Float, horizonS: Float = 4f): Trajectory {
        val pts = ArrayList<TrajectoryPoint>()
        var t = 0.5f
        while (t <= horizonS + 1e-3f) {
            val dy = if (accel < 0) { val ts = min(t, w.vY / -accel); w.vY * ts + 0.5f * accel * ts * ts } else w.vY * t + 0.5f * accel * t * t
            val X = if (targetLane == null) w.X else {
                val d = laneX(targetLane) - w.X
                w.X + sign(d) * min(abs(d), LAT_SPEED * t)
            }
            val prevX = if (targetLane == null) w.X else {
                val d = laneX(targetLane) - w.X; w.X + sign(d) * min(abs(d), LAT_SPEED * (t - 0.1f))
            }
            val vyNow = if (accel < 0) maxOf(0f, w.vY + accel * t) else w.vY + accel * t
            pts += TrajectoryPoint((t * 1000).roundToInt().toLong(), X - ego.X, w.Y + dy - ego.Y,
                atan2(vyNow, (X - prevX) / 0.1f))
            t += 0.5f
        }
        return Trajectory(mode, null, pts)
    }

    /** 궤적의 dt 1·2·3초 위치에 차량 크기(+불확실성 여유) 박스를 둔 risk_zones. 좌표는 기준 시점 자차 기준. */
    private fun zonesAlong(c: Trajectory, length: Float, width: Float, uptoMs: Long = 3000): List<RiskZone> =
        c.points.filter { it.dtMs % 1000L == 0L && it.dtMs <= uptoMs }.map { p ->
            val grow = 0.3f + p.dtMs / 1000f * 0.4f
            RiskZone(p.dtMs, boxRegion(Point2(p.x, p.y), length, width, grow))
        }

    private fun boxRegion(center: Point2, length: Float, width: Float, grow: Float): List<Point2> {
        val hl = length / 2 + grow; val hw = width / 2 + grow * 0.5f
        return listOf(Point2(center.x - hw, center.y - hl), Point2(center.x + hw, center.y - hl),
            Point2(center.x + hw, center.y + hl), Point2(center.x - hw, center.y + hl))
    }

    fun convexHull(points: List<Point2>): List<Point2> {
        val p = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size < 3) return p
        fun cross(o: Point2, a: Point2, b: Point2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Point2>()
        for (pt in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), pt) <= 0) lower.removeAt(lower.lastIndex); lower += pt }
        val upper = ArrayList<Point2>()
        for (pt in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), pt) <= 0) upper.removeAt(upper.lastIndex); upper += pt }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** 위험 스크립트에 넘기는 한 시점 정보. */
    private class Ctx(val t: Float, val ego: WorldState, val byId: Map<String, WorldState>) {
        fun gapAhead(w: WorldState) = w.Y - ego.Y - (w.length + ego.length) / 2
        fun gapBehind(w: WorldState) = ego.Y - w.Y - (w.length + ego.length) / 2
        fun ttcAhead(w: WorldState): Float? { val c = ego.vY - w.vY; val g = gapAhead(w); return if (c > 0.1f && g > 0) g / c else null }
        fun ttcBehind(w: WorldState): Float? { val c = w.vY - ego.vY; val g = gapBehind(w); return if (c > 0.1f && g > 0) g / c else null }
        fun inEgoLane(w: WorldState) = abs(w.X - ego.X) < (w.width + ego.width) / 2 + 0.3f
    }

    private data class RiskSpec(
        val level: RiskLevel, val causes: List<String>, val sources: List<String> = emptyList(),
        /** 위험 판단에 쓰인 궤적 mode(선택 필드 trajectory_mode) */
        val mode: String? = null, val zones: List<RiskZone>? = null, val ttc: Float? = null,
    )

    private fun build(
        id: String, title: String, description: String, specs: List<Spec>, durationS: Float,
        extraTrajectories: (Ctx, WorldState) -> List<Trajectory> = { _, _ -> emptyList() },
        risk: (Ctx, WorldState) -> RiskSpec?,
    ): ReplaySession {
        val world = simulate(specs, durationS)
        val frames = world.mapIndexed { i, states ->
            val ts = 1_700_000_000_000L + i * DT_MS
            val ego = states.first { it.id == "ego" }
            val ctx = Ctx(i * DT_MS / 1000f, ego, states.associateBy { it.id })
            val visible = states.filter { it.visible }
            val vf = VehicleFrame(ts, "ego", visible.map { toVehicle(it, ego) })
            val preds = ArrayList<VehiclePrediction>()
            val risks = ArrayList<VehicleRisk>()
            states.filter { it.id != "ego" }.forEach { w ->
                val keep = trajectory(w, ego, if (w.aY < -1f) "braking" else "lane_keep", null, w.aY)
                val trajs = listOf(keep) + extraTrajectories(ctx, w)
                if (w.visible) preds += VehiclePrediction(w.id, trajs)
                val r = risk(ctx, w) ?: RiskSpec(RiskLevel.NORMAL, emptyList())
                val used = trajs.firstOrNull { it.mode == r.mode } ?: keep
                val zones = when {
                    r.zones != null -> r.zones
                    r.level == RiskLevel.NORMAL -> emptyList()
                    else -> zonesAlong(used, w.length, w.width)
                }
                risks += VehicleRisk(
                    vehicleId = w.id, level = r.level, reasonCodes = r.causes, ttcS = r.ttc, riskZones = zones,
                    sourceVehicleIds = r.sources,
                    trajectoryMode = if (r.level != RiskLevel.NORMAL) used.mode else null,
                )
            }
            ReplayFrame(vf, PredictionFrame(ts, preds), RiskFrame(ts, risks))
        }
        return ReplaySession(id, title, description, ROAD, frames)
    }

    // ------------------------------------------------------------------ 시나리오 5종

    fun normal() = build(
        "normal", "정상 주행", "주변 차량이 일정한 간격으로 주행합니다. 모든 차량이 중립 색으로 표시되어야 합니다.",
        listOf(
            Spec("ego", false, 0f, 1, 27f),
            Spec("L1", false, -10f, 0, 27.5f),
            Spec("F1", false, 42f, 1, 27f),
            Spec("R1", false, 15f, 2, 26.5f, suv = true),
            Spec("T1", true, 60f, 2, 25f, listOf(Cmd(3f, accel = -0.5f, minSpeed = 24f))),
            Spec("B1", false, -30f, 1, 27f, suv = true),
        ), 8f,
    ) { _, _ -> null }

    fun frontBrake() = build(
        "front_brake", "전방 급감속", "앞 차량이 급감속하면 앞 차량과 내 뒤 차량의 위험이 차례로 변합니다.",
        listOf(
            Spec("ego", false, 0f, 1, 27f, listOf(Cmd(2.5f, accel = -5f, minSpeed = 8f))),
            Spec("F1", false, 35f, 1, 27f, listOf(Cmd(1.5f, accel = -6f, minSpeed = 8f)), suv = true),
            Spec("B1", false, -22f, 1, 27f, listOf(Cmd(3.1f, accel = -5f, minSpeed = 8f))),
            Spec("L1", false, 5f, 0, 26f, suv = true),
            Spec("R1", false, -12f, 2, 26f),
        ), 9f,
    ) { c, w ->
        when (w.id) {
            "F1" -> {
                val ttc = c.ttcAhead(w)
                when {
                    ttc != null && ttc < 2.5f -> RiskSpec(RiskLevel.WARNING, listOf("HARD_BRAKING", "FRONT_CLOSING"), ttc = ttc)
                    w.aY < -3f || (ttc != null && ttc < 5f) -> RiskSpec(RiskLevel.WATCH, listOf("HARD_BRAKING"), ttc = ttc)
                    else -> null
                }
            }
            "B1" -> {
                val ttc = c.ttcBehind(w)
                when {
                    ttc != null && ttc < 2f -> RiskSpec(RiskLevel.WARNING, listOf("REAR_CLOSING"), ttc = ttc)
                    ttc != null && ttc < 4.5f -> RiskSpec(RiskLevel.WATCH, listOf("REAR_CLOSING", "CHAIN_DECEL"), listOf("F1"), ttc = ttc)
                    else -> null
                }
            }
            else -> null
        }
    }

    private const val LC_LEFT = "lane_change_left"

    private fun cutInRisk(c: Ctx, w: WorldState, sources: List<String> = emptyList()): RiskSpec? {
        val movingToEgo = w.targetLane != w.lane || abs(w.vX) > 0.2f
        val headway = c.gapAhead(w) / c.ego.vY
        return when {
            c.inEgoLane(w) && w.Y > c.ego.Y && headway < 0.55f ->
                RiskSpec(RiskLevel.WARNING, listOf("LANE_CHANGE_INTRUSION", "FRONT_CLOSING"), sources, LC_LEFT, ttc = c.ttcAhead(w))
            movingToEgo && w.targetLane == ROAD.egoLaneIndex -> RiskSpec(RiskLevel.WATCH, listOf("PATH_OVERLAP"), sources, LC_LEFT)
            c.inEgoLane(w) && w.Y > c.ego.Y && headway < 0.8f -> RiskSpec(RiskLevel.WATCH, listOf("FRONT_CLOSING"), sources, ttc = c.ttcAhead(w))
            else -> null
        }
    }

    private fun laneChangeLeft(c: Ctx, w: WorldState, accel: Float = 0f) =
        trajectory(w, c.ego, LC_LEFT, ROAD.egoLaneIndex, accel)

    fun laneChange() = build(
        "lane_change", "차선 변경", "우측 차량이 내 차로로 들어옵니다. 진입이 예상되는 순간부터 해당 차량만 강조됩니다.",
        listOf(
            Spec("ego", false, 0f, 1, 26f),
            Spec("R1", false, 10f, 2, 27.5f, listOf(Cmd(2f, lane = 1)), suv = true),
            Spec("F1", false, 60f, 1, 26f),
            Spec("L1", false, -6f, 0, 26.5f),
            Spec("R2", false, -25f, 2, 26f),
        ), 9f,
        extraTrajectories = { c, w -> if (w.id == "R1" && w.lane == 2) listOf(laneChangeLeft(c, w)) else emptyList() },
    ) { c, w -> if (w.id == "R1") cutInRisk(c, w) else null }

    fun chain() = build(
        "chain", "연쇄 위험", "우측 차로 화물차가 급감속하자 그 뒤 차량이 내 차로로 피하며, 내 뒤 차량까지 위험이 이어집니다.",
        listOf(
            Spec("ego", false, 0f, 1, 27f, listOf(Cmd(4.6f, accel = -4f, minSpeed = 15f))),
            Spec("T1", true, 80f, 2, 23f, listOf(Cmd(1f, accel = -4.5f, minSpeed = 9f))),
            Spec("R1", false, 24f, 2, 26f, listOf(Cmd(3f, lane = 1, accel = -2.5f, minSpeed = 18f))),
            Spec("F1", false, 55f, 1, 27f, suv = true),
            Spec("L1", false, -8f, 0, 28f, suv = true),
            Spec("B1", false, -20f, 1, 27f, listOf(Cmd(5.6f, accel = -4f, minSpeed = 15f))),
        ), 9f,
        extraTrajectories = { c, w ->
            val t1 = c.byId.getValue("T1")
            if (w.id == "R1" && w.lane == 2 && t1.aY < -2f) listOf(laneChangeLeft(c, w, -3f)) else emptyList()
        },
    ) { c, w ->
        val t1 = c.byId.getValue("T1")
        when (w.id) {
            "T1" -> if (t1.aY < -2f) RiskSpec(RiskLevel.WATCH, listOf("HARD_BRAKING")) else null
            "R1" -> {
                if (w.lane == 2 && w.targetLane == 2 && t1.aY < -2f && c.t >= 1.2f)
                    RiskSpec(RiskLevel.WATCH, listOf("CHAIN_DECEL", "PATH_OVERLAP"), listOf("T1"), LC_LEFT)
                else cutInRisk(c, w, if (c.t < 6.5f) listOf("T1") else emptyList())
            }
            "B1" -> {
                val ttc = c.ttcBehind(w)
                when {
                    ttc != null && ttc < 2f -> RiskSpec(RiskLevel.WARNING, listOf("REAR_CLOSING"), listOf("R1"), ttc = ttc)
                    ttc != null && ttc < 4.5f -> RiskSpec(RiskLevel.WATCH, listOf("REAR_CLOSING", "CHAIN_DECEL"), listOf("R1"), ttc = ttc)
                    else -> null
                }
            }
            else -> null
        }
    }

    fun sensorDropout(): ReplaySession {
        // 차선 변경 시나리오에서 R1 이 2.4~3.6초 동안 센서에서 사라진다.
        var lastSeen: WorldState? = null
        var lastSeenT = 0f
        return build(
            "sensor_dropout", "센서 누락", "차선 변경 중인 차량이 1.2초간 관측되지 않습니다. 위치를 모르는 동안은 경고 대신 판단 보류로 표시합니다.",
            listOf(
                Spec("ego", false, 0f, 1, 26f),
                Spec("R1", false, 10f, 2, 27.5f, listOf(Cmd(2f, lane = 1), Cmd(2.4f, visible = false), Cmd(3.6f, visible = true))),
                Spec("F1", false, 60f, 1, 26f),
                Spec("L1", false, -6f, 0, 26.5f, suv = true),
            ), 8f,
            extraTrajectories = { c, w -> if (w.id == "R1" && w.lane == 2) listOf(laneChangeLeft(c, w)) else emptyList() },
        ) { c, w ->
            if (w.id != "R1") return@build null
            if (w.visible) {
                lastSeen = w; lastSeenT = c.t
                return@build cutInRisk(c, w)
            }
            val ls = lastSeen ?: return@build null
            val dt = c.t - lastSeenT
            // 누락 직후 0.2초간 Mock 위험 엔진은 직전 등급(WATCH)을 유지해 보낸다 →
            // 시각화 엔진이 위치 없는 차량을 UNKNOWN 으로 낮추는지 확인하기 위한 데이터.
            // 위치 추정 영역은 현재 시점(dt_ms = 0) risk_zone 으로 보낸다.
            val center = Point2(ls.X + ls.vX * dt - c.ego.X, ls.Y + ls.vY * dt - c.ego.Y)
            val zone = listOf(RiskZone(0, boxRegion(center, ls.length, ls.width, 0.8f + dt * 2.5f)))
            if (dt <= 0.2f) RiskSpec(RiskLevel.WATCH, listOf("PATH_OVERLAP"), zones = zone)
            else RiskSpec(RiskLevel.UNKNOWN, listOf("SENSOR_DROPOUT"), zones = zone)
        }
    }

    fun all(): List<ReplaySession> = listOf(normal(), frontBrake(), laneChange(), chain(), sensorDropout())
}

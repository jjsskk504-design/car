package com.prism.simulation.adapters

import com.prism.simulation.generators.Scenario
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * HighwayEnv 1.12.1 (MIT, (c) 2018 Edouard Leurent) 의 직선 도로와 차량 모델을 Kotlin 으로 옮긴 것.
 * 원본: highway_env/road/lane.py(StraightLane), road/road.py(Road, straight_road_network),
 *       vehicle/kinematics.py(Vehicle), vehicle/controller.py(ControlledVehicle), vehicle/behavior.py(IDMVehicle)
 * 이 엔진이 쓰는 부분만 옮겼다: 직선 다차선 도로, IDM 종방향, 차선 추종 조향, 수정 자전거 모델.
 * MOBIL 자동 차선변경(자발적 판단)은 원래도 꺼 두었으므로 옮기지 않았다.
 *
 * PRISM 추가 규칙(원본에 없음, 기존 7종 출력에는 영향 없음 — SeedTest 로 확인):
 * - request_lane_change: MOBIL 의 안전 기준(behavior.py mobil 의 new_following_pred_a >= -LANE_CHANGE_MAX_BRAKING_IMPOSED)과
 *   최소 차간 시간으로 목표 차선 진입 가능 여부를 판단하고, 불가하면 차선을 유지한다(취소).
 *   진행 중에도 원래 차선을 벗어나기 전까지 매 스텝 다시 확인해, 위험해지면 원래 차선으로 돌아온다(중단).
 * - 속도는 0 아래로 내려가지 않는다(후진 없음). 목표 속도 0 인 정지 차량은 제자리에 선다.
 */

const val SUBSTEPS = 5 // step_ms 하나를 5번 나눠 적분 (step_ms=100 -> 20ms)
val SIM_EVENTS = setOf("set_target_speed", "change_lane", "request_lane_change")

private const val LANE_WIDTH = 4.0 // StraightLane.DEFAULT_WIDTH
private const val ROAD_LENGTH = 10000.0
private const val SPEED_LIMIT = 30.0 // straight_road_network 기본값. IDM 목표속도 상한
private const val LANE_VEHICLE_LENGTH = 5.0 // AbstractLane.VEHICLE_LENGTH
private const val DEFAULT_LENGTH = 5.0 // Vehicle.LENGTH
private const val DEFAULT_WIDTH = 2.0 // Vehicle.WIDTH
private const val MAX_SPEED = 40.0
private const val MIN_SPEED = -40.0
// IDMVehicle
private const val ACC_MAX = 6.0
private const val COMFORT_ACC_MAX = 3.0
private const val COMFORT_ACC_MIN = -5.0
private const val DISTANCE_WANTED = 5.0 + DEFAULT_LENGTH
private const val TIME_WANTED = 1.5
private const val DELTA = 4.0
// ControlledVehicle
private const val TAU_HEADING = 0.2
private const val TAU_LATERAL = 0.6
private const val TAU_PURSUIT = 0.5 * TAU_HEADING
private const val KP_HEADING = 1 / TAU_HEADING
private const val KP_LATERAL = 1 / TAU_LATERAL
private const val MAX_STEERING_ANGLE = PI / 3
// MOBIL (behavior.py MOBIL 의 안전 기준 상수)
private const val LANE_CHANGE_MAX_BRAKING_IMPOSED = 2.0
// PRISM 추가: 목표 차선 앞·뒤 차량과의 최소 범퍼 간격 = MIN_GAP_M + 뒤차 속도 × MIN_TIME_GAP_S
private const val MIN_GAP_M = 2.0
private const val MIN_TIME_GAP_S = 0.8
private const val STOP_SPEED = 0.05
// PRISM 추가: 안전 확인 차선 변경의 최대 횡속도(m/s). 4 m 차선을 약 3~4초에 옮긴다(강제 change_lane 은 원본 그대로).
private const val COMFORT_LATERAL_SPEED = 1.2

private fun notZero(x: Double, eps: Double = 1e-2) = if (abs(x) > eps) x else if (x >= 0) eps else -eps

/** numpy 의 ((x + π) % 2π) - π 와 같은 결과(파이썬 % 는 나누는 수의 부호를 따름). */
private fun wrapToPi(x: Double): Double {
    val m = (x + PI) % (2 * PI)
    return (if (m < 0) m + 2 * PI else m) - PI
}

/** x 축과 평행한 직선 차선. lane i 의 중심선은 y = 4i (HighwayEnv: y 가 클수록 오른쪽). */
private class StraightLane(index: Int) {
    val startY = index * LANE_WIDTH

    fun longitudinal(x: Double) = x
    fun lateral(y: Double) = y - startY

    fun onLane(s: Double, r: Double, margin: Double) =
        abs(r) <= LANE_WIDTH / 2 + margin && -LANE_VEHICLE_LENGTH <= s && s < ROAD_LENGTH + LANE_VEHICLE_LENGTH

    fun distanceWithHeading(x: Double, y: Double, heading: Double): Double {
        val s = longitudinal(x)
        return abs(lateral(y)) + max(s - ROAD_LENGTH, 0.0) + max(-s, 0.0) + abs(wrapToPi(heading))
    }
}

/** 출력 스텝 하나의 차량 월드 상태 (HighwayEnv 좌표). */
data class WorldState(
    val id: String, val x: Double, val y: Double, val vx: Double, val vy: Double,
    val h: Double, val lengthM: Double, val widthM: Double, val lane: Int,
)

data class WorldFrame(val tMs: Int, val states: List<WorldState>)

data class Collision(val tMs: Int, val vehicleId: String)

/**
 * 안전 확인 차선 변경(request_lane_change)의 판단 기록. 시뮬레이터가 스스로 내린 판단이다(강제 아님).
 * result: accepted(방향지시등·진입 시작) / rejected(공간 부족으로 시작 전 취소) / aborted(진행 중 원래 차선 복귀) / completed
 */
data class LaneChangeDecision(
    val tMs: Int, val vehicleId: String, val fromLane: Int, val toLane: Int, val result: String, val reason: String,
)

data class SimResult(val frames: List<WorldFrame>, val collisions: List<Collision>, val decisions: List<LaneChangeDecision>)

private class Road(laneCount: Int) {
    val lanes = List(laneCount) { StraightLane(it) }
    val vehicles = mutableListOf<SimVehicle>()
    var tMs = 0 // 현재 적분 시각(판단 기록용)
    val decisions = mutableListOf<LaneChangeDecision>()

    fun closestLane(x: Double, y: Double, heading: Double) =
        lanes.indices.minBy { lanes[it].distanceWithHeading(x, y, heading) }

    /** Road.neighbour_vehicles: 주어진 차선에서 앞차와 뒤차. */
    fun neighbours(vehicle: SimVehicle, laneIndex: Int): Pair<SimVehicle?, SimVehicle?> {
        val lane = lanes[laneIndex]
        val s = lane.longitudinal(vehicle.x)
        var sFront: Double? = null
        var sRear: Double? = null
        var front: SimVehicle? = null
        var rear: SimVehicle? = null
        for (v in vehicles) {
            if (v === vehicle) continue
            val sv = lane.longitudinal(v.x)
            if (!lane.onLane(sv, lane.lateral(v.y), margin = 1.0)) continue
            if (s <= sv && (sFront == null || sv <= sFront)) { sFront = sv; front = v }
            if (sv < s && (sRear == null || sv > sRear)) { sRear = sv; rear = v }
        }
        return front to rear
    }

    fun act() = vehicles.forEach { it.act() }

    fun step(dt: Double) {
        vehicles.forEach { it.step(dt) }
        for (i in vehicles.indices) for (j in i + 1 until vehicles.size) {
            val a = vehicles[i]
            val b = vehicles[j]
            if (a.intersects(b, dt)) { a.crashed = true; b.crashed = true }
        }
    }
}

private class SimVehicle(
    val id: String, val road: Road, var x: Double, var y: Double, var speed: Double, var targetSpeed: Double,
    val length: Double, val width: Double,
) {
    var heading = 0.0 // 직선 도로의 차선 방향
    var laneIndex = road.closestLane(x, y, heading)
    var targetLane = laneIndex
    var steering = 0.0
    var acceleration = 0.0
    var crashed = false
    // 안전 확인 차선 변경 진행 상태
    private var checkedChange = false
    private var comfortLateral = false // 안전 확인 차선 변경(또는 그 중단 후 복귀)이 끝나 차선 중앙에 설 때까지
    private var originLane = laneIndex

    // Vehicle.velocity / direction: slip angle 없이 heading 방향 (IDM 상대속도 계산에 원본 그대로 사용)
    val dirX get() = cos(heading)
    val dirY get() = sin(heading)

    /** IDMVehicle.act (MOBIL 자발적 차선변경 꺼짐) + 안전 확인 차선 변경 감시. */
    fun act() {
        if (crashed) return
        monitorLaneChange()
        steering = steeringControl(targetLane).coerceIn(-MAX_STEERING_ANGLE, MAX_STEERING_ANGLE)
        var a = idm(road.neighbours(this, laneIndex).first)
        if (laneIndex != targetLane) a = min(a, idm(road.neighbours(this, targetLane).first))
        acceleration = a.coerceIn(-ACC_MAX, ACC_MAX)
        // 목표 속도 0 으로 멈춘 차량은 제동을 유지한다(IDM 자유항이 0 근처에서 앞뒤로 흔들리지 않게)
        if (targetSpeed <= 0.0 && speed <= STOP_SPEED) acceleration = min(acceleration, 0.0)
    }

    private fun log(from: Int, to: Int, result: String, reason: String) {
        road.decisions += LaneChangeDecision(road.tMs, id, from, to, result, reason)
    }

    /**
     * 목표 차선 진입이 안전하지 않은 이유(안전하면 null).
     * 1) 목표 차선 앞·뒤 차량과의 범퍼 간격 >= MIN_GAP_M + 뒤쪽 차량 속도 × MIN_TIME_GAP_S
     * 2) MOBIL 안전 기준: 새 뒤차가 나 때문에 LANE_CHANGE_MAX_BRAKING_IMPOSED 보다 세게 감속하지 않아야 한다
     * 3) 나도 새 앞차 때문에 그보다 세게 감속하지 않아야 한다
     */
    fun unsafeReason(lane: Int): String? {
        val (front, rear) = road.neighbours(this, lane)
        if (rear != null) {
            val gap = x - rear.x - (length + rear.length) / 2
            val need = MIN_GAP_M + max(rear.speed, 0.0) * MIN_TIME_GAP_S
            if (gap < need) return fmt("후방 %s 간격 %.1f m < 필요 %.1f m", rear.id, gap, need)
            val predA = rear.idm(this)
            if (predA < -LANE_CHANGE_MAX_BRAKING_IMPOSED) return fmt("후방 %s 예상 감속 %.1f m/s² (허용 -%.1f)", rear.id, predA, LANE_CHANGE_MAX_BRAKING_IMPOSED)
        }
        if (front != null) {
            val gap = front.x - x - (length + front.length) / 2
            val need = MIN_GAP_M + max(speed, 0.0) * MIN_TIME_GAP_S
            if (gap < need) return fmt("전방 %s 간격 %.1f m < 필요 %.1f m", front.id, gap, need)
            val ownA = idm(front)
            if (ownA < -LANE_CHANGE_MAX_BRAKING_IMPOSED) return fmt("전방 %s 때문에 예상 감속 %.1f m/s²", front.id, ownA)
        }
        return null
    }

    /** request_lane_change: 안전하면 방향지시등을 켜고 진입을 시작, 아니면 차선 유지(취소). */
    fun requestLaneChange(lane: Int) {
        if (lane !in road.lanes.indices || lane == laneIndex) { log(laneIndex, lane, "rejected", "잘못된 목표 차선"); return }
        val reason = unsafeReason(lane)
        if (reason != null) { log(laneIndex, lane, "rejected", "진입 공간 부족: $reason"); return }
        originLane = laneIndex
        targetLane = lane
        checkedChange = true
        comfortLateral = true
        log(laneIndex, lane, "accepted", "방향지시등 켬 · 목표 차선 앞뒤 간격 확인")
    }

    /** 원래 차선을 벗어나기 전에 위험해지면 중단하고 원래 차선으로 돌아온다. 목표 차선 중앙에 정렬되면 완료. */
    private fun monitorLaneChange() {
        if (!comfortLateral) return
        if (checkedChange && laneIndex == originLane && targetLane != originLane) {
            val reason = unsafeReason(targetLane) ?: return
            log(originLane, targetLane, "aborted", "진행 중 위험 감지: $reason")
            targetLane = originLane
            checkedChange = false
        } else if (laneIndex == targetLane && abs(road.lanes[targetLane].lateral(y)) < 0.3) {
            if (checkedChange) log(originLane, targetLane, "completed", "목표 차선 중앙 정렬 · 방향지시등 끔")
            checkedChange = false
            comfortLateral = false
        }
    }

    /** ControlledVehicle.steering_control: 횡위치 P제어 -> 목표 heading -> heading P제어 -> 조향각. */
    private fun steeringControl(target: Int): Double {
        val lateral = road.lanes[target].lateral(y)
        val lateralSpeedCommand = (-KP_LATERAL * lateral).let {
            if (comfortLateral) it.coerceIn(-COMFORT_LATERAL_SPEED, COMFORT_LATERAL_SPEED) else it
        }
        val headingCommand = asin((lateralSpeedCommand / notZero(speed)).coerceIn(-1.0, 1.0))
        val headingRef = 0.0 + headingCommand.coerceIn(-PI / 4, PI / 4) // 직선 차선의 미래 heading = 0
        val headingRateCommand = KP_HEADING * wrapToPi(headingRef - heading)
        val slipAngle = asin((length / 2 / notZero(speed) * headingRateCommand).coerceIn(-1.0, 1.0))
        return atan(2 * tan(slipAngle)).coerceIn(-MAX_STEERING_ANGLE, MAX_STEERING_ANGLE)
    }

    /** IDMVehicle.acceleration (Intelligent Driver Model). */
    fun idm(front: SimVehicle?): Double {
        val target = targetSpeed.coerceIn(0.0, SPEED_LIMIT)
        var a = COMFORT_ACC_MAX * (1 - (max(speed, 0.0) / abs(notZero(target))).pow(DELTA))
        if (front != null) {
            val d = front.x - x // lane_distance_to: 현재 차선 기준 종방향 거리
            a -= COMFORT_ACC_MAX * (desiredGap(front) / notZero(d)).pow(2)
        }
        return a
    }

    private fun desiredGap(front: SimVehicle): Double {
        val ab = -COMFORT_ACC_MAX * COMFORT_ACC_MIN
        val dv = (speed * dirX - front.speed * front.dirX) * dirX + (speed * dirY - front.speed * front.dirY) * dirY
        return DISTANCE_WANTED + speed * TIME_WANTED + speed * dv / (2 * sqrt(ab))
    }

    /** Vehicle.step: 수정 자전거 모델 적분. */
    fun step(dt: Double) {
        if (crashed) { steering = 0.0; acceleration = -speed }
        if (speed > MAX_SPEED) acceleration = min(acceleration, MAX_SPEED - speed)
        else if (speed < MIN_SPEED) acceleration = max(acceleration, MIN_SPEED - speed)
        val beta = slipBeta()
        x += speed * cos(heading + beta) * dt
        y += speed * sin(heading + beta) * dt
        heading += speed * sin(beta) / (length / 2) * dt
        speed += acceleration * dt
        if (speed < 0.0) speed = 0.0 // 후진 없음(PRISM 추가 규칙)
        laneIndex = road.closestLane(x, y, heading)
    }

    fun slipBeta() = atan(0.5 * tan(steering))

    private fun corners(): List<Pair<Double, Double>> {
        val c = cos(heading)
        val s = sin(heading)
        return listOf(-1 to -1, -1 to 1, 1 to 1, 1 to -1).map { (i, j) ->
            val px = i * length / 2
            val py = j * width / 2
            Pair(x + c * px - s * py, y + s * px + c * py)
        }
    }

    /**
     * 현재 차체 사각형끼리 겹치는지 (utils.are_polygons_intersecting 의 intersecting, 분리축 정리).
     * shortcut: 충돌 후 서로 밀어내는 impact 와 "곧 겹칠지" 예측은 옮기지 않음. 충돌 0건 시나리오만 쓰므로 판정만 필요. 충돌 재현이 필요해지면 추가.
     */
    fun intersects(other: SimVehicle, dt: Double): Boolean {
        if (hypot(other.x - x, other.y - y) > (hypot(length, width) + hypot(other.length, other.width)) / 2 + speed * dt) return false
        val a = corners()
        val b = other.corners()
        for (poly in listOf(a, b)) for (k in poly.indices) {
            val (x1, y1) = poly[k]
            val (x2, y2) = poly[(k + 1) % poly.size]
            val nx = -(y2 - y1)
            val ny = x2 - x1
            val pa = a.map { it.first * nx + it.second * ny }
            val pb = b.map { it.first * nx + it.second * ny }
            if (pa.max() < pb.min() || pb.max() < pa.min()) return false
        }
        return true
    }
}

private fun fmt(pattern: String, vararg args: Any) = String.format(java.util.Locale.ROOT, pattern, *args)

/** 시나리오를 돌려 출력 스텝별 월드 상태, 시뮬레이터가 판정한 충돌, 차선 변경 판단 기록을 반환한다. */
fun simulate(sc: Scenario): SimResult {
    val road = Road(sc.lanes)
    val vehicles = LinkedHashMap<String, SimVehicle>()
    for (spec in listOf(sc.ego) + sc.vehicles) {
        val v = SimVehicle(
            spec.id, road, spec.x, spec.lane * LANE_WIDTH, spec.speed, spec.targetSpeed ?: spec.speed,
            spec.lengthM ?: DEFAULT_LENGTH, spec.widthM ?: DEFAULT_WIDTH,
        )
        road.vehicles += v
        vehicles[spec.id] = v
    }
    val dt = sc.stepMs / 1000.0 / SUBSTEPS
    val nSteps = Math.round(sc.durationS * 1000 / sc.stepMs).toInt()
    val events = ArrayDeque(sc.events.filter { it.type in SIM_EVENTS }.sortedBy { it.tS })
    val frames = mutableListOf<WorldFrame>()
    val collisions = mutableListOf<Collision>()
    val crashed = mutableSetOf<String>()
    for (k in 0..nSteps) {
        val tMs = k * sc.stepMs
        road.tMs = tMs
        while (events.isNotEmpty() && events.first().tS * 1000 <= tMs) {
            val e = events.removeFirst()
            val v = vehicles.getValue(e.vehicle)
            when (e.type) {
                "set_target_speed" -> v.targetSpeed = e.value!!.toDouble()
                "change_lane" -> v.targetLane = e.value!!.toInt() // 강제(안전 확인 없음): 위험 연출용
                "request_lane_change" -> v.requestLaneChange(e.value!!.toInt()) // 안전 확인 후 진입 또는 취소
            }
        }
        frames += WorldFrame(tMs, vehicles.map { (id, v) ->
            // 실제 이동 방향 heading+beta 로 속도를 낸다 (HighwayEnv Vehicle.velocity 는 beta 를 빠뜨림)
            val b = v.slipBeta()
            WorldState(id, v.x, v.y, v.speed * cos(v.heading + b), v.speed * sin(v.heading + b), v.heading, v.length, v.width, v.laneIndex)
        })
        for ((id, v) in vehicles) if (v.crashed && crashed.add(id)) collisions += Collision(tMs, id)
        if (k < nSteps) repeat(SUBSTEPS) { j ->
            road.tMs = tMs + j * sc.stepMs / SUBSTEPS
            road.act(); road.step(dt)
        }
    }
    return SimResult(frames, collisions, road.decisions.toList())
}

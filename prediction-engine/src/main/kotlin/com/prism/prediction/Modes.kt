package com.prism.prediction

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

object Modes {
    private fun wrapToPi(a: Double): Double {
        val m = (a + PI) % (2 * PI)
        return (if (m < 0) m + 2 * PI else m) - PI
    }

    /**
     * 차선 유지 예측(도로 축 기준).
     * - 종방향: 등가속. 감속 중이면 속도 0 에서 멈춘다(등가속 외삽이 후진으로 넘어가지 않게). 최대 속도에서 더 가속하지 않는다.
     * - 횡방향: 현재 횡속도가 시간상수 τ = laneChangeDurationS/3 로 줄어드는 차선 중앙 정렬 모델. 총 횡이동은 한 차선 폭 이내.
     *   (차선 변경 중인 차량의 경로가 여러 차선을 가로질러 뻗지 않는다)
     * 도로 방향과 45° 이상 어긋나게 움직이거나 실제로 후진 중인 차량은 null → 기존 등가속 예측을 쓴다.
     */
    private fun laneBounded(
        observations: List<TimedVehicle>,
        current: VehicleState,
        config: PredictionConfig,
    ): List<TrajectoryPoint>? {
        val road = config.roadHeadingRad
        val ux = cos(road); val uy = sin(road)   // 도로 전방
        val lx = -uy; val ly = ux                 // 도로 왼쪽
        val speed = hypot(current.vx, current.vy)
        if (speed >= config.stationarySpeedMps && abs(wrapToPi(atan2(current.vy, current.vx) - road)) > PI / 4) return null
        val vLong = (current.vx * ux + current.vy * uy).coerceAtMost(config.maxSpeedMps)
        if (vLong < 0.0) return null
        val vLat = (current.vx * lx + current.vy * ly).coerceIn(-config.maxSpeedMps, config.maxSpeedMps)
        val aLong = Baseline.estimateAcceleration(observations, config)?.let { it.first * ux + it.second * uy } ?: 0.0
        val tau = config.laneChangeDurationS / 3.0
        return config.horizonsMs.map { dtMs ->
            val t = dtMs / 1000.0
            val (s, v) = when {
                aLong < 0.0 -> {
                    val tt = minOf(t, vLong / -aLong)
                    Pair(vLong * tt + 0.5 * aLong * tt * tt, vLong + aLong * tt)
                }
                aLong > 0.0 && vLong < config.maxSpeedMps -> {
                    val tt = minOf(t, (config.maxSpeedMps - vLong) / aLong)
                    Pair(vLong * tt + 0.5 * aLong * tt * tt + config.maxSpeedMps * (t - tt), vLong + aLong * tt)
                }
                else -> Pair(vLong * t, vLong)
            }
            val rawLat = vLat * tau * (1.0 - exp(-t / tau))
            val lat = rawLat.coerceIn(-config.laneWidthM, config.laneWidthM)
            val vLatT = if (abs(rawLat) >= config.laneWidthM) 0.0 else vLat * exp(-t / tau)
            val heading = if (hypot(v, vLatT) < config.stationarySpeedMps) current.headingRad else road + atan2(vLatT, v)
            TrajectoryPoint(dtMs, current.x + ux * s + lx * lat, current.y + uy * s + ly * lat, heading)
        }
    }

    private fun smoothStep(progress: Double): Double {
        val value = progress.coerceIn(0.0, 1.0)
        return value * value * (3.0 - 2.0 * value)
    }

    private fun laneKeepWithCurvature(
        base: List<TrajectoryPoint>,
        current: VehicleState,
        config: PredictionConfig,
    ): List<TrajectoryPoint> {
        val curvature = config.roadCurvaturePerM
        if (kotlin.math.abs(curvature) < 1e-12) return base
        val speed = hypot(current.vx, current.vy)
        val initialHeading = if (speed < config.stationarySpeedMps) {
            current.headingRad
        } else {
            atan2(current.vy, current.vx)
        }
        return base.map { point ->
            val distance = hypot(point.x - current.x, point.y - current.y)
            val endHeading = initialHeading + curvature * distance
            point.copy(
                x = current.x + (sin(endHeading) - sin(initialHeading)) / curvature,
                y = current.y - (cos(endHeading) - cos(initialHeading)) / curvature,
                headingRad = endHeading,
            )
        }
    }

    private fun braking(current: VehicleState, config: PredictionConfig): List<TrajectoryPoint> {
        val rawSpeed = hypot(current.vx, current.vy)
        val speed = rawSpeed.coerceAtMost(config.maxSpeedMps)
        val direction = if (speed < config.stationarySpeedMps || rawSpeed == 0.0) {
            Pair(0.0, 0.0)
        } else {
            Pair(current.vx / rawSpeed, current.vy / rawSpeed)
        }
        val stopTime = speed / config.brakingMps2
        return config.horizonsMs.map { dtMs ->
            val movingTime = (dtMs / 1000.0).coerceAtMost(stopTime)
            val distance = speed * movingTime - 0.5 * config.brakingMps2 * movingTime * movingTime
            TrajectoryPoint(
                dtMs,
                current.x + direction.first * distance,
                current.y + direction.second * distance,
                current.headingRad,
            )
        }
    }

    private fun laneChange(
        laneKeep: List<TrajectoryPoint>,
        current: VehicleState,
        config: PredictionConfig,
        direction: Int,
    ): List<TrajectoryPoint> {
        val speed = hypot(current.vx, current.vy)
        val (unitX, unitY) = if (speed < config.stationarySpeedMps) {
            Pair(cos(current.headingRad), sin(current.headingRad))
        } else {
            Pair(current.vx / speed, current.vy / speed)
        }
        val leftX = -unitY
        val leftY = unitX
        return laneKeep.map { point ->
            val progress = (point.dtMs / 1000.0) / config.laneChangeDurationS
            val offset = direction * config.laneWidthM * smoothStep(progress)
            point.copy(x = point.x + leftX * offset, y = point.y + leftY * offset)
        }
    }

    fun candidates(
        observations: List<TimedVehicle>,
        config: PredictionConfig = PredictionConfig(),
    ): List<CandidateTrajectory> {
        require(observations.isNotEmpty()) { "at least one observation is required" }
        val current = observations.last().vehicle
        val base = laneBounded(observations, current, config) ?: Baseline.constantAcceleration(observations, config)
        val laneKeep = laneKeepWithCurvature(base, current, config)
        return listOf(
            CandidateTrajectory("lane_keep", null, laneKeep),
            CandidateTrajectory("braking", null, braking(current, config)),
            CandidateTrajectory("lane_change_left", null, laneChange(laneKeep, current, config, 1)),
            CandidateTrajectory("lane_change_right", null, laneChange(laneKeep, current, config, -1)),
        )
    }
}

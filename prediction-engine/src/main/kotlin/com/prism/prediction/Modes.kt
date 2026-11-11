package com.prism.prediction

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object Modes {
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
        val laneKeep = laneKeepWithCurvature(Baseline.constantAcceleration(observations, config), current, config)
        return listOf(
            CandidateTrajectory("lane_keep", null, laneKeep),
            CandidateTrajectory("braking", null, braking(current, config)),
            CandidateTrajectory("lane_change_left", null, laneChange(laneKeep, current, config, 1)),
            CandidateTrajectory("lane_change_right", null, laneChange(laneKeep, current, config, -1)),
        )
    }
}

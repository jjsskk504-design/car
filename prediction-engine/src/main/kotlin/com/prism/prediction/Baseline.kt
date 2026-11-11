package com.prism.prediction

import kotlin.math.atan2
import kotlin.math.hypot

data class PredictionConfig(
    val horizonsMs: List<Long> = listOf(1000, 2000, 3000, 4000, 5000),
    val maxSpeedMps: Double = 55.0,
    val maxAccelMps2: Double = 8.0,
    val stationarySpeedMps: Double = 0.20,
    val brakingMps2: Double = 4.0,
    val laneWidthM: Double = 3.5,
    val laneChangeDurationS: Double = 4.0,
    val roadCurvaturePerM: Double = 0.0,
) {
    init {
        require(horizonsMs.isNotEmpty() && horizonsMs.all { it in 1..5000 }) {
            "horizonsMs must contain offsets in the 1-5000 ms simulation window"
        }
        require(horizonsMs == horizonsMs.distinct().sorted()) { "horizonsMs must be unique and increasing" }
        require(maxSpeedMps > 0.0 && maxAccelMps2 > 0.0) { "speed and acceleration limits must be positive" }
        require(brakingMps2 > 0.0 && laneWidthM > 0.0 && laneChangeDurationS > 0.0) {
            "braking and lane geometry values must be positive"
        }
    }
}
object Baseline {
    private fun boundedVector(x: Double, y: Double, maximum: Double): Pair<Double, Double> {
        val magnitude = hypot(x, y)
        if (magnitude == 0.0 || magnitude <= maximum) return Pair(x, y)
        val scale = maximum / magnitude
        return Pair(x * scale, y * scale)
    }

    private fun heading(vx: Double, vy: Double, fallback: Double, stationarySpeed: Double): Double =
        if (hypot(vx, vy) < stationarySpeed) fallback else atan2(vy, vx)

    fun constantVelocity(
        current: VehicleState,
        config: PredictionConfig = PredictionConfig(),
    ): List<TrajectoryPoint> {
        val (vx, vy) = boundedVector(current.vx, current.vy, config.maxSpeedMps)
        val heading = heading(vx, vy, current.headingRad, config.stationarySpeedMps)
        return config.horizonsMs.map { dtMs ->
            val seconds = dtMs / 1000.0
            TrajectoryPoint(dtMs, current.x + vx * seconds, current.y + vy * seconds, heading)
        }
    }

    fun estimateAcceleration(
        observations: List<TimedVehicle>,
        config: PredictionConfig,
    ): Pair<Double, Double>? {
        if (observations.size < 2) return null
        val previous = observations[observations.lastIndex - 1]
        val current = observations.last()
        val seconds = (current.timestampMs - previous.timestampMs) / 1000.0
        if (seconds <= 0.0) return null
        return boundedVector(
            (current.vehicle.vx - previous.vehicle.vx) / seconds,
            (current.vehicle.vy - previous.vehicle.vy) / seconds,
            config.maxAccelMps2,
        )
    }

    fun constantAcceleration(
        observations: List<TimedVehicle>,
        config: PredictionConfig = PredictionConfig(),
    ): List<TrajectoryPoint> {
        require(observations.isNotEmpty()) { "at least one observation is required" }
        val current = observations.last().vehicle
        val acceleration = estimateAcceleration(observations, config)
            ?: return constantVelocity(current, config)
        val (vx, vy) = boundedVector(current.vx, current.vy, config.maxSpeedMps)
        val (ax, ay) = acceleration
        return config.horizonsMs.map { dtMs ->
            val seconds = dtMs / 1000.0
            val (dx, dy) = boundedVector(
                vx * seconds + 0.5 * ax * seconds * seconds,
                vy * seconds + 0.5 * ay * seconds * seconds,
                config.maxSpeedMps * seconds,
            )
            val (futureVx, futureVy) = boundedVector(
                vx + ax * seconds,
                vy + ay * seconds,
                config.maxSpeedMps,
            )
            TrajectoryPoint(
                dtMs,
                current.x + dx,
                current.y + dy,
                heading(futureVx, futureVy, current.headingRad, config.stationarySpeedMps),
            )
        }
    }
}

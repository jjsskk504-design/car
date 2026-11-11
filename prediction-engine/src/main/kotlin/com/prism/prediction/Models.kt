package com.prism.prediction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleFrame(
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("ego_id") val egoId: String,
    val vehicles: List<VehicleState>,
)

@Serializable
data class VehicleState(
    val id: String,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    @SerialName("heading_rad") val headingRad: Double,
    @SerialName("length_m") val lengthM: Double,
    @SerialName("width_m") val widthM: Double,
    @SerialName("lane_id") val laneId: String,
)

@Serializable
data class PredictionFrame(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("timestamp_ms") val timestampMs: Long,
    val predictions: List<VehiclePrediction>,
)

@Serializable
data class VehiclePrediction(
    @SerialName("vehicle_id") val vehicleId: String,
    val trajectories: List<CandidateTrajectory>,
)

@Serializable
data class CandidateTrajectory(
    val mode: String,
    val confidence: Double? = null,
    val points: List<TrajectoryPoint>,
)

@Serializable
data class TrajectoryPoint(
    @SerialName("dt_ms") val dtMs: Long,
    val x: Double,
    val y: Double,
    @SerialName("heading_rad") val headingRad: Double,
)

data class TimedVehicle(val timestampMs: Long, val vehicle: VehicleState)

package com.prism.prediction

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ContractException(message: String) : IllegalArgumentException(message)

object History {
    fun prepare(frames: List<VehicleFrame>): List<VehicleFrame> {
        if (frames.isEmpty()) throw ContractException("VehicleFrame input is empty")
        frames.forEach(::validateVehicleFrame)
        val sorted = frames.sortedBy { it.timestampMs }
        if (sorted.map { it.timestampMs }.distinct().size != sorted.size) {
            throw ContractException("duplicate timestamp_ms is not allowed")
        }
        validateIdentityConsistency(sorted)
        return sorted
    }

    fun validateVehicleFrame(frame: VehicleFrame) {
        if (frame.timestampMs < 0) throw ContractException("timestamp_ms must be non-negative")
        if (frame.egoId.isBlank()) throw ContractException("ego_id must be non-empty")
        val ids = frame.vehicles.map { it.id }
        if (ids.size != ids.distinct().size) {
            throw ContractException("duplicate vehicle id at timestamp ${frame.timestampMs}")
        }
        if (frame.vehicles.none { it.id == frame.egoId }) {
            throw ContractException("ego_id '${frame.egoId}' is absent from vehicles")
        }
        frame.vehicles.forEach { vehicle ->
            if (vehicle.id.isBlank()) throw ContractException("vehicle id must be non-empty")
            val values = listOf(
                vehicle.x,
                vehicle.y,
                vehicle.vx,
                vehicle.vy,
                vehicle.headingRad,
                vehicle.lengthM,
                vehicle.widthM,
            )
            if (values.any { !it.isFinite() }) {
                throw ContractException("vehicle '${vehicle.id}' contains a non-finite number")
            }
            if (vehicle.lengthM <= 0.0 || vehicle.widthM <= 0.0) {
                throw ContractException("vehicle dimensions must be positive")
            }
        }
    }

    private fun validateIdentityConsistency(frames: List<VehicleFrame>) {
        val dimensions = mutableMapOf<String, Pair<Double, Double>>()
        frames.forEach { frame ->
            frame.vehicles.forEach { vehicle ->
                val previous = dimensions.getOrPut(vehicle.id) { Pair(vehicle.lengthM, vehicle.widthM) }
                if (maxOf(abs(vehicle.lengthM - previous.first), abs(vehicle.widthM - previous.second)) > 0.50) {
                    throw ContractException("possible ID reassignment for '${vehicle.id}': dimensions changed by over 0.50 m")
                }
            }
        }
    }

    fun estimateEgoPoses(frames: List<VehicleFrame>): List<Pose2D> {
        if (frames.isEmpty()) return emptyList()
        val firstHeading = frames.first().ego().headingRad
        val poses = mutableListOf(Pose2D(0.0, 0.0, 0.0))
        frames.zipWithNext().forEach { (previousFrame, frame) ->
            val seconds = (frame.timestampMs - previousFrame.timestampMs) / 1000.0
            if (seconds <= 0.0) throw ContractException("timestamps must increase after sorting")
            val previousEgo = previousFrame.ego()
            val previousPose = poses.last()
            val yaw = Coordinates.normalizeAngle(frame.ego().headingRad - firstHeading)
            val worldVx = cos(previousPose.yawRad) * previousEgo.vx - sin(previousPose.yawRad) * previousEgo.vy
            val worldVy = sin(previousPose.yawRad) * previousEgo.vx + cos(previousPose.yawRad) * previousEgo.vy
            poses += Pose2D(
                previousPose.x + worldVx * seconds,
                previousPose.y + worldVy * seconds,
                yaw,
            )
        }
        return poses
    }

    fun normalizeToCurrentEgo(
        frames: List<VehicleFrame>,
        egoPoses: List<Pose2D>? = null,
    ): List<VehicleFrame> {
        if (frames.isEmpty()) throw ContractException("at least one frame is required")
        frames.forEach(::validateVehicleFrame)
        val poses = egoPoses ?: estimateEgoPoses(frames)
        if (poses.size != frames.size) throw ContractException("ego pose count must match VehicleFrame count")
        val targetPose = poses.last()
        return frames.zip(poses).map { (frame, pose) ->
            frame.copy(vehicles = frame.vehicles.map { Coordinates.transformVehicle(it, pose, targetPose) })
        }
    }

    fun observations(frames: List<VehicleFrame>, vehicleId: String): List<TimedVehicle> =
        frames.mapNotNull { frame ->
            frame.vehicles.firstOrNull { it.id == vehicleId }?.let { TimedVehicle(frame.timestampMs, it) }
        }

    private fun VehicleFrame.ego(): VehicleState =
        vehicles.firstOrNull { it.id == egoId }
            ?: throw ContractException("ego_id '$egoId' is absent from vehicles")
}

package com.prism.prediction

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Pose2D(val x: Double, val y: Double, val yawRad: Double)

object Coordinates {
    fun normalizeAngle(angleRad: Double): Double = (angleRad + PI).mod(2.0 * PI) - PI

    private fun rotate(x: Double, y: Double, angleRad: Double): Pair<Double, Double> {
        val cosine = cos(angleRad)
        val sine = sin(angleRad)
        return Pair(cosine * x - sine * y, sine * x + cosine * y)
    }

    fun localToWorldPoint(x: Double, y: Double, pose: Pose2D): Pair<Double, Double> {
        val (rotatedX, rotatedY) = rotate(x, y, pose.yawRad)
        return Pair(rotatedX + pose.x, rotatedY + pose.y)
    }

    fun worldToLocalPoint(x: Double, y: Double, pose: Pose2D): Pair<Double, Double> =
        rotate(x - pose.x, y - pose.y, -pose.yawRad)

    fun transformPoint(
        x: Double,
        y: Double,
        sourcePose: Pose2D,
        targetPose: Pose2D,
    ): Pair<Double, Double> {
        val (worldX, worldY) = localToWorldPoint(x, y, sourcePose)
        return worldToLocalPoint(worldX, worldY, targetPose)
    }

    fun transformVector(
        x: Double,
        y: Double,
        sourcePose: Pose2D,
        targetPose: Pose2D,
    ): Pair<Double, Double> {
        val (worldX, worldY) = rotate(x, y, sourcePose.yawRad)
        return rotate(worldX, worldY, -targetPose.yawRad)
    }

    fun transformHeading(headingRad: Double, sourcePose: Pose2D, targetPose: Pose2D): Double =
        normalizeAngle(headingRad + sourcePose.yawRad - targetPose.yawRad)

    fun transformVehicle(
        vehicle: VehicleState,
        sourcePose: Pose2D,
        targetPose: Pose2D,
    ): VehicleState {
        val (x, y) = transformPoint(vehicle.x, vehicle.y, sourcePose, targetPose)
        val (vx, vy) = transformVector(vehicle.vx, vehicle.vy, sourcePose, targetPose)
        return vehicle.copy(
            x = x,
            y = y,
            vx = vx,
            vy = vy,
            headingRad = transformHeading(vehicle.headingRad, sourcePose, targetPose),
        )
    }
}

package ai.riskradar.engine02

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.IEEErem

data class PhysicalCollision(val timestampMs: Long, val a: String, val b: String)
data class PhysicalNearMiss(
    val timestampMs: Long, val a: String, val b: String, val clearanceM: Double,
)
data class PhysicalAudit(
    val id: String,
    val status: String,
    val samples: Int,
    val pairChecks: Int,
    val collisions: List<PhysicalCollision>,
    val nearMissesBelowOneM: List<PhysicalNearMiss>,
    val minClearanceM: Double,
    val minRoadBoundaryClearanceM: Double,
    val maxAccelerationMps2: Double,
    val maxJerkMps3: Double,
    val maxHeadingStepRad: Double,
    val maxStepM: Double,
    val maxPositionResidualM: Double,
    val laneBoundaryViolations: Int,
    val failures: List<String>,
)

/**
 * Synthetic sampled-physics QA: OBB all-pairs and lane/kinematic constraints.
 * Discrete 50ms checks are NOT a continuous-time collision safety proof.
 */
object PhysicalAuditor {
    private data class Point(val x: Double, val y: Double)
    private const val DT = PhysicalSimulator.DT

    private fun rectangle(v: PhysicalVehicle): List<Point> {
        val c = cos(v.headingRad)
        val s = sin(v.headingRad)
        return listOf(
            -v.lengthM / 2 to -v.widthM / 2,
            v.lengthM / 2 to -v.widthM / 2,
            v.lengthM / 2 to v.widthM / 2,
            -v.lengthM / 2 to v.widthM / 2,
        ).map { (u, w) -> Point(v.xM + u*c - w*s, v.yM + u*s + w*c) }
    }

    private fun collision(a: List<Point>, b: List<Point>): Boolean {
        for (poly in listOf(a, b)) {
            for (i in poly.indices) {
                val p = poly[i]
                val q = poly[(i + 1) % poly.size]
                val ax = -(q.y - p.y)
                val ay = q.x - p.x
                val asProj = a.map { it.x*ax + it.y*ay }
                val bsProj = b.map { it.x*ax + it.y*ay }
                if (asProj.max() < bsProj.min() || bsProj.max() < asProj.min()) {
                    return false
                }
            }
        }
        return true
    }

    private fun distanceToSegment(p: Point, a: Point, b: Point): Double {
        val dx = b.x-a.x
        val dy = b.y-a.y
        val denom = dx*dx+dy*dy
        val u = if (denom > 0) (((p.x-a.x)*dx+(p.y-a.y)*dy)/denom).coerceIn(0.0,1.0) else 0.0
        return hypot(p.x-a.x-u*dx, p.y-a.y-u*dy)
    }

    private fun clearance(a: List<Point>, b: List<Point>): Double {
        if (collision(a,b)) return 0.0
        var minimum = Double.POSITIVE_INFINITY
        for ((src,dst) in listOf(a to b, b to a)) {
            for (p in src) {
                for (i in dst.indices) {
                    minimum = min(minimum, distanceToSegment(p,dst[i],dst[(i+1)%dst.size]))
                }
            }
        }
        return minimum
    }

    fun audit(scene: PhysicalScene): PhysicalAudit {
        require(scene.world.isNotEmpty())
        val failures = mutableListOf<String>()
        val collisions = mutableListOf<PhysicalCollision>()
        val near = mutableListOf<PhysicalNearMiss>()
        val firstIds = scene.world.first().vehicles.map { it.id }
        if (firstIds.distinct().size != firstIds.size || "ego" !in firstIds) {
            failures.add("invalid_initial_ids")
        }
        if (scene.world.size != 201) failures.add("expected_201_physical_samples")
        val initialLane = scene.world.first().vehicles.associate {
            it.id to PhysicalSimulator.lanes.minBy { lane -> abs(lane-it.xM) }
        }

        var pairs = 0
        var minClearance = Double.POSITIVE_INFINITY
        var minBoundary = Double.POSITIVE_INFINITY
        var maxAccel = 0.0
        var maxJerk = 0.0
        var maxHeading = 0.0
        var maxStep = 0.0
        var maxResidual = 0.0
        var violations = 0
        var previous: Map<String, PhysicalVehicle>? = null
        val previousAccel = mutableMapOf<String, Pair<Double,Double>>()

        for ((index, frame) in scene.world.withIndex()) {
            val t = frame.timestampMs
            if (t != index*50L) failures.add("invalid_timestamp:" + t)
            if (frame.vehicles.map { it.id } != firstIds) failures.add("unstable_vehicle_ids:" + t)
            val shapes = mutableMapOf<String, List<Point>>()
            for (v in frame.vehicles) {
                if (!listOf(v.xM, v.yM, v.vxMps, v.vyMps, v.headingRad,
                        v.lengthM, v.widthM, v.ayMps2).all { it.isFinite() }) {
                    failures.add("nonfinite_vehicle:" + t + ":" + v.id)
                    continue
                }
                val poly = rectangle(v)
                shapes[v.id] = poly
                val margin = poly.minOf { 5.4 - abs(it.x) }
                minBoundary = min(minBoundary, margin)
                if (margin < 0.0) failures.add("road_boundary:" + t + ":" + v.id)
                if (v.vyMps < -0.001) failures.add("reverse:" + t + ":" + v.id)

                var lane = initialLane[v.id] ?: 0.0
                var corridorLow = lane-1.8
                var corridorHigh = lane+1.8
                var changing = false
                for (event in scene.events) {
                    if (event.id != v.id || event.event != "accepted"
                        || event.timestampMs > t || event.targetXM == null) continue
                    val target = event.targetXM
                    if (t < event.timestampMs + 5000L) {
                        corridorLow = min(lane,target)-1.8
                        corridorHigh = max(lane,target)+1.8
                        changing = true
                    } else {
                        lane = target
                        corridorLow = lane-1.8
                        corridorHigh = lane+1.8
                    }
                }
                val bad = (!changing && abs(v.xM-lane)>1e-6)
                    || poly.any { it.x < corridorLow-1e-6 || it.x > corridorHigh+1e-6 }
                if (bad) {
                    violations += 1
                    failures.add("lane_boundary:" + t + ":" + v.id)
                }
                val prev = previous?.get(v.id)
                if (prev != null) {
                    val ax = (v.vxMps-prev.vxMps)/DT
                    val ay = (v.vyMps-prev.vyMps)/DT
                    maxAccel = max(maxAccel, hypot(ax, ay))
                    val old = previousAccel[v.id]
                    if (old != null) {
                        maxJerk = max(maxJerk, hypot(ax-old.first, ay-old.second)/DT)
                    }
                    previousAccel[v.id] = ax to ay
                    maxHeading = max(maxHeading, abs((v.headingRad-prev.headingRad).IEEErem(2*PI)))
                    maxStep = max(maxStep, hypot(v.xM-prev.xM, v.yM-prev.yM))
                    maxResidual = max(maxResidual, hypot(
                        v.xM-prev.xM-0.5*(v.vxMps+prev.vxMps)*DT,
                        v.yM-prev.yM-0.5*(v.vyMps+prev.vyMps)*DT,
                    ))
                }
            }
            for (i in frame.vehicles.indices) {
                for (j in (i+1) until frame.vehicles.size) {
                    val a = frame.vehicles[i]
                    val b = frame.vehicles[j]
                    val pa = shapes[a.id] ?: continue
                    val pb = shapes[b.id] ?: continue
                    pairs += 1
                    val hit = collision(pa,pb)
                    val distance = clearance(pa,pb)
                    minClearance = min(minClearance,distance)
                    if (hit) collisions.add(PhysicalCollision(t,a.id,b.id))
                    else if (distance < 1.0) near.add(PhysicalNearMiss(t,a.id,b.id,distance))
                }
            }
            previous = frame.vehicles.associateBy { it.id }
        }

        val limits = listOf(
            "acceleration_mps2" to (maxAccel to 4.5),
            "jerk_mps3" to (maxJerk to 3.5),
            "heading_step_rad" to (maxHeading to 0.04),
            "position_residual_m" to (maxResidual to 0.003),
            "step_m" to (maxStep to 2.0),
        )
        for ((name, value) in limits) {
            if (value.first > value.second) failures.add(name + "_LIMIT")
        }
        if (collisions.isNotEmpty()) failures.add("ACTUAL_BODY_COLLISION")
        return PhysicalAudit(
            id = scene.id,
            status = if (failures.isEmpty()) "PASS" else "FAIL",
            samples = scene.world.size,
            pairChecks = pairs,
            collisions = collisions,
            nearMissesBelowOneM = near,
            minClearanceM = minClearance,
            minRoadBoundaryClearanceM = minBoundary,
            maxAccelerationMps2 = maxAccel,
            maxJerkMps3 = maxJerk,
            maxHeadingStepRad = maxHeading,
            maxStepM = maxStep,
            maxPositionResidualM = maxResidual,
            laneBoundaryViolations = violations,
            failures = failures,
        )
    }
}

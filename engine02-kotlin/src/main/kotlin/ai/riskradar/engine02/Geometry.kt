package ai.riskradar.engine02

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Experimental local road-tangent geometry: d points right, s points forward.
 * Returns observed OBB overlap separately from CV-projected *hypotheses*.
 * Neither quantity represents calibrated collision probability or real impact.
 */
internal object RoadGeometry {
    data class Point(val d: Double, val s: Double)

    private fun body(d: Double, s: Double, length: Double, width: Double, yaw: Double): List<Point> {
        val c = cos(yaw)
        val z = sin(yaw)
        return listOf(
            Point(-width / 2, -length / 2),
            Point(width / 2, -length / 2),
            Point(width / 2, length / 2),
            Point(-width / 2, length / 2),
        ).map { Point(d + it.d * c + it.s * z, s - it.d * z + it.s * c) }
    }

    private fun overlap(a: List<Point>, b: List<Point>): Boolean {
        for (poly in listOf(a, b)) {
            for (i in poly.indices) {
                val p = poly[i]
                val q = poly[(i + 1) % poly.size]
                val ax = q.s - p.s
                val ay = p.d - q.d
                val pa = a.map { it.d * ax + it.s * ay }
                val pb = b.map { it.d * ax + it.s * ay }
                if (pa.max() < pb.min() || pb.max() < pa.min()) return false
            }
        }
        return true
    }

    internal fun valid(n: Neighbor, o: Observation, p: EngineParameters): Boolean {
        val v = listOf(n.dM, n.sM, n.vdMps, n.vsMps, n.lengthM, n.widthM, n.yawRad)
        return n.trackId.isNotBlank() && n.trackId != o.trackId
            && n.timestampMs == o.timestampMs
            && v.all { it.isFinite() }
            && n.lengthM in 0.5..30.0 && n.widthM in 0.3..5.0
            && abs(n.vsMps) <= p.maxSpeedMps && abs(n.vdMps) <= p.maxLateralMps
    }

    fun calculate(o: Observation, neighbors: List<Neighbor>, p: EngineParameters): RiskGeometry {
        val observed = mutableListOf<String>()
        val projected = mutableListOf<ProjectedOverlap>()
        val gaps = mutableListOf<NeighborGap>()
        val egoCurrent = body(o.dM, o.sM, o.lengthM, o.widthM, o.yawRad)
        val steps = max(1, ceil(p.horizonS / p.projectionStepS).toInt())
        for (n in neighbors) {
            if (overlap(egoCurrent, body(n.dM, n.sM, n.lengthM, n.widthM, n.yawRad))) {
                observed.add(n.trackId)
            }
            for (k in 1..steps) {
                val t = min(p.horizonS, k * p.projectionStepS)
                val egoFuture = body(o.dM + o.vdMps * t, o.sM + o.vsMps * t,
                    o.lengthM, o.widthM, o.yawRad)
                val neighborFuture = body(n.dM + n.vdMps * t, n.sM + n.vsMps * t,
                    n.lengthM, n.widthM, n.yawRad)
                if (overlap(egoFuture, neighborFuture)) {
                    projected.add(ProjectedOverlap(n.trackId, t))
                    break
                }
            }
            val delta = n.sM - o.sM
            val gap = abs(delta) - (o.lengthM + n.lengthM) / 2
            val closing = if (delta >= 0) o.vsMps - n.vsMps else n.vsMps - o.vsMps
            val side = when {
                n.dM < o.dM - 1 -> "LEFT"
                n.dM > o.dM + 1 -> "RIGHT"
                else -> "SAME"
            }
            gaps.add(NeighborGap(
                n.trackId, side, if (delta >= 0) "FRONT" else "REAR",
                gap, closing,
                if (closing > 0.01) max(0.0, gap) / closing else null,
            ))
        }
        return RiskGeometry(
            status = "AVAILABLE",
            observedBodyOverlapIds = observed,
            cvProjectedOverlap = projected,
            neighborGaps = gaps,
            horizonS = p.horizonS,
            stepS = p.projectionStepS,
        )
    }
}

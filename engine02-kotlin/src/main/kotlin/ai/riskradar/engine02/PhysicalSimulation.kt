package ai.riskradar.engine02

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Synthetic *physical* world, independent of real sensors and prediction engines. */
data class PhysicalVehicle(
    val id: String, val xM: Double, val yM: Double,
    val vxMps: Double, val vyMps: Double,
    val headingRad: Double,
    val lengthM: Double, val widthM: Double,
    val ayMps2: Double,
)
data class PhysicalFrame(val timestampMs: Long, val vehicles: List<PhysicalVehicle>)
data class PhysicalEvent(
    val timestampMs: Long, val id: String, val event: String,
    val targetXM: Double? = null,
)
data class PhysicalScene(
    val id: String,
    val world: List<PhysicalFrame>,
    val events: List<PhysicalEvent>,
    val provenance: String = "SYNTHETIC_ONLY_KOTLIN_V2_RESEARCH",
)

/** A 50-ms deterministic simulator, not validated against observed real-world dynamics. */
object PhysicalSimulator {
    const val DT = 0.05
    val lanes = listOf(-3.6, 0.0, 3.6)
    val scenes = listOf("lane_change", "yield_merge", "shockwave", "dense")

    private data class Car(
        val id: String,
        var x: Double,
        var y: Double,
        var vy: Double,
        var desired: Double = 23.0,
        var ay: Double = 0.0,
        var vx: Double = 0.0,
        var heading: Double = PI / 2,
        val length: Double = 4.6,
        val width: Double = 1.9,
        var target: Double? = null,
        var requestAt: Double = 1.0,
        var changeStart: Double? = null,
        var origin: Double? = null,
        val duration: Double = 5.0,
    ) {
        fun snapshot() = PhysicalVehicle(id, x, y, vx, vy, heading, length, width, ay)
    }

    private fun clamp(x: Double, lo: Double, hi: Double) = x.coerceIn(lo, hi)
    private fun smooth(uIn: Double): Double {
        val u = clamp(uIn, 0.0, 1.0)
        return u.pow(3) * (10 - 15 * u + 6 * u * u)
    }
    private fun dsmooth(u: Double) = if (u > 0 && u < 1) 30 * u * u * (1-u).pow(2) else 0.0
    private fun occupants(c: Car): Set<Double> =
        if (c.changeStart != null && c.origin != null && c.target != null)
            setOf(c.origin!!, c.target!!)
        else setOf(lanes.minBy { abs(it - c.x) })

    private fun acceleration(c: Car, cars: List<Car>, desiredOverride: Double? = null): Double {
        val v0 = max(0.1, desiredOverride ?: c.desired)
        var a = min(1.5 * (1 - (c.vy/v0).pow(4)), 0.8 * (v0 - c.vy))
        for (other in cars) {
            if (other.id == c.id || other.y <= c.y) continue
            if (occupants(c).intersect(occupants(other)).isEmpty()) continue
            val gap = max(0.2, other.y-c.y-(other.length+c.length)/2)
            val wanted = 3.0 + max(0.0, c.vy*1.35 +
                c.vy*(c.vy-other.vy)/(2*sqrt(1.5*2)))
            a = min(a, 1.5 * (1 - (c.vy/v0).pow(4) - (wanted/gap).pow(2)))
        }
        return max(clamp(a, -4.0, 1.5), -sqrt(3 * max(0.0, c.vy)))
    }

    private fun safeChange(c: Car, cars: List<Car>, target: Double): Boolean {
        for (o in cars) {
            if (o.id == c.id || target !in occupants(o)) continue
            for (k in 0..(c.duration / DT).toInt()) {
                val t = k * DT
                val separation = o.y+o.vy*t-c.y-c.vy*t
                val gap = abs(separation)-(o.length+c.length)/2
                val closing = max(0.0,
                    if (separation >= 0) c.vy-o.vy else o.vy-c.vy)
                if (gap < 8 + closing * 1.5) return false
            }
            if (o.y < c.y) {
                val gap = c.y-o.y-(o.length+c.length)/2
                if (max(0.0, o.vy-c.vy).pow(2) / (2*max(0.1, gap-3)) > 2) {
                    return false
                }
            }
        }
        return true
    }

    private fun sceneCars(name: String): List<Car> = when (name) {
        "lane_change" -> listOf(
            Car("ego", 0.0, 0.0, 22.0),
            Car("changer", 0.0, 55.0, 23.0, target = -3.6),
            Car("fast_rear", -3.6, 45.0, 30.0, desired = 30.0),
            Car("target_front", -3.6, 145.0, 28.0, desired = 28.0),
            Car("right", 3.6, 35.0, 22.0),
        )
        "yield_merge" -> listOf(
            Car("ego", 0.0, 0.0, 21.0, desired = 22.0),
            Car("merger", 3.6, 55.0, 19.0, desired = 22.0,
                target = 0.0, requestAt = 0.5),
            Car("priority", 0.0, 60.0, 24.0, desired = 24.0),
            Car("rear", 0.0, -45.0, 21.0, desired = 22.0),
            Car("left", -3.6, 25.0, 22.0),
        )
        "shockwave" -> listOf(
            Car("ego", 0.0, 0.0, 22.0, desired = 24.0),
            Car("response", 0.0, 55.0, 22.0, desired = 24.0),
            Car("brake_lead", 0.0, 110.0, 22.0, desired = 24.0),
            Car("left", -3.6, 15.0, 23.0),
            Car("right", 3.6, 55.0, 22.0),
        )
        "dense" -> buildList {
            add(Car("ego", 0.0, 0.0, 19.0, desired = 21.0))
            for ((lane, x) in lanes.withIndex()) {
                for (i in -2..3) {
                    if (lane == 1 && i == 0) continue
                    add(Car("dense_" + lane + "_" + i, x,
                        i*36.0 + (lane-1)*10.0, 19.0 + (i % 2), desired = 21.0))
                }
            }
        }
        else -> throw IllegalArgumentException("Unknown scene: " + name)
    }

    fun simulate(name: String): PhysicalScene {
        val cars = sceneCars(name)
        val frames = mutableListOf<PhysicalFrame>()
        val events = mutableListOf<PhysicalEvent>()
        val delayed = mutableSetOf<String>()
        for (tick in 0..200) {
            val t = tick * DT
            for (c in cars) {
                val target = c.target
                if (target != null && c.changeStart == null && t >= c.requestAt) {
                    if (safeChange(c, cars, target)) {
                        c.changeStart = t
                        c.origin = c.x
                        events.add(PhysicalEvent((t*1000).toLong(), c.id, "accepted", target))
                    } else if (delayed.add(c.id)) {
                        events.add(PhysicalEvent((t*1000).toLong(), c.id, "delayed_unsafe_front_or_rear"))
                    }
                }
            }
            frames.add(PhysicalFrame(tick*50L, cars.map { it.snapshot() }))
            if (tick == 200) break
            val wanted = cars.map { c ->
                val desired = when {
                    name == "shockwave" && c.id == "brake_lead" && t >= 1.0 -> 5.0
                    name == "yield_merge" && c.id == "merger" && c.changeStart == null -> 16.0
                    else -> c.desired
                }
                acceleration(c, cars, desired)
            }
            for ((i, c) in cars.withIndex()) {
                val nextAy = c.ay + clamp(wanted[i] - c.ay, -2.5*DT, 2.5*DT)
                val jerk = (nextAy-c.ay)/DT
                c.y += c.vy*DT + 0.5*c.ay*DT*DT + jerk*DT*DT*DT/6
                c.vy += c.ay*DT + 0.5*jerk*DT*DT
                c.ay = nextAy
                val start = c.changeStart
                val origin = c.origin
                val target = c.target
                if (start != null && origin != null && target != null) {
                    val u = (t+DT-start)/c.duration
                    c.x = origin + (target-origin)*smooth(u)
                    c.vx = (target-origin)*dsmooth(u)/c.duration
                    if (u >= 1.0) {
                        c.x = target
                        c.vx = 0.0
                        c.target = null
                        c.changeStart = null
                        c.origin = null
                    }
                }
                if (hypot(c.vy, c.vx) > 1e-6) c.heading = atan2(c.vy, c.vx)
            }
        }
        for (c in cars) {
            if (c.target != null && c.changeStart == null) {
                events.add(PhysicalEvent(10000, c.id, "cancelled_no_safe_gap"))
            }
        }
        return PhysicalScene(name, frames, events)
    }
}

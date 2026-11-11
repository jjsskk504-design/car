package ai.prism.visualization.demo

import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.ui.RadarScreen
import android.util.Log
import android.view.Choreographer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlin.math.PI
import kotlin.math.sin

/**
 * FPS 측정용: 차량 N 대를 매 프레임 움직이며 RadarScreen 을 다시 그린다.
 * 2초마다 실제 화면 프레임 수(Choreographer 기준)를 logcat(PRISM_FPS)에 남긴다.
 */
@Composable
fun StressScreen(vehicleCount: Int) {
    var tMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { tMs = (it - start) / 1_000_000 }
    }
    DisposableEffect(vehicleCount) {
        val ch = Choreographer.getInstance()
        var frames = 0
        var windowStart = 0L
        var stopped = false
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(now: Long) {
                if (stopped) return
                if (windowStart == 0L) windowStart = now
                frames++
                val elapsed = now - windowStart
                if (elapsed >= 2_000_000_000L) {
                    Log.i("PRISM_FPS", "vehicles=$vehicleCount fps=%.1f".format(frames * 1e9 / elapsed))
                    frames = 0; windowStart = now
                }
                ch.postFrameCallback(this)
            }
        }
        ch.postFrameCallback(cb)
        onDispose { stopped = true }
    }
    val (vf, rf) = remember(tMs, vehicleCount) { syntheticFrames(vehicleCount, tMs) }
    RadarScreen(vf, Modifier.fillMaxSize(), riskFrame = rf, mode = DisplayMode.DETAIL)
}

/** 3차로에 N 대를 배치하고 시간에 따라 앞뒤·좌우로 흔든다. 일부는 WATCH/WARNING/UNKNOWN. */
fun syntheticFrames(n: Int, tMs: Long): Pair<VehicleFrame, RiskFrame> {
    val t = tMs / 1000.0
    val vehicles = ArrayList<VehicleState>()
    vehicles += VehicleState("ego", 0f, 0f, 0f, 27f, (PI / 2).toFloat(), 4.6f, 1.9f, "L2")
    val risks = ArrayList<VehicleRisk>()
    for (i in 0 until n) {
        val lane = i % 3
        val baseY = -28f + (i / 3) * (110f / ((n + 2) / 3).coerceAtLeast(1)) + if (lane == 1 && i / 3 == 0) 14f else 0f
        val y = baseY + (4 * sin(t * 0.8 + i)).toFloat()
        val x = (lane - 1) * 3.5f + (0.4 * sin(t * 1.3 + i * 0.7)).toFloat()
        val id = "V$i"
        vehicles += VehicleState(id, x, y, 0.3f, 27f, (PI / 2 + 0.05 * sin(t + i)).toFloat(), 4.6f, 1.9f, "L${lane + 1}")
        val level = when (i % 7) { 0 -> RiskLevel.WATCH; 3 -> RiskLevel.WARNING; 5 -> RiskLevel.UNKNOWN; else -> RiskLevel.NORMAL }
        risks += VehicleRisk(id, level, if (level == RiskLevel.NORMAL) emptyList() else listOf("FRONT_CLOSING"))
    }
    return VehicleFrame(tMs, "ego", vehicles) to RiskFrame(tMs, risks)
}

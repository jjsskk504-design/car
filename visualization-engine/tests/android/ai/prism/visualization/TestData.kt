package ai.prism.visualization

import ai.prism.visualization.model.Point2
import ai.prism.visualization.model.PredictionFrame
import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.RiskLevel
import ai.prism.visualization.model.RoadConfig
import ai.prism.visualization.model.RiskZone
import ai.prism.visualization.model.Trajectory
import ai.prism.visualization.model.TrajectoryPoint
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehiclePrediction
import ai.prism.visualization.model.VehicleRisk
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.replay.ReplayFrame
import ai.prism.visualization.replay.ReplaySession
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.PI

/** 계측 테스트용 합성 데이터(데모 모듈에 의존하지 않음). */
object TestData {
    private val fwd = (PI / 2).toFloat()
    fun v(id: String, x: Float, y: Float, lane: Int = 2) = VehicleState(id, x, y, 0f, 25f, fwd, 4.6f, 1.9f, "L$lane")

    fun vehicleFrame(ts: Long = 1000, aY: Float = 15f) = VehicleFrame(
        ts, "ego",
        listOf(v("ego", 0f, 0f), v("A", 3.5f, aY, 3), v("B", 3.5f, 45f, 3), v("C", -3.5f, -10f, 1)),
    )

    fun riskFrame(ts: Long = 1000, a: RiskLevel, sourceB: Boolean = false) = RiskFrame(
        ts,
        listOf(
            VehicleRisk(
                vehicleId = "A", level = a,
                reasonCodes = if (a == RiskLevel.NORMAL) emptyList() else listOf("PATH_OVERLAP"),
                // dt_ms = 0: 현재 시점 영역(자차 상대 운동 변환 없이 그대로 표시)
                riskZones = if (a == RiskLevel.NORMAL) emptyList() else
                    listOf(RiskZone(0, listOf(Point2(0.5f, 12f), Point2(4.5f, 12f), Point2(2f, 26f), Point2(-1f, 24f)))),
                sourceVehicleIds = if (sourceB) listOf("B") else emptyList(),
                trajectoryMode = "lane_change_left",
            ),
            VehicleRisk("B", RiskLevel.NORMAL, emptyList()),
        ),
    )

    fun predictionFrame(ts: Long = 1000, aY: Float = 15f) = PredictionFrame(
        ts,
        listOf(VehiclePrediction("A", listOf(
            Trajectory("lane_keep", null, (1..8).map { TrajectoryPoint(it * 500L, 3.5f, aY + it * 0.5f * 1.5f, fwd) }),
            Trajectory("lane_change_left", null, (1..8).map {
                TrajectoryPoint(it * 500L, (3.5f - it * 0.6f).coerceAtLeast(0f), aY + it * 0.5f * 1.5f, fwd)
            }),
        ))),
    )

    /** 5초짜리 리플레이: 1초부터 A 주의, 2~3초 경고, 4초 이후 정상. */
    fun session(id: String = "test"): ReplaySession {
        val frames = (0..50).map { i ->
            val ts = 10_000L + i * 100
            val lv = when (i) { in 10 until 20 -> RiskLevel.WATCH; in 20..30 -> RiskLevel.WARNING; in 31 until 40 -> RiskLevel.WATCH; else -> RiskLevel.NORMAL }
            val aY = 15f - i * 0.1f
            ReplayFrame(vehicleFrame(ts, aY), predictionFrame(ts, aY), riskFrame(ts, lv, sourceB = lv != RiskLevel.NORMAL))
        }
        return ReplaySession(id, "테스트 시나리오", "계측 테스트용 합성 데이터", RoadConfig(), frames)
    }

    /** 결과물(스크린샷·벤치마크)을 테스트 앱 외부 파일 폴더에 저장한다. */
    fun outputDir(): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.filesDir, "prism-viz").apply { mkdirs() }  // adb exec-out run-as <pkg> --user <id> cat files/prism-viz/<name>
    }

    fun save(bitmap: Bitmap, name: String): File {
        val f = File(outputDir(), name)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
}

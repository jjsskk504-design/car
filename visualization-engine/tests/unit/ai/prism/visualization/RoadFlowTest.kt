package ai.prism.visualization

import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.overlays.RoadPattern
import ai.prism.visualization.rendering.DisplayMode
import ai.prism.visualization.rendering.RoadOdometer
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.replay.ReplayJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

/**
 * 주행 방향 검증.
 * 레이더는 자차 기준 좌표라 자차가 화면에 고정된다. 도로 표식이 자차 주행거리만큼 흘러가야
 * 자차보다 느린 차량이 "후진"처럼 보이지 않는다.
 */
class RoadFlowTest {

    @Test
    fun odometer_integratesEgoSpeed() {
        val o = RoadOdometer()
        for (i in 0..10) o.advance(i * 100L, 25f)
        assertEquals(25.0, o.distanceM, 1e-6) // 25 m/s × 1 s
    }

    @Test
    fun odometer_doesNotInventMotionWithoutSpeed() {
        val o = RoadOdometer()
        o.advance(0, null); o.advance(100, Float.NaN); o.advance(200, null)
        assertEquals(0.0, o.distanceM, 0.0)
        o.advance(300, 0f); o.advance(1300, 0f) // 정지한 자차 → 도로도 정지
        assertEquals(0.0, o.distanceM, 0.0)
    }

    @Test
    fun odometer_rewindMovesRoadBackward() {
        val o = RoadOdometer()
        o.advance(0, 20f); o.advance(1000, 20f)
        assertEquals(20.0, o.distanceM, 1e-6)
        o.advance(500, 20f) // 리플레이 되감기
        assertEquals(10.0, o.distanceM, 1e-6)
    }

    @Test
    fun roadMarks_moveRearwardAsEgoAdvances() {
        val period = 12f
        assertEquals(-48f, RoadPattern.firstMark(-37f, period, 0f), 1e-4f) // 오프셋 0 = 기존 고정 배치(12의 배수)
        // 자차가 2.5 m 전진하면 도로 표식은 자차 기준 2.5 m 뒤로 이동한다
        val m0 = RoadPattern.firstMark(0f, period, 0f)
        val m1 = RoadPattern.firstMark(0f, period, 2.5f)
        assertEquals(0f, m0, 1e-4f)
        assertEquals(-2.5f, m1, 1e-4f)
        // 음수 오프셋(되감기)도 같은 격자 위에 놓인다
        assertEquals(-12f + 2.5f, RoadPattern.firstMark(-1f, period, -2.5f), 1e-4f)
    }

    @Test
    fun scene_carriesEgoForwardSpeed() {
        val h = (PI / 2).toFloat()
        fun v(id: String, vy: Float) = VehicleState(id, 0f, if (id == "ego") 0f else 20f, 0f, vy, h, 4.6f, 1.8f, "L2")
        val s = SceneBuilder.build(VehicleFrame(0, "ego", listOf(v("ego", 25f), v("a", 24f))), null, null, DisplayMode.DETAIL)
        assertEquals(25f, s.egoSpeedMps!!, 0f)
        val noEgo = SceneBuilder.build(VehicleFrame(0, "ego", listOf(v("a", 24f))), null, null, DisplayMode.DETAIL)
        assertNull(noEgo.egoSpeedMps)
    }

    /**
     * 예측 경로 첫 구간의 방향이 차량 진행 방향(heading)과 일치하는지(MOCK 6종: prediction_frames 포함).
     * 기본(AS_GIVEN, 도로 기준)은 어긋나는 경로가 없어야 하고, 예전 기본(RELATIVE_TO_EGO)은 자차보다 느린 차량의
     * 경로가 뒤·옆으로 눕는 건수를 함께 출력한다(수정 전후 비교용).
     */
    @Test
    fun predictedPaths_followVehicleHeading() {
        val dir = File(File("..").canonicalFile, "vehicle-integration/app/src/main/assets/scenarios")
        assumeTrue("팀 저장소 밖에서는 건너뜀", dir.isDirectory)
        val scenarios = dir.listFiles { f -> File(f, "prediction_frames.jsonl").exists() }.orEmpty().sortedBy { it.name }
        assertTrue(scenarios.isNotEmpty())
        fun misaligned(ref: ai.prism.visualization.rendering.TrajectoryReference, s: File): Triple<Int, Int, List<String>> {
            val session = ReplayJson.fromJsonl(s.name, s.name, File(s, "vehicle_frames.jsonl").readText(),
                File(s, "prediction_frames.jsonl").readText(), File(s, "risk_frames.jsonl").takeIf { it.exists() }?.readText()).session
            var bad = 0; var total = 0; val conflicts = ArrayList<String>()
            session.frames.forEach { f ->
                val scene = SceneBuilder.build(f.vehicle, f.prediction, f.risk, DisplayMode.DETAIL, f.vehicle.timestampMs,
                    ai.prism.visualization.rendering.SceneOptions(trajectoryReference = ref))
                val byId = f.vehicle.vehicles.associateBy { it.id }
                scene.regions.filter { it.kind == ai.prism.visualization.rendering.RegionKind.CONFLICT }.forEach { conflicts += "${f.vehicle.timestampMs}:${it.vehicleId}:${it.dtMs}" }
                scene.trajectories.forEach { tr ->
                    val v = byId[tr.vehicleId] ?: return@forEach
                    if (kotlin.math.hypot(v.vx, v.vy) < 3f || tr.points.size < 2) return@forEach // 거의 정지한 차량은 방향 판단 제외
                    val a = tr.points[0]; val b = tr.points[1]
                    val dir = kotlin.math.atan2(b.y - a.y, b.x - a.x)
                    var d = abs(dir - v.headingRad); if (d > PI) d = (2 * PI - d).toFloat()
                    total++
                    if (d > Math.toRadians(30.0)) bad++
                }
            }
            return Triple(bad, total, conflicts)
        }
        for (s in scenarios) {
            val (before, total, cBefore) = misaligned(ai.prism.visualization.rendering.TrajectoryReference.RELATIVE_TO_EGO, s)
            val (after, _, cAfter) = misaligned(ai.prism.visualization.rendering.TrajectoryReference.AS_GIVEN, s)
            println("${s.name}: 경로 ${total}개 중 진행 방향과 30° 이상 어긋남 — 수정 전 $before, 수정 후 $after")
            assertEquals("${s.name} 경로 방향 불일치", 0, after)
            assertEquals("${s.name} 충돌 영역 판정이 표시 기준에 따라 달라짐", cBefore, cAfter)
            println("  충돌 영역 판정 ${cAfter.size}건 (수정 전과 동일)")
        }
        assertEquals(ai.prism.visualization.rendering.TrajectoryReference.AS_GIVEN,
            ai.prism.visualization.rendering.SceneOptions().trajectoryReference)
    }

    /**
     * 팀 저장소의 실제 시나리오 13종 전체(읽기 전용):
     *  - 자차는 항상 원점(위치 불변), 모든 차량 heading 은 전방(π/2), 절대 전방속도 vy ≥ 0
     *  - 프레임 간 위치 변화 = (차량 vy − 자차 vy)·dt (속도 부호와 좌표 갱신 일치)
     *  - 도로 기준 이동량(Δy + 자차 주행거리) ≥ 0 → 도로에 대해 후진하는 차량 없음
     */
    @Test
    fun allTeamScenarios_noVehicleMovesBackwardRelativeToRoad() {
        val dir = File(File("..").canonicalFile, "vehicle-integration/app/src/main/assets/scenarios")
        assumeTrue("팀 저장소 밖에서는 건너뜀", dir.isDirectory)
        val scenarios = dir.listFiles { f -> File(f, "vehicle_frames.jsonl").exists() }.orEmpty().sortedBy { it.name }
        assertTrue(scenarios.size >= 13)
        val half = (PI / 2).toFloat()
        for (s in scenarios) {
            val frames = ReplayJson.fromJsonl(s.name, s.name, File(s, "vehicle_frames.jsonl").readText(), null, null)
                .session.frames.map { it.vehicle }
            val noisy = File(s, "scenario_manifest.json").takeIf { it.exists() }?.readText()?.contains("위치 노이즈") == true
            var fixedRoadBackward = 0
            var pairs = 0
            for (k in 1 until frames.size) {
                val a = frames[k - 1]; val b = frames[k]
                val ea = a.vehicles.first { it.id == a.egoId }; val eb = b.vehicles.first { it.id == b.egoId }
                assertTrue("${s.name} 자차 위치 변함", eb.x == 0f && eb.y == 0f)
                val dtS = (b.timestampMs - a.timestampMs) / 1000f
                val egoAdvance = (ea.vy + eb.vy) / 2f * dtS
                val prev = a.vehicles.associateBy { it.id }
                for (v in b.vehicles) {
                    if (v.id == b.egoId) continue
                    assertTrue("${s.name} ${v.id} heading", abs(v.headingRad - half) < 0.5f)
                    assertTrue("${s.name} ${v.id} vy<0", v.vy >= 0f)
                    val p = prev[v.id] ?: continue
                    pairs++
                    val dy = v.y - p.y
                    val expected = ((p.vy + v.vy) / 2f - egoAdvance / dtS) * dtS
                    // 누락 프레임 구간(dt > step)은 등가속 가정 오차가 커지므로 허용치를 dt 에 비례시킨다.
                    // 위치 노이즈를 의도적으로 주입한 시나리오(manifest 표기)는 위치-속도 일치 검사에서 뺀다.
                    if (!noisy) assertEquals("${s.name} t=${b.timestampMs} ${v.id} 위치·속도 부호 불일치", expected, dy, 0.3f + dtS)
                    if (dy < -1e-3f) fixedRoadBackward++
                    assertTrue("${s.name} t=${b.timestampMs} ${v.id} 도로 기준 후진 ${dy + egoAdvance}",
                        dy + egoAdvance >= -0.05f)
                }
            }
            println("${s.name}: 비교 ${pairs}건, 고정 도로에서 뒤로 밀려 보이던 건수 $fixedRoadBackward → 도로 기준 후진 0건")
        }
    }
}

package com.prism.vehicle.adapters

import com.prism.vehicle.replay.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

private class FileTextSource(private val base: File) : TextSource {
    override fun read(path: String) = File(base, path).takeIf { it.isFile }?.readText()
}

private fun loaderFor(base: String): Pair<JsonFileProviders, ScenarioLoader> {
    val p = JsonFileProviders(FileTextSource(File(base)))
    return p to ScenarioLoader(p, p, p)
}

/** 앱에 실제로 들어가는 assets 시나리오 전체를 Android 없이 검증한다. */
class AssetScenarioIntegrationTest {
    private val pl = engineLoader(FileTextSource(File("../app/src/main/assets"))) // 앱과 같은 로더(파일 + 01·02 엔진)
    private val providers = pl.first
    private val loader = pl.second

    private fun all() = providers.scenarioIds().also { assertTrue("index.json 비어 있음", it.isNotEmpty()) }
        .map { id -> id to loader.load(id) }

    @Test fun indexListsFiveRequiredScenariosPlusDropout() {
        assertTrue(providers.scenarioIds().containsAll(listOf("normal", "rear_approach", "sudden_brake", "lane_change", "chain_reaction", "dropout")))
    }

    @Test fun everyScenarioParsesAndAllIdsLink() {
        all().forEach { (id, r) ->
            if (r !is LoadResult.Ready) fail("$id: ${(r as LoadResult.Failed).issues}")
        }
    }

    @Test fun egoIsOriginFacingForwardInEveryFrame() {
        all().forEach { (id, r) ->
            val t = (r as LoadResult.Ready).timeline
            t.vehicleFrames.forEach { f ->
                val ego = f.vehicles.single { it.id == f.egoId }
                assertEquals("$id@${f.timestampMs} ego.x", 0.0, ego.x, 1e-9)
                assertEquals("$id@${f.timestampMs} ego.y", 0.0, ego.y, 1e-9)
                assertEquals("$id@${f.timestampMs} ego heading", PI / 2, ego.headingRad, 0.01)
            }
        }
    }

    @Test fun predictionAndRiskShareVehicleTimestamps() {
        all().forEach { (id, r) ->
            val t = (r as LoadResult.Ready).timeline
            t.vehicleFrames.forEach { f ->
                val s = t.snapshotAt(f.timestampMs)
                assertEquals("$id@${f.timestampMs}", f.timestampMs, s.prediction?.timestampMs)
                assertEquals("$id@${f.timestampMs}", f.timestampMs, s.risk?.timestampMs)
            }
        }
    }

    @Test fun riskScenariosActuallyRaiseRisk() {
        all().filter { it.first in setOf("rear_approach", "sudden_brake", "lane_change", "chain_reaction") }.forEach { (id, r) ->
            val t = (r as LoadResult.Ready).timeline
            val levels = (0..t.durationMs step 100).map { t.snapshotAt(it).topLevel.name }.toSet()
            assertTrue("$id levels=$levels", "WARNING" in levels || "WATCH" in levels)
        }
    }

    /** 03 시나리오는 예측·위험 파일 없이 01·02 엔진이 계산한다. */
    @Test fun sim03ScenariosAreComputedByEngines() {
        val sims = all().filter { it.first.startsWith("sim_") }
        assertEquals(10, sims.size) // 03 시나리오 7종 + 차선 변경 안전 확인·취소, 정지 후 재출발 3종
        sims.forEach { (id, r) ->
            val t = (r as LoadResult.Ready).timeline
            assertTrue("$id issues=${t.issues}", t.issues.none { it.severity == Severity.WARNING && ("01@" in it.where || "02@" in it.where) })
            val risks = t.vehicleFrames.mapNotNull { t.snapshotAt(it.timestampMs).risk }.flatMap { it.riskObjects }
            val levels = risks.groupingBy { it.riskLevel.name }.eachCount()
            println("$id levels=$levels blocked=${risks.count { "ENGINE02_INPUT_BLOCKED" in it.reasonCodes }}")
            assertTrue("$id: 02 입력이 막힌 차량이 있음", risks.none { "ENGINE02_INPUT_BLOCKED" in it.reasonCodes })
            assertTrue("$id: 01 예측 궤적 없음", t.vehicleFrames.any { t.snapshotAt(it.timestampMs).prediction?.predictions?.isNotEmpty() == true })
        }
    }

    /**
     * 03·01·05 연동: 모든 시나리오의 lane_keep 예측이
     *  - 한 차선 폭(4 m + 여유) 넘게 옆으로 뻗지 않고(여러 차선을 가로지르지 않음)
     *  - 전방으로 진행 중인 차량을 후진으로 예측하지 않는다.
     */
    @Test fun laneKeepPredictionsStayInLaneAndNeverReverse() {
        all().forEach { (id, r) ->
            val t = (r as LoadResult.Ready).timeline
            t.vehicleFrames.forEach { f ->
                val byId = f.vehicles.associateBy { it.id }
                t.snapshotAt(f.timestampMs).prediction?.predictions?.forEach { p ->
                    val v = byId[p.vehicleId] ?: return@forEach
                    val keep = p.trajectories.firstOrNull { it.mode == "lane_keep" } ?: return@forEach
                    keep.points.forEach { pt ->
                        assertTrue("$id@${f.timestampMs} ${p.vehicleId} 횡 ${pt.x - v.x}", abs(pt.x - v.x) <= 4.3)
                        if (v.vy >= 0) assertTrue("$id@${f.timestampMs} ${p.vehicleId} 후진 예측", pt.y >= v.y - 0.05)
                    }
                }
            }
        }
    }

    /** 03 안전 확인 차선 변경 판단(방향지시등·취소·중단)이 앱 이벤트 주석으로 들어온다. */
    @Test fun laneChangeDecisionsAppearAsAnnotations() {
        val labels = (loader.load("sim_lane_change_cancel") as LoadResult.Ready).timeline.manifest.eventAnnotation.map { it.label }
        assertTrue(labels.toString(), labels.any { "차선 변경 취소(시작 전)" in it })
        assertTrue(labels.toString(), labels.any { "차선 변경 중단" in it })
        val safe = (loader.load("sim_lane_change_safe") as LoadResult.Ready).timeline.manifest.eventAnnotation.map { it.label }
        assertTrue(safe.toString(), safe.any { "방향지시등 켬" in it } && safe.any { "차선 변경 완료" in it })
    }

    @Test fun dropoutScenarioHasStaleWindowAndUnknown() {
        val t = (loader.load("dropout") as LoadResult.Ready).timeline
        assertTrue(t.snapshotAt(5000).stale)
        assertTrue((0..t.durationMs step 100).any { ms -> t.snapshotAt(ms).risk?.riskObjects?.any { it.riskLevel.name == "UNKNOWN" } == true })
        // 7.0~8.0s car_02 관측 누락: 해당 프레임에 car_02 없음
        assertTrue(t.snapshotAt(7500).vehicle!!.vehicles.none { it.id == "car_02" })
    }

    @Test fun replayWalksWholeScenarioMonotonically() {
        val t = (loader.load("chain_reaction") as LoadResult.Ready).timeline
        var last = -1L
        for (ms in 0..t.durationMs step 33) {
            val ts = t.snapshotAt(ms).vehicle!!.timestampMs
            assertTrue(ts >= last && abs(ms - ts) < 100)
            last = ts
        }
    }
}

/** missing / invalid / stale JSON 오류 처리. tests/fixtures/scenarios/<case> */
class FixtureErrorHandlingTest {
    private val loader = loaderFor("../tests/fixtures").second

    private fun failed(case: String) = loader.load(case) as? LoadResult.Failed ?: fail("$case 는 실패해야 함") as Nothing
    private fun ready(case: String) = loader.load(case) as? LoadResult.Ready ?: fail("$case 는 재생 가능해야 함: ${loader.load(case)}") as Nothing

    @Test fun valid() { assertTrue(ready("valid").timeline.issues.isEmpty()) }
    @Test fun missingVehicleFileFails() { assertTrue(failed("missing_vehicle_file").issues.any { "파일 없음" in it.message }) }
    @Test fun missingPredictionFileStillPlaysWithWarning() {
        assertTrue(ready("missing_prediction_file").timeline.issues.any { it.severity == Severity.WARNING && "파일 없음" in it.message })
    }
    @Test fun badSchemaVersionFails() { assertTrue(failed("bad_schema_version").issues.any { "schema_version" in it.message }) }
    @Test fun unknownVehicleRefFails() { assertTrue(failed("unknown_vehicle_ref").issues.any { "ghost" in it.message }) }
    @Test fun egoMissingFails() { assertTrue(failed("ego_missing").issues.any { "ego" in it.message }) }
    @Test fun badRiskLevelFails() { failed("bad_risk_level") }
    @Test fun brokenJsonFailsWithLine() { assertTrue(failed("broken_json").issues.any { it.where.endsWith(":2") }) }
    @Test fun stalePredictionIsDroppedNotMixed() {
        val t = ready("stale_prediction").timeline
        assertTrue(t.issues.any { "9999" in it.where })
        val s = t.snapshotAt(9999)
        assertEquals(s.vehicle!!.timestampMs, s.prediction!!.timestampMs) // 9999 예측이 아닌, 표시 중인 VehicleFrame 시점의 예측
    }
    @Test fun gapIsPlayableButStale() { assertTrue(ready("gap").timeline.snapshotAt(250).stale) }
    @Test fun unknownScenarioFails() { failed("does_not_exist") }
    @Test fun singleJsonFilesAreAccepted() {
        val t = ready("single_json").timeline
        assertTrue(t.issues.isEmpty())
        assertEquals(200L, t.snapshotAt(200).prediction?.timestampMs) // prediction_frame.json = 200ms 프레임 하나
        assertEquals(null, t.snapshotAt(100).prediction)
        assertEquals(100L, t.snapshotAt(100).risk?.timestampMs)        // risk_frames.json = 배열
    }
}

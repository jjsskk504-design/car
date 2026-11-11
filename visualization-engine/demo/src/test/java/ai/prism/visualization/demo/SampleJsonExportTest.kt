package ai.prism.visualization.demo

import ai.prism.visualization.replay.ReplayJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mock 시나리오를 JSON 으로 내보내고, 데모 assets 의 샘플 파일이 최신인지 확인한다.
 * 생성 결과: demo/build/generated-scenarios/ (assets 갱신 시 이 폴더를 src/main/assets/scenarios 로 복사)
 */
class SampleJsonExportTest {

    private val assetsDir = File("src/main/assets/scenarios")
    private val outDir = File("build/generated-scenarios").apply { mkdirs() }

    @Test
    fun exportAndRoundTrip() {
        MockScenarios.all().forEach { s ->
            val json = ReplayJson.toJson(s)
            File(outDir, "${s.id}.json").writeText(json)
            val back = ReplayJson.parse(json)
            assertTrue("${s.id} warnings: ${back.warnings.take(3)}", back.warnings.isEmpty())
            assertEquals(s.frames.size, back.session.frames.size)
            assertEquals(s.id, back.session.id)
        }
    }

    @Test
    fun assetsAreUpToDate() {
        MockScenarios.all().forEach { s ->
            val asset = File(assetsDir, "${s.id}.json")
            assertTrue("missing asset ${asset.path} (run exportAndRoundTrip and copy)", asset.exists())
            assertEquals("asset ${s.id} is stale", ReplayJson.toJson(s), asset.readText())
        }
    }
}

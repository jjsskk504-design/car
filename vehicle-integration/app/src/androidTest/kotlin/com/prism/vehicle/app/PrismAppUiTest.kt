package com.prism.vehicle.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Simulation Mode 화면 흐름. SDK 를 호출하지 않으므로 일반 에뮬레이터·Pleos 에뮬레이터 어디서든 돈다. */
@RunWith(AndroidJUnit4::class)
class PrismAppUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(rule.activity)[PrismViewModel::class.java]

    private fun open(id: String) {
        rule.waitUntil(5_000) { vm.ui.value.scenarioIds.isNotEmpty() }
        rule.onNodeWithTag("scenario_list").performScrollToNode(hasTestTag("scenario_$id"))
        rule.onNodeWithTag("scenario_$id").performClick()
        rule.waitUntil(5_000) { vm.ui.value.screen != Screen.SELECT }
    }

    @Test fun listsAllScenarios() {
        rule.waitUntil(5_000) { vm.ui.value.scenarioIds.isNotEmpty() }
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithTag("scenario_normal").fetchSemanticsNode() }.isSuccess } // 첫 실행 시 창이 늦게 붙음
        listOf("normal", "rear_approach", "sudden_brake", "lane_change", "chain_reaction", "dropout").forEach {
            rule.onNodeWithTag("scenario_list").performScrollToNode(hasTestTag("scenario_$it")) // 좁은 창에서는 화면 밖 항목이 아직 그려지지 않음
            rule.onNodeWithTag("scenario_$it").assertExists()
        }
        rule.onNodeWithText("시뮬레이션 결과 재생 · 실시간 감지 아님").assertExists()
    }

    @Test fun playAdvancesTimeAndPauses() {
        open("chain_reaction")
        rule.onNodeWithText("MOCK 데이터").assertExists()
        rule.onNodeWithTag("play").performSemanticsAction(SemanticsActions.OnClick) // 차량 홈 TaskView 에서 좌표 터치 주입이 막히는 경우가 있어 semantics 클릭 사용
        rule.waitUntil(5_000) { vm.ui.value.clock.positionMs > 1_000 }
        // 재생 중에는 매 프레임 재구성이라 Compose 테스트가 'idle' 을 기다리면 재생이 끝나 버린다 → 일시정지는 UI 스레드에서 바로 호출
        rule.runOnUiThread { vm.pause() }
        val paused = vm.ui.value.clock.positionMs
        assertTrue("재생이 끝나기 전에 멈춰야 함: $paused", paused < 10_000)
        Thread.sleep(500)
        rule.waitForIdle()
        assertEquals(paused, vm.ui.value.clock.positionMs)
        rule.onNodeWithTag("play").assertExists() // 버튼이 '재생' 으로 돌아옴
    }

    @Test fun riskLevelChangesDuringPlayback() {
        open("lane_change")
        rule.runOnIdle { vm.seek(4_500) }
        rule.waitForIdle()
        rule.onNodeWithText("WARNING · 주변 차량 위험").assertExists()
    }

    @Test fun replayOnlyAfterParking() {
        open("sudden_brake")
        rule.onNodeWithTag("open_replay").assertIsNotEnabled()
        rule.onNodeWithTag("parked").performScrollTo().performClick()
        rule.onNodeWithTag("open_replay").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(3_000) { vm.ui.value.screen == Screen.REPLAY }
        rule.onNodeWithTag("speed_2.0").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(2f, vm.ui.value.clock.speed)
        rule.onNodeWithTag("seek").assertExists()
    }

    @Test fun dropoutShowsMissingData() {
        open("dropout")
        rule.runOnIdle { vm.seek(5_000) }
        rule.waitForIdle()
        rule.onNodeWithText("데이터 누락 — 현재 상태 알 수 없음").assertExists()
    }

    @Test fun unknownScenarioShowsErrorScreen() {
        rule.waitUntil(5_000) { vm.ui.value.scenarioIds.isNotEmpty() }
        rule.runOnIdle { vm.openScenario("does_not_exist") }
        rule.waitUntil(5_000) { vm.ui.value.screen == Screen.ERROR }
        rule.onNodeWithTag("error_title").assertExists()
        assertTrue(vm.ui.value.issues.any { "파일 없음" in it.message })
        rule.onNodeWithText("시나리오 목록").performClick()
        rule.waitUntil(3_000) { vm.ui.value.screen == Screen.SELECT }
    }

    @Test fun adasModeIsDisabled() {
        rule.onNodeWithText("ADAS Mode").assertIsNotEnabled()
    }
}

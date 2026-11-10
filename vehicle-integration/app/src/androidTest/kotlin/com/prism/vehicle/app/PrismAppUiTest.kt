package com.prism.vehicle.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        rule.onNodeWithTag("scenario_$id").performClick()
        rule.waitUntil(5_000) { vm.ui.value.screen != Screen.SELECT }
    }

    @Test fun listsAllScenarios() {
        rule.waitUntil(5_000) { vm.ui.value.scenarioIds.isNotEmpty() }
        listOf("normal", "rear_approach", "sudden_brake", "lane_change", "chain_reaction", "dropout").forEach {
            rule.onNodeWithTag("scenario_$it").assertExists()
        }
        rule.onNodeWithText("시뮬레이션 결과 재생 · 실시간 감지 아님").assertExists()
    }

    @Test fun playAdvancesTimeAndPauses() {
        open("chain_reaction")
        rule.onNodeWithText("MOCK 데이터").assertExists()
        rule.onNodeWithTag("play").performClick()
        rule.waitUntil(5_000) { vm.ui.value.clock.positionMs > 1_000 }
        rule.onNodeWithTag("pause").performClick()
        val paused = vm.ui.value.clock.positionMs
        Thread.sleep(500)
        rule.waitForIdle()
        assertEquals(paused, vm.ui.value.clock.positionMs)
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
        rule.onNodeWithTag("parked").performClick()
        rule.onNodeWithTag("open_replay").assertIsEnabled().performClick()
        rule.waitUntil(3_000) { vm.ui.value.screen == Screen.REPLAY }
        rule.onNodeWithTag("speed_2.0").performClick()
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

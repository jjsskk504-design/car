package ai.prism.visualization

import ai.prism.visualization.replay.ReplayScreen
import ai.prism.visualization.replay.ReplayState
import ai.prism.visualization.replay.ReplayTestTags
import ai.prism.visualization.replay.rememberReplayState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplayScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val session = TestData.session()

    @Test
    fun playPause_and_progressAdvances() {
        lateinit var state: ReplayState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            state = rememberReplayState(session.id)
            ReplayScreen(session, Modifier.size(1200.dp, 700.dp), state = state)
        }
        rule.onNodeWithTag(ReplayTestTags.PLAY).assertTextContains("재생").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(ReplayTestTags.PLAY).assertTextContains("일시정지")
        assertTrue("position=${state.positionMs}", state.positionMs in 700..1300)
        rule.onNodeWithTag(ReplayTestTags.PLAY).performClick()
        val stoppedAt = state.positionMs
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(stoppedAt, state.positionMs)
        assertFalse(state.playing)
    }

    @Test
    fun seekShowsCauseOfSelectedVehicle() {
        lateinit var state: ReplayState
        rule.setContent {
            state = rememberReplayState(session.id)
            ReplayScreen(session, Modifier.size(1200.dp, 700.dp), state = state)
        }
        rule.runOnIdle { state.positionMs = 2_500 } // 경고 구간
        rule.onNodeWithText("차량 A · 경고").assertIsDisplayed()
        rule.onNodeWithText("원인: 예상 경로 겹침").assertIsDisplayed()
        rule.onNodeWithText("영향을 준 차량: B").assertIsDisplayed()
        rule.onNodeWithTag(ReplayTestTags.TIME).assertTextContains("2.5 / 5.0초")
        rule.runOnIdle { state.positionMs = 4_800 } // 정상 구간
        rule.onNodeWithText("현재 강조된 위험 차량이 없습니다. 차량을 눌러 상세 정보를 볼 수 있습니다.").assertIsDisplayed()
    }

    @Test
    fun detailHiddenWhileDriving() {
        var stopped by mutableStateOf(false)
        rule.setContent { ReplayScreen(session, Modifier.size(1200.dp, 700.dp), isVehicleStopped = stopped) }
        rule.onNodeWithTag(ReplayTestTags.LOCKED).assertIsDisplayed()
        rule.onNodeWithTag(ReplayTestTags.PLAY).assertDoesNotExist()
        stopped = true
        rule.waitForIdle()
        rule.onNodeWithTag(ReplayTestTags.PLAY).assertIsDisplayed()
    }

    @Test
    fun lifecycle_stateSurvivesRecreation() {
        val tester = StateRestorationTester(rule)
        lateinit var state: ReplayState
        tester.setContent {
            state = rememberReplayState(session.id)
            ReplayScreen(session, Modifier.size(1200.dp, 700.dp), state = state)
        }
        rule.runOnIdle {
            state.positionMs = 2_300
            state.selectedVehicleId = "B"
            state.speed = 2f
        }
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(2_300L, state.positionMs)
            assertEquals("B", state.selectedVehicleId)
            assertEquals(2f, state.speed)
        }
        rule.onNodeWithText("차량 B · 정상").assertIsDisplayed()
    }
}

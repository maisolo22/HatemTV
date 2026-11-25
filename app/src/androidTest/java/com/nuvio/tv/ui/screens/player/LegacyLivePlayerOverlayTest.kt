package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.ui.screens.livetv.LiveTVChannel
import com.nuvio.tv.ui.screens.livetv.TVProgramme
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyLivePlayerOverlayTest {
    @get:Rule val compose = createComposeRule()
    @Test fun channelEpgAndActualPlaybackAddonAppearTogether() {
        val now = System.currentTimeMillis()
        val channel = LiveTVChannel("one", "channel", "My TV channel", "https://example.com", "Catalog provider")
        var pauses = 0
        compose.setContent {
            NuvioTheme {
                LegacyLivePlayerOverlay(channel, LegacyLivePlayerInfo(channel, listOf(
                    TVProgramme("Current programme", now - 1_800_000, now + 1_800_000),
                    TVProgramme("Next programme", now + 1_800_000, now + 3_600_000)), configured = true),
                    PlayerUiState(isPlaying = true, isBuffering = false, currentStreamAddonName = "Actual playback addon"),
                    PlayerMediaInfo(videoWidth = 1280, videoHeight = 720),
                    remember { FocusRequester() }, remember { FocusRequester() },
                    onPause = { pauses++ }, onGuide = {}, onAudio = {}, onSubtitles = {}, onSources = {}, onInfo = {},
                    onReload = {}, onAspect = {}, onInteraction = {}, onClearance = {})
            }
        }
        compose.onNodeWithText("My TV channel", substring = true).assertExists()
        compose.onNodeWithText("Actual playback addon", substring = true).assertExists()
        compose.onNodeWithText("Current programme", substring = true).assertExists()
        compose.onNodeWithText("Next programme", substring = true).assertExists()
        compose.onNodeWithText("1280 × 720", substring = true).assertExists()
        compose.onNodeWithText("Pause").performClick()
        compose.runOnIdle { assertEquals(1, pauses) }
    }
}

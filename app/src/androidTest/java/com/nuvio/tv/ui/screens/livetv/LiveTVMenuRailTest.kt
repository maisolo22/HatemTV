package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveTVMenuRailTest {
    @get:Rule val compose = createComposeRule()
    private val initial = listOf(TVMenuEntry("fr", "France"), TVMenuEntry("sport", "Sport"), TVMenuEntry("it", "Italia"))

    @Test fun movingKeepsCategorySelectionAndCommitsOnlyWithFreshOk() {
        var saved: List<String>? = null
        var selected = "fr"
        compose.setContent {
            var entries by remember { mutableStateOf(initial) }
            NuvioTheme {
                LiveTVMenuRail(entries, selected, remember { FocusRequester() }, onSelect = { selected = it },
                    onReorder = { keys -> saved = keys; entries = keys.map { key -> initial.first { it.key == key } } },
                    onChannels = { false }, onNewFolder = {}, modifier = Modifier.width(240.dp).height(440.dp))
            }
        }
        val sport = compose.onNodeWithText("Sport")
        sport.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        sport.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        sport.performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertNull(saved); assertEquals("fr", selected) }
        sport.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf("fr", "it", "sport"), saved); assertEquals("fr", selected) }
        // A second move cancelled with Back leaves the committed order unchanged.
        sport.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        sport.performKeyInput { pressKey(Key.DirectionUp) }
        sport.performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle { assertEquals(listOf("fr", "it", "sport"), saved) }
    }
}

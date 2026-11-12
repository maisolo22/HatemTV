package com.nuvio.tv.ui.screens.account

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.ServerCapabilities
import com.nuvio.tv.domain.model.ServerConfiguration
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerConnectionDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun verificationAndTrustStayReachableInShortDialog() {
        val serverUrl = "http://custom.example.com"
        val server = ServerConfiguration(
            backendUrl = serverUrl,
            publishableKey = "test",
            capabilities = ServerCapabilities(emailPasswordAuth = true, tvLogin = false),
            isCustom = true,
            discoveryUrl = "$serverUrl/.well-known/nuvio"
        )
        val state = mutableStateOf(
            ServerConnectionUiState(
                activeServer = server,
                error = List(5) { "Server verification could not be completed." }.joinToString(" ")
            )
        )
        var checkedUrl: String? = null
        var confirmations = 0

        composeRule.setContent {
            val shortScreen = Configuration(LocalConfiguration.current).apply { screenHeightDp = 360 }
            val density = Density(LocalDensity.current.density, fontScale = 1.4f)
            CompositionLocalProvider(
                LocalConfiguration provides shortScreen,
                LocalDensity provides density,
                LocalLayoutDirection provides LayoutDirection.Ltr
            ) {
                NuvioTheme {
                    ServerConnectionDialog(
                        uiState = state.value,
                        onDiscover = { url ->
                            checkedUrl = url
                            state.value = state.value.copy(discoveredServer = server, error = null)
                        },
                        onConnect = { confirmations++ },
                        onDismiss = {}
                    )
                }
            }
        }

        val check = button(R.string.custom_server_check).assertIsDisplayed()
        check.performSemanticsAction(SemanticsActions.RequestFocus)
        check.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            assertEquals(serverUrl, checkedUrl)
            assertEquals(0, confirmations)
        }

        val cancel = button(R.string.action_cancel).assertIsDisplayed()
        val trust = button(R.string.custom_server_trust_action).assertIsDisplayed()
        cancel.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        trust.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    private fun button(resource: Int) = composeRule.onNodeWithText(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
    )
}

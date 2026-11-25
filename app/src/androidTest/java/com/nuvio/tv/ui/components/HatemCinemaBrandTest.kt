package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HatemCinemaBrandTest {
    @get:Rule val compose = createComposeRule()
    @Test fun startupShowsExactBrandAndDiscreetAuthorInTopRightCorner() {
        compose.setContent { NuvioTheme { StartupSplashScreen(null, null) } }
        compose.onNodeWithText("Hatem Multi Media").assertIsDisplayed()
        val credit = compose.onNodeWithText("Application codé par El Habib Hatem").assertIsDisplayed()
        val creditBounds = credit.getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(creditBounds.left + creditBounds.right > root.left + root.right)
        assertTrue(creditBounds.top + creditBounds.bottom < root.top + root.bottom)
    }
    @Test fun profileHeaderUsesTheSameExactAuthorCredit() {
        compose.setContent {
            NuvioTheme {
                Box(Modifier.fillMaxSize()) {
                    HatemCinemaBackground()
                    HatemAuthorCredit(Modifier.align(AbsoluteAlignment.TopRight))
                }
            }
        }
        compose.onNodeWithText("Application codé par El Habib Hatem").assertIsDisplayed()
    }
}

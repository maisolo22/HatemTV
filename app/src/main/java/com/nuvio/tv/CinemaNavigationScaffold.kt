package com.nuvio.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CinemaNavigationScaffold(
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    items: List<DrawerItem>,
    activeProfileName: String,
    showProfileSelector: Boolean,
    onSwitchProfile: () -> Unit,
    onExitApp: () -> Unit
) {
    val showMenu = items.any { it.route == currentRoute }
    val showOverArtwork = currentRoute == Screen.Home.route
    val menuHeight = 64.dp
    val contentFocusRequester = remember { FocusRequester() }
    val menuRequesters = remember(items) { items.associate { it.route to FocusRequester() } }
    var menuHasFocus by remember { mutableStateOf(false) }

    fun openTab(route: String) {
        if (route == currentRoute) return
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(showMenu) {
        if (!showMenu) menuHasFocus = false
    }

    BackHandler(enabled = showMenu) {
        when {
            !menuHasFocus -> {
                val target = currentRoute?.let(menuRequesters::get) ?: menuRequesters.getValue(Screen.Home.route)
                runCatching { target.requestFocus() }
            }
            currentRoute != Screen.Home.route -> openTab(Screen.Home.route)
            else -> onExitApp()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = if (showMenu && !showOverArtwork) menuHeight else 0.dp)
        ) {
            CompositionLocalProvider(
                LocalSidebarExpanded provides (showMenu && menuHasFocus),
                LocalContentFocusRequester provides contentFocusRequester
            ) {
                NuvioNavHost(navController, startDestination)
            }
        }

        if (showMenu) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(menuHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xF5101010), if (showOverArtwork) Color.Transparent else Color(0xFF101010))
                        )
                    )
                    .padding(horizontal = 36.dp)
                    .onFocusChanged { menuHasFocus = it.hasFocus }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (item in items) {
                    CinemaTab(
                        label = item.label,
                        selected = currentRoute == item.route,
                        modifier = Modifier.focusRequester(menuRequesters.getValue(item.route)),
                        onClick = { openTab(item.route) }
                    )
                }
                if (showProfileSelector && activeProfileName.isNotBlank()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        CinemaTab(
                            label = activeProfileName,
                            selected = false,
                            modifier = Modifier.widthIn(max = 144.dp),
                            onClick = onSwitchProfile
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CinemaTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.BottomCenter) {
        Button(
            onClick = onClick,
            modifier = modifier.height(38.dp).semantics { this.selected = selected },
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (selected) Color.White else Color(0xFFD4D4D4),
                focusedContainerColor = Color.White,
                focusedContentColor = Color(0xFF101010)
            ),
            scale = ButtonDefaults.scale(focusedScale = 1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Box(
                Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .background(Color(0xFFE5092D))
            )
        }
    }
}

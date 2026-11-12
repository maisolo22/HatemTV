package com.nuvio.tv.ui.components

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.contentTextDirection

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.screens.ambient.AmbientDialogEffect
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NuvioDialog(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    width: Dp = 520.dp,
    titleTextAlign: TextAlign = TextAlign.Start,
    suppressFirstKeyUp: Boolean = true,
    usePlatformDefaultWidth: Boolean = true,
    containerBrush: Brush? = null,
    containerBorderColor: Color? = null,
    containerBorderWidth: Dp = NuvioTheme.spacing.hairline,
    containerCornerRadius: Dp = NuvioTheme.radii.xl,
    contentPadding: Dp = NuvioTheme.spacing.xl,
    contentSpacing: Dp = NuvioTheme.spacing.lg,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var isReady by remember { mutableStateOf(!suppressFirstKeyUp) }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp.dp - NuvioTheme.spacing.xxxl).coerceAtLeast(320.dp)
    val containerShape = RoundedCornerShape(containerCornerRadius)
    val backgroundModifier = if (containerBrush == null) {
        Modifier.background(NuvioTheme.colors.BackgroundElevated, containerShape)
    } else {
        Modifier.background(containerBrush, containerShape)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth)
    ) {
        AmbientDialogEffect(onDismiss)
        Box(
            modifier = Modifier
                .width(width)
                .heightIn(max = maxDialogHeight)
                .clip(containerShape)
                .then(backgroundModifier)
                .border(
                    containerBorderWidth,
                    containerBorderColor ?: NuvioTheme.colors.Border,
                    containerShape
                )
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isSelectKey(native.keyCode) || native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                        if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                            isReady = true
                        }
                        if (!isReady) {
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
        ) {
            backgroundContent()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                val body: @Composable ColumnScope.() -> Unit = {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                textDirection = title.contentTextDirection()
                            ),
                            color = NuvioTheme.colors.TextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = titleTextAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }

                    content()
                }

                if (actions == null) {
                    body()
                } else {
                    ScrollableDialogBody(
                        modifier = Modifier.weight(1f, fill = false),
                        contentSpacing = contentSpacing,
                        content = body
                    )
                    actions()
                }
            }
        }
    }
}

@Composable
private fun ScrollableDialogBody(
    modifier: Modifier,
    contentSpacing: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var bodyFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (!bodyFocused || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val page = (scroll.viewportSize / 2f).coerceAtLeast(1f)
                val distance = when {
                    event.key == Key.DirectionDown && scroll.canScrollForward -> page
                    event.key == Key.DirectionUp && scroll.canScrollBackward -> -page
                    else -> return@onKeyEvent false
                }
                scope.launch { scroll.scrollBy(distance) }
                true
            }
            .onFocusChanged { bodyFocused = it.isFocused }
            .focusable(scroll.canScrollForward || scroll.canScrollBackward)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
        content = content
    )
}

private fun isSelectKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

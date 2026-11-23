package com.nuvio.tv.ui.screens.livetv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.nuvio.tv.R

internal data class TVMenuEntry(val key: String, val label: String)

/** A move is a local draft until a fresh OK press confirms it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTVMenuRail(
    entries: List<TVMenuEntry>, selectedKey: String, entryFocus: FocusRequester,
    onSelect: (String) -> Unit, onReorder: (List<String>) -> Unit,
    onChannels: () -> Boolean, onNewFolder: () -> Unit, modifier: Modifier = Modifier
) {
    val keys = entries.map { it.key }
    var draft by remember { mutableStateOf(keys) }
    var moving by remember { mutableStateOf<String?>(null) }
    var selectHeld by remember { mutableStateOf(false) }
    var openingRelease by remember { mutableStateOf(false) }
    var confirmDown by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    val requests = remember(keys.toSet()) { keys.associateWith { FocusRequester() } }
    val visibleKeys = if (moving != null) draft else keys
    val byKey = entries.associateBy { it.key }
    fun cancel() { moving = null; draft = keys; confirmDown = false; openingRelease = false }
    fun commit() { val order = draft; moving = null; confirmDown = false; onReorder(order) }
    LaunchedEffect(keys) { cancel() }
    LaunchedEffect(visibleKeys, moving) {
        val key = moving ?: return@LaunchedEffect
        val index = visibleKeys.indexOf(key)
        if (index < 0) return@LaunchedEffect
        if (scroll.layoutInfo.visibleItemsInfo.none { it.key == key }) scroll.scrollToItem(index)
        withFrameNanos { }
        runCatching { requests[key]?.requestFocus() }
    }
    BackHandler(enabled = moving != null) { cancel() }
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF161619)).padding(8.dp)
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val down = native.action == KeyEvent.ACTION_DOWN
            val up = native.action == KeyEvent.ACTION_UP
            val select = native.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
            val moveKey = moving
            if (select) {
                if (moveKey == null) { selectHeld = down; false }
                else if (openingRelease) {
                    if (up) { openingRelease = false; selectHeld = false }
                    // Let Card finish the initiating long press; its onClick is guarded below.
                    !up
                } else {
                    if (down && native.repeatCount == 0) confirmDown = true
                    if (up) { selectHeld = false; if (confirmDown) commit() }
                    true
                }
            } else if (moveKey != null) {
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down) draft = moveTVMenuKey(draft, moveKey, if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { if (up) cancel(); true }
                    else -> false
                }
            } else if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && down) onChannels()
            else false
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (moving == null) R.string.tv_menu_hint else R.string.tv_menu_move_hint),
            style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFC0C7), modifier = Modifier.padding(4.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().focusRequester(entryFocus).focusRestorer().focusGroup(),
            state = scroll, contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleKeys, key = { it }) { key ->
                val entry = byKey[key] ?: return@items
                val active = key == selectedKey
                val dragged = key == moving
                Card(onClick = { if (moving == null) onSelect(key) }, onLongClick = {
                    if (moving == null) {
                        draft = keys; moving = key; openingRelease = selectHeld; confirmDown = false
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(requests.getValue(key)),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)), scale = CardDefaults.scale(focusedScale = 1f),
                    colors = CardDefaults.colors(containerColor = if (dragged) Color(0xFF785A1D) else if (active) Color(0xFF434348) else Color(0xFF222225),
                        contentColor = Color.White, focusedContainerColor = if (dragged) Color(0xFFFFD27A) else Color.White,
                        focusedContentColor = Color(0xFF141416))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (dragged) "↕" else if (active) "●" else "", modifier = Modifier.width(14.dp), style = MaterialTheme.typography.labelMedium)
                        Text(entry.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(key = "new-folder") {
                TVPill("+ " + stringResource(R.string.tv_folder_new), modifier = Modifier.fillMaxWidth(), onClick = onNewFolder)
            }
        }
    }
}

package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.collection.NuvioTextField
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTVFoldersDialog(library: LiveTVLibrary, viewModel: LiveTVLibraryViewModel, onDismiss: () -> Unit) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<LiveTVFolder?>(null) }
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.requestFocus() }
    val clean = name.trim().take(60)
    val valid = clean.isNotEmpty() && library.folders.none { it.id != editingId && it.name.equals(clean, true) }
    NuvioDialog(onDismiss, title = stringResource(R.string.tv_folders), actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onDismiss, Modifier.focusRequester(cancel)) { Text(stringResource(R.string.tv_cancel)) }
            Button(onClick = {
                if (editingId == null) viewModel.create(clean) else viewModel.rename(editingId!!, clean)
                editingId = null; name = ""
            }, enabled = valid) { Text(stringResource(if (editingId == null) R.string.tv_folder_new else R.string.tv_rename)) }
        }
    }) {
        Text(stringResource(R.string.tv_folders_hint), style = MaterialTheme.typography.bodyMedium)
        NuvioTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), stringResource(R.string.tv_folder_name))
        for (folder in library.folders) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { editingId = folder.id; name = folder.name }, modifier = Modifier.weight(1f)) {
                Text("${folder.name} · ${folder.channels.size}")
            }
            Button(onClick = { deleting = folder }) { Text(stringResource(R.string.tv_delete)) }
        }
    }
    deleting?.let { folder ->
        NuvioDialog(onDismiss = { deleting = null }, title = stringResource(R.string.tv_delete), actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { deleting = null }) { Text(stringResource(R.string.tv_cancel)) }
                Button(onClick = { viewModel.delete(folder.id); if (editingId == folder.id) { editingId = null; name = "" }; deleting = null }) {
                    Text(stringResource(R.string.tv_delete))
                }
            }
        }) { Text(stringResource(R.string.tv_delete_confirm, folder.name)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTVChannelDialog(channel: LiveTVChannel, library: LiveTVLibrary, viewModel: LiveTVLibraryViewModel, onDismiss: () -> Unit) {
    var folderName by remember(channel.key) { mutableStateOf("") }
    var epgId by remember(channel.key) { mutableStateOf(library.epgIds[channel.key].orEmpty()) }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { close.requestFocus() }
    val clean = folderName.trim().take(60)
    NuvioDialog(onDismiss, title = channel.name, subtitle = stringResource(R.string.tv_channel_folder_hint), actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onDismiss, Modifier.focusRequester(close)) { Text(stringResource(R.string.tv_close)) }
            Button(onClick = { viewModel.channelEpgId(channel, epgId); onDismiss() }) { Text(stringResource(R.string.tv_save)) }
        }
    }) {
        if (library.folders.isEmpty()) Text(stringResource(R.string.tv_folders_hint))
        for (folder in library.folders) {
            val contains = folder.channels.any { it.key == channel.key }
            Button(onClick = { viewModel.toggle(folder.id, channel) }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (contains) "✓" else "+"} ${folder.name}")
            }
        }
        NuvioTextField(folderName, { folderName = it.take(60) }, Modifier.fillMaxWidth(), stringResource(R.string.tv_folder_name))
        Button(onClick = { viewModel.create(clean, channel); folderName = "" },
            enabled = clean.isNotEmpty() && library.folders.none { it.name.equals(clean, true) }) {
            Text(stringResource(R.string.tv_folder_create_add))
        }
        Text(stringResource(R.string.tv_epg_id_hint), style = MaterialTheme.typography.bodySmall)
        NuvioTextField(epgId, { epgId = it.take(256) }, Modifier.fillMaxWidth(), stringResource(R.string.tv_epg_id))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTVEpgDialog(url: String, onSave: (String) -> Unit, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(url) }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { close.requestFocus() }
    val valid = draft.isBlank() || draft.trim().toHttpUrlOrNull() != null
    NuvioDialog(onDismiss, title = stringResource(R.string.tv_epg), actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onDismiss, Modifier.focusRequester(close)) { Text(stringResource(R.string.tv_cancel)) }
            Button(onClick = { onSave(draft.trim()) }, enabled = valid) { Text(stringResource(R.string.tv_save)) }
            Button(onClick = onRefresh, enabled = url.isNotBlank()) { Text(stringResource(R.string.tv_refresh)) }
        }
    }) {
        Text(stringResource(R.string.tv_epg_source_hint), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { draft = "https://epgshare01.online/epgshare01/epg_ripper_FR1.xml.gz" }) { Text(stringResource(R.string.tv_france)) }
            Button(onClick = { draft = "https://epgshare01.online/epgshare01/epg_ripper_IT1.xml.gz" }) { Text(stringResource(R.string.tv_italy)) }
            Button(onClick = { draft = "" }) { Text(stringResource(R.string.tv_disable)) }
        }
        NuvioTextField(draft, { draft = it.take(4096) }, Modifier.fillMaxWidth(), stringResource(R.string.tv_epg_url))
        Text(stringResource(R.string.tv_epg_matching_hint), style = MaterialTheme.typography.bodySmall)
    }
}

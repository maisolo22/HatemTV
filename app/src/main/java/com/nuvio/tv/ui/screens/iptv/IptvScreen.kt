package com.nuvio.tv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.collection.NuvioTextField
import com.nuvio.tv.ui.screens.livetv.LegacyTVChannelCard
import com.nuvio.tv.ui.screens.livetv.LiveTVEpgDialog
import com.nuvio.tv.ui.screens.livetv.LiveTVSearchDialog
import com.nuvio.tv.ui.screens.livetv.LiveTVViewMode
import com.nuvio.tv.ui.screens.livetv.TVBackground
import com.nuvio.tv.ui.screens.livetv.TVPill
import com.nuvio.tv.ui.screens.livetv.TVProgramme
import com.nuvio.tv.ui.screens.livetv.channelStartsWith
import com.nuvio.tv.ui.screens.livetv.tvClock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun IptvScreen(viewModel: IptvViewModel = hiltViewModel(), onPlay: (IptvPlayback) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profileId = state.profileId
    val selected = state.selected
    val channels = state.catalog?.channels.orEmpty()
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    var manage by remember(profileId) { mutableStateOf(false) }
    var editing by remember(profileId) { mutableStateOf<IptvPlaylist?>(null) }
    var confirmingDelete by remember(profileId) { mutableStateOf(false) }
    var search by remember(profileId) { mutableStateOf(false) }
    var editGuide by remember(profileId, selected?.id) { mutableStateOf(false) }
    var details by remember(profileId, selected?.id) { mutableStateOf<IptvChannel?>(null) }
    var query by rememberSaveable(profileId, selected?.id) { mutableStateOf("") }
    var group by rememberSaveable(profileId, selected?.id) { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable(profileId, selected?.id) { mutableStateOf(false) }
    var guideMode by rememberSaveable(profileId) { mutableStateOf(true) }
    var timeOffset by rememberSaveable(profileId, selected?.id) { mutableLongStateOf(0L) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val windowStart = now / HalfHour * HalfHour + timeOffset
    val groupChannels = remember(channels, group, favoritesOnly, state.favorites) {
        channels.filter { channel ->
            (group == null || channel.group == group) &&
                (!favoritesOnly || channel.key in state.favorites)
        }
    }
    val visible = remember(groupChannels, query) {
        if (query.isBlank()) groupChannels else groupChannels.filter { channelStartsWith(it.guideChannel.name, query) }
    }
    val schedules by produceState(emptyMap<String, List<TVProgramme>>(), state.guide, visible, now) {
        value = withContext(Dispatchers.Default) {
            visible.associate { it.key to state.guide.schedule(it.guideChannel, now = now) }
        }
    }
    val contentFocus = LocalContentFocusRequester.current
    val noGroup = stringResource(R.string.iptv_uncategorized)

    Column(
        Modifier.fillMaxSize().background(TVBackground).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.iptv_title), style = MaterialTheme.typography.headlineSmall)
            LazyRow(
                Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                item {
                    TVPill(stringResource(R.string.iptv_playlists), modifier = Modifier.focusRequester(contentFocus)) {
                        editing = null
                        manage = true
                    }
                }
                if (selected != null) {
                    item { TVPill(stringResource(R.string.tv_epg)) { editGuide = true } }
                    item { TVPill(stringResource(R.string.tv_search), query.isNotBlank()) { search = true } }
                    item { TVPill(stringResource(R.string.tv_guide), guideMode) { guideMode = true } }
                    item { TVPill(stringResource(R.string.tv_list), !guideMode) { guideMode = false } }
                    item { TVPill(stringResource(R.string.tv_refresh)) { viewModel.refresh() } }
                }
            }
            Text(tvClock(now), style = MaterialTheme.typography.titleMedium)
        }
        val status = when {
            state.storageFailed -> stringResource(R.string.tv_save_failed)
            state.playlistFailed -> stringResource(R.string.iptv_load_error)
            state.loadingGuide -> stringResource(R.string.tv_epg_loading)
            state.guideFailed -> stringResource(R.string.tv_epg_failed)
            state.guidePartial -> stringResource(R.string.tv_epg_partial)
            state.guideStale -> stringResource(R.string.tv_epg_stale)
            selected != null -> stringResource(R.string.iptv_source_count, selected.name, visible.size)
            else -> null
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        when {
            state.profileLoading || (state.loading && channels.isEmpty()) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            channels.isEmpty() -> Text(
                stringResource(if (state.playlistFailed) R.string.iptv_check_link else R.string.iptv_empty),
                style = MaterialTheme.typography.bodyLarge
            )
            else -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(
                    Modifier.width(198.dp).fillMaxHeight().focusRestorer(),
                    contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item("all") {
                        TVPill(stringResource(R.string.tv_all_channels), group == null && !favoritesOnly, Modifier.fillMaxWidth()) {
                            group = null
                            favoritesOnly = false
                        }
                    }
                    item("favorites") {
                        TVPill(stringResource(R.string.iptv_favorites), favoritesOnly, Modifier.fillMaxWidth()) {
                            group = null
                            favoritesOnly = true
                        }
                    }
                    items(groups, key = { "group:$it" }) { name ->
                        TVPill(name.ifEmpty { noGroup }, group == name && !favoritesOnly, Modifier.fillMaxWidth()) {
                            group = name
                            favoritesOnly = false
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (guideMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TVPill(stringResource(R.string.tv_previous_window)) { timeOffset = (timeOffset - HalfHour).coerceAtLeast(-6 * Hour) }
                            TVPill(stringResource(R.string.tv_now)) { timeOffset = 0L }
                            TVPill(stringResource(R.string.tv_next_window)) { timeOffset = (timeOffset + HalfHour).coerceAtMost(45 * Hour) }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Spacer(Modifier.width(170.dp))
                            repeat(6) { slot ->
                                Text(tvClock(windowStart + slot * HalfHour), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (visible.isEmpty()) Text(stringResource(if (favoritesOnly) R.string.iptv_no_favorites else R.string.tv_search_empty))
                    key(profileId, selected?.id, query, group, favoritesOnly) {
                        LazyColumn(
                            Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visible, key = { it.key }) { channel ->
                                LegacyTVChannelCard(
                                    channel.guideChannel, schedules[channel.key].orEmpty(), now,
                                    if (guideMode) LiveTVViewMode.GUIDE else LiveTVViewMode.LIST,
                                    windowStart, windowStart + 3 * Hour,
                                    onFocus = {},
                                    onPlay = { viewModel.startPlayback(channel, groupChannels)?.let(onPlay) },
                                    onOptions = { details = channel }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (search) LiveTVSearchDialog(query, { query = it }, onDismiss = { search = false })
    if (editGuide && selected != null) LiveTVEpgDialog(
        urls = selected.epgSources,
        onSave = { viewModel.saveGuideSources(it); editGuide = false },
        onRefresh = { viewModel.refreshGuide(); editGuide = false },
        onDismiss = { editGuide = false }
    )
    if (manage) {
        val dismiss = { manage = false; editing = null }
        val target = editing
        if (target != null) {
            IptvPlaylistEditor(target, onSave = { viewModel.save(it); dismiss() }, onDismiss = dismiss,
                onDelete = if (state.settings.playlists.any { it.id == target.id }) { { confirmingDelete = true } } else null)
        } else NuvioDialog(onDismiss = dismiss, title = stringResource(R.string.iptv_playlists)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (playlist in state.settings.playlists) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.select(playlist); dismiss() }, modifier = Modifier.weight(1f)) {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = { editing = playlist }, enabled = !state.saving) { Text(stringResource(R.string.iptv_edit)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = dismiss) { Text(stringResource(R.string.tv_close)) }
                Button(onClick = { editing = IptvPlaylist(UUID.randomUUID().toString(), "", "") },
                    enabled = !state.profileLoading && !state.saving && state.settings.playlists.size < 32) {
                    Text(stringResource(R.string.iptv_add_playlist))
                }
            }
        }
        if (confirmingDelete && target != null) NuvioDialog(
            onDismiss = { confirmingDelete = false },
            title = stringResource(R.string.tv_delete),
            subtitle = stringResource(R.string.iptv_delete_playlist, target.name)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.tv_cancel)) }
                Button(onClick = { viewModel.remove(target); confirmingDelete = false; dismiss() }) {
                    Text(stringResource(R.string.tv_delete))
                }
            }
        }
    }
    details?.let { channel ->
        val current = schedules[channel.key].orEmpty().firstOrNull { now >= it.start && now < it.end }
        NuvioDialog(onDismiss = { details = null }, title = channel.guideChannel.name, subtitle = current?.title) {
            current?.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { details = null }) { Text(stringResource(R.string.tv_close)) }
                Button(onClick = { viewModel.toggleFavorite(channel); details = null }, enabled = !state.saving) {
                    Text(stringResource(if (channel.key in state.favorites) R.string.iptv_remove_favorite else R.string.iptv_add_favorite))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IptvPlaylistEditor(
    playlist: IptvPlaylist, onSave: (IptvPlaylist) -> Unit, onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(playlist) { mutableStateOf(playlist.name) }
    var url by remember(playlist) { mutableStateOf(playlist.url) }
    val playlistUrl = remember(url) { url.trim().toHttpUrlOrNull() }
    NuvioDialog(onDismiss, title = stringResource(R.string.iptv_playlists), subtitle = stringResource(R.string.iptv_link_hint)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NuvioTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), stringResource(R.string.iptv_playlist_name))
            NuvioTextField(url, { url = it.take(8192) }, Modifier.fillMaxWidth(), stringResource(R.string.iptv_m3u_url))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onDismiss) { Text(stringResource(R.string.tv_cancel)) }
            Button(
                onClick = { onSave(playlist.copy(name = name.trim(), url = playlistUrl.toString())) },
                enabled = name.isNotBlank() && playlistUrl != null
            ) { Text(stringResource(R.string.tv_save)) }
            if (onDelete != null) Button(onDelete) { Text(stringResource(R.string.tv_delete)) }
        }
    }
}

private const val HalfHour = 1_800_000L
private const val Hour = 2 * HalfHour

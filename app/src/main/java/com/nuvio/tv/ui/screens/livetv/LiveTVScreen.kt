package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val TVBackground = Color(0xFF0D0D0F)
internal fun tvClock(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
internal data class TVTimelineSlot(val programme: TVProgramme, val offset: Float, val width: Float)
internal fun tvTimelineSlots(programmes: List<TVProgramme>, start: Long, end: Long): List<TVTimelineSlot> {
    if (end <= start) return emptyList()
    return programmes.filter { it.end > start && it.start < end }.map {
        TVTimelineSlot(it, ((maxOf(it.start, start) - start).toDouble() / (end - start)).toFloat(),
            ((minOf(it.end, end) - maxOf(it.start, start)).toDouble() / (end - start)).toFloat())
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTVScreen(
    viewModel: LiveTVViewModel = hiltViewModel(),
    libraryViewModel: LiveTVLibraryViewModel = hiltViewModel(),
    onManageAddons: () -> Unit,
    onPlay: (LiveTVChannel) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scoped by libraryViewModel.settings.collectAsStateWithLifecycle()
    val epg by libraryViewModel.epg.collectAsStateWithLifecycle()
    val epgLoading by libraryViewModel.epgLoading.collectAsStateWithLifecycle()
    val epgFailed by libraryViewModel.epgFailed.collectAsStateWithLifecycle()
    val epgStale by libraryViewModel.epgStale.collectAsStateWithLifecycle()
    val epgPartial by libraryViewModel.epgPartial.collectAsStateWithLifecycle()
    val saveFailed by libraryViewModel.saveFailed.collectAsStateWithLifecycle()
    val library = scoped.library
    val roomyScreen = LocalConfiguration.current.screenHeightDp >= 700
    var showInformation by rememberSaveable(scoped.profileId) { mutableStateOf(roomyScreen) }
    var folderId by rememberSaveable(scoped.profileId) { mutableStateOf<String?>(null) }
    var allChannelsSelected by rememberSaveable(scoped.profileId) { mutableStateOf(false) }
    var focusedKey by rememberSaveable(scoped.profileId) { mutableStateOf<String?>(null) }
    var windowOffset by rememberSaveable(scoped.profileId) { mutableLongStateOf(0L) }
    var manageFolders by remember(scoped.profileId) { mutableStateOf(false) }
    var epgDialog by remember(scoped.profileId) { mutableStateOf(false) }
    var channelOptions by remember(scoped.profileId) { mutableStateOf<LiveTVChannel?>(null) }
    var returnToChannel by rememberSaveable(scoped.profileId) { mutableStateOf(false) }
    val channelFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    val sidebarExpanded = LocalSidebarExpanded.current
    val contentFocus = LocalContentFocusRequester.current
    LaunchedEffect(scoped.profileId) {
        if (scoped.profileId != 0 && !sidebarExpanded) {
            repeat(2) { withFrameNanos { } }
            val target = if (returnToChannel) channelFocus else firstAction
            if (runCatching { target.requestFocus() }.isFailure) runCatching { firstAction.requestFocus() }
            returnToChannel = false
        }
    }
    LaunchedEffect(library.folders) { if (folderId != null && library.folders.none { it.id == folderId }) folderId = null }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val windowStart = now / 1_800_000L * 1_800_000L + windowOffset
    val windowEnd = windowStart + 3 * 3_600_000L
    val activeBases = remember(state.sources) { state.sources.map { it.addon.baseUrl }.toSet() }
    val channels = remember(state.row, state.selected, library.folders, folderId, activeBases) {
        if (folderId != null) library.folders.firstOrNull { it.id == folderId }?.channels.orEmpty()
            .filter { it.addonBaseUrl in activeBases }
        else state.selected?.let { source -> state.row?.items.orEmpty().map { LiveTVChannel.from(it, source) } }.orEmpty()
    }
    LaunchedEffect(scoped.profileId, channels, library.folders) {
        libraryViewModel.targets(scoped.profileId, channels + library.folders.flatMap { it.channels }.filter { it.addonBaseUrl in activeBases })
    }
    val selected = channels.firstOrNull { it.key == focusedKey } ?: channels.firstOrNull()
    val schedule = selected?.let { epg.schedule(it, library.epgIds[it.key]) }.orEmpty()
    val current = schedule.firstOrNull { it.start <= now && it.end > now }
    val next = schedule.firstOrNull { it.start > now }

    Column(Modifier.fillMaxSize().background(TVBackground).padding(horizontal = 30.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tv_guide_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.width(16.dp))
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                item { TVPill(stringResource(R.string.tv_list), library.viewMode == LiveTVViewMode.LIST,
                    Modifier.focusRequester(contentFocus).focusRequester(firstAction)) { libraryViewModel.mode(LiveTVViewMode.LIST) } }
                item { TVPill(stringResource(R.string.tv_mosaic), library.viewMode == LiveTVViewMode.MOSAIC) { libraryViewModel.mode(LiveTVViewMode.MOSAIC) } }
                item { TVPill(stringResource(R.string.tv_guide), library.viewMode == LiveTVViewMode.GUIDE) { libraryViewModel.mode(LiveTVViewMode.GUIDE) } }
                item { TVPill(stringResource(R.string.tv_details), showInformation) { showInformation = !showInformation } }
                item { TVPill(stringResource(R.string.tv_folders)) { manageFolders = true } }
                item { TVPill(stringResource(R.string.tv_epg)) { epgDialog = true } }
                item { TVPill(stringResource(R.string.legacy_addons)) { onManageAddons() } }
            }
            Text(tvClock(now), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        if (showInformation) {
        Row(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF161619))) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(selected?.name ?: stringResource(R.string.legacy_live_tv), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(current?.title ?: stringResource(R.string.tv_no_programme), style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(if (current != null) "${tvClock(current.start)} – ${tvClock(current.end)}" else selected?.addonName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBFC0C7))
                if (current != null) TVProgrammeProgress(current, now)
                Text(next?.let { stringResource(R.string.tv_next_programme, tvClock(it.start), it.title) }
                    ?: stringResource(R.string.tv_channel_options_hint),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFBFC0C7))
            }
            Box(Modifier.width(220.dp).fillMaxHeight()) {
                AsyncImage(selected?.artwork ?: selected?.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF161619), Color.Transparent))))
                if (selected != null) AsyncImage(selected.logo, null, Modifier.size(80.dp).align(Alignment.Center), contentScale = ContentScale.Fit)
            }
        }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (library.viewMode == LiveTVViewMode.GUIDE) {
                        TVPill("‹ " + stringResource(R.string.tv_previous_window)) { windowOffset = (windowOffset - 1_800_000).coerceAtLeast(-6 * 3_600_000L) }
                        TVPill(stringResource(R.string.tv_now)) { windowOffset = 0 }
                        TVPill(stringResource(R.string.tv_next_window) + " ›") { windowOffset = (windowOffset + 1_800_000).coerceAtMost(45 * 3_600_000L) }
                    }
                    if (selected != null) TVPill(stringResource(R.string.tv_channel_options)) { channelOptions = selected }
                    Text(when {
                        saveFailed -> stringResource(R.string.tv_save_failed)
                        epgLoading -> stringResource(R.string.tv_epg_loading)
                        epgFailed -> stringResource(R.string.tv_epg_failed)
                        epgPartial -> stringResource(R.string.tv_epg_partial)
                        epgStale -> stringResource(R.string.tv_epg_stale)
                        library.epgSources.isEmpty() -> stringResource(R.string.tv_epg_not_set)
                        else -> stringResource(R.string.tv_channel_count, channels.size)
                    }, style = MaterialTheme.typography.bodySmall, color = Color(0xFFBFC0C7), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        folderId == null && state.loading && state.row == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                        channels.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(if (folderId != null) R.string.tv_folder_empty else if (state.error) R.string.legacy_tv_error else R.string.legacy_tv_empty), color = Color.White)
                            if (state.error && folderId == null) TVPill(stringResource(R.string.legacy_tv_retry)) { viewModel.retry() }
                        }
                        else -> key(scoped.profileId, folderId, state.selectedKey, library.viewMode) {
                            val footer: @Composable () -> Unit = {
                                if (folderId == null) {
                                    if (state.loading) LoadingIndicator()
                                    else if (state.error) TVPill(stringResource(R.string.legacy_tv_retry)) { viewModel.loadMore() }
                                    else if (state.row?.hasMore == true) TVPill(stringResource(R.string.legacy_tv_more)) { viewModel.loadMore() }
                                }
                            }
                            if (library.viewMode == LiveTVViewMode.MOSAIC) {
                                LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize().focusRestorer(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(8.dp)) {
                                    items(channels, key = { it.key }) { channel ->
                                        LegacyTVChannelCard(channel, epg.schedule(channel, library.epgIds[channel.key]), now,
                                            LiveTVViewMode.MOSAIC, windowStart, windowEnd,
                                            onFocus = { focusedKey = channel.key }, onPlay = { focusedKey = channel.key; returnToChannel = true; onPlay(channel) },
                                            onOptions = { channelOptions = channel },
                                            modifier = if (channel.key == selected?.key) Modifier.focusRequester(channelFocus) else Modifier)
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) { footer() }
                                }
                            } else {
                                Column {
                                    if (library.viewMode == LiveTVViewMode.GUIDE) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                            Text(SimpleDateFormat("EEE dd/MM", Locale.getDefault()).format(Date(windowStart)),
                                                color = Color(0xFFBFC0C7), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(170.dp))
                                            repeat(6) { Text(tvClock(windowStart + it * 1_800_000L), color = Color(0xFFBFC0C7),
                                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                    LazyColumn(Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(channels, key = { it.key }) { channel ->
                                            LegacyTVChannelCard(channel, epg.schedule(channel, library.epgIds[channel.key]), now,
                                                library.viewMode, windowStart, windowEnd,
                                                onFocus = { focusedKey = channel.key }, onPlay = { focusedKey = channel.key; returnToChannel = true; onPlay(channel) },
                                                onOptions = { channelOptions = channel },
                                                modifier = if (channel.key == selected?.key) Modifier.focusRequester(channelFocus) else Modifier)
                                        }
                                        item { footer() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            key(scoped.profileId) {
                val available = listOf(TVMenuEntry("channels", stringResource(R.string.tv_all_channels))) +
                    library.folders.map { TVMenuEntry("folder:${it.id}", "${it.name} · ${it.channels.size}") } +
                    state.sources.map { TVMenuEntry("source:${it.key}", it.label) }
                val byKey = available.associateBy { it.key }
                val entries = orderedTVMenuKeys(library.menuOrder, available.map { it.key }).mapNotNull { byKey[it] }
                val activeMenuKey = folderId?.let { "folder:$it" }
                    ?: if (allChannelsSelected) "channels" else state.selectedKey?.let { "source:$it" } ?: "channels"
                LiveTVMenuRail(entries, activeMenuKey, menuFocus,
                    onSelect = { key ->
                        focusedKey = null
                        when {
                            key == "channels" -> { folderId = null; allChannelsSelected = true }
                            key.startsWith("folder:") -> { folderId = key.removePrefix("folder:"); allChannelsSelected = false }
                            key.startsWith("source:") -> {
                                folderId = null; allChannelsSelected = false
                                val sourceKey = key.removePrefix("source:")
                                if (sourceKey != state.selectedKey) viewModel.select(sourceKey)
                            }
                        }
                    }, onReorder = libraryViewModel::menuOrder,
                    onChannels = { channels.isNotEmpty() && runCatching { channelFocus.requestFocus() }.getOrDefault(false) },
                    onNewFolder = { manageFolders = true },
                    modifier = Modifier.width((LocalConfiguration.current.screenWidthDp * 0.24f).coerceIn(180f, 240f).dp).fillMaxHeight())
            }
        }
    }
    if (manageFolders) LiveTVFoldersDialog(library, libraryViewModel, onDismiss = { manageFolders = false })
    if (epgDialog) LiveTVEpgDialog(library.epgSources, onSave = { libraryViewModel.epgSources(it); epgDialog = false },
        onRefresh = { libraryViewModel.refreshEpg(true); epgDialog = false }, onDismiss = { epgDialog = false })
    channelOptions?.let { channel ->
        LiveTVChannelDialog(channel, library, libraryViewModel, onDismiss = { channelOptions = null })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TVPill(label: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.height(36.dp), shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = if (selected) Color(0xFF434348) else Color(0xFF222225),
            contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color(0xFF141416))) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TVProgrammeProgress(programme: TVProgramme, now: Long) {
    val fraction = ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(2.dp).background(Color.Gray.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(LocalContentColor.current))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LegacyTVChannelCard(
    channel: LiveTVChannel, programmes: List<TVProgramme>, now: Long, mode: LiveTVViewMode,
    windowStart: Long, windowEnd: Long,
    onFocus: () -> Unit, onPlay: () -> Unit, onOptions: () -> Unit, modifier: Modifier = Modifier
) {
    val current = programmes.firstOrNull { it.start <= now && it.end > now }
    val next = programmes.firstOrNull { it.start > now }
    Card(onClick = onPlay, onLongClick = onOptions,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocus() },
        colors = CardDefaults.colors(containerColor = Color(0xFF202024), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color(0xFF151518)),
        shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)), scale = CardDefaults.scale(focusedScale = 1f)) {
        if (mode == LiveTVViewMode.MOSAIC) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                AsyncImage(channel.logo, null, Modifier.fillMaxWidth().height(56.dp), contentScale = ContentScale.Fit)
                Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current?.title ?: stringResource(R.string.tv_no_programme), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current != null) TVProgrammeProgress(current, now)
            }
        } else Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.width(if (mode == LiveTVViewMode.GUIDE) 148.dp else 190.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(channel.logo, null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
                Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (mode == LiveTVViewMode.GUIDE) {
                BoxWithConstraints(Modifier.weight(1f).height(48.dp)) {
                    val width = maxWidth
                    val slots = remember(programmes, windowStart, windowEnd) { tvTimelineSlots(programmes, windowStart, windowEnd) }
                    if (slots.isEmpty()) Text(stringResource(R.string.tv_no_programme), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterStart))
                    for (slot in slots) {
                        Column(Modifier.offset(x = width * slot.offset).width(width * slot.width).fillMaxHeight()
                            .padding(end = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color.Gray.copy(alpha = 0.2f)).padding(5.dp)) {
                            Text(slot.programme.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text("${tvClock(slot.programme.start)} – ${tvClock(slot.programme.end)}", maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (now in windowStart..windowEnd) Box(Modifier.offset(x = width * ((now - windowStart).toFloat() / (windowEnd - windowStart)))
                        .width(1.dp).fillMaxHeight().background(Color(0xFFE52F43)))
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current?.title ?: stringResource(R.string.tv_no_programme), style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) {
                        Text("${tvClock(current.start)} – ${tvClock(current.end)}", style = MaterialTheme.typography.bodySmall)
                        TVProgrammeProgress(current, now)
                    }
                    if (next != null) Text(stringResource(R.string.tv_next_programme, tvClock(next.start), next.title),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.livetv.LiveTVChannel
import com.nuvio.tv.ui.screens.livetv.tvClock
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LegacyLivePlayerOverlay(
    channel: LiveTVChannel,
    info: LegacyLivePlayerInfo,
    player: PlayerUiState,
    media: PlayerMediaInfo,
    playPauseFocus: FocusRequester,
    streamInfoFocus: FocusRequester,
    onPause: () -> Unit,
    onGuide: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onInfo: () -> Unit,
    onReload: () -> Unit,
    onAspect: () -> Unit,
    onInteraction: () -> Unit,
    onClearance: (Float) -> Unit
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(15_000); value = System.currentTimeMillis() }
    }
    val programmes = remember(info.programmes, now) { legacyLiveProgrammes(info.programmes, now) }
    val current = programmes.current
    val next = programmes.next
    val addon = player.currentStreamAddonName?.takeIf { it.isNotBlank() } ?: channel.addonName
    val technical = remember(media, player.detectedFrameRate) { legacyLiveTechnicalLabels(media, player.detectedFrameRate) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(300.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
        if (player.osdClockEnabled) Row(Modifier.align(Alignment.TopEnd).padding(24.dp)
            .clip(RoundedCornerShape(30.dp)).background(Color(0xC018181C)).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tvClock(now), color = Color.White, style = MaterialTheme.typography.titleMedium)
            current?.let { Text(stringResource(R.string.lp_until, tvClock(it.end)), color = Color(0xFFD0D0D4), style = MaterialTheme.typography.bodyMedium) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { onClearance(it.height.toFloat()) }
            .padding(horizontal = 28.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xD41A1A1F))
                .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(channel.logo ?: player.logo, null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(channel.name, color = Color.White, style = MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text(stringResource(R.string.lp_live), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFD82439)).padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                        Text(stringResource(R.string.lp_addon, addon) + info.folders.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = " · ").orEmpty(), color = Color(0xFFD0D0D4), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (player.isBuffering || !player.isPlaying) Text(stringResource(if (player.isBuffering) R.string.lp_buffering else R.string.lp_paused),
                        color = Color(0xFFE4D39D), style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(current?.title ?: stringResource(when {
                        info.loading -> R.string.lp_epg_loading
                        !info.configured -> R.string.lp_epg_not_set
                        else -> R.string.tv_no_programme
                    }), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (current != null) Text("${tvClock(current.start)} – ${tvClock(current.end)}", color = Color(0xFFD0D0D4), style = MaterialTheme.typography.bodySmall)
                }
                if (current != null) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(programmes.progress).fillMaxHeight().background(Color.White))
                    }
                }
                next?.let { Text(stringResource(R.string.tv_next_programme, tvClock(it.start), it.title), color = Color(0xFFD0D0D4),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (technical.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(technical) { label -> Text(label, color = Color(0xFFEBEBEF), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 7.dp, vertical = 3.dp)) }
                }

            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp),
                modifier = Modifier.onPreviewKeyEvent { onInteraction(); false }) {
                item("pause") { LivePlayerAction(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (player.isPlaying) R.string.lp_pause else R.string.lp_play), Modifier.focusRequester(playPauseFocus), onInteraction, onPause) }
                item("guide") { LivePlayerAction(Icons.Default.GridView, stringResource(R.string.tv_guide), onFocus = onInteraction, onClick = onGuide) }
                item("audio") { LivePlayerAction(Icons.Default.GraphicEq, stringResource(R.string.lp_audio), onFocus = onInteraction, onClick = onAudio) }
                item("subtitles") { LivePlayerAction(Icons.Default.Subtitles, stringResource(R.string.lp_subtitles), onFocus = onInteraction, onClick = onSubtitles) }
                item("sources") { LivePlayerAction(Icons.Default.Layers, stringResource(R.string.lp_sources), onFocus = onInteraction, onClick = onSources) }
                item("info") { LivePlayerAction(Icons.Default.Info, stringResource(R.string.lp_info), Modifier.focusRequester(streamInfoFocus), onInteraction, onInfo) }
                item("reload") { LivePlayerAction(Icons.Default.Refresh, stringResource(R.string.lp_reload), onFocus = onInteraction, onClick = onReload) }
                item("aspect") { LivePlayerAction(Icons.Default.AspectRatio, stringResource(R.string.lp_aspect), onFocus = onInteraction, onClick = onAspect) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LivePlayerAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(40.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)), scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = Color(0xD42B2B30), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color(0xFF141418))) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
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
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTVScreen(
    viewModel: LiveTVViewModel = hiltViewModel(),
    onManageAddons: () -> Unit,
    onPlay: (MetaPreview, LiveTVSource) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentFocus = LocalContentFocusRequester.current
    val sidebarExpanded = LocalSidebarExpanded.current
    val firstAction = remember { FocusRequester() }
    // The same requester is the sidebar's entry point. No focus stealing on return from playback.
    LaunchedEffect(Unit) { if (!sidebarExpanded) runCatching { firstAction.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background)
            .padding(horizontal = 38.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.legacy_live_tv), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f))
            Button(onClick = onManageAddons, modifier = Modifier.focusRequester(contentFocus).focusRequester(firstAction)) {
                Text(stringResource(R.string.legacy_addons))
            }
        }
        Text(stringResource(R.string.legacy_tv_hint), style = MaterialTheme.typography.bodyMedium)
        if (state.sources.isNotEmpty()) {
            Text(stringResource(R.string.legacy_tv_sources), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                items(state.sources, key = { it.key }) { source ->
                    Button(
                        onClick = { viewModel.select(source.key) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (source.key == state.selectedKey) NuvioTheme.colors.FocusBackground
                                else NuvioTheme.colors.SurfaceVariant
                        )
                    ) { Text(source.label, maxLines = 1) }
                }
            }
        }
        if (state.loading && state.row == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else if (state.sources.isEmpty()) {
            Text(stringResource(R.string.legacy_tv_empty), style = MaterialTheme.typography.titleLarge)
        } else {
            val source = state.selected
            // Per-catalog grid state survives selecting a channel and coming back.
            key(state.selectedKey) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)
                ) {
                    items(state.row?.items.orEmpty(), key = { it.id }) { channel ->
                        Card(onClick = { if (source != null) onPlay(channel, source) }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(model = channel.logo ?: channel.poster, contentDescription = null,
                                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(74.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(channel.name, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!channel.description.isNullOrBlank()) {
                                    Text(channel.description, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.error) {
                                Text(stringResource(R.string.legacy_tv_error))
                                Button(onClick = { if (state.row == null) viewModel.retry() else viewModel.loadMore() }) {
                                    Text(stringResource(R.string.legacy_tv_retry))
                                }
                            } else if (state.loading) {
                                LoadingIndicator()
                            } else if (state.row?.hasMore == true) {
                                Button(onClick = viewModel::loadMore) { Text(stringResource(R.string.legacy_tv_more)) }
                            } else if (state.row?.items.isNullOrEmpty()) {
                                Text(stringResource(R.string.legacy_tv_empty))
                            }
                        }
                    }
                }
            }
        }
    }
}

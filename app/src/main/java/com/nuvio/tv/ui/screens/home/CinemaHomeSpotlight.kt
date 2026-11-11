package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.isPlaceholder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CinemaHomeSpotlight(
    activeItem: State<ModernCarouselItem?>,
    enrichedPreviews: State<Map<String, MetaPreview>>,
    onPlayCatalogItem: (String, String, String) -> Unit,
    onOpenDetails: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onOpenFolder: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayedItem by remember(activeItem) { mutableStateOf<ModernCarouselItem?>(null) }
    LaunchedEffect(activeItem) {
        snapshotFlow { activeItem.value?.takeUnless { it.imageUrl.isPlaceholder() } }
            .collectLatest { item ->
                if (displayedItem != null && item != null && displayedItem?.key != item.key) {
                    delay(160)
                }
                displayedItem = item
            }
    }

    val item = displayedItem ?: return
    val preview = item.heroPreview
    val enriched = item.metaPreview?.id?.let { enrichedPreviews.value[it] }
    val subtitle = listOfNotNull(preview.yearText, preview.contentTypeText, preview.runtimeText)
        .filter { it.isNotBlank() }.joinToString("  ·  ")

    fun showDetails() {
        if (item.payload !is ModernPayload.CollectionFolder) {
            HeroBackdropState.update(enriched?.backdropUrl ?: preview.backdrop)
        }
        when (val payload = item.payload) {
            is ModernPayload.Catalog -> onOpenDetails(payload.itemId, payload.itemType, payload.addonBaseUrl)
            is ModernPayload.ContinueWatching -> onOpenDetails(payload.item.contentId(), payload.item.contentType(), "")
            is ModernPayload.CollectionFolder -> onOpenFolder(payload.collectionId, payload.folderId)
        }
    }

    val play: (() -> Unit)? = when (val payload = item.payload) {
        is ModernPayload.Catalog -> {
            if (ServerCatalog.isCollection(payload.itemId, payload.itemType)) null else {
                {
                    HeroBackdropState.update(enriched?.backdropUrl ?: preview.backdrop)
                    onPlayCatalogItem(payload.itemId, payload.itemType, payload.addonBaseUrl)
                }
            }
        }
        is ModernPayload.ContinueWatching -> {
            val episode = payload.item
            if (episode is ContinueWatchingItem.NextUp && !episode.info.hasAired) null else {
                { onContinueWatchingClick(episode) }
            }
        }
        is ModernPayload.CollectionFolder -> null
    }
    val continueWatching = (item.payload as? ModernPayload.ContinueWatching)?.item
    val playLabel = stringResource(
        if (continueWatching is ContinueWatchingItem.InProgress) R.string.detail_btn_resume else R.string.hero_play
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = enriched?.name ?: preview.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotEmpty()) {
            Text(text = subtitle, style = MaterialTheme.typography.labelLarge, color = Color(0xFFDDDDDD))
        }
        (enriched?.description ?: preview.description)?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE6E6E6),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (play != null) {
                Button(
                    onClick = play,
                    modifier = Modifier.height(39.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF101010),
                        focusedContainerColor = Color(0xFFD20929),
                        focusedContentColor = Color.White
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(playLabel)
                }
            }
            Button(
                onClick = ::showDetails,
                modifier = Modifier.height(39.dp),
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xB3373737),
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color(0xFF101010)
                ),
                scale = ButtonDefaults.scale(focusedScale = 1f)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(stringResource(R.string.cinema_more_info))
            }
        }
    }
}

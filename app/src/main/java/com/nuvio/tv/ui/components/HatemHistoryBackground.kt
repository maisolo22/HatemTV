package com.nuvio.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/** Bounded, decorative poster wall. It has no focus targets and preserves profile avatars. */
@Composable
fun HatemHistoryBackground(posters: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        HatemCinemaBackground()
        val cellWidth = maxWidth / 6f
        val cellHeight = cellWidth * 1.5f
        Crossfade(posters, animationSpec = tween(350), label = "profileHistoryPosters") { images ->
            if (images.isNotEmpty()) {
                Column(Modifier.requiredSize(cellWidth * 8f, cellHeight * 4f)
                    .graphicsLayer { rotationZ = -12f; alpha = 0.70f },
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(8) { col ->
                                val url = images[(row * 8 + col) % images.size]
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(url).size(260, 390).build(),
                                    contentDescription = null,
                                    modifier = Modifier.width(cellWidth).height(cellHeight)
                                        .clip(RoundedCornerShape(10.dp)).background(Color(0xFF17171D)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.32f), Color(0xFF080912).copy(alpha = 0.88f)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            Color.Black.copy(alpha = 0.30f), Color.Transparent, Color.Black.copy(alpha = 0.30f)))))
    }
}

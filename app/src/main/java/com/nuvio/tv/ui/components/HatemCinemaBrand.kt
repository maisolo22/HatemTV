package com.nuvio.tv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import kotlin.math.sin

@Composable
fun HatemAuthorCredit(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.hatem_author_credit), modifier = modifier.absolutePadding(top = 16.dp, right = 24.dp),
        color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Normal, maxLines = 1)
}

@Composable
fun HatemCinemaBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Color(0xFF09080D))) {
        val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(Color(0xFF77051E), Color.Transparent),
            center = Offset(-w * 0.05f, -h * 0.25f), radius = w * 0.8f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF65041A), Color.Transparent),
            center = Offset(w * 1.1f, h * 1.2f), radius = w * 0.7f))
        val top = Path().apply {
            moveTo(0f, 0f); lineTo(w * 0.62f, 0f)
            cubicTo(w * 0.25f, h * 0.02f, w * 0.08f, h * 0.04f, 0f, h * 0.25f); close()
        }
        drawPath(top, Brush.linearGradient(listOf(Color(0xFFB40A2E).copy(alpha = 0.52f), Color.Transparent),
            Offset.Zero, Offset(w * 0.5f, h * 0.2f)))
        val bottom = Path().apply {
            moveTo(w, h); lineTo(w * 0.35f, h)
            cubicTo(w * 0.82f, h * 0.99f, w * 0.96f, h * 0.87f, w, h * 0.68f); close()
        }
        drawPath(bottom, Brush.linearGradient(listOf(Color.Transparent, Color(0xFFC50934).copy(alpha = 0.44f)),
            Offset(w * 0.5f, h * 0.7f), Offset(w, h)))
    }
}

@Composable
fun HatemStartupContent(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hatemStartup")
    val phase by transition.animateFloat(0f, 6.283185f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "loadingDots")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.hatem_cinema_mark), null, Modifier.size(132.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.hatem_app_name), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            repeat(3) { index ->
                val alpha = 0.25f + 0.75f * ((sin(phase - index * 0.8f) + 1f) / 2f)
                Box(Modifier.size(8.dp).background(Color(0xFFFF1746).copy(alpha = alpha), CircleShape))
            }
        }
    }
}

@Composable
fun HatemProfileAvatar(name: String, id: Int, primary: Boolean, imageUrl: String?, size: Dp) {
    val index = if (primary) 0 else Math.floorMod(id, 3)
    val color = listOf(Color(0xFFC8173B), Color(0xFF7432AB), Color(0xFFF0A800))[index]
    val art = listOf(R.drawable.hatem_avatar_popcorn, R.drawable.hatem_avatar_family, R.drawable.hatem_avatar_rocket)[index]
    Box(Modifier.size(size).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.65f)))),
        contentAlignment = Alignment.Center) {
        Image(painterResource(art), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        if (!imageUrl.isNullOrBlank()) AsyncImage(imageUrl, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.brandWordmarkResource

@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    drawableOverride: Int? = null
) {
    Image(
        painter = painterResource(id = if (BuildConfig.FEATURE_CINEMA_UI_ENABLED) R.drawable.hatem_brand_wordmark else drawableOverride ?: NuvioTheme.currentTheme.brandWordmarkResource),
        contentDescription = if (BuildConfig.FEATURE_CINEMA_UI_ENABLED) stringResource(R.string.hatem_app_name) else contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha
    )
}

package com.ogh.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.StreamingProvider
import com.ogh.shared.ui.theme.OghColors

/**
 * Draws the provider's brand icon on a 24-unit grid. The logo is decorative:
 * callers keep the provider name in text or in the control's content description.
 */
@Composable
internal fun ProviderLogo(
    provider: StreamingProvider,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val layers = remember(provider) { logoLayers(provider) }
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            layers.forEach { (path, color) -> drawPath(path, color) }
        }
    }
}

/** The connected provider behind a destination, or null for manual RTMP. */
internal val DestinationType.provider: StreamingProvider?
    get() = when (this) {
        DestinationType.YOUTUBE_OAUTH -> StreamingProvider.YOUTUBE
        DestinationType.TWITCH_OAUTH -> StreamingProvider.TWITCH
        DestinationType.RTMP_MANUAL -> null
    }

private fun logoLayers(provider: StreamingProvider): List<Pair<Path, Color>> = when (provider) {
    StreamingProvider.YOUTUBE -> listOf(
        parse(YOUTUBE_FRAME) to provider.brandColor,
        parse(YOUTUBE_PLAY) to Color.White,
    )
    StreamingProvider.TWITCH -> listOf(
        parse(TWITCH_BUBBLE) to Color.White,
        parse(TWITCH_GLITCH) to provider.brandColor,
    )
}

private val StreamingProvider.brandColor: Color
    get() = colorFromHex(colorHex, OghColors.Violet)

private fun parse(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

// Brand icon geometry from Simple Icons (CC0). Trademarks remain with their owners
// and identify the provider an account or destination belongs to.
private const val YOUTUBE_FRAME =
    "M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505" +
        "A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136" +
        "c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136" +
        "C24 15.93 24 12 24 12s0-3.93-.502-5.814z"
private const val YOUTUBE_PLAY = "M9.545 15.568V8.432L15.818 12l-6.273 3.568z"
private const val TWITCH_BUBBLE = "M6.857 1.714h13.714v9.429l-3.428 3.428h-3.429l-3 3v-3H6.857z"
private const val TWITCH_GLITCH =
    "M11.571 4.714h1.715v5.143H11.57zm4.715 0H18v5.143h-1.714zM6 0L1.714 4.286v15.428h5.143V24" +
        "l4.286-4.286h3.428L22.286 12V0zm14.571 11.143l-3.428 3.428h-3.429l-3 3v-3H6.857V1.714h13.714Z"

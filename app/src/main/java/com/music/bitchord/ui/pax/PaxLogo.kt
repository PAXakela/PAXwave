package com.music.bitchord.ui.pax

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.music.bitchord.R

/** The mark's width over its height: the frame is a touch taller than wide. */
const val PAX_LOGO_ASPECT = 553f / 574f

/**
 * PAXwave's logo at [height]: in full colour on a dark page, and as a single
 * colour on a light one, where the warm-white frame would disappear. [onDark]
 * forces the colour version, for a logo that sits on its own black tile.
 */
@Composable
fun PaxLogo(height: Dp, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val dark = onDark || MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(if (dark) R.drawable.ic_logo_color else R.drawable.ic_logo),
        contentDescription = null,
        colorFilter = if (dark) null else ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        modifier = modifier.size(width = height * PAX_LOGO_ASPECT, height = height),
    )
}

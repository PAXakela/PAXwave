package com.music.bitchord.ui.pax

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TabletLayout
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.theme.PaxAqua
import kotlin.math.max
import androidx.compose.ui.unit.min

/** Whether the app is laid out for a tablet: side navigation, wider grids. */
val LocalTabletMode = staticCompositionLocalOf { false }

/** How wide the page area is, beside the navigation in tablet mode. */
val LocalContentWidth = compositionLocalOf { 400.dp }

/** Android's own line between phones and tablets: sw600dp. */
private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

/** From this window width the sidebar shows its labels beside the icons. */
private val EXPANDED_RAIL_MIN_WINDOW = 1000.dp

/** Compact rail: icons with small labels beneath. */
val PAX_RAIL_WIDTH = 92.dp

/** Expanded sidebar: icons with labels beside them. */
val PAX_SIDEBAR_WIDTH = 240.dp

/**
 * Whether the tablet layout is in use: [TabletLayout.AUTO] picks it on a
 * device whose shorter side is at least 600dp — a tablet, or a foldable
 * opened up — and the setting can force either layout.
 */
@Composable
fun rememberTabletMode(windowWidth: Dp): Boolean {
    val choice by AppSettings.tabletLayout.collectAsStateWithLifecycle()
    val smallest = LocalConfiguration.current.smallestScreenWidthDp
    return when (choice) {
        TabletLayout.TABLET -> true
        TabletLayout.PHONE -> false
        TabletLayout.AUTO -> smallest >= TABLET_MIN_SMALLEST_WIDTH_DP && windowWidth >= 600.dp
    }
}

/** Whether the sidebar has room for labels beside its icons. */
fun paxSidebarExpanded(windowWidth: Dp): Boolean = windowWidth >= EXPANDED_RAIL_MIN_WINDOW

/** The navigation's width for this window, in tablet mode. */
fun paxNavigationWidth(windowWidth: Dp): Dp =
    if (paxSidebarExpanded(windowWidth)) PAX_SIDEBAR_WIDTH else PAX_RAIL_WIDTH

/**
 * How many tiles sit across a grid row: [phone] on a phone, and on a tablet as
 * many as fit at [minTileWidth] each — never fewer than on a phone, never more
 * than [maxColumns].
 */
@Composable
fun paxGridColumns(phone: Int, minTileWidth: Dp, maxColumns: Int = phone * 3, spacing: Dp = 12.dp): Int {
    if (!LocalTabletMode.current) return phone
    val available = LocalContentWidth.current - PAGE_GUTTER * 2
    val fit = ((available + spacing) / (minTileWidth + spacing)).toInt()
    return max(phone, fit).coerceAtMost(maxColumns)
}

/** The widest a list page — Settings, History and the like — grows on a tablet. */
val PAX_READING_WIDTH = 760.dp

/** The pages that keep to [PAX_READING_WIDTH] in tablet mode. */
val PAX_READING_PAGES = setOf("settings", "history", "discord", "account_scrobbling", "sources", "equalizer")

/**
 * Centres [content] in a column no wider than [PAX_READING_WIDTH] when
 * [enabled]: rows of settings stretched across a landscape tablet are hard to
 * read, with the label at one edge and its switch at the other.
 */
@Composable
fun PaxReadingColumn(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = PAX_READING_WIDTH)) {
            CompositionLocalProvider(
                LocalContentWidth provides min(LocalContentWidth.current, PAX_READING_WIDTH),
                content = content,
            )
        }
    }
}

/**
 * PAXwave's tablet navigation: a sidebar down the left edge with the logo at
 * the top, the four tabs and Search in the middle, and Settings at the foot.
 * A compact rail on a portrait tablet; a labelled sidebar where the window is
 * wide enough to spare the room.
 */
@Composable
fun PaxNavRail(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    searchSelected: Boolean,
    settingsSelected: Boolean,
    expanded: Boolean,
    searchIcon: ImageVector,
    searchLabel: String,
    settingsLabel: String,
    onTabSelected: (Int) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.colorScheme.background
    val raised = Color.White.copy(alpha = 0.035f).compositeOver(base)
    Row(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (expanded) PAX_SIDEBAR_WIDTH else PAX_RAIL_WIDTH)
                .background(Brush.verticalGradient(listOf(raised, base)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .padding(horizontal = if (expanded) 16.dp else 10.dp, vertical = 18.dp),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            // The mark, and in the wide sidebar the name beside it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (expanded) 10.dp else 0.dp, top = 4.dp, bottom = 30.dp),
            ) {
                PaxLogo(height = if (expanded) 32.dp else 36.dp)
                if (expanded) {
                    Spacer(Modifier.width(12.dp))
                    PaxwaveWordmark(size = 24.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (expanded) 4.dp else 10.dp)) {
                tabs.forEachIndexed { index, tab ->
                    RailItem(
                        icon = tab.icon,
                        label = tab.label,
                        selected = !searchSelected && !settingsSelected && index == selectedIndex,
                        expanded = expanded,
                        onClick = { onTabSelected(index) },
                    )
                }
                RailItem(
                    icon = searchIcon,
                    label = searchLabel,
                    selected = searchSelected && !settingsSelected,
                    expanded = expanded,
                    onClick = onSearch,
                )
            }
            Spacer(Modifier.weight(1f))
            RailItem(
                icon = Icons.Rounded.Settings,
                label = settingsLabel,
                selected = settingsSelected,
                expanded = expanded,
                onClick = onSettings,
            )
        }
        // A hairline between the navigation and the page.
        Box(
            Modifier
                .fillMaxHeight()
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (selected) PaxAqua else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "railTint",
    )
    val fill by animateColorAsState(
        if (selected) PaxAqua.copy(alpha = 0.16f) else Color.Transparent,
        label = "railFill",
    )
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(fill)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) PaxAqua else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .width(PAX_RAIL_WIDTH - 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) PaxAqua else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

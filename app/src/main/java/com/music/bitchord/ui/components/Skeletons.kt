package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.music.bitchord.ui.theme.PaxAqua
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grey stand-ins for content still on the wire, laid out to the same metrics as
 * the real rows and cards so nothing jumps when the data lands.
 */

/** One full shimmer cycle: the sweep across the screen, then a short rest. */
private const val SHIMMER_PERIOD_MS = 1700

/** The share of [SHIMMER_PERIOD_MS] the light spends moving; the rest is a pause. */
private const val SHIMMER_SWEEP_SHARE = 0.78f

private val BlockShape = RoundedCornerShape(6.dp)
private val LineShape = RoundedCornerShape(4.dp)

// Ragged widths, so a run of rows reads as text rather than as a barcode.
private val TitleWidths = listOf(0.68f, 0.46f, 0.58f, 0.74f, 0.52f)
private val SubtitleWidths = listOf(0.34f, 0.44f, 0.27f, 0.38f, 0.31f)

/**
 * The shimmer's position in its cycle, 0..1, taken from the frame clock.
 *
 * Every placeholder reads the same frame time, so all of them agree on the
 * phase: one band of light travels across the whole screen instead of each
 * block flickering on its own schedule.
 */
@Composable
private fun rememberShimmerPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t -> phase.floatValue = (t % SHIMMER_PERIOD_MS).toFloat() / SHIMMER_PERIOD_MS }
        }
    }
    return phase
}

/**
 * One placeholder block, lit by a diagonal band of light that sweeps across the
 * screen.
 *
 * The band is laid out in window coordinates, so neighbouring blocks show
 * consecutive slices of the same sweep. The phase and position are read in the
 * draw phase only: a screenful of these redraws each frame but never recomposes.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = BlockShape) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val light = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f).compositeOver(base)
    val glint = PaxAqua.copy(alpha = 0.12f).compositeOver(light)
    val colors = remember(base, light, glint) { listOf(base, light, glint, light, base) }
    val phase = rememberShimmerPhase()
    val origin = remember { floatArrayOf(0f, 0f) }
    val screen = with(LocalDensity.current) {
        val config = LocalConfiguration.current
        config.screenWidthDp.dp.toPx() to config.screenHeightDp.dp.toPx()
    }
    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                origin[0] = pos.x
                origin[1] = pos.y
            }
            .drawBehind {
                val (screenW, screenH) = screen
                val band = screenW * 0.42f
                val slope = 0.45f
                val sweep = (phase.value / SHIMMER_SWEEP_SHARE).coerceAtMost(1f)
                val eased = FastOutSlowInEasing.transform(sweep)
                // The band's centre runs from fully off the left edge to fully
                // past the bottom-right corner, slanted so lower rows light up
                // a moment after the ones above them.
                val travel = screenW + screenH * slope + band * 2
                val centre = -band + eased * travel
                val sx = centre - band - origin[0]
                val sy = -origin[1]
                drawRect(
                    Brush.linearGradient(
                        colors = colors,
                        start = Offset(sx, sy),
                        end = Offset(sx + band * 2, sy + band * 2 * slope),
                    ),
                )
            },
    )
}

/** A placeholder for one line of text, sized as a fraction of its parent. */
@Composable
private fun SkeletonLine(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier.fillMaxWidth(fraction).height(height),
        shape = LineShape,
    )
}

/**
 * Stands in for a section heading. Only the title line is drawn — most shelves
 * come back without a subtitle, and guessing wrong shifts everything under it.
 */
@Composable
private fun SectionHeaderSkeleton(index: Int = 0) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp)) {
        SkeletonLine(fraction = TitleWidths[index % TitleWidths.size] * 0.7f, height = 18.dp)
    }
}

/**
 * Stands in for one `SongRow`, down to the 52dp of artwork and the 20dp gutter.
 * [circular] matches the browse rows, where artists are drawn as circles.
 */
@Composable
fun SongRowSkeleton(index: Int = 0, circular: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(52.dp), if (circular) CircleShape else RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}

/** A run of track-row placeholders, for search hits and release track lists. */
fun LazyListScope.songListSkeleton(
    count: Int = 8,
    keyPrefix: String = "skeleton:song",
    circular: Boolean = false,
    alpha: Float = 1f,
) {
    items(count, key = { "$keyPrefix:$it" }) { index ->
        Box(Modifier.graphicsLayer { this.alpha = alpha }) {
            SongRowSkeleton(index = index, circular = circular)
        }
    }
}

/**
 * The lead shelf on Home and Explore: near-page-width cards that page sideways.
 *
 * Both carousels below are built on a [LazyRow] with scrolling off rather than a
 * plain [Row], so a card running past the right edge is measured and clipped
 * exactly as the real shelf's is — which is the whole point of a skeleton.
 */
@Composable
private fun HeroShelfSkeleton() {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeaderSkeleton()
        BoxWithConstraints {
            val cardWidth = heroCardWidth(maxWidth)
            LazyRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false,
            ) {
                items(2) {
                    ShimmerBox(
                        modifier = Modifier.width(cardWidth).aspectRatio(HERO_CARD_RATIO),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }
    }
}

/** Reserved at the top of Play while Recents loads, matching its saved layout. */
fun LazyListScope.recentlyPlayedSkeleton(listLayout: Boolean) {
    item(key = "skeleton:recently-played") {
        Column(Modifier.padding(bottom = 26.dp)) {
            RecentsHeaderSkeleton()
            if (listLayout) {
                BoxWithConstraints {
                    val columnWidth = trackColumnWidth(maxWidth)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false,
                    ) {
                        item {
                            Column(Modifier.width(columnWidth)) {
                                repeat(4) { index -> CompactSongRowSkeleton(index) }
                            }
                        }
                    }
                }
            } else {
                BoxWithConstraints {
                    val cardWidth = heroCardWidth(maxWidth)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        userScrollEnabled = false,
                    ) {
                        items(2) {
                            ShimmerBox(
                                modifier = Modifier.width(cardWidth).aspectRatio(HERO_CARD_RATIO),
                                shape = RoundedCornerShape(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Recents heading plus the adjacent list/grid toggle placeholder. */
@Composable
private fun RecentsHeaderSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths.first() * 0.7f, height = 18.dp)
        }
        Spacer(Modifier.width(8.dp))
        ShimmerBox(Modifier.size(32.dp), CircleShape)
    }
}

/** The compact carousel of square cards used by every shelf below the first. */
@Composable
fun ShelfSkeleton(index: Int = 0, cardWidth: Dp = SHELF_CARD_WIDTH, cardCorner: Dp = 12.dp) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeaderSkeleton(index = index)
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(3) { card ->
                Column(Modifier.width(cardWidth)) {
                    ShimmerBox(
                        modifier = Modifier.width(cardWidth).aspectRatio(1f),
                        shape = RoundedCornerShape(cardCorner),
                    )
                    Spacer(Modifier.height(12.dp))
                    SkeletonLine(fraction = TitleWidths[card % TitleWidths.size], height = 13.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(fraction = SubtitleWidths[card % SubtitleWidths.size], height = 11.dp)
                }
            }
        }
    }
}

/** Home and Explore while the first page of shelves is still loading. */
fun LazyListScope.feedSkeleton(shelves: Int = 3, firstIsHero: Boolean = true) {
    if (firstIsHero) {
        item(key = "skeleton:hero") { HeroShelfSkeleton() }
        items(shelves - 1, key = { "skeleton:shelf:$it" }) { index ->
            ShelfSkeleton(index = index + 1)
        }
    } else {
        items(shelves, key = { "skeleton:shelf:$it" }) { index ->
            ShelfSkeleton(index = index)
        }
    }
}

/** Appended to the feed while a further page of shelves is on its way. */
fun LazyListScope.feedMoreSkeleton() {
    item(key = "skeleton:more") { ShelfSkeleton() }
}

/** The signed-in library: saved collections, then the run of liked tracks. */
fun LazyListScope.librarySkeleton() {
    item(key = "skeleton:library:shelf") { ShelfSkeleton() }
    item(key = "skeleton:library:header") { SectionHeaderSkeleton(index = 1) }
    songListSkeleton(count = 7, keyPrefix = "skeleton:library:song")
}

/** The Play / Shuffle pair, which only appears once there is something to play. */
@Composable
private fun DetailActionsSkeleton(isArtist: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Matches the real pair's inset, height and corner, so the header
            // above them doesn't shift when the track list lands.
            .padding(horizontal = PAGE_GUTTER + 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        ShimmerBox(Modifier.size(50.dp), CircleShape)
        ShimmerBox(Modifier.width(130.dp).height(50.dp), CircleShape)
        if (!isArtist) {
            ShimmerBox(Modifier.size(50.dp), CircleShape)
        }
    }
}

/**
 * An album, playlist or artist page below its header — the header itself is
 * drawn from what the row that was tapped already knew, so it never waits.
 */
fun LazyListScope.detailSkeleton(isArtist: Boolean) {
    if (isArtist) {
        item(key = "skeleton:detail:actions") {
            Column(Modifier.graphicsLayer { alpha = 0.4f }) {
                DetailActionsSkeleton(isArtist = true)
                Spacer(Modifier.height(22.dp))
            }
        }
        item(key = "skeleton:detail:top") {
            Column(Modifier.graphicsLayer { alpha = 0.4f }) {
                SectionHeaderSkeleton()
                // Top songs page four at a time — see [trackColumnWidth] for how
                // wide a page lands.
                BoxWithConstraints {
                    val columnWidth = trackColumnWidth(maxWidth)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false,
                    ) {
                        item {
                            Column(Modifier.width(columnWidth)) {
                                repeat(4) { index -> CompactSongRowSkeleton(index) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "skeleton:detail:sections") {
            Box(Modifier.graphicsLayer { alpha = 0.4f }) {
                ShelfSkeleton(index = 2, cardCorner = 10.dp)
            }
        }
        return
    }
    item(key = "skeleton:detail:actions") {
        Column(Modifier.graphicsLayer { alpha = 0.4f }) {
            DetailActionsSkeleton(isArtist = false)
            Spacer(Modifier.height(20.dp))
        }
    }
    songListSkeleton(count = 8, keyPrefix = "skeleton:detail:song", alpha = 0.4f)
}

/** The tighter row used inside the artist page's top-songs pager. */
@Composable
private fun CompactSongRowSkeleton(index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(48.dp), RoundedCornerShape(7.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}

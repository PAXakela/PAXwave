package com.music.bitchord.ui.pax

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.EpisodeProgress
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.ui.components.PAGE_GUTTER
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

// ───────────────────────────── Basics ─────────────────────────────

/** The section title every PAXwave page uses: the Home shelves' size, an optional "see all". */
@Composable
fun PaxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onAction != null) {
            if (actionLabel != null) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = actionLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PaxArtwork(
    url: String?,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(corner),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            fallback,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxSize(0.38f),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A row of pill chips; the selected one fills with the accent. */
@Composable
fun <T> PaxChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        options.forEach { option ->
            PaxChip(label(option), option == selected, onClick = { onSelect(option) })
        }
    }
}

@Composable
fun PaxChip(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "chip",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun PaxEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 14.dp),
    )
}

/** A filled round play/pause button in the accent colour. */
@Composable
fun PaxPlayButton(playing: Boolean, onClick: () -> Unit, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

// ───────────────────────────── Podcasts ─────────────────────────────

fun formatEpisodeDate(millis: Long?): String? {
    millis ?: return null
    val age = System.currentTimeMillis() - millis
    return when {
        age in 0 until TimeUnit.DAYS.toMillis(1) -> null
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

@Composable
fun episodeMeta(episode: PodcastEpisode, progress: EpisodeProgress?): String {
    val age = episode.publishedAt?.let { System.currentTimeMillis() - it }
    val date = when {
        age == null -> null
        age in 0 until TimeUnit.DAYS.toMillis(1) -> stringResource(R.string.pax_today)
        age in 0 until TimeUnit.DAYS.toMillis(2) -> stringResource(R.string.pax_yesterday)
        else -> formatEpisodeDate(episode.publishedAt)
    }
    val length = when {
        progress?.played == true -> stringResource(R.string.pax_played)
        progress?.inProgress == true && progress.durationMs > 0 -> stringResource(
            R.string.pax_left,
            shortDuration((progress.durationMs - progress.positionMs) / 1000),
        )
        else -> episode.durationSeconds?.let(::shortDuration)
    }
    return listOfNotNull(date, length).joinToString(" · ")
}

/** "1 h 32 min", "45 min" — how podcast apps say it. */
fun shortDuration(seconds: Long): String {
    val minutes = (seconds + 30) / 60
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "${m.coerceAtLeast(1)} min"
    }
}

/** A wide card for horizontal rows on Home and the hub: art, title, show, progress, play. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeCard(
    show: Podcast,
    episode: PodcastEpisode,
    progress: EpisodeProgress?,
    playing: Boolean,
    current: Boolean,
    onPlay: () -> Unit,
    onOpenShow: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = onOpenShow,
) {
    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .border(
                0.5.dp,
                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(18.dp),
            )
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaxArtwork(
                episode.imageUrl ?: show.imageUrl,
                Icons.Rounded.Podcasts,
                Modifier.size(64.dp).clickable(onClick = onOpenShow),
                corner = 12.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    show.title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaxPlayButton(playing = playing, onClick = onPlay, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episodeMeta(episode, progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress?.inProgress == true) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

/** One episode in a list: art, show, title, meta and progress; play, queue and more on the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRow(
    show: Podcast,
    episode: PodcastEpisode,
    progress: EpisodeProgress?,
    current: Boolean,
    playing: Boolean,
    queued: Boolean,
    showName: Boolean,
    onPlay: () -> Unit,
    onToggleQueue: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val played = progress?.played == true
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onMore, onLongClick = onMore)
            .padding(start = PAGE_GUTTER, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box {
            PaxArtwork(episode.imageUrl ?: show.imageUrl, Icons.Rounded.Podcasts, Modifier.size(56.dp))
            if (current) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showName) {
                Text(
                    show.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (played) FontWeight.W500 else FontWeight.W600,
                color = when {
                    current -> MaterialTheme.colorScheme.primary
                    played -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onBackground
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val downloads by com.music.bitchord.data.pax.PodcastDownloads.records.collectAsStateWithLifecycle()
            val download = downloads[episode.id]
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (download?.status) {
                    com.music.bitchord.data.pax.PodcastDownloads.Status.DONE -> {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            contentDescription = stringResource(R.string.pax_downloaded),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    com.music.bitchord.data.pax.PodcastDownloads.Status.QUEUED,
                    com.music.bitchord.data.pax.PodcastDownloads.Status.RUNNING -> {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { download.fraction },
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    else -> Unit
                }
                if (played) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    episodeMeta(episode, progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress?.inProgress == true) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(0.55f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                    drawStopIndicator = {},
                )
            }
        }
        IconButton(onClick = onToggleQueue) {
            Icon(
                if (queued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = stringResource(if (queued) R.string.pax_remove_from_queue else R.string.pax_add_to_queue),
                tint = if (queued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PaxPlayButton(playing = current && playing, onClick = onPlay, size = 34.dp)
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A show in the subscriptions grid: square art with an unplayed count. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastTile(
    show: Podcast,
    newCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            PaxArtwork(show.imageUrl, Icons.Rounded.Podcasts, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            show.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        show.author?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A chart entry or search hit: art and name, not yet subscribed. */
@Composable
fun DiscoverTile(
    title: String,
    author: String?,
    imageUrl: String?,
    rank: Int?,
    subscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            PaxArtwork(imageUrl, Icons.Rounded.Podcasts, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
            if (subscribed) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.pax_subscribed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        author?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ───────────────────────────── Radio ─────────────────────────────

/** A station for horizontal rows and grids: round art, name, a live dot while it plays. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationTile(
    station: RadioStation,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.width(size).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PaxArtwork(
                station.faviconUrl,
                Icons.Rounded.Radio,
                Modifier
                    .size(size)
                    .then(
                        if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                    ),
                shape = CircleShape,
            )
            if (current) {
                Box(
                    Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(size * 0.36f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            station.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A badge marking a live stream. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE5484D))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** Gradient call-to-action card, for an empty section that should invite rather than apologise. */
@Composable
fun PaxPromoCard(
    title: String,
    body: String,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

val PaxRowPadding = PaddingValues(horizontal = PAGE_GUTTER)

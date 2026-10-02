package com.music.bitchord.ui.pax

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.ui.components.PAGE_GUTTER
import kotlinx.coroutines.launch

private enum class EpisodeFilter { ALL, UNPLAYED, IN_PROGRESS }

/**
 * One podcast: a header lit by its own artwork, subscribe / play / pin, the
 * description, and every episode. Works for a show opened from Discover or a
 * search too, before it is subscribed.
 */
@Composable
fun PodcastShowScreen(
    feedKey: String,
    controller: MediaController?,
    contentPadding: PaddingValues,
    onOpenShow: OpenShow,
) {
    val podcasts by PodcastStore.podcasts.collectAsStateWithLifecycle()
    val previews by PodcastStore.previews.collectAsStateWithLifecycle()
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val queue by PodcastStore.queue.collectAsStateWithLifecycle()
    val pins by LibraryPins.pins.collectAsStateWithLifecycle()
    val nowPlaying = rememberPaxNowPlaying(controller)
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(EpisodeFilter.ALL) }
    var sheetEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }

    val show = podcasts.firstOrNull { it.key == feedKey } ?: previews[feedKey]
    if (show == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding)) { PaxEmpty(stringResource(R.string.pax_not_subscribed)) }
        return
    }
    val subscribed = podcasts.any { it.key == feedKey }
    val pinned = pins.any { it.kind == LibraryPins.Kind.PODCAST && it.id == feedKey }
    val resume = show.episodes.firstOrNull { progress[it.id]?.inProgress == true }
    val headline = resume ?: show.episodes.firstOrNull()

    fun play(episode: PodcastEpisode) {
        controller?.let { c -> scope.launch { PaxPlayer.playEpisode(c, show, episode) } }
    }

    val episodes = when (filter) {
        EpisodeFilter.ALL -> show.episodes
        EpisodeFilter.UNPLAYED -> show.episodes.filter { progress[it.id]?.played != true }
        EpisodeFilter.IN_PROGRESS -> show.episodes.filter { progress[it.id]?.inProgress == true }
    }

    val ld = LocalLayoutDirection.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(ld),
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth()) {
                // The artwork, blurred and darkened, behind the whole header.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !show.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = show.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().blur(60.dp),
                    )
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = contentPadding.calculateTopPadding() + 8.dp)
                        .padding(horizontal = PAGE_GUTTER + 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PaxArtwork(show.imageUrl, Icons.Rounded.Podcasts, Modifier.size(200.dp), corner = 20.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        show.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    show.author?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.pax_episode_count, show.episodes.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (headline != null) {
                            val isPlaying = nowPlaying.isPlaying(headline.id)
                            PillButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                text = stringResource(if (resume != null) R.string.pax_resume else R.string.pax_play_latest),
                                filled = true,
                                onClick = { play(headline) },
                            )
                        }
                        PillButton(
                            icon = if (subscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                            text = stringResource(if (subscribed) R.string.pax_subscribed else R.string.pax_subscribe),
                            filled = false,
                            enabled = !working,
                            onClick = {
                                if (subscribed) {
                                    PodcastStore.unsubscribe(show.key)
                                    LibraryPins.unpin(LibraryPins.Kind.PODCAST, show.key)
                                } else {
                                    scope.launch {
                                        working = true
                                        PodcastStore.subscribe(show.feedUrl)
                                        working = false
                                    }
                                }
                            },
                        )
                        RoundIconButton(
                            icon = if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            active = pinned,
                            contentDescription = stringResource(if (pinned) R.string.pax_unpin else R.string.pax_pin),
                        ) { LibraryPins.toggle(LibraryPins.podcastPin(show)) }
                        if (subscribed) {
                            Box(contentAlignment = Alignment.Center) {
                                RoundIconButton(Icons.Rounded.Refresh, active = false, contentDescription = stringResource(R.string.pax_refresh)) {
                                    scope.launch {
                                        working = true
                                        PodcastStore.refresh(show.key)
                                        working = false
                                    }
                                }
                                if (working) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    show.description?.let { text ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        )
                    }
                    if (subscribed) {
                        val autoDownload by PodcastStore.autoDownload.collectAsStateWithLifecycle()
                        val on = feedKey in autoDownload
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { PodcastStore.setAutoDownload(feedKey, !on) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.pax_auto_download),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    stringResource(R.string.pax_auto_download_detail),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = on,
                                onCheckedChange = { checked ->
                                    PodcastStore.setAutoDownload(feedKey, checked)
                                    if (checked) {
                                        com.music.bitchord.data.pax.PodcastDownloads.autoDownload(
                                            listOf(show), progress, setOf(feedKey),
                                        )
                                    }
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item(key = "filters") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pax_episodes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = PAGE_GUTTER, top = 8.dp),
                )
            }
            PaxChips(
                options = EpisodeFilter.entries,
                selected = filter,
                onSelect = { filter = it },
                label = {
                    stringResource(
                        when (it) {
                            EpisodeFilter.ALL -> R.string.pax_filter_all
                            EpisodeFilter.UNPLAYED -> R.string.pax_filter_unplayed
                            EpisodeFilter.IN_PROGRESS -> R.string.pax_filter_in_progress
                        },
                    )
                },
            )
        }
        if (episodes.isEmpty()) item(key = "none") { PaxEmpty(stringResource(R.string.pax_no_results)) }
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(
                show, episode, progress[episode.id],
                current = nowPlaying.isCurrent(episode.id),
                playing = nowPlaying.isPlaying,
                queued = episode.id in queue,
                showName = false,
                onPlay = { play(episode) },
                onToggleQueue = {
                    if (episode.id in queue) PaxPlayer.dequeue(controller, episode.id)
                    else scope.launch { PaxPlayer.enqueue(controller, show, episode) }
                },
                onMore = { sheetEpisode = episode },
            )
        }
    }

    sheetEpisode?.let { episode ->
        EpisodeSheet(show, episode, controller, nowPlaying, onOpenShow = null, onDismiss = { sheetEpisode = null })
    }
}

@Composable
fun PillButton(
    icon: ImageVector,
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, active: Boolean, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

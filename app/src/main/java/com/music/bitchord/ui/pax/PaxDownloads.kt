package com.music.bitchord.ui.pax

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastDownloads
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.ui.components.PAGE_GUTTER
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Downloaded podcast episodes, grouped by show, for the Downloads page's
 * Podcasts tab: how much space they take, and each show's episodes ready
 * to play offline.
 */
@Composable
fun PodcastDownloadsTab(
    controller: MediaController?,
    contentPadding: PaddingValues,
    onOpenShow: OpenShow,
) {
    val records by PodcastDownloads.records.collectAsStateWithLifecycle()
    val podcasts by PodcastStore.podcasts.collectAsStateWithLifecycle()
    val previews by PodcastStore.previews.collectAsStateWithLifecycle()
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val queue by PodcastStore.queue.collectAsStateWithLifecycle()
    val nowPlaying = rememberPaxNowPlaying(controller)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<Pair<Podcast, PodcastEpisode>?>(null) }

    // DownloadManager reports progress only when asked; ask while this is on screen.
    // On screen means the app in front, too: a composition left open behind a
    // locked phone kept querying the download database every few seconds.
    val foreground = com.music.bitchord.ui.rememberIsForeground()
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            PodcastDownloads.refresh()
            delay(if (records.values.any { it.status != PodcastDownloads.Status.DONE }) 1_500L else 8_000L)
        }
    }

    val shows = (podcasts + previews.values).associateBy { it.key }
    val groups: List<Pair<Podcast, List<PodcastEpisode>>> = records.values
        .filter { it.status != PodcastDownloads.Status.FAILED }
        .groupBy { it.feedKey }
        .mapNotNull { (key, recs) ->
            val show = shows[key] ?: return@mapNotNull null
            val ids = recs.map { it.episodeId }.toSet()
            show to show.episodes.filter { it.id in ids }
        }
        .filter { it.second.isNotEmpty() }
        .sortedBy { it.first.title.lowercase() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (groups.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.pax_downloads_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "summary") {
            val count = groups.sumOf { it.second.size }
            Row(
                Modifier
                    .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceVariant),
                        ),
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.pax_episode_count, count),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        stringResource(R.string.pax_storage_used, Formatter.formatShortFileSize(context, PodcastDownloads.totalBytes())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        groups.forEach { (show, episodes) ->
            item(key = "h:${show.key}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenShow(show.key, show.title, show.imageUrl) }
                        .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaxArtwork(show.imageUrl, Icons.Rounded.Podcasts, Modifier.size(44.dp), corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(show.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.pax_episode_count, episodes.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(episodes, key = { "d:${it.id}" }) { episode ->
                EpisodeRow(
                    show, episode, progress[episode.id],
                    current = nowPlaying.isCurrent(episode.id),
                    playing = nowPlaying.isPlaying,
                    queued = episode.id in queue,
                    showName = false,
                    onPlay = { controller?.let { c -> scope.launch { PaxPlayer.playEpisode(c, show, episode) } } },
                    onToggleQueue = {
                        if (episode.id in queue) PaxPlayer.dequeue(controller, episode.id)
                        else scope.launch { PaxPlayer.enqueue(controller, show, episode) }
                    },
                    onMore = { sheet = show to episode },
                )
            }
        }
    }
    sheet?.let { (show, episode) ->
        EpisodeSheet(
            show, episode, controller, nowPlaying,
            onOpenShow = { sheet = null; onOpenShow(show.key, show.title, show.imageUrl) },
            onDismiss = { sheet = null },
        )
    }
}

@Suppress("unused")
private val spacing = Arrangement.spacedBy(0.dp)

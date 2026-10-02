package com.music.bitchord.ui.pax

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.ui.components.PAGE_GUTTER
import kotlinx.coroutines.launch

/** Popular stations, fetched once per process for Home's radio row when there are no favourites. */
private object HomeRadioCache {
    var popular: List<RadioStation>? = null
}

/**
 * Podcasts and radio on Home, between YouTube Music's shelves: what you were
 * listening to, what is new from your shows, and your stations — each with a
 * way into its full page.
 */
@Composable
fun PaxHomeSection(
    controller: MediaController?,
    onOpenShow: OpenShow,
    onOpenPodcasts: (PodcastSegment) -> Unit,
    onOpenRadio: () -> Unit,
) {
    val podcasts by PodcastStore.podcasts.collectAsStateWithLifecycle()
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val dismissed by PodcastStore.dismissed.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Pair<Podcast, PodcastEpisode>?>(null) }
    val favourites by RadioStore.favourites.collectAsStateWithLifecycle()
    val pins by LibraryPins.pins.collectAsStateWithLifecycle()
    val nowPlaying = rememberPaxNowPlaying(controller)
    val scope = rememberCoroutineScope()
    val liveLabel = stringResource(R.string.pax_live)
    var popular by remember { mutableStateOf(HomeRadioCache.popular) }

    LaunchedEffect(favourites.isEmpty()) {
        if (favourites.isEmpty() && popular == null) {
            RadioStore.popular().onSuccess { list ->
                HomeRadioCache.popular = list.take(12)
                popular = HomeRadioCache.popular
            }
        }
    }
    LaunchedEffect(Unit) {
        if (podcasts.any { System.currentTimeMillis() - it.refreshedAt > 60 * 60_000L }) PodcastStore.refreshAll()
    }

    fun play(show: Podcast, episode: PodcastEpisode) {
        controller?.let { c -> scope.launch { PaxPlayer.playEpisode(c, show, episode) } }
    }

    val continuing = remember(podcasts, progress, dismissed) {
        podcasts.flatMap { show -> show.episodes.map { show to it } }
            .filter { progress[it.second.id]?.inProgress == true && it.second.id !in dismissed }
            .sortedByDescending { progress[it.second.id]?.updatedAt ?: 0L }
            .take(8)
    }
    val fresh = remember(podcasts, progress) { newEpisodes(podcasts, progress).take(12) }

    Column(Modifier.padding(bottom = 18.dp)) {
        if (continuing.isNotEmpty()) {
            PaxSectionHeader(
                stringResource(R.string.pax_continue_listening),
                subtitle = stringResource(R.string.pax_podcasts),
            )
            EpisodeCardRow(continuing, progress, nowPlaying, ::play, onOpenShow, onLongPress = { menuFor = it })
            Spacer(Modifier.height(14.dp))
        }
        menuFor?.let { (show, episode) ->
            ContinueMenuSheet(
                episodeTitle = episode.title,
                onRemove = {
                    PodcastStore.dismissFromContinue(episode.id)
                    menuFor = null
                },
                onOpenShow = {
                    menuFor = null
                    onOpenShow(show.key, show.title, show.imageUrl)
                },
                onDismiss = { menuFor = null },
            )
        }
        if (podcasts.isEmpty()) {
            PaxPromoCard(
                title = stringResource(R.string.pax_promo_podcasts_title),
                body = stringResource(R.string.pax_promo_podcasts_body),
                icon = Icons.Rounded.Explore,
                colors = listOf(Color(0xFF0A3D62), Color(0xFF1B9AAA)),
                onClick = { onOpenPodcasts(PodcastSegment.DISCOVER) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else if (fresh.isNotEmpty()) {
            PaxSectionHeader(
                stringResource(R.string.pax_latest_episodes),
                subtitle = stringResource(R.string.pax_from_your_podcasts),
                actionLabel = stringResource(R.string.pax_see_all),
                onAction = { onOpenPodcasts(PodcastSegment.NEW) },
            )
            EpisodeCardRow(fresh, progress, nowPlaying, ::play, onOpenShow)
            Spacer(Modifier.height(14.dp))
        }

        val pinnedStations = pins.mapNotNull { it.station }
        val stations = (favourites + pinnedStations).distinctBy { it.id }.ifEmpty { popular.orEmpty() }
        PaxSectionHeader(
            stringResource(if (favourites.isEmpty()) R.string.pax_radio_popular_home else R.string.pax_your_stations),
            subtitle = stringResource(R.string.pax_live),
            actionLabel = stringResource(R.string.pax_see_all),
            onAction = onOpenRadio,
        )
        if (stations.isEmpty()) {
            PaxPromoCard(
                title = stringResource(R.string.pax_radio),
                body = stringResource(R.string.pax_promo_radio_body),
                icon = Icons.Rounded.Radio,
                colors = listOf(Color(0xFF1B1464), Color(0xFF12CBC4)),
                onClick = onOpenRadio,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(stations, key = { it.id }) { station ->
                    StationTile(
                        station,
                        current = nowPlaying.isCurrent(PaxMedia.radioId(station)),
                        playing = nowPlaying.isPlaying,
                        onClick = { controller?.let { c -> scope.launch { PaxPlayer.playStation(c, station, liveLabel) } } },
                        onLongClick = { LibraryPins.toggle(LibraryPins.stationPin(station)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCardRow(
    episodes: List<Pair<Podcast, PodcastEpisode>>,
    progress: Map<String, com.music.bitchord.data.pax.EpisodeProgress>,
    nowPlaying: PaxNowPlaying,
    onPlay: (Podcast, PodcastEpisode) -> Unit,
    onOpenShow: OpenShow,
    onLongPress: ((Pair<Podcast, PodcastEpisode>) -> Unit)? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(episodes, key = { it.second.id }) { (show, episode) ->
            EpisodeCard(
                show, episode, progress[episode.id],
                playing = nowPlaying.isPlaying(episode.id),
                current = nowPlaying.isCurrent(episode.id),
                onPlay = { onPlay(show, episode) },
                onOpenShow = { onOpenShow(show.key, show.title, show.imageUrl) },
                onLongPress = onLongPress?.let { { it(show to episode) } }
                    ?: { onOpenShow(show.key, show.title, show.imageUrl) },
            )
        }
    }
}

/** Long-press on a "Continue listening" card: drop it from the row, or go to its show. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ContinueMenuSheet(
    episodeTitle: String,
    onRemove: () -> Unit,
    onOpenShow: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 24.dp).navigationBarsPadding()) {
            androidx.compose.material3.Text(
                episodeTitle,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            MenuRow(androidx.compose.material.icons.Icons.Rounded.VisibilityOff, androidx.compose.ui.res.stringResource(R.string.pax_remove_from_continue), onRemove)
            MenuRow(androidx.compose.material.icons.Icons.Rounded.Podcasts, androidx.compose.ui.res.stringResource(R.string.pax_go_to_podcast), onOpenShow)
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
    }
}

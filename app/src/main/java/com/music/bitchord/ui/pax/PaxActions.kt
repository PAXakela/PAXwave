package com.music.bitchord.ui.pax

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.PodcastDownloads
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.launch

/**
 * The player's ⋮ menu for a podcast episode or a radio station — the things
 * that apply to them, in place of a song's radio, playlist, album, artist and
 * YouTube-link actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaxActionsSheet(
    song: Song,
    controller: MediaController?,
    onOpenShow: OpenShow,
    onOpenRadio: () -> Unit,
    onDismiss: () -> Unit,
) {
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val queue by PodcastStore.queue.collectAsStateWithLifecycle()
    val downloads by PodcastDownloads.records.collectAsStateWithLifecycle()
    val favourites by RadioStore.favourites.collectAsStateWithLifecycle()
    val pins by LibraryPins.pins.collectAsStateWithLifecycle()
    val speed by AppSettings.playbackSpeed.collectAsStateWithLifecycle()
    var sleepOpen by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val isRadio = PaxMedia.isRadioId(song.videoId)
                PaxArtwork(
                    song.thumbnailUrl,
                    if (isRadio) Icons.Rounded.Radio else Icons.Rounded.Podcasts,
                    Modifier.size(56.dp),
                    shape = if (isRadio) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.size(6.dp))

            val found = PodcastStore.findEpisode(song.videoId)
            if (found != null) {
                val (show, episode) = found
                val played = progress[episode.id]?.played == true
                val queued = episode.id in queue
                val download = downloads[episode.id]
                MenuRow(Icons.Rounded.Podcasts, stringResource(R.string.pax_go_to_podcast)) {
                    onOpenShow(show.key, show.title, show.imageUrl)
                }
                MenuRow(
                    if (queued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                    stringResource(if (queued) R.string.pax_remove_from_queue else R.string.pax_add_to_queue),
                ) {
                    if (queued) PaxPlayer.dequeue(controller, episode.id)
                    else scope.launch { PaxPlayer.enqueue(controller, show, episode) }
                }
                MenuRow(
                    if (played) Icons.Rounded.Undo else Icons.Rounded.CheckCircle,
                    stringResource(if (played) R.string.pax_mark_unplayed else R.string.pax_mark_played),
                ) { PodcastStore.setPlayed(episode.id, !played) }
                MenuRow(
                    if (download?.status == PodcastDownloads.Status.DONE) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
                    stringResource(
                        when (download?.status) {
                            PodcastDownloads.Status.DONE -> R.string.pax_remove_download
                            PodcastDownloads.Status.QUEUED, PodcastDownloads.Status.RUNNING -> R.string.pax_cancel_download
                            else -> R.string.pax_download
                        },
                    ),
                ) {
                    if (download != null && download.status != PodcastDownloads.Status.FAILED) PodcastDownloads.delete(episode.id)
                    else PodcastDownloads.download(show, episode)
                }
                MenuRow(Icons.Rounded.Speed, stringResource(R.string.pax_speed, trimSpeed(speed))) { PaxPlayer.cycleSpeed() }
            } else if (PaxMedia.isRadioId(song.videoId)) {
                val stationId = song.videoId.removePrefix(PaxMedia.RADIO_PREFIX)
                val station = favourites.firstOrNull { it.id == stationId }
                    ?: pins.firstOrNull { it.kind == LibraryPins.Kind.RADIO && it.id == stationId }?.station
                    ?: song.localUri?.let { url -> RadioStation(stationId, song.title, url, song.thumbnailUrl) }
                val favourite = favourites.any { it.id == stationId }
                val pinned = pins.any { it.kind == LibraryPins.Kind.RADIO && it.id == stationId }
                if (station != null) {
                    MenuRow(
                        if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        stringResource(if (favourite) R.string.pax_remove_favourite else R.string.pax_add_favourite),
                    ) { RadioStore.toggleFavourite(station) }
                    MenuRow(
                        if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        stringResource(if (pinned) R.string.pax_unpin else R.string.pax_pin),
                    ) { LibraryPins.toggle(LibraryPins.stationPin(station)) }
                }
                MenuRow(Icons.Rounded.Radio, stringResource(R.string.pax_go_to_radio), onOpenRadio)
            }
            MenuRow(Icons.Rounded.Bedtime, stringResource(R.string.pax_sleep_timer)) { sleepOpen++ }
        }
    }
    if (sleepOpen > 0) SleepTimerSheet(onDismiss = { sleepOpen = 0 })
}

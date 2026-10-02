package com.music.bitchord.ui.pax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────────── Episode details ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSheet(
    show: Podcast,
    episode: PodcastEpisode,
    controller: MediaController?,
    nowPlaying: PaxNowPlaying,
    onOpenShow: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val queue by PodcastStore.queue.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val p = progress[episode.id]
    val queued = episode.id in queue
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaxArtwork(
                    episode.imageUrl ?: show.imageUrl,
                    Icons.Rounded.Podcasts,
                    Modifier.size(84.dp).then(if (onOpenShow != null) Modifier.clickable(onClick = onOpenShow) else Modifier),
                    corner = 14.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        show.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onOpenShow != null) Modifier.clickable(onClick = onOpenShow) else Modifier,
                    )
                    Text(
                        episode.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        episodeMeta(episode, p),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PaxPlayButton(
                    playing = nowPlaying.isPlaying(episode.id),
                    onClick = { controller?.let { c -> scope.launch { PaxPlayer.playEpisode(c, show, episode) } } },
                    size = 52.dp,
                )
                SheetAction(
                    icon = if (queued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = stringResource(if (queued) R.string.pax_remove_from_queue else R.string.pax_add_to_queue),
                    active = queued,
                ) {
                    if (queued) PaxPlayer.dequeue(controller, episode.id)
                    else scope.launch { PaxPlayer.enqueue(controller, show, episode) }
                }
                SheetAction(Icons.Rounded.QueuePlayNext, stringResource(R.string.pax_play_next)) {
                    scope.launch { PaxPlayer.enqueue(controller, show, episode, next = true) }
                }
                SheetAction(
                    icon = if (p?.played == true) Icons.Rounded.Undo else Icons.Rounded.CheckCircle,
                    label = stringResource(if (p?.played == true) R.string.pax_mark_unplayed else R.string.pax_mark_played),
                ) { PodcastStore.setPlayed(episode.id, p?.played != true) }
                val downloads by com.music.bitchord.data.pax.PodcastDownloads.records.collectAsStateWithLifecycle()
                val download = downloads[episode.id]
                SheetAction(
                    icon = when (download?.status) {
                        com.music.bitchord.data.pax.PodcastDownloads.Status.DONE -> Icons.Rounded.DeleteOutline
                        com.music.bitchord.data.pax.PodcastDownloads.Status.QUEUED,
                        com.music.bitchord.data.pax.PodcastDownloads.Status.RUNNING -> Icons.Rounded.Close
                        else -> Icons.Rounded.Download
                    },
                    label = stringResource(
                        when (download?.status) {
                            com.music.bitchord.data.pax.PodcastDownloads.Status.DONE -> R.string.pax_remove_download
                            com.music.bitchord.data.pax.PodcastDownloads.Status.QUEUED,
                            com.music.bitchord.data.pax.PodcastDownloads.Status.RUNNING -> R.string.pax_cancel_download
                            else -> R.string.pax_download
                        },
                    ),
                    active = download?.status == com.music.bitchord.data.pax.PodcastDownloads.Status.DONE,
                ) {
                    if (download != null && download.status != com.music.bitchord.data.pax.PodcastDownloads.Status.FAILED) {
                        com.music.bitchord.data.pax.PodcastDownloads.delete(episode.id)
                    } else {
                        com.music.bitchord.data.pax.PodcastDownloads.download(show, episode)
                    }
                }
            }
            episode.description?.let {
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.pax_show_notes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ───────────────────────────── Sleep timer ─────────────────────────────

/**
 * AntennaPod's sleep timer, in this app's clothes: a minute count set with
 * presets or ±, "at the end of this episode", extend while running, and a
 * gentle fade over the last half minute.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val deadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    val fade by SleepTimer.fadeOut.collectAsStateWithLifecycle()
    var minutes by remember { mutableIntStateOf(SleepTimer.minutes.value ?: 30) }
    var remaining by remember { mutableLongStateOf(SleepTimer.remainingMs() ?: 0L) }
    LaunchedEffect(deadline) {
        while (deadline != null) {
            remaining = SleepTimer.remainingMs() ?: 0L
            delay(1000)
        }
    }
    val running = deadline != null || afterTrack
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.pax_sleep_timer), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(14.dp))
            if (running) {
                Text(
                    if (afterTrack) stringResource(R.string.pax_sleep_end_of_episode)
                    else formatClock(remaining),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        PaxChip("+$m min", selected = false, onClick = { SleepTimer.extend(m) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                PaxChip(stringResource(R.string.pax_sleep_stop), selected = true, onClick = { SleepTimer.cancel() }, icon = Icons.Rounded.Close)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(Icons.Rounded.Remove) { minutes = (minutes - 5).coerceAtLeast(1) }
                    Text(
                        "$minutes",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(120.dp),
                        textAlign = TextAlign.Center,
                    )
                    StepButton(Icons.Rounded.Add) { minutes = (minutes + 5).coerceAtMost(600) }
                }
                Text(stringResource(R.string.pax_minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SleepTimer.SHEET_PRESETS.forEach { m ->
                        PaxChip("$m min", selected = m == minutes, onClick = { minutes = m })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { SleepTimer.start(minutes); onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.pax_sleep_start, minutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { SleepTimer.startAfterTrack(); onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.pax_sleep_end_of_episode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pax_sleep_fade), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.pax_sleep_fade_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = fade,
                    onCheckedChange = { SleepTimer.fadeOut.value = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
}

fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ───────────────────────────── Now playing (podcast) ─────────────────────────────

/**
 * The episode that is playing, with the controls a podcast listener reaches
 * for: back 10, play, forward 30, speed and the sleep timer.
 */
@Composable
fun PodcastNowPlayingCard(
    show: Podcast,
    episode: PodcastEpisode,
    controller: MediaController?,
    nowPlaying: PaxNowPlaying,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speed by AppSettings.playbackSpeed.collectAsStateWithLifecycle()
    val deadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    var sleepOpen by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaxArtwork(episode.imageUrl ?: show.imageUrl, Icons.Rounded.Podcasts, Modifier.size(64.dp), corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pax_now_playing).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    show.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PaxPlayer.cycleSpeed() }) {
                Text("${trimSpeed(speed)}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { PaxPlayer.seekBy(controller, -10_000L) }) {
                Icon(Icons.Rounded.Replay10, contentDescription = stringResource(R.string.pax_back_10), tint = MaterialTheme.colorScheme.onSurface)
            }
            PaxPlayButton(
                playing = nowPlaying.isPlaying,
                onClick = { controller?.let { c -> scope.launch { PaxPlayer.playEpisode(c, show, episode) } } },
                size = 52.dp,
            )
            IconButton(onClick = { PaxPlayer.seekBy(controller, 30_000L) }) {
                Icon(Icons.Rounded.Forward30, contentDescription = stringResource(R.string.pax_forward_30), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { sleepOpen++ }) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = stringResource(R.string.pax_sleep_timer),
                    tint = if (deadline != null || afterTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (sleepOpen > 0) SleepTimerSheet(onDismiss = { sleepOpen = 0 })
}

@Suppress("unused")
private val speedIcon = Icons.Rounded.Speed

fun trimSpeed(speed: Float): String =
    if (speed % 1f == 0f) "%.0f".format(speed) else "%.2f".format(speed).trimEnd('0').trimEnd('.', ',')

// ───────────────────────────── Country picker ─────────────────────────────

/** 🇩🇪 for "de": two regional-indicator letters. */
fun flagOf(code: String): String =
    code.uppercase().filter { it in 'A'..'Z' }.take(2)
        .map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/** The region switch: a roomy pill with the flag and name, opening [CountryPickerSheet]. */
@Composable
fun CountryButton(code: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableIntStateOf(0) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { open++ }
            .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(flagOf(code), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            com.music.bitchord.data.pax.PodcastDiscover.countryName(code),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            androidx.compose.material.icons.Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open > 0) {
        CountryPickerSheet(selected = code, onPick = { onPick(it); open = 0 }, onDismiss = { open = 0 })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.pax_choose_region),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(com.music.bitchord.data.pax.PodcastDiscover.COUNTRIES.size) { index ->
                    val code = com.music.bitchord.data.pax.PodcastDiscover.COUNTRIES[index]
                    val isSelected = code == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(code) }
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(flagOf(code), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            com.music.bitchord.data.pax.PodcastDiscover.countryName(code),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

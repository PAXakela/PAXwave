package com.music.bitchord.ui.pax

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Storage
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.components.PAGE_GUTTER

enum class LibraryFilter { ALL, PLAYLISTS, ALBUMS, ARTISTS, PODCASTS, RADIO, DEVICE }

@Composable
fun libraryFilterLabel(filter: LibraryFilter): String = stringResource(
    when (filter) {
        LibraryFilter.ALL -> R.string.pax_search_all
        LibraryFilter.PLAYLISTS -> R.string.pax_lib_playlists
        LibraryFilter.ALBUMS -> R.string.pax_lib_albums
        LibraryFilter.ARTISTS -> R.string.pax_lib_artists
        LibraryFilter.PODCASTS -> R.string.pax_podcasts
        LibraryFilter.RADIO -> R.string.pax_radio
        LibraryFilter.DEVICE -> R.string.on_device
    },
)

/** Which kind of collection a library card opens, read off its browse id. */
fun pinKindOf(item: ShelfItem): LibraryPins.Kind? {
    val id = item.browseId ?: return null
    return when (MainViewModel.browseTypeOf(id)) {
        BrowseType.PLAYLIST -> LibraryPins.Kind.PLAYLIST
        BrowseType.ALBUM -> LibraryPins.Kind.ALBUM
        BrowseType.ARTIST -> LibraryPins.Kind.ARTIST
        else -> null
    }
}

fun kindIcon(kind: LibraryPins.Kind): ImageVector = when (kind) {
    LibraryPins.Kind.PODCAST -> Icons.Rounded.Podcasts
    LibraryPins.Kind.RADIO -> Icons.Rounded.Radio
    LibraryPins.Kind.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
    LibraryPins.Kind.ALBUM -> Icons.Rounded.Album
    LibraryPins.Kind.ARTIST -> Icons.Rounded.Person
}

@Composable
fun kindLabel(kind: LibraryPins.Kind): String = stringResource(
    when (kind) {
        LibraryPins.Kind.PODCAST -> R.string.pax_podcast
        LibraryPins.Kind.RADIO -> R.string.pax_station
        LibraryPins.Kind.PLAYLIST -> R.string.pax_lib_playlist
        LibraryPins.Kind.ALBUM -> R.string.pax_lib_album
        LibraryPins.Kind.ARTIST -> R.string.pax_lib_artist
    },
)

/** Cards in Pinned and the playlist rail: half a regular shelf card. */
val COMPACT_CARD_WIDTH = 84.dp

/** YouTube Music's own podcast shelf in the library — PAXwave shows its own podcasts instead. */
fun isYouTubePodcastShelf(shelf: com.music.bitchord.data.model.HomeShelf): Boolean {
    if (shelf.title.contains("podcast", ignoreCase = true)) return true
    val podcastItems = shelf.items.count(::isYouTubePodcastItem)
    return shelf.items.isNotEmpty() && podcastItems * 2 > shelf.items.size
}

/** "Episodes for Later" and YouTube podcast shows. */
fun isYouTubePodcastItem(item: ShelfItem): Boolean {
    val id = item.browseId.orEmpty()
    return id == "VLSE" || id == "SE" || id.startsWith("MPSP") || id.startsWith("VLMPSP") ||
        item.title.equals("Episodes for Later", ignoreCase = true) ||
        item.title.equals("Folgen für später", ignoreCase = true) ||
        item.title.equals("Episoden für später", ignoreCase = true)
}

/**
 * The listener's pinned favourites, top of the library: a row of compact
 * cards shaped like every other card on the page — square art (round for
 * artists and stations), name, and what it is.
 */
@Composable
fun PinnedGrid(
    pins: List<LibraryPins.Pin>,
    currentRadioId: String?,
    onOpen: (LibraryPins.Pin) -> Unit,
    onEdit: () -> Unit,
) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = PAGE_GUTTER, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pax_pinned),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.pax_edit_pins), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (pins.isEmpty()) {
            Row(
                Modifier
                    .padding(horizontal = PAGE_GUTTER, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onEdit)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.pax_pins_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            return@Column
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pins, key = { it.key }) { pin ->
                PinCard(
                    pin,
                    live = pin.kind == LibraryPins.Kind.RADIO && currentRadioId == pin.id,
                    onClick = { onOpen(pin) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinCard(pin: LibraryPins.Pin, live: Boolean, onClick: () -> Unit) {
    val round = pin.kind == LibraryPins.Kind.ARTIST || pin.kind == LibraryPins.Kind.RADIO
    Column(
        Modifier
            .width(COMPACT_CARD_WIDTH)
            .combinedClickable(onClick = onClick, onLongClick = { LibraryPins.unpin(pin.kind, pin.id) }),
    ) {
        Box {
            PaxArtwork(
                pin.imageUrl,
                kindIcon(pin.kind),
                Modifier.size(COMPACT_CARD_WIDTH),
                shape = if (round) CircleShape else RoundedCornerShape(10.dp),
            )
            if (live) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE5484D)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            pin.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            kindLabel(pin.kind),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Choose what goes in Pinned: podcasts, stations, and the library's playlists, albums and artists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinEditorSheet(libraryItems: List<ShelfItem>, onDismiss: () -> Unit) {
    val pins by LibraryPins.pins.collectAsStateWithLifecycle()
    val podcasts by PodcastStore.podcasts.collectAsStateWithLifecycle()
    val stations by RadioStore.favourites.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(LibraryPins.Kind.PODCAST) }
    val candidates: List<LibraryPins.Pin> = when (kind) {
        LibraryPins.Kind.PODCAST -> podcasts.map(LibraryPins::podcastPin)
        LibraryPins.Kind.RADIO -> stations.map(LibraryPins::stationPin)
        else -> libraryItems.filter { pinKindOf(it) == kind }.distinctBy { it.browseId }.map {
            LibraryPins.Pin(kind, it.browseId!!, it.title, it.subtitle, it.thumbnailUrl)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.pax_edit_pins),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            PaxChips(
                options = LibraryPins.Kind.entries,
                selected = kind,
                onSelect = { kind = it },
                label = { kindLabel(it) },
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                if (candidates.isEmpty()) item { PaxEmpty(stringResource(R.string.pax_pins_none_of_kind)) }
                items(candidates, key = { it.key }) { pin ->
                    val pinned = pins.any { it.key == pin.key }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { LibraryPins.toggle(pin) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val round = pin.kind == LibraryPins.Kind.ARTIST || pin.kind == LibraryPins.Kind.RADIO
                        PaxArtwork(pin.imageUrl, kindIcon(pin.kind), Modifier.size(48.dp), shape = if (round) CircleShape else RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pin.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            pin.subtitle?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Icon(
                            if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(if (pinned) R.string.pax_unpin else R.string.pax_pin),
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Podcasts and radio inside the library, shaped by the active filter. */
@Composable
fun LibraryPaxContent(
    filter: LibraryFilter,
    controller: androidx.media3.session.MediaController?,
    nowPlaying: PaxNowPlaying,
    onOpenShow: OpenShow,
    onOpenRadio: () -> Unit,
) {
    val podcasts by PodcastStore.podcasts.collectAsStateWithLifecycle()
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val stations by RadioStore.favourites.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val liveLabel = stringResource(R.string.pax_live)
    val playStation: (com.music.bitchord.data.pax.RadioStation) -> Unit = { station ->
        controller?.let { c -> scope.launch { PaxPlayer.playStation(c, station, liveLabel) } }
    }
    val newCount: (com.music.bitchord.data.pax.Podcast) -> Int = { show ->
        show.episodes.take(10).count { progress[it.id] == null }
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        when (filter) {
            LibraryFilter.PODCASTS -> {
                if (podcasts.isEmpty()) PaxEmpty(stringResource(R.string.pax_no_podcasts))
                podcasts.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { show ->
                            PodcastTile(show, newCount(show), onClick = { onOpenShow(show.key, show.title, show.imageUrl) }, modifier = Modifier.weight(1f))
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            LibraryFilter.RADIO -> {
                if (stations.isEmpty()) PaxEmpty(stringResource(R.string.pax_no_favourites))
                stations.forEach { station ->
                    StationRow(
                        station, favourite = true,
                        pinned = LibraryPins.isPinned(LibraryPins.Kind.RADIO, station.id),
                        current = nowPlaying.isCurrent(com.music.bitchord.data.pax.PaxMedia.radioId(station)),
                        playing = nowPlaying.isPlaying,
                        onClick = { playStation(station) },
                    )
                }
                PaxSectionHeader(stringResource(R.string.pax_browse_stations), actionLabel = stringResource(R.string.pax_see_all), onAction = onOpenRadio)
            }
            else -> {
                if (podcasts.isNotEmpty()) {
                    PaxSectionHeader(stringResource(R.string.pax_podcasts))
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(podcasts, key = { it.key }) { show ->
                            PodcastTile(show, newCount(show), onClick = { onOpenShow(show.key, show.title, show.imageUrl) }, modifier = Modifier.width(124.dp))
                        }
                    }
                }
                PaxSectionHeader(
                    stringResource(R.string.pax_radio),
                    actionLabel = stringResource(R.string.pax_see_all),
                    onAction = onOpenRadio,
                )
                if (stations.isEmpty()) {
                    PaxEmpty(stringResource(R.string.pax_no_favourites))
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(stations, key = { it.id }) { station ->
                            StationTile(
                                station,
                                current = nowPlaying.isCurrent(com.music.bitchord.data.pax.PaxMedia.radioId(station)),
                                playing = nowPlaying.isPlaying,
                                onClick = { playStation(station) },
                                onLongClick = { LibraryPins.toggle(LibraryPins.stationPin(station)) },
                                size = 84.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The device folders as a compact row of pills, instead of a shelf of big cards. */
@Composable
fun DevicePills(title: String, items: List<ShelfItem>, onItemClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        )
        Row(
            Modifier
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                val icon = when (item.browseId) {
                    "local:downloads" -> Icons.Rounded.DownloadDone
                    "local:all" -> Icons.Rounded.LibraryMusic
                    "local:webdav" -> Icons.Rounded.Cloud
                    "local:smb" -> Icons.Rounded.Storage
                    else -> Icons.AutoMirrored.Rounded.QueueMusic
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onItemClick(item) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                        Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

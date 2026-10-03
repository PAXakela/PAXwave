package com.music.bitchord.ui.pax

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.PodcastDiscover
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PillTextField
import kotlinx.coroutines.launch

/** Genre tags offered on the radio page, as Radio Browser spells them. */
val RADIO_TAGS = listOf("", "pop", "rock", "news", "jazz", "classical", "electronic", "chillout", "hiphop", "80s", "schlager", "talk")

/** Live radio: what is on now, favourites, and browsing by country, genre or name. */
@Composable
fun RadioScreen(controller: MediaController?, contentPadding: PaddingValues) {
    val favourites by RadioStore.favourites.collectAsStateWithLifecycle()
    val pins by LibraryPins.pins.collectAsStateWithLifecycle()
    val playbackError by PaxMedia.playbackError.collectAsStateWithLifecycle()
    val nowPlaying = rememberPaxNowPlaying(controller)
    val scope = rememberCoroutineScope()
    val liveLabel = stringResource(R.string.pax_live)
    val offline = stringResource(R.string.pax_offline)

    var query by rememberSaveable { mutableStateOf("") }
    var customName by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(PodcastDiscover.defaultCountry()) }
    var tag by rememberSaveable { mutableStateOf("") }
    var browse by remember { mutableStateOf<List<RadioStation>?>(null) }
    var results by remember { mutableStateOf<List<RadioStation>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sleepOpen by remember { mutableIntStateOf(0) }
    val isUrl = PodcastStore.looksLikeUrl(query)

    LaunchedEffect(country, tag) {
        browse = null
        val upper = country.uppercase()
        (if (tag.isBlank()) RadioStore.popular(upper) else RadioStore.byTag(tag, upper))
            .onSuccess { browse = it }
            .onFailure { browse = emptyList(); error = offline }
    }

    fun play(station: RadioStation) {
        controller?.let { c -> scope.launch { PaxPlayer.playStation(c, station, liveLabel) } }
    }

    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        scope.launch {
            busy = true
            error = null
            if (isUrl) {
                RadioStore.custom(text, customName)
                    .onSuccess { station ->
                        if (!RadioStore.isFavourite(station.id)) RadioStore.toggleFavourite(station)
                        query = ""
                        customName = ""
                        play(station)
                    }
                    .onFailure { error = it.message }
            } else {
                RadioStore.search(text).onSuccess { results = it }.onFailure { error = offline }
            }
            busy = false
        }
    }

    val known = favourites + browse.orEmpty() + results.orEmpty()
    val current = nowPlaying.mediaId?.takeIf { PaxMedia.isRadioId(it) }?.let { id ->
        known.firstOrNull { PaxMedia.radioId(it) == id }
            ?: RadioStation(id.removePrefix(PaxMedia.RADIO_PREFIX), nowPlaying.title.orEmpty(), "", nowPlaying.artworkUrl)
    }

    val favColumns = paxGridColumns(phone = 4, minTileWidth = 96.dp, maxColumns = 10)
    val favRows = remember(favourites, favColumns) { favourites.chunked(favColumns) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (current != null) {
            item(key = "on-air") {
                OnAirCard(
                    station = current,
                    liveTitle = nowPlaying.liveTitle,
                    playing = nowPlaying.isPlaying,
                    favourite = favourites.any { it.id == current.id },
                    sleepActive = SleepTimer.isRunning,
                    onToggle = { controller?.let { c -> if (c.isPlaying) c.pause() else c.play() } },
                    onFavourite = { if (current.streamUrl.isNotBlank()) RadioStore.toggleFavourite(current) },
                    onSleep = { sleepOpen++ },
                )
            }
        }
        playbackError?.let { message ->
            item(key = "error") {
                Text(
                    stringResource(R.string.pax_playback_failed, message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 4.dp).clickable { PaxMedia.clearError() },
                )
            }
        }
        item(key = "search") {
            Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillTextField(
                        value = query,
                        onValueChange = { query = it; if (it.isBlank()) results = null },
                        placeholder = stringResource(R.string.pax_radio_search_hint),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { submit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Icon(if (isUrl) Icons.Rounded.Add else Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (isUrl) {
                    Spacer(Modifier.height(8.dp))
                    PillTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        placeholder = stringResource(R.string.pax_station_name_hint),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        results?.let { found ->
            item(key = "results-h") { PaxSectionHeader(stringResource(R.string.pax_search_results), actionLabel = null, onAction = null) }
            if (found.isEmpty()) item(key = "results-e") { PaxEmpty(stringResource(R.string.pax_no_results)) }
            itemsIndexed(found, key = { i, it -> "r:$i:${it.id}" }) { _, station ->
                StationRow(
                    station, favourite = favourites.any { it.id == station.id },
                    pinned = pins.any { it.kind == LibraryPins.Kind.RADIO && it.id == station.id },
                    current = nowPlaying.isCurrent(PaxMedia.radioId(station)), playing = nowPlaying.isPlaying,
                    onClick = { play(station) },
                )
            }
        }

        item(key = "fav-h") { PaxSectionHeader(stringResource(R.string.pax_favourites)) }
        if (favourites.isEmpty()) {
            item(key = "fav-e") { PaxEmpty(stringResource(R.string.pax_no_favourites)) }
        } else {
            items(favRows.size, key = { "fav-row-$it" }) { index ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val row = favRows[index]
                    row.forEach { station ->
                        StationTile(
                            station,
                            current = nowPlaying.isCurrent(PaxMedia.radioId(station)),
                            playing = nowPlaying.isPlaying,
                            onClick = { play(station) },
                            onLongClick = { LibraryPins.toggle(LibraryPins.stationPin(station)) },
                            size = 76.dp,
                        )
                    }
                    repeat(favColumns - row.size) { Spacer(Modifier.width(76.dp)) }
                }
            }
        }

        if (results == null) {
            item(key = "browse-h") {
                Text(
                    stringResource(R.string.pax_browse_stations),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 18.dp),
                )
                CountryButton(
                    code = country,
                    onPick = { country = it },
                    modifier = Modifier.padding(start = PAGE_GUTTER, top = 12.dp, bottom = 6.dp),
                )
                PaxChips(
                    options = RADIO_TAGS,
                    selected = tag,
                    onSelect = { tag = it },
                    label = { if (it.isBlank()) stringResource(R.string.pax_popular) else radioTagLabel(it) },
                )
            }
            val list = browse
            if (list == null) {
                item(key = "browse-l") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                itemsIndexed(list, key = { i, it -> "b:$i:${it.id}" }) { _, station ->
                    StationRow(
                        station, favourite = favourites.any { it.id == station.id },
                        pinned = pins.any { it.kind == LibraryPins.Kind.RADIO && it.id == station.id },
                        current = nowPlaying.isCurrent(PaxMedia.radioId(station)), playing = nowPlaying.isPlaying,
                        onClick = { play(station) },
                    )
                }
            }
        }
    }
    if (sleepOpen > 0) SleepTimerSheet(onDismiss = { sleepOpen = 0 })
}

@Composable
fun radioTagLabel(tag: String): String = when (tag) {
    "news" -> stringResource(R.string.pax_cat_news)
    "classical" -> stringResource(R.string.pax_tag_classical)
    "talk" -> stringResource(R.string.pax_tag_talk)
    "80s" -> stringResource(R.string.pax_tag_80s)
    "hiphop" -> "Hip-Hop"
    "chillout" -> "Chill"
    else -> tag.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationRow(
    station: RadioStation,
    favourite: Boolean,
    pinned: Boolean,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { LibraryPins.toggle(LibraryPins.stationPin(station)) })
            .padding(start = PAGE_GUTTER, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PaxArtwork(station.faviconUrl, Icons.Rounded.Radio, Modifier.size(52.dp), corner = 12.dp)
            if (current) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(station.country, station.details.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (pinned) {
            Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.pax_unpin), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { RadioStore.toggleFavourite(station) }) {
            Icon(
                if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (favourite) R.string.pax_remove_favourite else R.string.pax_add_favourite),
                tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PaxPlayButton(playing = current && playing, onClick = onClick, size = 34.dp, modifier = Modifier.padding(end = 10.dp))
    }
}

@Composable
private fun OnAirCard(
    station: RadioStation,
    liveTitle: String?,
    playing: Boolean,
    favourite: Boolean,
    sleepActive: Boolean,
    onToggle: () -> Unit,
    onFavourite: () -> Unit,
    onSleep: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B1464), MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                ),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaxArtwork(station.faviconUrl, Icons.Rounded.Radio, Modifier.size(76.dp), corner = 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                LiveBadge()
                Spacer(Modifier.height(6.dp))
                Text(station.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    liveTitle ?: stringResource(R.string.pax_live),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFavourite) {
                Icon(if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = Color.White)
            }
            PaxPlayButton(playing = playing, onClick = onToggle, size = 56.dp)
            IconButton(onClick = onSleep) {
                Icon(Icons.Rounded.Bedtime, stringResource(R.string.pax_sleep_timer), tint = if (sleepActive) MaterialTheme.colorScheme.primary else Color.White)
            }
        }
    }
}

@Suppress("unused")
private val pinOutline = Icons.Outlined.PushPin

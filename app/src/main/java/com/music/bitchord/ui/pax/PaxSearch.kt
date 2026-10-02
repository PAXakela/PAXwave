package com.music.bitchord.ui.pax

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastSearchResult
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.ui.components.PAGE_GUTTER
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class SearchCategory { ALL, MUSIC, PODCASTS, RADIO }

/** Podcast and station hits for the term YouTube Music is being searched for. */
@Stable
class PaxSearchState {
    var term by mutableStateOf("")
    var podcasts by mutableStateOf<List<PodcastSearchResult>?>(null)
    var stations by mutableStateOf<List<RadioStation>?>(null)
    var loading by mutableStateOf(false)
    var opening by mutableStateOf<String?>(null)
}

@Composable
fun rememberPaxSearch(submittedTerm: String): PaxSearchState {
    val state = remember { PaxSearchState() }
    LaunchedEffect(submittedTerm) {
        state.term = submittedTerm
        if (submittedTerm.isBlank()) {
            state.podcasts = null
            state.stations = null
            return@LaunchedEffect
        }
        state.loading = true
        coroutineScope {
            val p = async { PodcastStore.search(submittedTerm).getOrDefault(emptyList()) }
            val r = async { RadioStore.search(submittedTerm).getOrDefault(emptyList()) }
            state.podcasts = p.await()
            state.stations = r.await()
        }
        state.loading = false
    }
    return state
}

@Composable
fun SearchCategoryChips(selected: SearchCategory, onSelect: (SearchCategory) -> Unit) {
    PaxChips(
        options = SearchCategory.entries,
        selected = selected,
        onSelect = onSelect,
        label = {
            stringResource(
                when (it) {
                    SearchCategory.ALL -> R.string.pax_search_all
                    SearchCategory.MUSIC -> R.string.pax_search_music
                    SearchCategory.PODCASTS -> R.string.pax_podcasts
                    SearchCategory.RADIO -> R.string.pax_radio
                },
            )
        },
    )
}

/**
 * The podcast and radio parts of a search. Under "All" each shows its best
 * three with a way to the rest; under its own category, everything.
 */
fun LazyListScope.paxSearchSections(
    state: PaxSearchState,
    category: SearchCategory,
    subscribed: List<Podcast>,
    nowPlaying: PaxNowPlaying,
    favourites: List<RadioStation>,
    onSeeAll: (SearchCategory) -> Unit,
    onOpenPodcast: (PodcastSearchResult) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
) {
    if (category == SearchCategory.MUSIC || state.term.isBlank()) return
    val limit = if (category == SearchCategory.ALL) 3 else Int.MAX_VALUE
    if (state.loading && state.podcasts == null) {
        item(key = "pax-search-loading") {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }
    if (category != SearchCategory.RADIO) {
        val found = state.podcasts.orEmpty()
        if (found.isNotEmpty() || category == SearchCategory.PODCASTS) {
            item(key = "pax-search-podcasts-h") {
                PaxSectionHeader(
                    stringResource(R.string.pax_podcasts),
                    actionLabel = if (category == SearchCategory.ALL && found.size > limit) stringResource(R.string.pax_see_all) else null,
                    onAction = if (category == SearchCategory.ALL && found.size > limit) ({ onSeeAll(SearchCategory.PODCASTS) }) else null,
                )
            }
            if (found.isEmpty()) item(key = "pax-search-podcasts-e") { PaxEmpty(stringResource(R.string.pax_no_results)) }
            // Indexed keys: the directory can list one feed twice, and a
            // repeated key in a lazy list is a crash, not a duplicate row.
            itemsIndexed(found.take(limit), key = { i, it -> "pax-sp:$i:${it.feedUrl}" }) { _, result ->
                val isSubscribed = subscribed.any { it.key == PaxMedia.feedKey(result.feedUrl) }
                SearchHitRow(
                    imageUrl = result.imageUrl,
                    fallback = Icons.Rounded.Podcasts,
                    title = result.title,
                    subtitle = listOfNotNull(stringResource(R.string.pax_podcast), result.author).joinToString(" · "),
                    trailingDone = isSubscribed,
                    busy = state.opening == result.feedUrl,
                    onClick = { onOpenPodcast(result) },
                )
            }
        }
    }
    if (category != SearchCategory.PODCASTS) {
        val found = state.stations.orEmpty()
        if (found.isNotEmpty() || category == SearchCategory.RADIO) {
            item(key = "pax-search-radio-h") {
                PaxSectionHeader(
                    stringResource(R.string.pax_radio),
                    actionLabel = if (category == SearchCategory.ALL && found.size > limit) stringResource(R.string.pax_see_all) else null,
                    onAction = if (category == SearchCategory.ALL && found.size > limit) ({ onSeeAll(SearchCategory.RADIO) }) else null,
                )
            }
            if (found.isEmpty()) item(key = "pax-search-radio-e") { PaxEmpty(stringResource(R.string.pax_no_results)) }
            itemsIndexed(found.take(limit), key = { i, it -> "pax-sr:$i:${it.id}" }) { _, station ->
                StationRow(
                    station,
                    favourite = favourites.any { it.id == station.id },
                    pinned = LibraryPins.isPinned(LibraryPins.Kind.RADIO, station.id),
                    current = nowPlaying.isCurrent(PaxMedia.radioId(station)),
                    playing = nowPlaying.isPlaying,
                    onClick = { onPlayStation(station) },
                )
            }
        }
    }
}

@Composable
private fun SearchHitRow(
    imageUrl: String?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailingDone: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PAGE_GUTTER, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaxArtwork(imageUrl, fallback, Modifier.size(52.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            trailingDone -> Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.pax_subscribed), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Suppress("unused")
private val searchIcons = listOf(Icons.Rounded.Search, Icons.Rounded.LibraryMusic, Icons.Rounded.Radio)

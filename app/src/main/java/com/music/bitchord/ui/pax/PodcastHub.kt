package com.music.bitchord.ui.pax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.pax.DiscoverItem
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastDiscover
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastSearchResult
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PillTextField
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class PodcastSegment { SHOWS, NEW, QUEUE, DISCOVER }

/** Opens a show's page: feed key, title, artwork. */
typealias OpenShow = (key: String, title: String, imageUrl: String?) -> Unit

/** The Podcasts tab: your shows, what is new, the queue, and Discover. */
@Composable
fun PodcastHubScreen(
    controller: MediaController?,
    contentPadding: PaddingValues,
    segment: PodcastSegment,
    onSegmentChange: (PodcastSegment) -> Unit,
    onOpenShow: OpenShow,
    title: String,
    listState: LazyListState = rememberLazyListState(),
) {
    val podcasts by PodcastStore.podcasts.collectAsStateWithLifecycle()
    val progress by PodcastStore.progress.collectAsStateWithLifecycle()
    val queue by PodcastStore.queue.collectAsStateWithLifecycle()
    val refreshing by PodcastStore.refreshing.collectAsStateWithLifecycle()
    val playbackError by PaxMedia.playbackError.collectAsStateWithLifecycle()
    val nowPlaying = rememberPaxNowPlaying(controller)
    val scope = rememberCoroutineScope()
    var sheetEpisode by remember { mutableStateOf<Pair<Podcast, PodcastEpisode>?>(null) }

    LaunchedEffect(Unit) {
        if (podcasts.any { System.currentTimeMillis() - it.refreshedAt > 30 * 60_000L }) PodcastStore.refreshAll()
    }

    val playing = nowPlaying.mediaId?.let { PodcastStore.findEpisode(it) }
    val showColumns = paxGridColumns(phone = 3, minTileWidth = 150.dp)

    fun play(show: Podcast, episode: PodcastEpisode) {
        controller?.let { c -> scope.launch { PaxPlayer.playEpisode(c, show, episode) } }
    }

    fun toggleQueue(show: Podcast, episode: PodcastEpisode) {
        if (episode.id in queue) PaxPlayer.dequeue(controller, episode.id)
        else scope.launch { PaxPlayer.enqueue(controller, show, episode) }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "title") {
            Text(
                title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            )
        }
        playing?.let { (show, episode) ->
            item(key = "now") {
                PodcastNowPlayingCard(
                    show, episode, controller, nowPlaying,
                    onOpen = { onOpenShow(show.key, show.title, show.imageUrl) },
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
                )
            }
        }
        playbackError?.let { message ->
            item(key = "error") {
                Text(
                    stringResource(R.string.pax_playback_failed, message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = PAGE_GUTTER, vertical = 4.dp)
                        .clickable { PaxMedia.clearError() },
                )
            }
        }
        item(key = "segments") {
            val newCount = remember(podcasts, progress) { newEpisodes(podcasts, progress).size }
            PaxChips(
                options = PodcastSegment.entries,
                selected = segment,
                onSelect = onSegmentChange,
                label = {
                    when (it) {
                        PodcastSegment.SHOWS -> stringResource(R.string.pax_seg_shows)
                        PodcastSegment.NEW -> if (newCount > 0) stringResource(R.string.pax_seg_new_count, newCount)
                        else stringResource(R.string.pax_seg_new)
                        PodcastSegment.QUEUE -> if (queue.isNotEmpty()) stringResource(R.string.pax_seg_queue_count, queue.size)
                        else stringResource(R.string.pax_seg_queue)
                        PodcastSegment.DISCOVER -> stringResource(R.string.pax_seg_discover)
                    }
                },
            )
        }
        when (segment) {
            PodcastSegment.SHOWS -> showsSegment(
                podcasts = podcasts,
                newCounts = { show -> show.episodes.take(10).count { progress[it.id] == null && isRecent(it) } },
                refreshing = refreshing,
                onRefresh = { scope.launch { PodcastStore.refreshAll() } },
                onOpenShow = onOpenShow,
                onDiscover = { onSegmentChange(PodcastSegment.DISCOVER) },
                showColumns = showColumns,
            )
            PodcastSegment.NEW -> {
                val fresh = newEpisodes(podcasts, progress)
                if (fresh.isEmpty()) {
                    item(key = "new-empty") { PaxEmpty(stringResource(R.string.pax_new_empty)) }
                }
                items(fresh, key = { "new:${it.second.id}" }) { (show, episode) ->
                    EpisodeRow(
                        show, episode, progress[episode.id],
                        current = nowPlaying.isCurrent(episode.id),
                        playing = nowPlaying.isPlaying,
                        queued = episode.id in queue,
                        showName = true,
                        onPlay = { play(show, episode) },
                        onToggleQueue = { toggleQueue(show, episode) },
                        onMore = { sheetEpisode = show to episode },
                    )
                }
            }
            PodcastSegment.QUEUE -> queueSegment(
                queued = PodcastStore.queuedEpisodes(),
                progress = progress,
                nowPlaying = nowPlaying,
                onPlayAll = { controller?.let { c -> scope.launch { PaxPlayer.playQueue(c) } } },
                onClear = { PodcastStore.clearQueue() },
                onPlay = ::play,
                onRemove = { PaxPlayer.dequeue(controller, it.id) },
                onMore = { show, episode -> sheetEpisode = show to episode },
            )
            PodcastSegment.DISCOVER -> item(key = "discover") {
                DiscoverPane(podcasts = podcasts, onOpenShow = onOpenShow)
            }
        }
    }

    sheetEpisode?.let { (show, episode) ->
        EpisodeSheet(
            show = show,
            episode = episode,
            controller = controller,
            nowPlaying = nowPlaying,
            onOpenShow = {
                sheetEpisode = null
                onOpenShow(show.key, show.title, show.imageUrl)
            },
            onDismiss = { sheetEpisode = null },
        )
    }
}

private fun isRecent(episode: PodcastEpisode): Boolean =
    episode.publishedAt?.let { System.currentTimeMillis() - it < TimeUnit.DAYS.toMillis(30) } ?: false

/** Unplayed episodes from the last month across every subscription, newest first. */
fun newEpisodes(
    podcasts: List<Podcast>,
    progress: Map<String, com.music.bitchord.data.pax.EpisodeProgress>,
): List<Pair<Podcast, PodcastEpisode>> =
    podcasts.flatMap { show -> show.episodes.take(20).filter { isRecent(it) && progress[it.id]?.played != true }.map { show to it } }
        .sortedByDescending { it.second.publishedAt ?: 0L }
        .take(80)

private fun LazyListScope.showsSegment(
    podcasts: List<Podcast>,
    newCounts: (Podcast) -> Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenShow: OpenShow,
    onDiscover: () -> Unit,
    showColumns: Int,
) {
    item(key = "shows-head") {
        Row(
            Modifier.fillMaxWidth().padding(start = PAGE_GUTTER, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pax_episode_count_shows, podcasts.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.pax_refresh))
            }
        }
    }
    if (podcasts.isEmpty()) {
        item(key = "shows-empty") {
            PaxPromoCard(
                title = stringResource(R.string.pax_promo_podcasts_title),
                body = stringResource(R.string.pax_promo_podcasts_body),
                icon = Icons.Rounded.Explore,
                colors = listOf(Color(0xFF0A3D62), Color(0xFF1B9AAA)),
                onClick = onDiscover,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }
    val sorted = podcasts.sortedByDescending { it.episodes.firstOrNull()?.publishedAt ?: 0L }
    val columns = showColumns
    val rows = (sorted.map<Podcast, Podcast?> { it } + listOf(null)).chunked(columns)
    items(rows.size, key = { "shows-row-$it" }) { index ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rows[index].forEach { show ->
                if (show != null) {
                    PodcastTile(
                        show = show,
                        newCount = newCounts(show),
                        onClick = { onOpenShow(show.key, show.title, show.imageUrl) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    AddShowTile(onClick = onDiscover, modifier = Modifier.weight(1f))
                }
            }
            repeat(columns - rows[index].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AddShowTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.pax_add_podcast),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun LazyListScope.queueSegment(
    queued: List<Pair<Podcast, PodcastEpisode>>,
    progress: Map<String, com.music.bitchord.data.pax.EpisodeProgress>,
    nowPlaying: PaxNowPlaying,
    onPlayAll: () -> Unit,
    onClear: () -> Unit,
    onPlay: (Podcast, PodcastEpisode) -> Unit,
    onRemove: (PodcastEpisode) -> Unit,
    onMore: (Podcast, PodcastEpisode) -> Unit,
) {
    if (queued.isEmpty()) {
        item(key = "queue-empty") { PaxEmpty(stringResource(R.string.pax_queue_empty)) }
        return
    }
    item(key = "queue-head") {
        val totalSeconds = queued.sumOf { (_, e) ->
            val p = progress[e.id]
            ((e.durationSeconds ?: 0L) - (p?.positionMs ?: 0L) / 1000).coerceAtLeast(0L)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pax_episode_count, queued.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (totalSeconds > 0) {
                    Text(
                        stringResource(R.string.pax_queue_total, shortDuration(totalSeconds)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.pax_clear)) }
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.pax_play_all), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
    itemsIndexed(queued, key = { _, it -> "queue:${it.second.id}" }) { index, (show, episode) ->
        EpisodeRow(
            show, episode, progress[episode.id],
            current = nowPlaying.isCurrent(episode.id),
            playing = nowPlaying.isPlaying,
            queued = true,
            showName = true,
            onPlay = { onPlay(show, episode) },
            onToggleQueue = { onRemove(episode) },
            onMore = { onMore(show, episode) },
            leading = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.pax_move_up),
                        tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(26.dp).clip(CircleShape).clickable(enabled = index > 0) {
                            PodcastStore.moveInQueue(index, index - 1)
                        },
                    )
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.pax_move_down),
                        tint = if (index < queued.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(26.dp).clip(CircleShape).clickable(enabled = index < queued.lastIndex) {
                            PodcastStore.moveInQueue(index, index + 1)
                        },
                    )
                }
            },
        )
    }
}

// ───────────────────────────── Discover ─────────────────────────────

@Composable
fun DiscoverPane(podcasts: List<Podcast>, onOpenShow: OpenShow) {
    val scope = rememberCoroutineScope()
    var country by rememberSaveable { mutableStateOf(PodcastDiscover.defaultCountry()) }
    var category by rememberSaveable { mutableStateOf(PodcastDiscover.Category.ALL) }
    var chart by remember { mutableStateOf<List<DiscoverItem>?>(null) }
    var chartError by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastSearchResult>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(country, category) {
        chart = null
        chartError = false
        PodcastDiscover.top(country, category)
            .onSuccess { chart = it }
            .onFailure { chartError = true }
    }

    fun openFeed(feedUrl: String, marker: String) {
        scope.launch {
            opening = marker
            error = null
            PodcastStore.preview(feedUrl)
                .onSuccess { onOpenShow(it.key, it.title, it.imageUrl) }
                .onFailure { error = it.message }
            opening = null
        }
    }

    fun openChart(item: DiscoverItem) {
        scope.launch {
            opening = item.appleId
            error = null
            PodcastDiscover.feedUrl(item.appleId)
                .onSuccess { feed -> openFeed(feed, item.appleId) }
                .onFailure { error = it.message; opening = null }
        }
    }

    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        if (PodcastStore.looksLikeUrl(text)) {
            openFeed(text, text)
            return
        }
        scope.launch {
            busy = true
            error = null
            PodcastStore.search(text).onSuccess { results = it }.onFailure { error = it.message }
            busy = false
        }
    }

    Column {
        Row(
            Modifier.padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillTextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) results = null },
                placeholder = stringResource(R.string.pax_podcast_search_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { submit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (PodcastStore.looksLikeUrl(query)) Icons.Rounded.Add else Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 4.dp))
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 4.dp))
        }
        results?.let { found ->
            PaxSectionHeader(stringResource(R.string.pax_search_results))
            if (found.isEmpty()) PaxEmpty(stringResource(R.string.pax_no_results))
            DiscoverGrid(
                found.map { DiscoverCell(it.feedUrl, it.title, it.author, it.imageUrl, null, PaxMedia.feedKey(it.feedUrl)) },
                podcasts, opening,
            ) { cell -> openFeed(cell.id, cell.id) }
            return@Column
        }
        Text(
            stringResource(R.string.pax_top_podcasts),
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
            options = PodcastDiscover.Category.entries,
            selected = category,
            onSelect = { category = it },
            label = { categoryLabel(it) },
        )
        when {
            chartError -> PaxEmpty(stringResource(R.string.pax_offline))
            chart == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> DiscoverGrid(
                chart.orEmpty().mapIndexed { i, it -> DiscoverCell(it.appleId, it.title, it.author, it.imageUrl, i + 1, null) },
                podcasts, opening,
            ) { cell -> chart?.firstOrNull { it.appleId == cell.id }?.let(::openChart) }
        }
    }
}

private data class DiscoverCell(
    val id: String,
    val title: String,
    val author: String?,
    val imageUrl: String?,
    val rank: Int?,
    val feedKey: String?,
)

@Composable
private fun DiscoverGrid(
    cells: List<DiscoverCell>,
    podcasts: List<Podcast>,
    opening: String?,
    onClick: (DiscoverCell) -> Unit,
) {
    val titles = remember(podcasts) { podcasts.map { it.title.lowercase() }.toSet() }
    val columns = paxGridColumns(phone = 3, minTileWidth = 150.dp)
    cells.chunked(columns).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { cell ->
                Box(Modifier.weight(1f)) {
                    DiscoverTile(
                        title = cell.title,
                        author = cell.author,
                        imageUrl = cell.imageUrl,
                        rank = cell.rank,
                        subscribed = cell.feedKey?.let { k -> podcasts.any { it.key == k } }
                            ?: (cell.title.lowercase() in titles),
                        onClick = { onClick(cell) },
                    )
                    if (opening == cell.id) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun categoryLabel(category: PodcastDiscover.Category): String = stringResource(
    when (category) {
        PodcastDiscover.Category.ALL -> R.string.pax_cat_all
        PodcastDiscover.Category.NEWS -> R.string.pax_cat_news
        PodcastDiscover.Category.COMEDY -> R.string.pax_cat_comedy
        PodcastDiscover.Category.SOCIETY -> R.string.pax_cat_society
        PodcastDiscover.Category.TRUE_CRIME -> R.string.pax_cat_true_crime
        PodcastDiscover.Category.SPORTS -> R.string.pax_cat_sports
        PodcastDiscover.Category.HISTORY -> R.string.pax_cat_history
        PodcastDiscover.Category.SCIENCE -> R.string.pax_cat_science
        PodcastDiscover.Category.EDUCATION -> R.string.pax_cat_education
        PodcastDiscover.Category.BUSINESS -> R.string.pax_cat_business
        PodcastDiscover.Category.TECHNOLOGY -> R.string.pax_cat_technology
        PodcastDiscover.Category.HEALTH -> R.string.pax_cat_health
        PodcastDiscover.Category.ARTS -> R.string.pax_cat_arts
        PodcastDiscover.Category.MUSIC -> R.string.pax_cat_music
        PodcastDiscover.Category.TV_FILM -> R.string.pax_cat_tv_film
        PodcastDiscover.Category.KIDS -> R.string.pax_cat_kids
        PodcastDiscover.Category.FICTION -> R.string.pax_cat_fiction
        PodcastDiscover.Category.LEISURE -> R.string.pax_cat_leisure
        PodcastDiscover.Category.RELIGION -> R.string.pax_cat_religion
    },
)

@Suppress("unused")
private val podcastIcon = Icons.Rounded.Podcasts

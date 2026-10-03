package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import com.music.bitchord.ui.pax.PaxChips
import com.music.bitchord.ui.pax.PaxSectionHeader
import com.music.bitchord.ui.pax.paxGridColumns
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.feedSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: UiState<List<MoodGenreSection>>,
    listState: LazyListState,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        val scope = rememberCoroutineScope()
        val sections = (state as? UiState.Success)?.data.orEmpty()
        // The chip for whichever section is at the top of the page.
        val currentSection by remember(sections) {
            derivedStateOf {
                val index = listState.firstVisibleItemIndex - EXPLORE_HEADER_ITEMS
                sections.getOrNull(index.coerceIn(0, (sections.size - 1).coerceAtLeast(0)))?.title
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "explore-title") {
                Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.explore),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.pax_explore_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // PAXwave: jump straight to a section instead of scrolling for it.
            item(key = "explore-chips") {
                if (sections.size > 1) {
                    PaxChips(
                        options = sections.map { it.title },
                        selected = currentSection ?: sections.first().title,
                        label = { it },
                        onSelect = { title ->
                            val index = sections.indexOfFirst { it.title == title }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index + EXPLORE_HEADER_ITEMS) }
                        },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            when (state) {
                UiState.Loading -> item { ExploreSkeleton() }
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                }
                is UiState.Success -> state.data.forEach { section ->
                    item(key = section.title) {
                        MoodGenreGrid(section = section, onCategoryClick = onCategoryClick)
                    }
                }
            }
        }
    }
}

/** The title and the chip row, which sit above the first section. */
private const val EXPLORE_HEADER_ITEMS = 2

private val CardShape = RoundedCornerShape(18.dp)

@Composable
private fun MoodGenreGrid(
    section: MoodGenreSection,
    onCategoryClick: (MoodGenre) -> Unit,
) {
    val columns = paxGridColumns(phone = 2, minTileWidth = 230.dp, maxColumns = 5)
    Column(Modifier.padding(bottom = 18.dp)) {
        PaxSectionHeader(section.title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth - PAGE_GUTTER * 2 - 12.dp * (columns - 1)) / columns
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = PAGE_GUTTER),
            ) {
                section.items.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            MoodGenreCard(
                                item = item,
                                onClick = { onCategoryClick(item) },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.width(cardWidth)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodGenreCard(
    item: MoodGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = moodColor(item.title)
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .height(108.dp)
            .clip(CardShape)
            .background(surface)
            .clickable(onClick = onClick),
    ) {
        // The playlist's own artwork fills the right side and dissolves into
        // the card, rather than sitting on it as a tilted sleeve.
        item.thumbnailUrl?.let { artwork ->
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to surface,
                            0.38f to surface,
                            0.72f to surface.copy(alpha = 0.55f),
                            1f to surface.copy(alpha = 0.05f),
                        ),
                    )
                    // A soft glow of the category's colour from the top-left
                    // corner: colour as a hint, not a flood.
                    drawRect(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = size.width * 0.75f,
                        ),
                    )
                },
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp)
                .size(width = 20.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.66f)
                .padding(start = 14.dp, bottom = 12.dp),
        )
        Box(
            Modifier
                .matchParentSize()
                .border(0.5.dp, Color.White.copy(alpha = 0.07f), CardShape),
        )
    }
}

private fun moodColor(title: String): Color = when ((title.hashCode() and Int.MAX_VALUE) % 8) {
    0 -> Color(0xFFFF7A45)
    1 -> Color(0xFFFF4F8B)
    2 -> Color(0xFFB08CFF)
    3 -> Color(0xFF7C6CFF)
    4 -> Color(0xFFFFB347)
    5 -> Color(0xFF4F8DFF)
    6 -> Color(0xFF4FD8EB)
    else -> Color(0xFFE05BC6)
}

@Composable
private fun ExploreSkeleton() {
    val columns = paxGridColumns(phone = 2, minTileWidth = 230.dp, maxColumns = 5)
    Column(Modifier.padding(horizontal = PAGE_GUTTER)) {
        repeat(5) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                repeat(columns) {
                    ShimmerBox(Modifier.weight(1f).height(108.dp), CardShape)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodGenrePlaylistsScreen(
    title: String,
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            )
        }
        when (state) {
            UiState.Loading -> feedSkeleton()
            is UiState.Error -> item {
                MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
            }
            is UiState.Success -> items(state.data, key = { it.title }) { shelf ->
                Shelf(shelf = shelf, onItemClick = onItemClick)
            }
        }
    }
}

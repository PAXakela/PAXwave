package com.music.bitchord.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.pax.LocalContentWidth
import com.music.bitchord.ui.pax.LocalTabletMode
import com.music.bitchord.ui.pax.PaxNavRail
import com.music.bitchord.ui.pax.PodcastHubScreen
import com.music.bitchord.ui.pax.PodcastSegment
import com.music.bitchord.ui.pax.RadioScreen
import com.music.bitchord.ui.pax.paxNavigationWidth
import com.music.bitchord.ui.pax.paxSidebarExpanded
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletShots {

    private val now = System.currentTimeMillis()

    private fun show(title: String, n: Int): Podcast {
        val url = "https://example.org/$title.xml"
        val key = PaxMedia.feedKey(url)
        return Podcast(
            feedUrl = url, key = key, title = title, author = "Host",
            description = "A weekly show.", refreshedAt = now,
            episodes = (1..n).map { i ->
                PodcastEpisode(
                    id = PaxMedia.episodeId(key, "$title-$i"), title = "Episode $i",
                    audioUrl = "https://example.org/$i.mp3", publishedAt = now - i * 86_400_000L,
                    durationSeconds = 1800L,
                )
            },
        )
    }

    @Before
    fun seed() {
        PodcastStore.seedForTest(
            listOf("Lage der Nation", "Fest & Flauschig", "Baywatch Berlin", "Gemischtes Hack", "Zeit Verbrechen", "Hotel Matze", "Apokalypse & Filterkaffee", "Alles gesagt?", "Kurt Krömer").map { show(it, 4) },
            emptyMap(), queue = emptyList(),
        )
        RadioStore.seedForTest(
            (1..9).map { RadioStation("$it", "Station $it", "https://x/$it", country = "DE") },
        )
    }

    private fun frame(name: String, width: Dp, content: @Composable () -> Unit) =
        captureRoboImage("build/shots/$name.png") {
            BitChordTheme(darkTheme = true) {
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    PaxNavRail(
                        tabs = listOf(
                            BottomTab("Home", BitChordIcons.Home),
                            BottomTab("Explore", BitChordIcons.Explore),
                            BottomTab("Podcasts", Icons.Rounded.Podcasts),
                            BottomTab("Library", BitChordIcons.Library),
                        ),
                        selectedIndex = 2,
                        searchSelected = false,
                        settingsSelected = false,
                        expanded = paxSidebarExpanded(width),
                        searchIcon = BitChordIcons.Search,
                        searchLabel = "Search",
                        settingsLabel = "Settings",
                        onTabSelected = {}, onSearch = {}, onSettings = {},
                    )
                    CompositionLocalProvider(
                        LocalTabletMode provides true,
                        LocalContentWidth provides width - paxNavigationWidth(width),
                    ) {
                        Box(Modifier.weight(1f)) { content() }
                    }
                }
            }
        }

    private val pad = PaddingValues(top = 24.dp, bottom = 24.dp)

    @Config(sdk = [35], qualifiers = "en-w1280dp-h800dp-land-xhdpi")
    @Test fun landscapeHub() = frame("tablet_land_hub", 1280.dp) {
        PodcastHubScreen(null, pad, PodcastSegment.SHOWS, {}, { _, _, _ -> }, title = "Podcasts")
    }

    @Config(sdk = [35], qualifiers = "en-w800dp-h1280dp-port-xhdpi")
    @Test fun portraitRadio() = frame("tablet_port_radio", 800.dp) {
        RadioScreen(null, pad)
    }
}

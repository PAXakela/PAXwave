package com.music.bitchord.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.EpisodeProgress
import com.music.bitchord.data.pax.LibraryPins
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.pax.RadioStore
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.components.PaxwaveMark
import com.music.bitchord.ui.components.TopBarAccountButton
import com.music.bitchord.ui.pax.DevicePills
import com.music.bitchord.ui.pax.PaxChips
import com.music.bitchord.ui.pax.PaxHomeSection
import com.music.bitchord.ui.pax.PaxNowPlaying
import com.music.bitchord.ui.pax.LibraryPaxContent
import com.music.bitchord.ui.pax.LibraryFilter
import com.music.bitchord.ui.pax.PinnedGrid
import com.music.bitchord.ui.pax.PodcastHubScreen
import com.music.bitchord.ui.pax.PodcastNowPlayingCard
import com.music.bitchord.ui.pax.PodcastSegment
import com.music.bitchord.ui.pax.PodcastShowScreen
import com.music.bitchord.ui.pax.libraryFilterLabel
import com.music.bitchord.ui.replay.ReplayCreditCard
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi")
class PaxShots {

    private val now = System.currentTimeMillis()
    private fun daysAgo(d: Int) = now - TimeUnit.DAYS.toMillis(d.toLong())

    private fun show(title: String, author: String, n: Int): Podcast {
        val url = "https://example.org/$title.xml"
        val key = PaxMedia.feedKey(url)
        return Podcast(
            feedUrl = url, key = key, title = title, author = author,
            description = "Der wöchentliche Podcast über alles, was die Woche bewegt hat — mit Gästen, Hintergründen und einer Prise Humor.",
            refreshedAt = now,
            episodes = (1..n).map { i ->
                PodcastEpisode(
                    id = PaxMedia.episodeId(key, "$title-$i"),
                    title = "Folge ${400 - i}: " + listOf("Wie geht es weiter?", "Das große Interview", "Was wir gelernt haben", "Zwischen den Zeilen", "Live vom Festival")[i % 5],
                    audioUrl = "https://example.org/$i.mp3",
                    publishedAt = daysAgo(i * 2 - 1),
                    durationSeconds = 1800L + i * 611L,
                    description = "In dieser Folge sprechen wir über die wichtigsten Themen der Woche.",
                )
            },
        )
    }

    private val shows = listOf(
        show("Lage der Nation", "Philip Banse & Ulf Buermeyer", 12),
        show("Fest & Flauschig", "Jan Böhmermann & Olli Schulz", 10),
        show("Baywatch Berlin", "Klaas Heufer-Umlauf", 9),
        show("Gemischtes Hack", "Felix Lobrecht & Tommi Schmitt", 8),
        show("Zeit Verbrechen", "DIE ZEIT", 7),
    )

    private val stations = listOf(
        RadioStation("1", "1LIVE", "https://x/1", country = "DE", codec = "MP3", bitrate = 128, tags = "pop,charts"),
        RadioStation("2", "Deutschlandfunk", "https://x/2", country = "DE", codec = "AAC", bitrate = 96, tags = "news,talk"),
        RadioStation("3", "SWR3", "https://x/3", country = "DE", codec = "MP3", bitrate = 128),
        RadioStation("4", "FluxFM", "https://x/4", country = "DE", codec = "MP3", bitrate = 192, tags = "indie"),
        RadioStation("5", "Klassik Radio", "https://x/5", country = "DE"),
    )

    @Before
    fun seed() {
        val progress = mapOf(
            shows[0].episodes[0].id to EpisodeProgress(positionMs = 1_500_000, durationMs = 3_600_000, updatedAt = now),
            shows[2].episodes[1].id to EpisodeProgress(positionMs = 600_000, durationMs = 2_800_000, updatedAt = now - 1000),
            shows[1].episodes[2].id to EpisodeProgress(played = true, durationMs = 3_000_000),
        )
        PodcastStore.seedForTest(
            shows, progress,
            queue = listOf(shows[0].episodes[0].id, shows[3].episodes[0].id, shows[4].episodes[1].id),
        )
        RadioStore.seedForTest(stations)
        LibraryPins.seedForTest(
            listOf(
                LibraryPins.podcastPin(shows[0]),
                LibraryPins.stationPin(stations[1]),
                LibraryPins.Pin(LibraryPins.Kind.PLAYLIST, "VLPL1", "Liked Music", "Playlist"),
                LibraryPins.Pin(LibraryPins.Kind.ARTIST, "UC1", "Billie Eilish", "Artist"),
                LibraryPins.Pin(LibraryPins.Kind.ALBUM, "MPREb1", "Hit Me Hard and Soft", "Album"),
            ),
        )
    }

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("build/shots/$name.png") {
            BitChordTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }

    private val pad = PaddingValues(top = 24.dp, bottom = 24.dp)

    @Test fun hubShows() = shot("hub_shows") {
        PodcastHubScreen(null, pad, PodcastSegment.SHOWS, {}, { _, _, _ -> }, title = "Podcasts")
    }

    @Test fun hubNew() = shot("hub_new") {
        PodcastHubScreen(null, pad, PodcastSegment.NEW, {}, { _, _, _ -> }, title = "Podcasts")
    }

    @Test fun hubQueue() = shot("hub_queue") {
        PodcastHubScreen(null, pad, PodcastSegment.QUEUE, {}, { _, _, _ -> }, title = "Podcasts")
    }

    @Test fun showPage() = shot("show_page") {
        PodcastShowScreen(shows[0].key, null, pad) { _, _, _ -> }
    }

    @Test fun nowPlayingCard() = shot("now_playing_card") {
        Column(Modifier.padding(12.dp)) {
            PodcastNowPlayingCard(shows[0], shows[0].episodes[0], null, PaxNowPlaying(isPlaying = true), onOpen = {})
        }
    }

    @Test fun homeSection() = shot("home_section") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            PaxHomeSection(null, { _, _, _ -> }, {}, {})
        }
    }

    @Test fun library() = shot("library") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            Text("Bibliothek", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 10.dp))
            PinnedGrid(LibraryPins.pins.value, currentRadioId = "2", onOpen = {}, onEdit = {})
            PaxChips(LibraryFilter.entries, LibraryFilter.ALL, label = { libraryFilterLabel(it) }, onSelect = {})
            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                ReplayCreditCard("Minuten gehört", "12.480", "Mehr als 94 % der Hörer", null, "Nico", "03/24", onClick = {}, modifier = Modifier.width(300.dp))
            }
            LibraryPaxContent(LibraryFilter.ALL, null, PaxNowPlaying(), { _, _, _ -> }, {})
            DevicePills(
                "Auf dem Gerät",
                listOf(
                    ShelfItem("Downloads", "Heruntergeladen", null, null, "local:downloads"),
                    ShelfItem("Lokale Musik", "Audiodateien", null, null, "local:all"),
                    ShelfItem("WebDAV", "Nicht eingerichtet", null, null, "local:webdav"),
                ),
                onItemClick = {},
            )
        }
    }

    @Test fun header() = shot("header") {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PaxwaveMark(Modifier.weight(1f))
                TopBarAccountButton(account = Account("Nico", "n@x", null), onClick = {})
            }
        }
    }
}

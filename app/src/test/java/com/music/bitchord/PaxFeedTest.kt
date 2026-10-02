package com.music.bitchord

import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.PaxMedia.episodeSong
import com.music.bitchord.data.pax.PodcastFeedParser
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaxFeedTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
             xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Lage der Nation</title>
            <link>https://lagedernation.org</link>
            <itunes:author>Philip Banse &amp; Ulf Buermeyer</itunes:author>
            <description><![CDATA[<p>Der Politik-Podcast <b>aus Berlin</b>.</p>]]></description>
            <itunes:image href="https://example.org/cover.jpg"/>
            <item>
              <title>LdN 400 – Older</title>
              <guid isPermaLink="false">ldn-400</guid>
              <pubDate>Thu, 18 Sep 2026 06:00:00 +0200</pubDate>
              <enclosure url="https://cdn.example.org/ldn400.mp3" length="1" type="audio/mpeg"/>
              <itunes:duration>5400</itunes:duration>
            </item>
            <item>
              <title>LdN 401 – Newer</title>
              <guid>ldn-401</guid>
              <pubDate>Thu, 25 Sep 2026 06:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example.org/ldn401.m4a" type="audio/mp4"/>
              <itunes:duration>1:31:05</itunes:duration>
              <itunes:image href="https://example.org/401.jpg"/>
              <content:encoded><![CDATA[<p>Shownotes</p>]]></content:encoded>
            </item>
            <item>
              <title>Announcement without audio</title>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parsesRssWithItunesExtensions() {
        val show = PodcastFeedParser.parse("https://feed.example.org/ldn", rss.byteInputStream())
        assertEquals("Lage der Nation", show.title)
        assertEquals("Philip Banse & Ulf Buermeyer", show.author)
        assertEquals("Der Politik-Podcast aus Berlin.", show.description)
        assertEquals("https://example.org/cover.jpg", show.imageUrl)
        assertEquals(PaxMedia.feedKey("https://feed.example.org/ldn"), show.key)

        // Items without an enclosure are skipped; the rest come newest first.
        assertEquals(2, show.episodes.size)
        val newest = show.episodes[0]
        assertEquals("LdN 401 – Newer", newest.title)
        assertEquals("https://cdn.example.org/ldn401.m4a", newest.audioUrl)
        assertEquals(5465L, newest.durationSeconds)
        assertEquals("https://example.org/401.jpg", newest.imageUrl)
        assertEquals("Shownotes", newest.description)
        assertEquals(5400L, show.episodes[1].durationSeconds)
        assertTrue(newest.publishedAt!! > show.episodes[1].publishedAt!!)
        assertTrue(newest.id.startsWith("podcast:${show.key}:"))
    }

    @Test
    fun episodeIdsAreStableAcrossRefreshes() {
        val a = PodcastFeedParser.parse("https://feed.example.org/ldn", rss.byteInputStream())
        val b = PodcastFeedParser.parse("https://feed.example.org/ldn", rss.byteInputStream())
        assertEquals(a.episodes.map { it.id }, b.episodes.map { it.id })
    }

    @Test
    fun episodeSongPlaysFromItsEnclosure() {
        val show = PodcastFeedParser.parse("https://feed.example.org/ldn", rss.byteInputStream())
        val song = show.episodeSong(show.episodes[0])
        assertEquals("https://cdn.example.org/ldn401.m4a", song.localUri)
        assertEquals("Lage der Nation", song.artist)
        assertEquals("1:31:05", song.durationText)
        assertTrue(PaxMedia.isPaxId(song.videoId))
        assertTrue(PaxMedia.isPodcastId(song.videoId))
        assertEquals(PaxMedia.showPage(show.key), song.playbackSourceId)
        assertEquals(show.key, PaxMedia.feedKeyOfPage(song.playbackSourceId))
    }

    @Test
    fun parsesAtomEnclosures() {
        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Cast</title>
              <entry>
                <id>tag:1</id>
                <title>First</title>
                <published>2026-09-01T10:00:00Z</published>
                <link rel="enclosure" href="https://example.org/1.ogg" type="audio/ogg"/>
              </entry>
            </feed>
        """.trimIndent()
        val show = PodcastFeedParser.parse("https://example.org/atom", atom.byteInputStream())
        assertEquals("Atom Cast", show.title)
        assertEquals("https://example.org/1.ogg", show.episodes.single().audioUrl)
        assertNotNull(show.episodes.single().publishedAt)
    }

    @Test(expected = PodcastFeedParser.FeedException::class)
    fun rejectsHtmlPages() {
        PodcastFeedParser.parse("https://example.org", "<html><body>hi</body></html>".byteInputStream())
    }

    @Test
    fun durationsAndDates() {
        assertEquals(61L, PodcastFeedParser.parseDuration("1:01"))
        assertEquals(3661L, PodcastFeedParser.parseDuration("01:01:01"))
        assertEquals(42L, PodcastFeedParser.parseDuration("42"))
        assertEquals(90L, PodcastFeedParser.parseDuration("1:30.5"))
        assertNull(PodcastFeedParser.parseDuration("soon"))
        assertNotNull(PodcastFeedParser.parseDate("Wed, 1 Oct 2026 08:00:00 +0000"))
        assertNotNull(PodcastFeedParser.parseDate("2026-10-01"))
    }

    @Test
    fun urlsTypedByHand() {
        assertEquals("https://example.org/feed", PodcastStore.normaliseUrl(" feed://example.org/feed "))
        assertEquals("https://example.org/rss", PodcastStore.normaliseUrl("example.org/rss"))
        assertEquals("http://radio.example:8000/live", PodcastStore.normaliseUrl("http://radio.example:8000/live"))
        assertTrue(PodcastStore.looksLikeUrl("https://example.org/feed.xml"))
        assertTrue(PodcastStore.looksLikeUrl("example.org/feed.xml"))
        assertFalse(PodcastStore.looksLikeUrl("lage der nation"))
        assertFalse(PodcastStore.looksLikeUrl("fest & flauschig"))
    }

    @Test
    fun parsesRadioBrowserStations() {
        val body = """
            [
              {"stationuuid":"960e57c5-0601-11e8-ae97-52543be04c81","name":"1LIVE",
               "url":"http://wdr-1live-live.icecast.wdr.de/wdr/1live/live/mp3/128/stream.mp3",
               "url_resolved":"https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3",
               "favicon":"https://www1.wdr.de/radio/1live/favicon.ico","tags":"pop,rock,news",
               "countrycode":"DE","codec":"MP3","bitrate":128},
              {"stationuuid":"x","name":"","url":"http://broken"},
              {"stationuuid":"y","name":"No URL"}
            ]
        """.trimIndent()
        val stations = RadioStore.parseStations(body)
        assertEquals(1, stations.size)
        val live = stations.single()
        assertEquals("1LIVE", live.name)
        assertTrue(live.streamUrl.startsWith("https://"))
        assertEquals("MP3 · 128 kbps · pop, rock, news", live.details)
        assertTrue(PaxMedia.isRadioId(PaxMedia.radioId(live)))
        assertFalse(PaxMedia.isPodcastId(PaxMedia.radioId(live)))
    }
}

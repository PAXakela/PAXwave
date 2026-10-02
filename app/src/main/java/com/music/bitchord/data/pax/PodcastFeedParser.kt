package com.music.bitchord.data.pax

import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads a podcast feed: RSS 2.0 with the iTunes extensions every podcast host
 * writes, and the odd Atom feed. DOM rather than a pull parser so the same code
 * runs in the JVM unit tests, where android.util.Xml is only a stub.
 */
object PodcastFeedParser {

    class FeedException(message: String) : Exception(message)

    fun parse(feedUrl: String, input: InputStream, maxEpisodes: Int = MAX_EPISODES): Podcast {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // Feeds are untrusted input: no DTDs, no external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = try {
            factory.newDocumentBuilder().parse(input)
        } catch (e: Exception) {
            throw FeedException("That link is not a podcast feed (${e.message ?: "unreadable"})")
        }
        val root = document.documentElement ?: throw FeedException("The feed is empty")
        val key = PaxMedia.feedKey(feedUrl)
        return when (root.nodeName.lowercase(Locale.ROOT)) {
            "rss", "rdf:rdf" -> parseRss(feedUrl, key, root, maxEpisodes)
            "feed" -> parseAtom(feedUrl, key, root, maxEpisodes)
            else -> throw FeedException("That link is not a podcast feed")
        }
    }

    private fun parseRss(feedUrl: String, key: String, root: Element, max: Int): Podcast {
        val channel = root.child("channel") ?: throw FeedException("The feed has no channel")
        val channelImage = channel.child("itunes:image")?.getAttribute("href")?.nonBlank()
            ?: channel.child("image")?.child("url")?.text()
            ?: channel.child("media:thumbnail")?.getAttribute("url")?.nonBlank()
        val items = (channel.children("item") + root.children("item"))
        val episodes = items.mapNotNull { item ->
            val enclosure = item.children("enclosure").firstOrNull { it.getAttribute("url").isNotBlank() }
                ?: item.children("media:content").firstOrNull { it.getAttribute("url").isNotBlank() }
            val audioUrl = enclosure?.getAttribute("url")?.trim()?.nonBlank() ?: return@mapNotNull null
            val guid = item.child("guid")?.text() ?: audioUrl
            PodcastEpisode(
                id = PaxMedia.episodeId(key, guid),
                title = item.child("title")?.text() ?: item.child("itunes:title")?.text() ?: "Episode",
                audioUrl = audioUrl,
                publishedAt = (item.child("pubDate") ?: item.child("dc:date"))?.text()?.let(::parseDate),
                durationSeconds = item.child("itunes:duration")?.text()?.let(::parseDuration),
                description = (item.child("itunes:summary") ?: item.child("description")
                    ?: item.child("content:encoded"))?.text()?.let(::plainText),
                imageUrl = item.child("itunes:image")?.getAttribute("href")?.nonBlank(),
            )
        }
        if (episodes.isEmpty() && items.isEmpty()) throw FeedException("The feed lists no episodes")
        return Podcast(
            feedUrl = feedUrl,
            key = key,
            title = channel.child("title")?.text() ?: "Podcast",
            author = (channel.child("itunes:author") ?: channel.child("author")
                ?: channel.child("managingEditor"))?.text(),
            description = (channel.child("itunes:summary") ?: channel.child("description"))?.text()
                ?.let(::plainText),
            imageUrl = channelImage,
            link = channel.child("link")?.text(),
            // One id per episode: some feeds repeat a guid, and the lists key on it.
            episodes = episodes.distinctBy { it.id }.sortedByDescending { it.publishedAt ?: 0L }.take(max),
        )
    }

    private fun parseAtom(feedUrl: String, key: String, root: Element, max: Int): Podcast {
        val episodes = root.children("entry").mapNotNull { entry ->
            val enclosure = entry.children("link").firstOrNull { it.getAttribute("rel") == "enclosure" }
            val audioUrl = enclosure?.getAttribute("href")?.trim()?.nonBlank() ?: return@mapNotNull null
            PodcastEpisode(
                id = PaxMedia.episodeId(key, entry.child("id")?.text() ?: audioUrl),
                title = entry.child("title")?.text() ?: "Episode",
                audioUrl = audioUrl,
                publishedAt = (entry.child("published") ?: entry.child("updated"))?.text()?.let(::parseDate),
                durationSeconds = entry.child("itunes:duration")?.text()?.let(::parseDuration),
                description = (entry.child("summary") ?: entry.child("content"))?.text()?.let(::plainText),
            )
        }
        return Podcast(
            feedUrl = feedUrl,
            key = key,
            title = root.child("title")?.text() ?: "Podcast",
            author = root.child("author")?.child("name")?.text(),
            description = root.child("subtitle")?.text()?.let(::plainText),
            imageUrl = (root.child("logo") ?: root.child("icon"))?.text(),
            // One id per episode: some feeds repeat a guid, and the lists key on it.
            episodes = episodes.distinctBy { it.id }.sortedByDescending { it.publishedAt ?: 0L }.take(max),
        )
    }

    /** "3600", "61:30", "1:01:30" or "1:01:30.5" — whichever the host wrote. */
    fun parseDuration(text: String): Long? {
        val parts = text.trim().split(':')
        if (parts.isEmpty() || parts.size > 3) return null
        val numbers = parts.map { it.trim().substringBefore('.').toLongOrNull() ?: return null }
        return numbers.fold(0L) { total, part -> total * 60 + part }.takeIf { it > 0 }
    }

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd",
    )

    fun parseDate(text: String): Long? {
        val value = text.trim().replace(Regex("\\s+"), " ")
        for (pattern in DATE_FORMATS) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }
            runCatching { format.parse(value) }.getOrNull()?.let { return it.time }
        }
        return null
    }

    fun plainText(html: String): String? =
        runCatching { Jsoup.parse(html).text() }.getOrDefault(html).trim().nonBlank()

    private fun Element.children(name: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.equals(name, ignoreCase = true)) {
                out += node as Element
            }
        }
        return out
    }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun Element.text(): String? = textContent?.trim()?.nonBlank()

    private fun String.nonBlank(): String? = takeIf { it.isNotBlank() }

    const val MAX_EPISODES = 400
}

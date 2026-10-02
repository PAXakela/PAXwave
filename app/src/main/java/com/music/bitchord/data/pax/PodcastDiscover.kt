package com.music.bitchord.data.pax

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One show from the Apple Podcasts charts, before its feed is known. */
data class DiscoverItem(
    val appleId: String,
    val title: String,
    val author: String?,
    val imageUrl: String?,
)

/**
 * Discovering podcasts by country and category, from Apple's public top
 * charts — the same source AntennaPod's Discover page uses. A chart lists
 * shows by Apple id; [feedUrl] looks up the RSS feed behind one when it is
 * opened.
 */
object PodcastDiscover {

    /** Apple Podcasts category ids. Names come from the app's strings. */
    enum class Category(val appleId: Int?) {
        ALL(null), NEWS(1489), COMEDY(1303), SOCIETY(1324), TRUE_CRIME(1488), SPORTS(1545),
        HISTORY(1487), SCIENCE(1533), EDUCATION(1304), BUSINESS(1321), TECHNOLOGY(1318),
        HEALTH(1512), ARTS(1301), MUSIC(1310), TV_FILM(1309), KIDS(1305), FICTION(1483),
        LEISURE(1502), RELIGION(1314),
    }

    /** Regions offered in the switcher; the phone's own country leads when it is one of them. */
    val COUNTRIES = listOf(
        "de", "at", "ch", "us", "gb", "fr", "nl", "it", "es", "se", "pl", "tr", "ca", "au", "br", "jp",
    )

    fun defaultCountry(): String =
        Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it in COUNTRIES } ?: "de"

    fun countryName(code: String): String =
        Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(Locale.getDefault()).ifBlank { code.uppercase() }

    private val json = Json { ignoreUnknownKeys = true }
    private val charts = ConcurrentHashMap<String, List<DiscoverItem>>()
    private val feeds = ConcurrentHashMap<String, String>()

    suspend fun top(country: String, category: Category): Result<List<DiscoverItem>> =
        withContext(Dispatchers.IO) {
            val cacheKey = "$country/${category.name}"
            charts[cacheKey]?.let { return@withContext Result.success(it) }
            runCatching {
                val genre = category.appleId?.let { "genre=$it/" }.orEmpty()
                val url = "https://itunes.apple.com/$country/rss/toppodcasts/limit=60/${genre}explicit=true/json"
                parseChart(get(url)).also { charts[cacheKey] = it }
            }
        }

    /** The RSS feed behind an Apple id. */
    suspend fun feedUrl(appleId: String): Result<String> = withContext(Dispatchers.IO) {
        feeds[appleId]?.let { return@withContext Result.success(it) }
        runCatching {
            val body = get("https://itunes.apple.com/lookup?id=$appleId&entity=podcast")
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
            val feed = results.firstNotNullOfOrNull { (it as? JsonObject)?.get("feedUrl")?.jsonPrimitive?.contentOrNull }
                ?: error("This podcast has no public feed")
            feeds[appleId] = feed
            feed
        }
    }

    fun parseChart(body: String): List<DiscoverItem> {
        val entries = json.parseToJsonElement(body).jsonObject["feed"]?.jsonObject?.get("entry") ?: return emptyList()
        // A chart of one comes back as an object rather than a list.
        val list = (entries as? JsonArray)?.toList() ?: listOf(entries)
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonObject?.get("attributes")?.jsonObject?.get("im:id")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            DiscoverItem(
                appleId = id,
                title = o.label("im:name") ?: return@mapNotNull null,
                author = o.label("im:artist"),
                imageUrl = (o["im:image"] as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.label() }
                    ?.let(::biggerArtwork),
            )
        }
    }

    /** Chart artwork comes at 170px; Apple serves the same image at any size asked for. */
    private fun biggerArtwork(url: String): String =
        url.replace(Regex("/\\d+x\\d+(bb)?\\.(png|jpg)$"), "/600x600bb.jpg")

    private fun JsonObject.label(key: String): String? = (this[key] as? JsonObject)?.label()

    private fun JsonObject.label(): String? = this["label"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun get(url: String): String =
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", PaxMedia.USER_AGENT).build())
            .execute().use { response ->
                if (!response.isSuccessful) error("Directory answered ${response.code}")
                response.body.string()
            }

    @Suppress("unused")
    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
}

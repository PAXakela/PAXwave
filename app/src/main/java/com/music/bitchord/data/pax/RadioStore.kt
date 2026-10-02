package com.music.bitchord.data.pax

import android.content.Context
import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.util.Locale

/**
 * Internet radio: the listener's saved stations, and lookups against the
 * community Radio Browser directory (radio-browser.info), which needs no key.
 */
object RadioStore {

    private const val TAG = "PaxRadio"
    private const val USER_AGENT = PaxMedia.USER_AGENT

    /** Radio Browser asks clients to spread load over its mirrors. */
    private val MIRRORS = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val stationList = ListSerializer(RadioStation.serializer())
    private var file: File? = null

    private val _favourites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favourites: StateFlow<List<RadioStation>> = _favourites.asStateFlow()

    fun init(context: Context) {
        val root = File(context.filesDir, "paxwave").apply { mkdirs() }
        file = File(root, "radio_favourites.json")
        runCatching {
            file?.takeIf { it.exists() }?.readText()?.let { _favourites.value = json.decodeFromString(stationList, it) }
        }.onFailure { Log.w(TAG, "could not read favourites: ${it.message}") }
    }

    /** Screenshot tests only. */
    fun seedForTest(favourites: List<RadioStation>) {
        _favourites.value = favourites
    }

    fun isFavourite(id: String): Boolean = _favourites.value.any { it.id == id }

    fun station(mediaId: String): RadioStation? =
        _favourites.value.firstOrNull { PaxMedia.radioId(it) == mediaId }

    fun toggleFavourite(station: RadioStation) {
        _favourites.update { list ->
            if (list.any { it.id == station.id }) list.filterNot { it.id == station.id } else list + station
        }
        save()
    }

    fun moveFavourite(from: Int, to: Int) {
        _favourites.update { list ->
            if (from !in list.indices || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
        save()
    }

    /** Most-listened stations for the phone's country, which is what an empty search shows. */
    suspend fun popular(countryCode: String = Locale.getDefault().country): Result<List<RadioStation>> =
        query("/json/stations/search") {
            if (countryCode.isNotBlank()) addQueryParameter("countrycode", countryCode)
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
            addQueryParameter("hidebroken", "true")
            addQueryParameter("limit", "60")
        }

    /** Popular stations for a genre tag, optionally within one country. */
    suspend fun byTag(tag: String, countryCode: String? = null): Result<List<RadioStation>> =
        query("/json/stations/search") {
            addQueryParameter("tag", tag)
            if (!countryCode.isNullOrBlank()) addQueryParameter("countrycode", countryCode)
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
            addQueryParameter("hidebroken", "true")
            addQueryParameter("limit", "60")
        }

    suspend fun search(term: String): Result<List<RadioStation>> =
        query("/json/stations/search") {
            addQueryParameter("name", term.trim())
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
            addQueryParameter("hidebroken", "true")
            addQueryParameter("limit", "80")
        }

    /**
     * A station from a URL the listener typed. Playlist files (.pls / .m3u) are
     * opened and their first stream taken, since the player wants the stream.
     */
    suspend fun custom(url: String, name: String?): Result<RadioStation> = withContext(Dispatchers.IO) {
        runCatching {
            val normalised = PodcastStore.normaliseUrl(url)
            val stream = resolvePlaylist(normalised) ?: normalised
            val host = runCatching { stream.toHttpUrl().host }.getOrNull()
            RadioStation(
                id = "url-" + PaxMedia.hash(stream).take(16),
                name = name?.trim()?.takeIf { it.isNotBlank() } ?: host ?: stream,
                streamUrl = stream,
            )
        }
    }

    private fun resolvePlaylist(url: String): String? {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        if (!path.endsWith(".pls") && !path.endsWith(".m3u")) return null
        val body = Http.client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
            .execute().use { if (it.isSuccessful) it.body.string() else null } ?: return null
        return body.lineSequence()
            .map { it.trim() }
            .map { line -> if (line.startsWith("File", ignoreCase = true) && '=' in line) line.substringAfter('=') else line }
            .firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private suspend fun query(
        path: String,
        params: okhttp3.HttpUrl.Builder.() -> Unit,
    ): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (mirror in MIRRORS) {
            val result = runCatching {
                val url = "$mirror$path".toHttpUrl().newBuilder().apply(params).build()
                val body = Http.client.newCall(
                    Request.Builder().url(url).header("User-Agent", USER_AGENT).build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) error("Radio directory answered ${response.code}")
                    response.body.string()
                }
                parseStations(body)
            }
            if (result.isSuccess) return@withContext result
            last = result.exceptionOrNull()
            Log.w(TAG, "$mirror failed: ${last?.message}")
        }
        Result.failure(last ?: IllegalStateException("Radio directory unreachable"))
    }

    fun parseStations(body: String): List<RadioStation> =
        json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val stream = s("url_resolved") ?: s("url") ?: return@mapNotNull null
            RadioStation(
                id = s("stationuuid") ?: ("url-" + PaxMedia.hash(stream).take(16)),
                name = s("name") ?: return@mapNotNull null,
                streamUrl = stream,
                faviconUrl = s("favicon"),
                country = s("countrycode"),
                tags = s("tags"),
                codec = s("codec"),
                bitrate = o["bitrate"]?.jsonPrimitive?.intOrNull,
                homepage = s("homepage"),
            )
        }.distinctBy { it.id }

    private fun save() {
        val target = file ?: return
        val snapshot = _favourites.value
        runCatching { target.writeText(json.encodeToString(stationList, snapshot)) }
            .onFailure { Log.w(TAG, "could not save favourites: ${it.message}") }
    }
}

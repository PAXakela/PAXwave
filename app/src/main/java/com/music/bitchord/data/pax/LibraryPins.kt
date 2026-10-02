package com.music.bitchord.data.pax

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What the listener pinned to the top of their library: any mix of podcasts,
 * stations, playlists, albums and artists, in the order they arranged them.
 */
object LibraryPins {

    @Serializable
    enum class Kind { PODCAST, RADIO, PLAYLIST, ALBUM, ARTIST }

    @Serializable
    data class Pin(
        val kind: Kind,
        /** Feed key for a podcast, station id for radio, browse id for everything else. */
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val imageUrl: String? = null,
        /** A station carries itself, so it plays without a directory lookup. */
        val station: RadioStation? = null,
    ) {
        val key: String get() = "${kind.name}:$id"
    }

    private const val TAG = "PaxPins"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(Pin.serializer())
    private var file: File? = null

    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    fun init(context: Context) {
        val root = File(context.filesDir, "paxwave").apply { mkdirs() }
        file = File(root, "library_pins.json")
        runCatching {
            file?.takeIf { it.exists() }?.readText()?.let { _pins.value = json.decodeFromString(serializer, it) }
        }.onFailure { Log.w(TAG, "could not read pins: ${it.message}") }
    }

    /** Screenshot tests only. */
    fun seedForTest(pins: List<Pin>) {
        _pins.value = pins
    }

    fun isPinned(kind: Kind, id: String): Boolean = _pins.value.any { it.kind == kind && it.id == id }

    fun toggle(pin: Pin) {
        _pins.update { list ->
            if (list.any { it.key == pin.key }) list.filterNot { it.key == pin.key } else list + pin
        }
        save()
    }

    fun unpin(kind: Kind, id: String) {
        _pins.update { list -> list.filterNot { it.kind == kind && it.id == id } }
        save()
    }

    fun move(from: Int, to: Int) {
        _pins.update { list ->
            if (from !in list.indices || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
        save()
    }

    fun podcastPin(show: Podcast) = Pin(Kind.PODCAST, show.key, show.title, show.author, show.imageUrl)

    fun stationPin(station: RadioStation) =
        Pin(Kind.RADIO, station.id, station.name, station.country, station.faviconUrl, station)

    private fun save() {
        val target = file ?: return
        val snapshot = _pins.value
        runCatching { target.writeText(json.encodeToString(serializer, snapshot)) }
            .onFailure { Log.w(TAG, "could not save pins: ${it.message}") }
    }
}

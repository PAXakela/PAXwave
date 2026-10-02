package com.music.bitchord.data.pax

import android.content.Context
import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.util.Locale

/**
 * The listener's podcasts: subscriptions with their last-read episode lists,
 * and how far into each episode they got.
 *
 * Kept as two small JSON files in the app's private storage. The player
 * service shares this process and writes progress straight into [progress]
 * — see [onPlaybackProgress] — so the pages showing it update live.
 */
object PodcastStore {

    private const val TAG = "PaxPodcasts"
    private const val USER_AGENT = PaxMedia.USER_AGENT

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val podcastList = ListSerializer(Podcast.serializer())
    private val progressMap = MapSerializer(String.serializer(), EpisodeProgress.serializer())

    private var dir: File? = null

    private val _podcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val podcasts: StateFlow<List<Podcast>> = _podcasts.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())
    val progress: StateFlow<Map<String, EpisodeProgress>> = _progress.asStateFlow()

    /** Episode ids waiting to be played, in order — AntennaPod's queue. */
    private val _queue = MutableStateFlow<List<String>>(emptyList())
    val queue: StateFlow<List<String>> = _queue.asStateFlow()

    /** Shows opened from search or Discover without subscribing, by feed key. */
    private val _previews = MutableStateFlow<Map<String, Podcast>>(emptyMap())
    val previews: StateFlow<Map<String, Podcast>> = _previews.asStateFlow()

    /**
     * Podcast AutoPlay, separate from YouTube Music's: on, the player carries
     * on past the queue into the newest episodes not heard yet; off, it plays
     * the queue and stops.
     */
    private val _autoplay = MutableStateFlow(false)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    /** Shows whose new episodes download by themselves, by feed key. */
    private val _autoDownload = MutableStateFlow<Set<String>>(emptySet())
    val autoDownload: StateFlow<Set<String>> = _autoDownload.asStateFlow()

    /** Episodes taken off "Continue listening" by hand. */
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var lastProgressWrite = 0L

    fun init(context: Context) {
        val root = File(context.filesDir, "paxwave").apply { mkdirs() }
        dir = root
        prefs = context.getSharedPreferences("paxwave_podcasts", Context.MODE_PRIVATE).also { p ->
            _autoplay.value = p.getBoolean("autoplay", false)
            _autoDownload.value = p.getStringSet("auto_download", emptySet()).orEmpty().toSet()
            _dismissed.value = p.getStringSet("dismissed", emptySet()).orEmpty().toSet()
        }
        runCatching {
            File(root, "podcasts.json").takeIf { it.exists() }?.readText()
                ?.let { text ->
                    _podcasts.value = json.decodeFromString(podcastList, text)
                        .map { show -> show.copy(episodes = show.episodes.distinctBy { it.id }) }
                }
        }.onFailure { Log.w(TAG, "could not read podcasts: ${it.message}") }
        runCatching {
            File(root, "podcast_progress.json").takeIf { it.exists() }?.readText()
                ?.let { _progress.value = json.decodeFromString(progressMap, it) }
        }.onFailure { Log.w(TAG, "could not read progress: ${it.message}") }
        runCatching {
            File(root, "podcast_queue.json").takeIf { it.exists() }?.readText()
                ?.let { _queue.value = json.decodeFromString(ListSerializer(String.serializer()), it) }
        }.onFailure { Log.w(TAG, "could not read queue: ${it.message}") }
    }

    /** Fills the store without disk or network — screenshot tests only. */
    fun seedForTest(podcasts: List<Podcast>, progress: Map<String, EpisodeProgress> = emptyMap(), queue: List<String> = emptyList()) {
        _podcasts.value = podcasts
        _progress.value = progress
        _queue.value = queue
    }

    fun setAutoplay(on: Boolean) {
        _autoplay.value = on
        prefs?.edit()?.putBoolean("autoplay", on)?.apply()
    }

    fun setAutoDownload(key: String, on: Boolean) {
        _autoDownload.update { if (on) it + key else it - key }
        prefs?.edit()?.putStringSet("auto_download", _autoDownload.value)?.apply()
    }

    /** Takes an episode off "Continue listening" until it is played again. */
    fun dismissFromContinue(episodeId: String) {
        _dismissed.update { it + episodeId }
        prefs?.edit()?.putStringSet("dismissed", _dismissed.value)?.apply()
    }

    /** Back on "Continue listening" — called when the episode is started again by hand. */
    fun undismiss(episodeId: String) {
        if (episodeId !in _dismissed.value) return
        _dismissed.update { it - episodeId }
        prefs?.edit()?.putStringSet("dismissed", _dismissed.value)?.apply()
    }

    /**
     * What podcast AutoPlay plays next: the newest episodes across every
     * subscription that have not been finished, skipping [exclude].
     */
    fun nextAutoplayEpisodes(exclude: Set<String>, count: Int): List<Pair<Podcast, PodcastEpisode>> {
        val progress = _progress.value
        return _podcasts.value
            .flatMap { show -> show.episodes.take(25).map { show to it } }
            .filter { (_, e) -> e.id !in exclude && progress[e.id]?.played != true }
            .sortedByDescending { it.second.publishedAt ?: 0L }
            .take(count)
    }

    fun isSubscribed(key: String): Boolean = _podcasts.value.any { it.key == key }

    /** A subscribed show, or one opened for a look. */
    fun anyPodcast(key: String): Podcast? = podcast(key) ?: _previews.value[key]

    /** The episode behind [episodeId], with its show, whether subscribed or only previewed. */
    fun findEpisode(episodeId: String): Pair<Podcast, PodcastEpisode>? {
        if (!PaxMedia.isPodcastId(episodeId)) return null
        val feedKey = episodeId.removePrefix(PaxMedia.PODCAST_PREFIX).substringBefore(':')
        val show = anyPodcast(feedKey) ?: return null
        return show.episodes.firstOrNull { it.id == episodeId }?.let { show to it }
    }

    /** Reads a feed to show it without subscribing. Subscribed shows come straight from the list. */
    suspend fun preview(feedUrl: String): Result<Podcast> = withContext(Dispatchers.IO) {
        val url = normaliseUrl(feedUrl)
        podcast(PaxMedia.feedKey(url))?.let { return@withContext Result.success(it) }
        runCatching {
            fetch(url).also { show -> _previews.update { it + (show.key to show) } }
        }
    }

    // ── Queue ──────────────────────────────────────────────────────────────

    fun isQueued(episodeId: String): Boolean = episodeId in _queue.value

    fun enqueue(episodeId: String, next: Boolean = false) {
        _queue.update { q ->
            val rest = q - episodeId
            if (next) listOf(episodeId) + rest else rest + episodeId
        }
        saveQueue()
    }

    fun dequeue(episodeId: String) {
        if (!isQueued(episodeId)) return
        _queue.update { it - episodeId }
        saveQueue()
    }

    fun moveInQueue(from: Int, to: Int) {
        _queue.update { q ->
            if (from !in q.indices || to !in q.indices) return@update q
            q.toMutableList().apply { add(to, removeAt(from)) }
        }
        saveQueue()
    }

    fun clearQueue() {
        _queue.value = emptyList()
        saveQueue()
    }

    /** The queue as playable episodes, skipping any whose show has since been dropped. */
    fun queuedEpisodes(): List<Pair<Podcast, PodcastEpisode>> = _queue.value.mapNotNull(::findEpisode)

    private fun saveQueue() {
        val root = dir ?: return
        val snapshot = _queue.value
        runCatching {
            writeAtomically(File(root, "podcast_queue.json"), json.encodeToString(ListSerializer(String.serializer()), snapshot))
        }.onFailure { Log.w(TAG, "could not save queue: ${it.message}") }
    }

    fun podcast(key: String): Podcast? = _podcasts.value.firstOrNull { it.key == key }

    /** Finds the episode behind a player media id, with the show it belongs to. */
    fun episode(mediaId: String): Pair<Podcast, PodcastEpisode>? {
        if (!PaxMedia.isPodcastId(mediaId)) return null
        val feedKey = mediaId.removePrefix(PaxMedia.PODCAST_PREFIX).substringBefore(':')
        val show = podcast(feedKey) ?: return null
        return show.episodes.firstOrNull { it.id == mediaId }?.let { show to it }
    }

    /** Reads [feedUrl] and subscribes to it, or refreshes it if already subscribed. */
    suspend fun subscribe(feedUrl: String): Result<Podcast> = withContext(Dispatchers.IO) {
        runCatching {
            val url = normaliseUrl(feedUrl)
            val fetched = fetch(url)
            val existing = _podcasts.value.firstOrNull { it.key == fetched.key }
            val now = System.currentTimeMillis()
            val stored = fetched.copy(
                subscribedAt = existing?.subscribedAt ?: now,
                refreshedAt = now,
            )
            _podcasts.update { list -> listOf(stored) + list.filterNot { it.key == stored.key } }
            _previews.update { it - stored.key }
            savePodcasts()
            PodcastDownloads.autoDownload(listOf(stored), _progress.value, _autoDownload.value)
            stored
        }
    }

    fun unsubscribe(key: String) {
        _podcasts.update { list -> list.filterNot { it.key == key } }
        _progress.update { map -> map.filterKeys { !it.startsWith("${PaxMedia.PODCAST_PREFIX}$key:") } }
        _queue.update { q -> q.filterNot { it.startsWith("${PaxMedia.PODCAST_PREFIX}$key:") } }
        PodcastDownloads.deleteShow(key)
        setAutoDownload(key, false)
        savePodcasts()
        saveProgress()
        saveQueue()
    }

    /** Re-reads every subscribed feed, four at a time. Failures keep the old episode list. */
    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        if (_refreshing.value) return@withContext
        _refreshing.value = true
        try {
            val gate = Semaphore(4)
            val current = _podcasts.value
            val refreshed = coroutineScope {
                current.map { show ->
                    async {
                        gate.withPermit {
                            runCatching { fetch(show.feedUrl) }
                                .onFailure { Log.w(TAG, "refresh of ${show.feedUrl} failed: ${it.message}") }
                                .getOrNull()
                                ?.copy(subscribedAt = show.subscribedAt, refreshedAt = System.currentTimeMillis())
                                ?: show
                        }
                    }
                }.awaitAll()
            }
            val byKey = refreshed.associateBy { it.key }
            // Merged by key so a subscription added while this ran is kept.
            _podcasts.update { list -> list.map { byKey[it.key] ?: it } }
            savePodcasts()
            PodcastDownloads.autoDownload(_podcasts.value, _progress.value, _autoDownload.value)
        } finally {
            _refreshing.value = false
        }
    }

    suspend fun refresh(key: String): Result<Podcast> {
        val show = podcast(key) ?: return Result.failure(IllegalStateException("Not subscribed"))
        return subscribe(show.feedUrl)
    }

    /** Writes progress now — called when playback pauses, so a killed app loses nothing. */
    fun flush() = saveProgress()

    fun setPlayed(episodeId: String, played: Boolean) {
        if (played) dequeue(episodeId)
        _progress.update { map ->
            val old = map[episodeId] ?: EpisodeProgress()
            map + (episodeId to old.copy(
                played = played,
                positionMs = if (played) old.positionMs else 0L,
                updatedAt = System.currentTimeMillis(),
            ))
        }
        saveProgress()
    }

    /**
     * Called by the player once a second while an episode is audible. Kept
     * cheap: the state updates every call, the file at most every ten seconds.
     */
    fun onPlaybackProgress(mediaId: String, positionMs: Long, durationMs: Long) {
        if (!PaxMedia.isPodcastId(mediaId) || positionMs < 0) return
        val finished = durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS
        _progress.update { map ->
            val old = map[mediaId]
            map + (mediaId to EpisodeProgress(
                positionMs = positionMs,
                durationMs = durationMs.coerceAtLeast(old?.durationMs ?: 0L),
                played = finished || (old?.played == true && positionMs < 15_000L),
                updatedAt = System.currentTimeMillis(),
            ))
        }
        if (finished && isQueued(mediaId)) dequeue(mediaId)

        val now = System.currentTimeMillis()
        if (finished || now - lastProgressWrite > 10_000L) {
            lastProgressWrite = now
            saveProgress()
        }
    }

    /** Where to start [episodeId]: the saved spot, or the top once it was finished. */
    fun resumePositionMs(episodeId: String): Long {
        val p = _progress.value[episodeId] ?: return 0L
        if (p.played) return 0L
        return (p.positionMs - 3_000L).coerceAtLeast(0L)
    }

    /** Searches Apple's public podcast directory, which lists each show's RSS feed. */
    suspend fun search(term: String): Result<List<PodcastSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("media", "podcast")
                .addQueryParameter("entity", "podcast")
                .addQueryParameter("limit", "30")
                .addQueryParameter("country", Locale.getDefault().country.ifBlank { "US" })
                .addQueryParameter("term", term.trim())
                .build()
            val body = Http.client.newCall(
                Request.Builder().url(url).header("User-Agent", USER_AGENT).build(),
            ).execute().use { response ->
                if (!response.isSuccessful) error("Search failed (${response.code})")
                response.body.string()
            }
            json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val feed = o["feedUrl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                PodcastSearchResult(
                    title = o["collectionName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    author = o["artistName"]?.jsonPrimitive?.contentOrNull,
                    feedUrl = feed,
                    imageUrl = (o["artworkUrl600"] ?: o["artworkUrl100"])?.jsonPrimitive?.contentOrNull,
                )
            }.distinctBy { it.feedUrl }
        }
    }

    private fun fetch(feedUrl: String): Podcast {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PodcastFeedParser.FeedException("The feed answered ${response.code}")
            }
            val body = response.body
            return body.byteStream().use { PodcastFeedParser.parse(feedUrl, it) }
        }
    }

    fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("feed://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.startsWith("podcast://", ignoreCase = true) -> "https://" + trimmed.substring(10)
            trimmed.startsWith("itpc://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        return t.contains("://") || (t.contains('.') && !t.contains(' ') && t.contains('/'))
    }

    private fun savePodcasts() {
        val root = dir ?: return
        val snapshot = _podcasts.value
        runCatching { writeAtomically(File(root, "podcasts.json"), json.encodeToString(podcastList, snapshot)) }
            .onFailure { Log.w(TAG, "could not save podcasts: ${it.message}") }
    }

    private fun saveProgress() {
        val root = dir ?: return
        val snapshot = _progress.value
        runCatching { writeAtomically(File(root, "podcast_progress.json"), json.encodeToString(progressMap, snapshot)) }
            .onFailure { Log.w(TAG, "could not save progress: ${it.message}") }
    }

    @Synchronized
    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    /** Within this much of the end an episode counts as finished. */
    private const val FINISHED_MARGIN_MS = 30_000L
}

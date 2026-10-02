package com.music.bitchord.data.pax

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * Downloaded podcast episodes.
 *
 * The system DownloadManager does the fetching — it survives the app being
 * closed, resumes after a dropped connection and shows its own progress
 * notification — into this app's own folder on shared storage, which needs
 * no permission and is removed with the app. What finished where is kept in
 * a small JSON file beside the subscriptions.
 */
object PodcastDownloads {

    @Serializable
    enum class Status { QUEUED, RUNNING, DONE, FAILED }

    @Serializable
    data class Record(
        val episodeId: String,
        val feedKey: String,
        val downloadId: Long,
        val path: String,
        val status: Status,
        val bytes: Long = 0L,
        val total: Long = 0L,
        val finishedAt: Long = 0L,
    ) {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private const val TAG = "PaxDownloads"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Record.serializer())

    private var app: Context? = null
    private var file: File? = null
    private var tried: File? = null

    private val _records = MutableStateFlow<Map<String, Record>>(emptyMap())
    val records: StateFlow<Map<String, Record>> = _records.asStateFlow()

    /** Episodes auto-download has already fetched once, so a deleted one stays deleted. */
    private var attempted: MutableSet<String> = mutableSetOf()

    fun init(context: Context) {
        val appContext = context.applicationContext
        app = appContext
        val root = File(appContext.filesDir, "paxwave").apply { mkdirs() }
        file = File(root, "podcast_downloads.json")
        tried = File(root, "podcast_auto_tried.txt")
        runCatching {
            file?.takeIf { it.exists() }?.readText()?.let { _records.value = json.decodeFromString(serializer, it) }
        }.onFailure { Log.w(TAG, "could not read downloads: ${it.message}") }
        runCatching {
            tried?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.let { attempted = it.toMutableSet() }
        }
        // Finished while the app was away, or the file was cleared from storage.
        refresh()
        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refresh()
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun record(episodeId: String): Record? = _records.value[episodeId]

    /** A playable `file://` for [episodeId], once its download has finished and is still there. */
    fun localUri(episodeId: String): String? {
        val r = _records.value[episodeId] ?: return null
        if (r.status != Status.DONE) return null
        val f = File(r.path)
        return if (f.exists()) Uri.fromFile(f).toString() else null
    }

    fun isDownloaded(episodeId: String): Boolean = localUri(episodeId) != null

    fun download(show: Podcast, episode: PodcastEpisode) {
        val context = app ?: return
        val existing = _records.value[episode.id]
        if (existing != null && existing.status != Status.FAILED) return
        val dm = context.getSystemService(DownloadManager::class.java) ?: return
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), show.key).apply { mkdirs() }
        val target = File(dir, PaxMedia.hash(episode.id).take(16) + "." + extensionOf(episode.audioUrl))
        target.delete()
        val request = runCatching {
            DownloadManager.Request(Uri.parse(episode.audioUrl))
                .setTitle(episode.title)
                .setDescription(show.title)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(target))
                .addRequestHeader("User-Agent", PaxMedia.USER_AGENT)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
        }.getOrElse {
            Log.w(TAG, "cannot download ${episode.audioUrl}: ${it.message}")
            return
        }
        val id = runCatching { dm.enqueue(request) }.getOrElse {
            Log.w(TAG, "enqueue failed: ${it.message}")
            return
        }
        _records.update {
            it + (episode.id to Record(episode.id, show.key, id, target.absolutePath, Status.QUEUED))
        }
        save()
    }

    fun delete(episodeId: String) {
        val context = app ?: return
        val r = _records.value[episodeId] ?: return
        runCatching { context.getSystemService(DownloadManager::class.java)?.remove(r.downloadId) }
        runCatching { File(r.path).delete() }
        _records.update { it - episodeId }
        save()
    }

    /** Removes every download of a show, for when it is unsubscribed. */
    fun deleteShow(feedKey: String) {
        _records.value.values.filter { it.feedKey == feedKey }.forEach { delete(it.episodeId) }
    }

    /** Reads the state of every unfinished download back from the system. */
    fun refresh() {
        val context = app ?: return
        val dm = context.getSystemService(DownloadManager::class.java) ?: return
        val pending = _records.value.values.filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
        var changed = false
        val updated = _records.value.toMutableMap()
        for (r in pending) {
            val cursor = runCatching { dm.query(DownloadManager.Query().setFilterById(r.downloadId)) }.getOrNull()
            if (cursor == null) continue
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    updated[r.episodeId] = r.copy(status = Status.FAILED)
                    changed = true
                    return@use
                }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val next = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> r.copy(status = Status.DONE, bytes = bytes, total = total, finishedAt = System.currentTimeMillis())
                    DownloadManager.STATUS_FAILED -> r.copy(status = Status.FAILED)
                    DownloadManager.STATUS_RUNNING -> r.copy(status = Status.RUNNING, bytes = bytes, total = total)
                    else -> r.copy(bytes = bytes, total = total)
                }
                if (next != r) {
                    updated[r.episodeId] = next
                    changed = true
                }
            }
        }
        // A finished file someone deleted by hand is no longer a download.
        for (r in _records.value.values.filter { it.status == Status.DONE }) {
            if (!File(r.path).exists()) {
                updated.remove(r.episodeId)
                changed = true
            }
        }
        if (changed) {
            _records.value = updated
            save()
        }
    }

    /**
     * For shows with automatic downloads on: fetch the newest few episodes that
     * are recent, unfinished and never fetched before.
     */
    fun autoDownload(podcasts: List<Podcast>, progress: Map<String, EpisodeProgress>, enabled: Set<String>) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        var added = false
        podcasts.filter { it.key in enabled }.forEach { show ->
            show.episodes.take(3)
                .filter { (it.publishedAt ?: 0L) >= weekAgo || show.subscribedAt >= weekAgo }
                .filter { progress[it.id]?.played != true && it.id !in attempted && _records.value[it.id] == null }
                .forEach { episode ->
                    download(show, episode)
                    attempted += episode.id
                    added = true
                }
        }
        if (added) runCatching { tried?.writeText(attempted.joinToString("\n")) }
    }

    fun totalBytes(): Long = _records.value.values.filter { it.status == Status.DONE }.sumOf { File(it.path).length() }

    private fun extensionOf(url: String): String {
        val path = Uri.parse(url).lastPathSegment.orEmpty().lowercase(Locale.ROOT)
        val ext = path.substringAfterLast('.', "")
        return ext.takeIf { it in setOf("mp3", "m4a", "aac", "ogg", "opus", "mp4", "wav", "flac") } ?: "mp3"
    }

    private fun save() {
        val target = file ?: return
        val snapshot = _records.value
        runCatching { target.writeText(json.encodeToString(serializer, snapshot)) }
            .onFailure { Log.w(TAG, "could not save downloads: ${it.message}") }
    }
}

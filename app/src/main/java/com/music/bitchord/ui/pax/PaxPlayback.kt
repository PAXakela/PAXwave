package com.music.bitchord.ui.pax

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.music.bitchord.data.pax.PaxMedia
import com.music.bitchord.data.pax.PaxMedia.episodeSong
import com.music.bitchord.data.pax.PaxMedia.song
import com.music.bitchord.data.pax.Podcast
import com.music.bitchord.data.pax.PodcastEpisode
import com.music.bitchord.data.pax.PodcastStore
import com.music.bitchord.data.pax.RadioStation
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the player is doing, as far as the podcast and radio pages care. */
data class PaxNowPlaying(
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    /** A station's "now playing" line, read off the stream's ICY metadata. */
    val liveTitle: String? = null,
    val title: String? = null,
    val artworkUrl: String? = null,
    val speed: Float = 1f,
) {
    fun isCurrent(id: String) = mediaId == id
    fun isPlaying(id: String) = mediaId == id && isPlaying
}

@Composable
fun rememberPaxNowPlaying(controller: MediaController?): PaxNowPlaying {
    var state by remember { mutableStateOf(PaxNowPlaying()) }
    DisposableEffect(controller) {
        val c = controller
        fun read() {
            if (c == null) {
                state = PaxNowPlaying()
                return
            }
            val item = c.currentMediaItem
            val staticTitle = item?.mediaMetadata?.title?.toString()
            val dynamicTitle = c.mediaMetadata.title?.toString()
            state = PaxNowPlaying(
                mediaId = item?.mediaId,
                isPlaying = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
                liveTitle = dynamicTitle?.takeIf { it.isNotBlank() && it != staticTitle },
                title = staticTitle,
                artworkUrl = item?.mediaMetadata?.artworkUri?.toString(),
                speed = c.playbackParameters.speed,
            )
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = read()
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = read()
        }
        c?.addListener(listener)
        read()
        onDispose { c?.removeListener(listener) }
    }
    return state
}

/**
 * Plays podcast episodes and stations on their own — no AutoPlay, no mixing
 * into a music queue — the way AntennaPod does: an episode is followed by
 * whatever is in the listener's episode queue.
 */
object PaxPlayer {

    suspend fun play(controller: MediaController, songs: List<Song>, index: Int, startPositionMs: Long = 0L) {
        if (songs.isEmpty()) return
        PaxMedia.clearError()
        val items = withContext(Dispatchers.Default) { songs.map { it.toMediaItem() } }
        controller.setMediaItems(items, index.coerceIn(items.indices), startPositionMs.coerceAtLeast(0L))
        controller.prepare()
        controller.play()
    }

    /** Plays [episode], then the rest of the queue after it (or all of it, if it is not queued). */
    suspend fun playEpisode(controller: MediaController, show: Podcast, episode: PodcastEpisode) {
        if (controller.currentMediaItem?.mediaId == episode.id) {
            if (controller.isPlaying) controller.pause() else controller.play()
            return
        }
        PodcastStore.undismiss(episode.id)
        val queued = PodcastStore.queuedEpisodes()
        val at = queued.indexOfFirst { it.second.id == episode.id }
        val following = if (at >= 0) queued.drop(at + 1) else queued.filterNot { it.second.id == episode.id }
        val songs = listOf(show.episodeSong(episode)) + following.map { (s, e) -> s.episodeSong(e) }
        play(controller, songs, 0, PodcastStore.resumePositionMs(episode.id))
    }

    /** Starts the queue from its top. */
    suspend fun playQueue(controller: MediaController) {
        val queued = PodcastStore.queuedEpisodes()
        val (show, first) = queued.firstOrNull() ?: return
        playEpisode(controller, show, first)
    }

    suspend fun playStation(controller: MediaController, station: RadioStation, liveLabel: String) {
        if (controller.currentMediaItem?.mediaId == PaxMedia.radioId(station)) {
            if (controller.isPlaying) controller.pause() else controller.play()
            return
        }
        play(controller, listOf(station.song(liveLabel)), 0)
    }

    /**
     * Adds an episode to the queue, and — when an episode is playing right now —
     * to the end of what the player will play next, so the queue and the
     * player agree without restarting anything.
     */
    suspend fun enqueue(controller: MediaController?, show: Podcast, episode: PodcastEpisode, next: Boolean = false) {
        PodcastStore.enqueue(episode.id, next)
        val c = controller ?: return
        if (!PaxMedia.isPodcastId(c.currentMediaItem?.mediaId)) return
        if ((0 until c.mediaItemCount).any { c.getMediaItemAt(it).mediaId == episode.id }) return
        val item = withContext(Dispatchers.Default) { show.episodeSong(episode).toMediaItem() }
        if (next) c.addMediaItem(c.currentMediaItemIndex + 1, item) else c.addMediaItem(item)
    }

    fun dequeue(controller: MediaController?, episodeId: String) {
        PodcastStore.dequeue(episodeId)
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull {
            it != c.currentMediaItemIndex && c.getMediaItemAt(it).mediaId == episodeId
        } ?: return
        c.removeMediaItem(index)
    }

    fun seekBy(controller: MediaController?, deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    val SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)

    /** Through the app setting, which the player service applies and keeps across tracks. */
    fun cycleSpeed() {
        val now = AppSettings.playbackSpeed.value
        val next = SPEEDS.firstOrNull { it > now + 0.01f } ?: SPEEDS.first()
        AppSettings.setPlaybackSpeed(next)
    }
}

package com.music.bitchord.data.pax

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.music.bitchord.data.model.PlaybackSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.music.bitchord.data.model.Song
import java.security.MessageDigest

/**
 * PAXwave's own media: podcast episodes and internet radio stations.
 *
 * Both play straight off an HTTP(S) URL, which is the path WebDAV tracks
 * already take through the player: the URL rides in [Song.localUri] and the
 * resolver lets anything without a YouTube `v` parameter through untouched.
 * What sets these apart is their id prefix, which the player checks wherever
 * a YouTube-only feature (AutoPlay, scrobbling, the disk cache, read-ahead,
 * listening stats) would otherwise reach for a video id that does not exist.
 */
object PaxMedia {

    /** Sent with feed, directory and stream requests; some podcast CDNs refuse bare library agents. */
    const val USER_AGENT = "PAXwave/1.0 (Android; like AntennaPod)"

    const val PODCAST_PREFIX = "podcast:"
    const val RADIO_PREFIX = "radio:"

    /** Library pages. */
    const val PAGE_PREFIX = "pax:"
    const val PODCASTS_PAGE = "pax:podcasts"
    const val RADIO_PAGE = "pax:radio"
    private const val SHOW_PAGE_PREFIX = "pax:podcast:"

    /** The last podcast or radio stream that could not be played, and why; the pages show it. */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun reportError(title: String?, error: PlaybackException) {
        var cause: Throwable? = error
        var code: Int? = null
        while (cause != null && code == null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) code = cause.responseCode
            cause = cause.cause
        }
        val why = when {
            code != null -> "server answered $code"
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "no connection"
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "unsupported audio format"
            else -> error.errorCodeName
        }
        _playbackError.value = listOfNotNull(title?.takeIf { it.isNotBlank() }, why).joinToString(": ")
    }

    fun clearError() {
        _playbackError.value = null
    }

    fun isPaxId(id: String?): Boolean =
        id != null && (id.startsWith(PODCAST_PREFIX) || id.startsWith(RADIO_PREFIX))

    fun isPodcastId(id: String?): Boolean = id?.startsWith(PODCAST_PREFIX) == true

    fun isRadioId(id: String?): Boolean = id?.startsWith(RADIO_PREFIX) == true

    fun isPaxPage(browseId: String?): Boolean = browseId?.startsWith(PAGE_PREFIX) == true

    fun showPage(feedKey: String): String = "$SHOW_PAGE_PREFIX$feedKey"

    fun feedKeyOfPage(browseId: String?): String? =
        browseId?.takeIf { it.startsWith(SHOW_PAGE_PREFIX) }?.removePrefix(SHOW_PAGE_PREFIX)

    /** Short, stable key for a feed URL; it names the show's page and its episodes' ids. */
    fun feedKey(feedUrl: String): String = hash(feedUrl.trim()).take(12)

    fun episodeId(feedKey: String, episodeGuid: String): String =
        "$PODCAST_PREFIX$feedKey:${hash(episodeGuid).take(16)}"

    fun radioId(station: RadioStation): String = "$RADIO_PREFIX${station.id}"

    fun Podcast.episodeSong(episode: PodcastEpisode): Song = Song(
        videoId = episode.id,
        title = episode.title,
        artist = title,
        thumbnailUrl = episode.imageUrl ?: imageUrl,
        durationText = episode.durationSeconds?.let(::formatDuration),
        albumName = title,
        // A finished download plays from the phone; otherwise the feed's file.
        localUri = PodcastDownloads.localUri(episode.id) ?: episode.audioUrl,
        playbackSource = title,
        playbackSourceType = PlaybackSourceType.BROWSE,
        playbackSourceId = showPage(key),
    )

    fun RadioStation.song(liveLabel: String): Song = Song(
        videoId = radioId(this),
        title = name,
        artist = listOfNotNull(liveLabel, country?.takeIf { it.isNotBlank() }).joinToString(" · "),
        thumbnailUrl = faviconUrl?.takeIf { it.startsWith("http") },
        localUri = streamUrl,
        playbackSource = liveLabel,
        playbackSourceType = PlaybackSourceType.BROWSE,
        playbackSourceId = RADIO_PAGE,
    )

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

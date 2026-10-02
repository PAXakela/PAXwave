package com.music.bitchord.data.pax

import kotlinx.serialization.Serializable

/** A subscribed podcast and the episodes its feed listed at the last refresh. */
@Serializable
data class Podcast(
    val feedUrl: String,
    val key: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val link: String? = null,
    val subscribedAt: Long = 0L,
    val refreshedAt: Long = 0L,
    /** Newest first. */
    val episodes: List<PodcastEpisode> = emptyList(),
)

@Serializable
data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val publishedAt: Long? = null,
    val durationSeconds: Long? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

/** Where a listener left an episode, and whether they finished it. */
@Serializable
data class EpisodeProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val played: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val inProgress: Boolean get() = !played && positionMs > 15_000L
}

/** A search hit from the podcast directory, not yet subscribed. */
data class PodcastSearchResult(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val imageUrl: String?,
)

@Serializable
data class RadioStation(
    /** Radio Browser's station uuid, or a hash of the URL for one added by hand. */
    val id: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String? = null,
    val country: String? = null,
    val tags: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val homepage: String? = null,
) {
    /** "MP3 · 128 kbps · pop, rock" — whatever the directory knew. */
    val details: String
        get() = listOfNotNull(
            codec?.takeIf { it.isNotBlank() && it != "UNKNOWN" },
            bitrate?.takeIf { it > 0 }?.let { "$it kbps" },
            tags?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.take(3)
                ?.joinToString(", ")?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
}

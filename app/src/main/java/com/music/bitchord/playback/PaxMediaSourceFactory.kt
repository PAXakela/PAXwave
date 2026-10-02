package com.music.bitchord.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.music.bitchord.data.Http
import com.music.bitchord.data.pax.PaxMedia

/**
 * Sends podcast episodes and radio streams down their own plain HTTP path,
 * and everything else down the app's YouTube-shaped one.
 *
 * The YouTube path is several layers deep — a disk cache, a resolver that
 * turns `bitchord://` ids into stream URLs, a ranged-request chunker, an SMB
 * router — and each layer is a DataSource that hides what is underneath it.
 * In particular none of them pass the server's response headers up, and
 * that is what broke radio: ExoPlayer always asks a server for ICY metadata,
 * and strips the metadata blocks out of the audio only once it has read the
 * `icy-metaint` header that says where they are. With the header swallowed,
 * the server interleaved its "now playing" text and the decoder played it as
 * audio — the regular crackle. A podcast episode is a plain file on a plain
 * web server and gains nothing from any of those layers either.
 */
@OptIn(UnstableApi::class)
class PaxMediaSourceFactory(
    context: Context,
    private val music: DefaultMediaSourceFactory,
) : MediaSource.Factory {

    private val pax = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(Http.client).setUserAgent(PaxMedia.USER_AGENT),
        ),
    )

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        music.setDrmSessionManagerProvider(provider)
        pax.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        music.setLoadErrorHandlingPolicy(policy)
        pax.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = music.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (PaxMedia.isPaxId(mediaItem.mediaId)) {
            pax.createMediaSource(mediaItem)
        } else {
            music.createMediaSource(mediaItem)
        }
}

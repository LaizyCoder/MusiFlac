package com.laizycoder.musiflac.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.laizycoder.musiflac.data.MusicTrack
import com.laizycoder.musiflac.online.OnlineTrack
import java.io.File

class MusicPlayer(
    context: Context
) {

    companion object {
        const val REPEAT_OFF =
            Player.REPEAT_MODE_OFF

        const val REPEAT_ALL =
            Player.REPEAT_MODE_ALL

        const val REPEAT_ONE =
            Player.REPEAT_MODE_ONE
    }

    private val applicationContext =
        context.applicationContext

    private var player: MediaController? =
        null

    private var queue: List<MusicTrack> =
        emptyList()

    private var currentIndex: Int =
        -1

    private var pendingTrack: MusicTrack? =
        null

    var onTrackChanged: ((MusicTrack) -> Unit)? =
        null

    private val controllerListener =
        object : MediaController.Listener {

            override fun onDisconnected(
                controller: MediaController
            ) {
                player = null
            }
        }

    private val playerListener =
        object : Player.Listener {

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                val controller =
                    player ?: return

                val index =
                    controller.currentMediaItemIndex

                if (
                    index >= 0 &&
                    index < queue.size
                ) {
                    currentIndex = index

                    val track =
                        queue[index]

                    pendingTrack = null

                    onTrackChanged?.invoke(
                        track
                    )
                }
            }
        }

    init {

        val sessionToken =
            SessionToken(
                applicationContext,
                ComponentName(
                    applicationContext,
                    MusicPlaybackService::class.java
                )
            )

        val controllerFuture =
            MediaController.Builder(
                applicationContext,
                sessionToken
            )
                .setListener(controllerListener)
                .buildAsync()

        controllerFuture.addListener(
            {
                try {

                    val controller =
                        controllerFuture.get()

                    player =
                        controller

                    controller.addListener(
                        playerListener
                    )

                    pendingTrack?.let { track ->

                        pendingTrack = null

                        play(track)
                    }

                } catch (_: Exception) {

                    player = null
                }

            },
            androidx.core.content.ContextCompat.getMainExecutor(
                applicationContext
            )
        )
    }

    fun setQueue(
        tracks: List<MusicTrack>,
        selectedTrack: MusicTrack
    ) {

        queue =
            tracks

        currentIndex =
            tracks.indexOfFirst {
                it.id == selectedTrack.id
            }

        if (currentIndex < 0) {
            currentIndex = 0
        }

        val controller =
            player ?: return

        val mediaItems =
            tracks.map { track ->

                val uri =
                    Uri.fromFile(
                        File(track.filePath)
                    )

                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(
                        track.id.toString()
                    )
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build()
                    )
                    .build()
            }

        if (mediaItems.isEmpty()) {
            return
        }

        controller.setMediaItems(
            mediaItems,
            currentIndex,
            0L
        )

        controller.prepare()
    }

    fun play(
        track: MusicTrack
    ) {

        val index =
            queue.indexOfFirst {
                it.id == track.id
            }

        if (index < 0) {
            return
        }

        currentIndex =
            index

        val controller =
            player

        if (controller == null) {
            pendingTrack = track
            return
        }

        if (
            controller.mediaItemCount !=
            queue.size
        ) {
            setQueue(
                queue,
                track
            )
        }

        controller.seekTo(
            currentIndex,
            0L
        )

        controller.play()
    }

    /**
     * Plays an online track from a locally downloaded/resolved audio file.
     *
     * The online file is intentionally kept separate from the local
     * MusicTrack queue so existing library playback behavior is unchanged.
     */
    fun playOnline(
        track: OnlineTrack,
        audioPath: String
    ) {
        val controller =
            player

        if (controller == null) {
            return
        }

        val file =
            File(audioPath)

        if (!file.exists() || !file.isFile) {
            return
        }

        val mediaMetadata =
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .apply {
                    track.artworkUrl
                        ?.takeIf { it.isNotBlank() }
                        ?.let { artwork ->
                            setArtworkUri(
                                Uri.parse(artwork)
                            )
                        }
                }
                .build()

        val mediaItem =
            MediaItem.Builder()
                .setUri(
                    Uri.fromFile(file)
                )
                .setMediaId(
                    "online:${track.source}:${track.id}"
                )
                .setMediaMetadata(
                    mediaMetadata
                )
                .build()

        queue =
            emptyList()

        currentIndex =
            -1

        pendingTrack =
            null

        controller.setMediaItem(
            mediaItem,
            0L
        )

        controller.prepare()
        controller.play()
    }

    fun next(): MusicTrack? {

        val controller =
            player

        if (
            controller != null &&
            controller.hasNextMediaItem()
        ) {
            controller.seekToNextMediaItem()

            return queue.getOrNull(
                controller.currentMediaItemIndex
            )
        }

        return null
    }

    fun previous(): MusicTrack? {

        val controller =
            player

        if (
            controller != null &&
            controller.hasPreviousMediaItem()
        ) {
            controller.seekToPreviousMediaItem()

            return queue.getOrNull(
                controller.currentMediaItemIndex
            )
        }

        return null
    }

    fun setRepeatMode(
        mode: Int
    ) {
        player?.repeatMode =
            mode
    }

    fun getRepeatMode(): Int {
        return player?.repeatMode
            ?: Player.REPEAT_MODE_OFF
    }

    fun cycleRepeatMode(): Int {

        val controller =
            player
                ?: return Player.REPEAT_MODE_OFF

        val nextMode =
            when (controller.repeatMode) {

                Player.REPEAT_MODE_OFF ->
                    Player.REPEAT_MODE_ALL

                Player.REPEAT_MODE_ALL ->
                    Player.REPEAT_MODE_ONE

                else ->
                    Player.REPEAT_MODE_OFF
            }

        controller.repeatMode =
            nextMode

        return nextMode
    }

    fun setShuffleEnabled(
        enabled: Boolean
    ) {
        player?.shuffleModeEnabled =
            enabled
    }

    fun isShuffleEnabled(): Boolean {
        return player?.shuffleModeEnabled == true
    }

    fun toggleShuffle(): Boolean {

        val controller =
            player
                ?: return false

        controller.shuffleModeEnabled =
            !controller.shuffleModeEnabled

        return controller.shuffleModeEnabled
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }

    fun stop() {
        player?.stop()
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun getDuration(): Long {

        val duration =
            player?.duration ?: 0L

        return if (duration > 0L) {
            duration
        } else {
            0L
        }
    }

    fun seekTo(
        position: Long
    ) {
        player?.seekTo(position)
    }

    fun release() {

        player?.removeListener(
            playerListener
        )

        player?.release()

        player = null
    }
}

package com.laizycoder.musiflac.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player =
            ExoPlayer.Builder(this)
                .build()

        val launchIntent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )
            }

        mediaSession =
            MediaSession.Builder(
                this,
                player
            )
                .apply {
                    pendingIntent?.let {
                        setSessionActivity(it)
                    }
                }
                .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {
        if (
            mediaSession?.player?.playWhenReady == true
        ) {
            return
        }

        stopSelf()
    }

    override fun onDestroy() {

        mediaSession?.run {
            player.release()
            release()
        }

        mediaSession = null

        super.onDestroy()
    }
}
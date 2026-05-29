package com.jayc180.rhythmengine.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.jayc180.rhythmengine.MainActivity

class MediaSessionManager(
    private val context:         Context,
    private val onPlayRequest:   () -> Unit,
    private val onStopRequest:   () -> Unit,
    private val onToggleRequest: () -> Unit,
) {
    companion object {
        private const val TAG             = "ProgMetronome"
        private const val CHANNEL_ID      = "rhythm_media"
        private const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val session = MediaSessionCompat(context, TAG).apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay()  { onPlayRequest() }
            override fun onPause() { onStopRequest() }
            override fun onStop()  { onStopRequest() }
        })
        setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Prog Metronome")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                .build()
        )
        // keep alive
        isActive = true
    }

    val token: MediaSessionCompat.Token get() = session.sessionToken

    init {
        createChannel()
        update(isPlaying = false)
    }

    fun update(isPlaying: Boolean) {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE,
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else           PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1f,
            )

        session.setPlaybackState(stateBuilder.build())
        showNotification(isPlaying)
    }

    private fun showNotification(isPlaying: Boolean) {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseIntent = androidx.media.session.MediaButtonReceiver
            .buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PLAY_PAUSE,
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Prog Metronome")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying) // sticky while playing
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause
                else           android.R.drawable.ic_media_play,
                if (isPlaying) "Stop" else "Play",
                playPauseIntent,
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun release() {
        notificationManager.cancel(NOTIFICATION_ID)
        session.isActive = false
        session.release()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
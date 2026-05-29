package com.jayc180.rhythmengine

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver

class PlaybackService : Service() {

    private val TAG = "RhythmDebug"

    companion object {
        private const val CHANNEL_ID      = "rhythm_media"
        private const val NOTIFICATION_ID = 1001

        // Broadcast actions — received by MainActivity
//        const val ACTION_PLAY  = "com.jayc180.rhythmengine.PLAY"
//        const val ACTION_STOP  = "com.jayc180.rhythmengine.STOP"

        var onPlayCallback:  (() -> Unit)? = null
        var onStopCallback:  (() -> Unit)? = null

        const val EXTRA_PLAYING = "is_playing"

        fun update(context: Context, isPlaying: Boolean) {
            val intent = Intent(context, PlaybackService::class.java)
                .putExtra(EXTRA_PLAYING, isPlaying)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        fun getService() = this@PlaybackService
    }

    private val binder    = LocalBinder()
    private var session:  MediaSessionCompat? = null
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createChannel()

        session = MediaSessionCompat(this, "ProgMetronome").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { onPlayCallback?.invoke() }
                override fun onPause() { onStopCallback?.invoke() }
                override fun onStop()  { onStopCallback?.invoke() }
            })
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Prog Metronome")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .build()
            )
            isActive = true
        }

        showNotification(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        session?.let { MediaButtonReceiver.handleIntent(it, intent) }

        // update playing state from ViewModel wire call
        val newPlaying = intent?.getBooleanExtra(EXTRA_PLAYING, isPlaying) ?: isPlaying
        if (newPlaying != isPlaying || intent?.extras == null) {
            isPlaying = newPlaying
            updateState(isPlaying)
            showNotification(isPlaying)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun updateState(playing: Boolean) {
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE,
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING
                    else         PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build()
        )
    }

    private fun showNotification(playing: Boolean) {
        val token = session?.sessionToken ?: return

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_PLAY_PAUSE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Prog Metronome")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0),
            )
            .addAction(
                if (playing) android.R.drawable.ic_media_pause
                else         android.R.drawable.ic_media_play,
                if (playing) "Stop" else "Play",
                playPauseIntent,
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Playback Controls", NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        session?.isActive = false
        session?.release()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }
}
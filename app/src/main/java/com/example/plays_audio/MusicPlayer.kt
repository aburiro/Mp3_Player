package com.example.plays_audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.graphics.Color
import androidx.core.app.NotificationCompat

class MusicPlayer : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private var isPlaying = false

    // Add progress tracking
    private var progress = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    progress = player.currentPosition
                    updateNotification()
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayer = this@MusicPlayer
    }

    override fun onCreate() {
        super.onCreate()
        initializeMediaPlayer()
        createNotificationChannel()
    }

    private fun initializeMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.saari_duniya)?.apply {
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    handleMediaPlayerError(what, extra)
                    true
                }
                setOnPreparedListener {
                    // Ready to play
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun handleMediaPlayerError(what: Int, extra: Int) {

        when (what) {
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
               release()
                initializeMediaPlayer()
            }
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
               stopPlayback()
            }
        }
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
/// Notification code ???
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
                enableLights(true)
                lightColor = Color.BLUE
                setShowBadge(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
// Create Notifications
    private fun createNotification(): Notification {
        val playPauseIntent = Intent(this, MusicPlayer::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            PLAY_PAUSE_REQUEST_CODE,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicPlayer::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val duration = mediaPlayer?.duration ?: 1
        val progressPercent = ((progress.toFloat() / duration) * 100).toInt()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Player")
            .setContentText(if (isPlaying) "Playing - $progressPercent%" else "Paused")
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(isPlaying)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, false)
            .build()
    }
// Notification Modidie code
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
// Mthods
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    fun play() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            startForeground(NOTIFICATION_ID, createNotification())
            // Start progress updates
            handler.post(updateProgressRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            updateNotification()
            // Stop progress updates
            handler.removeCallbacks(updateProgressRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    fun stop() {
        stopPlayback()
        stopForeground(true)
        stopSelf()
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.prepare() // Prepare for future playbacks
            isPlaying = false
            progress = 0
            handler.removeCallbacks(updateProgressRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun release() {
        handler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.example.plays_audio.PLAY"
        const val ACTION_PAUSE = "com.example.plays_audio.PAUSE"
        const val ACTION_STOP = "com.example.plays_audio.STOP"
        const val PLAY_PAUSE_REQUEST_CODE = 1
        const val STOP_REQUEST_CODE = 2
    }
}
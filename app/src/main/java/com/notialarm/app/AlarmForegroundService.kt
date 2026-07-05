package com.notialarm.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException

class AlarmForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var originalVolume: Int = -1

    companion object {
        const val CHANNEL_ID = "alarm_foreground_service_channel"
        const val NOTIFICATION_ID = 9999
        
        var isAlarming = false
        var activeTriggerApp: String = ""
        var activeTriggerTitle: String = ""
        var activeTriggerText: String = ""
        var activeMatchedKeyword: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_ALARM") {
            stopSelf()
            return START_NOT_STICKY
        }

        isAlarming = true
        activeTriggerApp = intent?.getStringExtra("app") ?: "Test App"
        activeTriggerTitle = intent?.getStringExtra("title") ?: "Test Title"
        activeTriggerText = intent?.getStringExtra("text") ?: "Test Text"
        activeMatchedKeyword = intent?.getStringExtra("keyword") ?: "Test Keyword"

        createNotificationChannel()
        
        // Build fullscreen intent to wake up screen
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("app", activeTriggerApp)
            putExtra("title", activeTriggerTitle)
            putExtra("text", activeTriggerText)
            putExtra("keyword", activeMatchedKeyword)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotiAlarm Triggered!")
            .setContentText("Alarm triggered by $activeTriggerApp")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Wake screen / overlay
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "NotiAlarm:WakeLock"
            )
            wakeLock.acquire(10000)
        } catch (e: Exception) {
            Log.e("NotiAlarm", "Failed to acquire wake lock: ${e.message}")
        }

        // Start playing custom sound
        val settings = SettingsManager(this)
        val soundUriStr = settings.soundUri
        val soundUri = if (!soundUriStr.isNullOrEmpty()) Uri.parse(soundUriStr) else null

        // Maximize volume
        try {
            originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        } catch (e: Exception) {
            Log.e("NotiAlarm", "Failed to force alarm volume: ${e.message}")
        }

        startAudio(soundUri)
        startVibration()

        // Launch full-screen overlay directly
        startActivity(alarmIntent)

        return START_STICKY
    }

    private fun startAudio(uri: Uri?) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
            try {
                if (uri != null) {
                    setDataSource(this@AlarmForegroundService, uri)
                } else {
                    // Fallback to default alarm ringtone
                    val defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    setDataSource(this@AlarmForegroundService, defaultUri)
                }
                prepare()
                start()
            } catch (e: IOException) {
                Log.e("NotiAlarm", "Failed to play audio source: ${e.message}")
                // Final fallback
                try {
                    val defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    setDataSource(this@AlarmForegroundService, defaultUri)
                    prepare()
                    start()
                } catch (ex: Exception) {
                    Log.e("NotiAlarm", "All audio paths failed: ${ex.message}")
                }
            }
        }
    }

    private fun startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 1000, 500, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 1000, 500, 1000), 0)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Foreground alerts for active notification alarms"
                setBypassDnd(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isAlarming = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
        
        // Restore original volume
        if (originalVolume != -1) {
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            } catch (e: Exception) {
                Log.e("NotiAlarm", "Failed to restore original volume: ${e.message}")
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

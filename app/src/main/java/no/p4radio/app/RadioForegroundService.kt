package no.p4radio.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RadioForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "p4radio_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_STATION_NAME = "STATION_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "P4 Radio"
                val notification = buildNotification(stationName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Radio avspilling",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "P4 Radio avspilling"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(stationName: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(stationName)
            .setContentText("Spiller av • P4 Radio")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}

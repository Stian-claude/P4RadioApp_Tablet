package no.p4radio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            // Stop radio foreground service (works even if activity is not running)
            context.startService(
                Intent(context, RadioForegroundService::class.java).apply {
                    action = RadioForegroundService.ACTION_STOP
                }
            )
        }
    }

    companion object {
        const val ACTION_STOP = "no.radioapp.player.ACTION_STOP"
    }
}

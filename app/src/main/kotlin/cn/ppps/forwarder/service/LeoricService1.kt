/*
 * Leoric 保活 - Persistent 进程 Service
 * 运行在独立进程，与 Daemon 进程互守
 */
package cn.ppps.forwarder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.FRONT_CHANNEL_ID
import cn.ppps.forwarder.utils.FRONT_CHANNEL_NAME
import cn.ppps.forwarder.utils.SettingUtils

class LeoricService1 : Service() {

    companion object {
        private const val NOTIFY_ID = 202
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        startForeground(NOTIFY_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "${FRONT_CHANNEL_ID}_leoric_p"
        val channelName = "${FRONT_CHANNEL_NAME}_LeoricPersist"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
            val importance = if (SettingUtils.showLeoric1Notification)
                NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }

        val flags = if (Build.VERSION.SDK_INT >= 30) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(),
            flags
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_forwarder)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_forwarder)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun showNotification() {
        startForeground(NOTIFY_ID, createNotification())
    }
}

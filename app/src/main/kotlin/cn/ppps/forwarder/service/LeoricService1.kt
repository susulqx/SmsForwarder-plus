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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.FRONT_CHANNEL_ID
import cn.ppps.forwarder.utils.FRONT_CHANNEL_NAME
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

class LeoricService1 : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        startForeground(NOTIFY_ID, createNotification())
        startTcpKeepalive()
        startHttpHeartbeat()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTcpKeepalive()
        stopHttpHeartbeat()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "${FRONT_CHANNEL_ID}_leoric_p"
        val channelName = "${FRONT_CHANNEL_NAME}_LeoricPersist"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (SettingUtils.showLeoric1Notification) {
                NotificationManager.IMPORTANCE_LOW
            } else {
                NotificationManager.IMPORTANCE_MIN
            }
            val channel = NotificationChannel(channelId, channelName, importance)
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

        val notifyText = SettingUtils.leoric1NotificationText

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(notifyText)
                .setSmallIcon(R.drawable.ic_forwarder)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(notifyText)
                .setSmallIcon(R.drawable.ic_forwarder)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun showNotification() {
        startForeground(NOTIFY_ID, createNotification())
    }

    // —— 网络保活（运行在 :leoric_p 守护进程，不被冻结）——

    private var tcpKeepaliveThread: Thread? = null
    private var tcpSocket: Socket? = null
    private val httpHeartbeatHandler = Handler(Looper.getMainLooper())
    private val httpHeartbeatRunner = object : Runnable {
        override fun run() {
            if (!SettingUtils.enableHttpHeartbeat) return
            Thread {
                try {
                    val conn = URL("https://www.baidu.com").openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "HEAD"
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) { }
                httpHeartbeatHandler.postDelayed(this, SettingUtils.httpHeartbeatInterval * 1000L)
            }.start()
        }
    }

    private fun startTcpKeepalive() {
        if (!SettingUtils.enableTcpKeepalive) return
        stopTcpKeepalive()
        tcpKeepaliveThread = Thread {
            while (SettingUtils.enableTcpKeepalive) {
                try {
                    tcpSocket = Socket("www.baidu.com", 443)
                    tcpSocket?.soTimeout = 0
                    tcpSocket?.keepAlive = true
                    Log.i(TAG, "TCP keepalive connected")
                    tcpSocket?.getInputStream()?.read()
                } catch (e: Exception) {
                    Log.w(TAG, "TCP keepalive disconnected: ${e.message}")
                } finally {
                    try { tcpSocket?.close() } catch (_: Exception) { }
                    tcpSocket = null
                }
                if (SettingUtils.enableTcpKeepalive) {
                    try {
                        Thread.sleep(SettingUtils.tcpKeepaliveInterval * 1000L)
                    } catch (_: InterruptedException) { break }
                }
            }
        }
        tcpKeepaliveThread?.start()
    }

    private fun stopTcpKeepalive() {
        tcpKeepaliveThread?.interrupt()
        try { tcpSocket?.close() } catch (_: Exception) { }
        tcpSocket = null
        tcpKeepaliveThread = null
    }

    private fun startHttpHeartbeat() {
        httpHeartbeatHandler.removeCallbacks(httpHeartbeatRunner)
        if (!SettingUtils.enableHttpHeartbeat) return
        httpHeartbeatHandler.post(httpHeartbeatRunner)
    }

    private fun stopHttpHeartbeat() {
        httpHeartbeatHandler.removeCallbacks(httpHeartbeatRunner)
    }

    companion object {
        private const val NOTIFY_ID = 202
        private val TAG = LeoricService1::class.java.simpleName
    }
}

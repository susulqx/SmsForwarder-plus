package cn.ppps.forwarder.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.ppps.forwarder.service.ForegroundService
import cn.ppps.forwarder.service.LeoricService1
import cn.ppps.forwarder.service.LeoricService2
import cn.ppps.forwarder.utils.ACTION_START
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.ShizukuKeepaliveHelper

/**
 * 第5层保活：AlarmManager 自检看门狗
 * 定期检查核心服务是否存活，死了就拉起
 */
class ServiceGuardReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceGuard"
        private const val ALARM_REQUEST_CODE = 9001
        private const val ACTION_GUARD_CHECK = "cn.ppps.forwarder.action.GUARD_CHECK"

        /**
         * 启动或更新看门狗闹钟
         */
        fun schedule(context: Context) {
            if (!SettingUtils.enableServiceGuard) {
                cancel(context)
                return
            }
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceGuardReceiver::class.java).apply {
                action = ACTION_GUARD_CHECK
            }
            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

            val intervalMs = SettingUtils.serviceGuardInterval * 60 * 1000L
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    alarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        intervalMs,
                        pendingIntent
                    )
                } else {
                    alarmMgr.setRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        intervalMs,
                        intervalMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Guard scheduled every ${SettingUtils.serviceGuardInterval} min")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule guard: ${e.message}")
            }
        }

        fun cancel(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceGuardReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
            pendingIntent?.let { alarmMgr.cancel(it) }
            Log.d(TAG, "Guard cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_GUARD_CHECK) return

        Log.d(TAG, "Guard check running...")

        // 第0层：Shizuku 循环保活
        if (SettingUtils.enableShizukuDoze) {
            try {
                ShizukuKeepaliveHelper.executeKeepaliveCycle(context)
                Log.d(TAG, "Shizuku keepalive cycle completed")
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku cycle failed: ${e.message}")
            }
        }

        // 检查并拉起 ForegroundService（无设置项，始终应运行）
        if (!ForegroundService.isRunning) {
            Log.w(TAG, "ForegroundService is dead, restarting...")
            val serviceIntent = Intent(context, ForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart ForegroundService: ${e.message}")
            }
        }

        // 检查 LeoricService1
        if (SettingUtils.enableLeoric && !isServiceRunning(context, LeoricService1::class.java)) {
            Log.w(TAG, "LeoricService1 is dead, restarting...")
            try {
                val si = Intent(context, LeoricService1::class.java)
                context.startService(si)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart LeoricService1: ${e.message}")
            }
        }

        // 检查 LeoricService2
        if (SettingUtils.enableLeoric && !isServiceRunning(context, LeoricService2::class.java)) {
            Log.w(TAG, "LeoricService2 is dead, restarting...")
            try {
                val si = Intent(context, LeoricService2::class.java)
                context.startService(si)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart LeoricService2: ${e.message}")
            }
        }

        // 重新调度下一次检查
        schedule(context)
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }
}

package cn.ppps.forwarder.utils

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * 第0层保活：通过 Shizuku 获得 ADB 权限，将自身包名添加到系统 Doze 白名单。
 * 一次执行，永久生效，不需 root。
 */
object ShizukuKeepaliveHelper {

    private const val TAG = "ShizukuKeepalive"

    /** 检查 Shizuku 是否已安装并可用 */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    /** 检查当前 APP 是否已获得 Shizuku 授权 */
    fun isPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /** 请求 Shizuku 授权（会弹出系统对话框） */
    fun requestPermission() {
        try {
            if (!isPermissionGranted()) {
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) { }
    }

    /**
     * 执行 Doze 白名单命令：cmd deviceidle whitelist +<package>
     * @return 执行结果的输出字符串，null 表示执行失败
     */
    fun executeDozeWhitelist(context: Context): String? {
        if (!isShizukuAvailable() || !isPermissionGranted()) return null

        return try {
            val packageName = context.packageName
            val process = Shizuku.newProcess(
                arrayOf("cmd", "deviceidle", "whitelist", "+$packageName"),
                null, null
            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Log.i(TAG, "Doze whitelist result: $output")
            output.ifBlank { "ok" }
        } catch (e: Exception) {
            Log.e(TAG, "Doze whitelist failed: ${e.message}")
            null
        }
    }

    /**
     * 一键完成所有 Shizuku 保活操作：
     * 1. Doze 白名单
     * 返回操作摘要
     */
    fun executeAll(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("Shizuku: ${if (isShizukuAvailable()) "可用" else "不可用"}")
        sb.appendLine("授权: ${if (isPermissionGranted()) "已授权" else "未授权"}")
        val result = executeDozeWhitelist(context)
        sb.appendLine("Doze白名单: ${result ?: "失败"}")
        return sb.toString()
    }
}

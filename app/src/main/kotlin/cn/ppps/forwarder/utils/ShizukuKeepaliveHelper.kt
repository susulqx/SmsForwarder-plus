package cn.ppps.forwarder.utils

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * 第0层保活：通过 Shizuku 获得 ADB 权限执行系统级保活命令。
 * 不需要 root，安装 Shizuku App 并授权即可。
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

    /** 请求 Shizuku 授权（会弹出系统 Shizuku 对话框） */
    fun requestPermission() {
        try {
            if (!isPermissionGranted()) {
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) { }
    }

    /**
     * 执行任意 shell 命令（供 Shizuku ADB 终端使用）
     * @return 命令输出，null 表示执行失败
     */
    fun execCommand(command: String): String? {
        if (!isShizukuAvailable() || !isPermissionGranted()) return null
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            Log.d(TAG, "execCommand output: $output")
            if (error.isNotBlank()) Log.w(TAG, "execCommand stderr: $error")
            (output + error).ifBlank { "(无输出)" }
        } catch (e: Exception) {
            Log.e(TAG, "execCommand failed: ${e.message}")
            e.message
        }
    }

    /**
     * 执行 Doze 白名单命令：先试 cmd，失败自动回退到 dumpsys
     * cmd deviceidle whitelist +包名    → Android 8–11 有效
     * dumpsys deviceidle whitelist +包名 → Android 12+ 备选
     * @return 执行结果的输出字符串，null 表示全部失败
     */
    fun executeDozeWhitelist(context: Context): String? {
        if (!isShizukuAvailable() || !isPermissionGranted()) return null
        val pkg = context.packageName

        // 方案1：cmd deviceidle（标准路径）
        try {
            val proc = Shizuku.newProcess(arrayOf("cmd", "deviceidle", "whitelist", "+$pkg"), null, null)
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            Log.d(TAG, "cmd deviceidle exit=$code, out=$out")
            if (code == 0 && !out.contains("Permission", ignoreCase = true) && !out.contains("SecurityException")) {
                return out.ifBlank { "ok" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cmd 方案失败，回退到 dumpsys: ${e.message}")
        }

        // 方案2：dumpsys deviceidle（兼容性更好）
        return try {
            val proc = Shizuku.newProcess(arrayOf("dumpsys", "deviceidle", "whitelist", "+$pkg"), null, null)
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            Log.d(TAG, "dumpsys deviceidle out=$out")
            out.ifBlank { "ok" }
        } catch (e: Exception) {
            Log.e(TAG, "dumpsys 方案也失败了: ${e.message}")
            null
        }
    }

    /**
     * Shizuku 循环保活：除了白名单，还执行额外的系统级保活操作
     * @return 各命令的执行结果汇总
     */
    fun executeKeepaliveCycle(context: Context): String {
        if (!isShizukuAvailable() || !isPermissionGranted()) return "Shizuku: 不可用/未授权"
        val pkg = context.packageName
        val sb = StringBuilder()

        // 1. Doze 白名单
        sb.appendLine("Doze白名单: ${executeDozeWhitelist(context) ?: "失败"}")

        // 2. 取消 inactive 标记（Android 9+）
        try {
            val proc = Shizuku.newProcess(arrayOf("am", "set-inactive", pkg, "false"), null, null)
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            sb.appendLine("set-inactive: ${out.ifBlank { "ok" }}")
        } catch (e: Exception) {
            sb.appendLine("set-inactive: ${e.message}")
        }

        // 3. 触发前台服务保活（通知 AMS 此包活跃）
        try {
            val proc = Shizuku.newProcess(arrayOf("am", "broadcast", "-a", "android.intent.action.PACKAGE_CHANGED", "-p", pkg), null, null)
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            sb.appendLine("broadcast: ${out.ifBlank { "ok" }}")
        } catch (e: Exception) {
            sb.appendLine("broadcast: ${e.message}")
        }

        Log.i(TAG, "Keepalive cycle:\n$sb")
        return sb.toString()
    }

    /**
     * 一键完成全部 Shizuku 保活操作
     */
    fun executeAll(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("Shizuku: ${if (isShizukuAvailable()) "可用" else "不可用"}")
        sb.appendLine("授权: ${if (isPermissionGranted()) "已授权" else "未授权"}")
        sb.appendLine("---")
        sb.append(executeKeepaliveCycle(context))
        return sb.toString()
    }
}

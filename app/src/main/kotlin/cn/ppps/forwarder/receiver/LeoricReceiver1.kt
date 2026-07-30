/*
 * Leoric 保活 - Persistent 进程 BroadcastReceiver
 * 用于在进程创建时触发初始化
 */
package cn.ppps.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LeoricReceiver1 : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // 由 Leoric 框架通过 Service 自动管理，此处为空实现
    }
}

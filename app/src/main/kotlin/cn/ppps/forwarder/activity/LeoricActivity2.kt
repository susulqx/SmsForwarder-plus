/*
 * Leoric 保活 - Daemon 进程 Activity
 * 用于进程挂载，实际不可见
 */
package cn.ppps.forwarder.activity

import android.app.Activity
import android.os.Bundle

class LeoricActivity2 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 立即结束，仅用于进程挂载
        finish()
    }
}

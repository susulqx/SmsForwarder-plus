/*
 * Original Copyright 2015 Mars Kwok
 * Modified work Copyright (c) 2020, weishu
 * Adapted for SmsForwarder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ppps.forwarder.leoric

import android.content.Context
import android.content.SharedPreferences
import cn.ppps.forwarder.utils.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

object Leoric {

    private const val TAG = "Leoric"

    private const val DAEMON_PERMITTING_SP_FILENAME = "d_permit"
    private const val DAEMON_PERMITTING_SP_KEY = "permitted"

    private var mConfigurations: LeoricConfigs? = null
    private var mBufferedReader: BufferedReader? = null

    /**
     * 初始化 Leoric 保活系统
     * 需要在 Application.attachBaseContext() 中调用
     */
    fun init(base: Context, configurations: LeoricConfigs) {
        // 解除 Android 9+ 隐藏 API 限制
        try {
            val reflectionClass = Class.forName("me.weishu.reflection.Reflection")
            val unsealMethod = reflectionClass.getMethod("unseal", Context::class.java)
            unsealMethod.invoke(null, base)
        } catch (e: Exception) {
            Log.w(TAG, "Reflection.unseal failed, trying fallback: ${e.message}")
            // 如果 FreeReflection 不可用，尝试直接绕过 hidden API 限制
            tryUnsealFallback()
        }
        mConfigurations = configurations
        initDaemon(base)
    }

    /**
     * 备用的解除隐藏 API 限制方案
     */
    private fun tryUnsealFallback() {
        try {
            val clazz = Class.forName("dalvik.system.VMRuntime")
            val getRuntimeMethod = clazz.getDeclaredMethod("getRuntime")
            val runtime = getRuntimeMethod.invoke(null)
            val setHiddenApiExemptions = clazz.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setHiddenApiExemptions.invoke(runtime, arrayOf("L"))
            Log.i(TAG, "Hidden API restriction bypassed via VMRuntime")
        } catch (e: Exception) {
            Log.w(TAG, "VMRuntime fallback also failed: ${e.message}")
        }
    }

    private fun initDaemon(base: Context) {
        if (!isDaemonPermitting(base) || mConfigurations == null) {
            return
        }

        val processName = getProcessName() ?: return
        val packageName = base.packageName

        when {
            processName.startsWith(mConfigurations!!.persistentConfig.processName) -> {
                ILeoricProcess.Fetcher.fetchStrategy()
                    .onPersistentCreate(base, mConfigurations!!)
            }
            processName.startsWith(mConfigurations!!.daemonAssistantConfig.processName) -> {
                ILeoricProcess.Fetcher.fetchStrategy()
                    .onDaemonAssistantCreate(base, mConfigurations!!)
            }
            processName.startsWith(packageName) -> {
                ILeoricProcess.Fetcher.fetchStrategy().onInit(base)
            }
        }

        releaseIO()
    }

    private fun getProcessName(): String? {
        return try {
            val file = File("/proc/self/cmdline")
            mBufferedReader = BufferedReader(FileReader(file))
            mBufferedReader!!.readLine().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun releaseIO() {
        try {
            mBufferedReader?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mBufferedReader = null
    }

    private fun isDaemonPermitting(context: Context): Boolean {
        val sp = context.getSharedPreferences(
            DAEMON_PERMITTING_SP_FILENAME,
            Context.MODE_PRIVATE
        )
        return sp.getBoolean(DAEMON_PERMITTING_SP_KEY, true)
    }

    fun setDaemonPermitting(context: Context, isPermitting: Boolean): Boolean {
        val sp = context.getSharedPreferences(
            DAEMON_PERMITTING_SP_FILENAME,
            Context.MODE_PRIVATE
        )
        val editor: SharedPreferences.Editor = sp.edit()
        editor.putBoolean(DAEMON_PERMITTING_SP_KEY, isPermitting)
        return editor.commit()
    }
}

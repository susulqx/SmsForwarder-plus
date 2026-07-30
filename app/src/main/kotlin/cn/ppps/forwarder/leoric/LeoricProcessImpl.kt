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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.util.Log
import java.io.File
import java.io.IOException

class LeoricProcessImpl : ILeoricProcess {

    companion object {
        private const val TAG = "LeoricProcessImpl"

        private const val INDICATOR_DIR_NAME = "indicators"
        private const val INDICATOR_PERSISTENT_FILENAME = "indicator_p"
        private const val INDICATOR_DAEMON_ASSISTANT_FILENAME = "indicator_d"
        private const val OBSERVER_PERSISTENT_FILENAME = "observer_p"
        private const val OBSERVER_DAEMON_ASSISTANT_FILENAME = "observer_d"
    }

    private var mRemote: IBinder? = null
    private var mServiceData: Parcel? = null

    private var mPid: Int = Process.myPid()

    override fun onInit(context: Context): Boolean {
        return initIndicatorFiles(context)
    }

    override fun onPersistentCreate(context: Context, configs: LeoricConfigs) {
        initAmsBinder()
        initServiceParcel(context, configs.daemonAssistantConfig.serviceName)
        startServiceByAmsBinder()

        Thread {
            val indicatorDir = context.getDir(INDICATOR_DIR_NAME, Context.MODE_PRIVATE)
            NativeLeoric().doDaemon(
                File(indicatorDir, INDICATOR_PERSISTENT_FILENAME).absolutePath,
                File(indicatorDir, INDICATOR_DAEMON_ASSISTANT_FILENAME).absolutePath,
                File(indicatorDir, OBSERVER_PERSISTENT_FILENAME).absolutePath,
                File(indicatorDir, OBSERVER_DAEMON_ASSISTANT_FILENAME).absolutePath
            )
        }.start()
    }

    override fun onDaemonAssistantCreate(context: Context, configs: LeoricConfigs) {
        initAmsBinder()
        initServiceParcel(context, configs.persistentConfig.serviceName)
        startServiceByAmsBinder()

        Thread {
            val indicatorDir = context.getDir(INDICATOR_DIR_NAME, Context.MODE_PRIVATE)
            NativeLeoric().doDaemon(
                File(indicatorDir, INDICATOR_DAEMON_ASSISTANT_FILENAME).absolutePath,
                File(indicatorDir, INDICATOR_PERSISTENT_FILENAME).absolutePath,
                File(indicatorDir, OBSERVER_DAEMON_ASSISTANT_FILENAME).absolutePath,
                File(indicatorDir, OBSERVER_PERSISTENT_FILENAME).absolutePath
            )
        }.start()
    }

    override fun onDaemonDead() {
        Log.i(TAG, "on daemon dead!")
        if (startServiceByAmsBinder()) {
            val pid = Process.myPid()
            Log.i(TAG, "mPid: $mPid current pid: $pid")
            Process.killProcess(mPid)
        }
    }

    private fun initAmsBinder() {
        try {
            val activityManagerNative = Class.forName("android.app.ActivityManagerNative")
            val amn = activityManagerNative.getMethod("getDefault").invoke(activityManagerNative)
            val mRemoteField = amn.javaClass.getDeclaredField("mRemote")
            mRemoteField.isAccessible = true
            mRemote = mRemoteField.get(amn) as IBinder
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @SuppressLint("Recycle")
    private fun initServiceParcel(context: Context, serviceName: String) {
        val intent = Intent()
        val component = ComponentName(context.packageName, serviceName)
        intent.component = component

        val parcel = Parcel.obtain()
        intent.writeToParcel(parcel, 0)

        mServiceData = Parcel.obtain()
        if (Build.VERSION.SDK_INT >= 26) {
            // Android 8.1
            mServiceData!!.writeInterfaceToken("android.app.IActivityManager")
            mServiceData!!.writeStrongBinder(null)
            mServiceData!!.writeInt(1)
            intent.writeToParcel(mServiceData!!, 0)
            mServiceData!!.writeString(null)
            mServiceData!!.writeInt(
                if (context.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.O) 1 else 0
            )
            mServiceData!!.writeString(context.packageName)
            mServiceData!!.writeInt(0)
        } else {
            // Android 7.x and below
            mServiceData!!.writeInterfaceToken("android.app.IActivityManager")
            mServiceData!!.writeStrongBinder(null)
            intent.writeToParcel(mServiceData!!, 0)
            mServiceData!!.writeString(null)
            if (Build.VERSION.SDK_INT > 22) {
                mServiceData!!.writeString(context.packageName)
            }
            mServiceData!!.writeInt(0)
        }
    }

    private fun startServiceByAmsBinder(): Boolean {
        return try {
            if (mRemote == null || mServiceData == null) {
                Log.e("Daemon", "REMOTE IS NULL or PARCEL IS NULL !!!")
                return false
            }
            val code = when (Build.VERSION.SDK_INT) {
                26, 27 -> 26
                28 -> 30
                29 -> 24
                30 -> 26
                31 -> 27
                else -> 34
            }
            mRemote!!.transact(code, mServiceData!!, null, 1)
            true
        } catch (e: RemoteException) {
            e.printStackTrace()
            false
        }
    }

    private fun initIndicatorFiles(context: Context): Boolean {
        val dirFile = context.getDir(INDICATOR_DIR_NAME, Context.MODE_PRIVATE)
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }
        return try {
            createNewFile(dirFile, INDICATOR_PERSISTENT_FILENAME)
            createNewFile(dirFile, INDICATOR_DAEMON_ASSISTANT_FILENAME)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    @Throws(IOException::class)
    private fun createNewFile(dirFile: File, fileName: String) {
        val file = File(dirFile, fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
    }
}

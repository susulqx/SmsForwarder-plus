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

interface ILeoricProcess {
    /**
     * Initialization some files or other when 1st time
     */
    fun onInit(context: Context): Boolean

    /**
     * when Persistent processName create
     */
    fun onPersistentCreate(context: Context, configs: LeoricConfigs)

    /**
     * when DaemonAssistant processName create
     */
    fun onDaemonAssistantCreate(context: Context, configs: LeoricConfigs)

    /**
     * when watches the processName dead which it watched
     */
    fun onDaemonDead()

    companion object Fetcher {
        @Volatile
        private var mDaemonStrategy: ILeoricProcess? = null

        /**
         * fetch the strategy for this device
         *
         * @return the daemon strategy for this device
         */
        fun fetchStrategy(): ILeoricProcess {
            if (mDaemonStrategy != null) {
                return mDaemonStrategy!!
            }
            mDaemonStrategy = LeoricProcessImpl()
            return mDaemonStrategy!!
        }
    }
}

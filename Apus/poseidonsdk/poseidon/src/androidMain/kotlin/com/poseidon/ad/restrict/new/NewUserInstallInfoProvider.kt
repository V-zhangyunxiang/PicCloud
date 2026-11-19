package com.poseidon.ad.restrict.new

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 新用户安装信息提供者，封装安装/更新时间及新用户判定逻辑。
 */
internal object NewUserInstallInfoProvider {

    /**
     * 判断当前设备是否为新用户（安装时间等于更新时间）。
     */
    fun isNewUser(): Boolean {
        return runCatching {
            val installTime = getAppInstallTime()
            val updateTime = getAppUpdateTime()
            val isNew = installTime == updateTime
            logInfo(NEW_USER_FIRST_SHOW_TAG) {
                "App time info - installTime: $installTime, updateTime: $updateTime, isNewUser=$isNew"
            }
            isNew
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to determine if new user: ${throwable.message}" }
        }.getOrDefault(false)
    }

    private fun getAppInstallTime(): Long {
        return runCatching {
            PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().getAppInstallTime().also {
                logInfo(NEW_USER_FIRST_SHOW_TAG) { "App install time: $it" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to get app install time: ${throwable.message}" }
        }.getOrDefault(System.currentTimeMillis())
    }

    private fun getAppUpdateTime(): Long {
        return runCatching {
            PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().getAppUpdateTime().also {
                logInfo(NEW_USER_FIRST_SHOW_TAG) { "App update time: $it" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to get app update time: ${throwable.message}" }
        }.getOrDefault(System.currentTimeMillis())
    }
}

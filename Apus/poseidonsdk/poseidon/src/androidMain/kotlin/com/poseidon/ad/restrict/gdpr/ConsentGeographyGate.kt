package com.poseidon.ad.restrict.gdpr

import com.poseidon.ad.statistics.biz.ConsentManagementPlatformAnalyzer
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.SpHelper
import com.poseidon.ad.common.toolkit.CompletionListener
import com.poseidon.ad.common.toolkit.logInfo

/**
 * 负责「仅在欧洲执行」的判定逻辑，支持缓存结果并复用 TaskCompletionCoordinator。
 */
internal class ConsentGeographyGate(
    private val tag: String,
    private val sharedPreferenceProvider: () -> SpHelper,
    private val completionHandler: (CompletionListener<Boolean, Throwable>) -> Unit
) {

    fun runIfNeeded(action: () -> Unit) {
        val sharedPreference = sharedPreferenceProvider()
        if (sharedPreference.getBoolean(Key.CmpIsInEurope, false)) {
            logInfo(tag) { "[onlyInEurope] true!" }
            ConsentManagementPlatformAnalyzer.euCheck(isInEurope = true).analyze()
            action()
            return
        }

        completionHandler(
            object : CompletionListener<Boolean, Throwable> {
                override fun onSuccess(data: Boolean) {
                    logInfo(tag) { "[onlyInEurope] $data!" }
                    ConsentManagementPlatformAnalyzer.euCheck(isInEurope = data).analyze()
                    sharedPreference.putBoolean(Key.CmpIsInEurope, data)
                    if (data) {
                        action()
                    }
                }

                override fun onFailure(data: Throwable) {
                    logInfo(tag) { "[onlyInEurope] $data!" }
                    ConsentManagementPlatformAnalyzer.euCheck(isInEurope = false).analyze()
                    ConsentManagementPlatformAnalyzer.errorType(4).analyze()
                    ConsentManagementPlatformAnalyzer.errorMessage(data.message.orEmpty()).analyze()
                    sharedPreference.putBoolean(Key.CmpIsInEurope, false)
                }
            }
        )
    }
}

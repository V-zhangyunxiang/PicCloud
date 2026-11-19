package com.poseidon.ad.mediation.max.init

import android.os.Debug
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.poseidon.ad.BuildConfig
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.core.processor.MediationSetupCallback
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.mediation.MEDIATION_MAX

object MaxSetup : MediationSetup() {
    private const val TAG = "MaxInitializer"

    override fun getMediationId(): Int = MEDIATION_MAX

    override fun initMediation(callback: MediationSetupCallback) {
        val maxConfig = PoseidonConfig.Companion.get()?.mediations?.max
        if (maxConfig == null) {
            callback.onFailure("max configuration is null!")
        } else {
            val applovinSdkKey = maxConfig.applovinSdkKey
            if (applovinSdkKey.isEmpty()) {
                callback.onFailure("applovinSdkKey is empty!")
            } else {
                initializeAppLovin(applovinSdkKey, callback)
            }
        }
    }

    private fun initializeAppLovin(
        applovinSdkKey: String,
        callback: MediationSetupCallback
    ) {
        val application = PoseidonSetupProcessor.Companion.getInstance().application

        // Mitigate the MAX init-time ANR caused by creating a WebView on the main
        // thread for JS postbacks. The guard must be prepared before the first
        // AppLovinSdk.getInstance() call and installed right after it.
        val webTrackerGuardEnabled = MaxWebTrackerGuard.isEnabled()
        if (webTrackerGuardEnabled) {
            MaxWebTrackerGuard.prepare(application, applovinSdkKey)
        }else {
            logInfo(TAG) {
                "WebTracked Disabled"
            }
        }

        val appLovinSdk = AppLovinSdk.getInstance(application)

        if (webTrackerGuardEnabled) {
            MaxWebTrackerGuard.installNoOpTrackerManager(appLovinSdk)
        }

        if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
            // Debug/QA 版本开启更详细的 MAX 日志，方便排查聚合异常。
            // https://developers.applovin.com/en/max/android/overview/advanced-settings#enable-verbose-logging
            appLovinSdk.settings.setVerboseLogging(true)
        } else {
            appLovinSdk.settings.setExtraParameter(
                "disable_all_logs",
                "true"
            )
        }
        // Create the initialization configuration
        val initConfig =
            AppLovinSdkInitializationConfiguration.builder(applovinSdkKey)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                // Perform any additional configuration/setting changes
                .build()
        // Initialize the SDK with the configuration
        appLovinSdk.initialize(initConfig) { sdkConfig ->
            logInfo(TAG) {
                "Initialize complete, countryCode=${sdkConfig.countryCode}, isTestModeEnabled=${sdkConfig.isTestModeEnabled}, enabledAmazonAdUnitIds=${
                    sdkConfig.enabledAmazonAdUnitIds?.joinToString(
                        ","
                    )
                }, consentFlowUserGeography=${sdkConfig.consentFlowUserGeography}"
            }
            // Start loading ads
            callback.onSuccess()
//            appLovinSdk.showMediationDebugger()
        }
    }
}

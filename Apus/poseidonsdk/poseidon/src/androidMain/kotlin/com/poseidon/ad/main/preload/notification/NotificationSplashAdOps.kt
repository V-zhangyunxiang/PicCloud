package com.poseidon.ad.main.preload.notification

import com.poseidon.ad.common.toolkit.ExtraDO
import com.poseidon.ad.main.processor.isGlobalSwitchOn
import com.poseidon.ad.main.strategy.common.NotificationSplashAdCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.mediation.isValidMediationId

internal fun NotificationSplashAdCfg?.check(): ExtraDO<Boolean, String> {
    if (!isGlobalSwitchOn()) {
        return ExtraDO.stringExtra(data = false, extra = "Global ad switch is off")
    }
    val notificationSplashAd = this
        ?: return ExtraDO.stringExtra(data = false, extra = "NotificationSplashAd configuration is empty")
    if (!notificationSplashAd.isSwitchOn()) {
        return ExtraDO.stringExtra(data = false, extra = "NotificationSplashAd switch is off")
    }
    val adUnitNetwork = notificationSplashAd.mediation
    if (!isValidMediationId(adUnitNetwork)) {
        return ExtraDO.stringExtra(data = false, extra = "The configured adUnitNetwork for NotificationSplashAd is invalid: $adUnitNetwork")
    }
    val adUnitId = notificationSplashAd.placementId
    if (adUnitId.isEmpty()) {
        return ExtraDO.stringExtra(data = false, extra = "The adUnitId in NotificationSplashAd configuration is empty")
    }
    val showAdSceneName = notificationSplashAd.showAdSceneName
    if (showAdSceneName.isEmpty()) {
        return ExtraDO.stringExtra(data = false, extra = "The showAdSceneName in NotificationSplashAd configuration is empty")
    }
    val preloadAdWhenNotificationDisplayedSceneName = notificationSplashAd.preloadAdWhenNotificationDisplayedSceneName
    if (preloadAdWhenNotificationDisplayedSceneName.isEmpty()) {
        return ExtraDO.stringExtra(data = false, extra = "The preloadAdWhenNotificationDisplayedSceneName in NotificationSplashAd configuration is empty")
    }
    val preloadAdWhenNotificationClickedSceneName = notificationSplashAd.preloadAdWhenNotificationClickedSceneName
    if (preloadAdWhenNotificationClickedSceneName.isEmpty()) {
        return ExtraDO.stringExtra(data = false, extra = "The preloadAdWhenNotificationClickedSceneName in NotificationSplashAd configuration is empty")
    }
    return ExtraDO.stringExtra(data = true, extra = "NotificationSplashAd configuration check passed")
}

internal fun isNotificationSplashAdShowScene(sceneName: String): Boolean {
    val notificationSplashAd = PoseidonConfig.get()?.notificationSplashAd ?: return false
    return sceneName == notificationSplashAd.showAdSceneName
}

internal fun isNotificationSplashAdPreloadScene(sceneName: String): Boolean {
    val notificationSplashAd = PoseidonConfig.get()?.notificationSplashAd ?: return false
    return sceneName == notificationSplashAd.preloadAdWhenNotificationDisplayedSceneName ||
        sceneName == notificationSplashAd.preloadAdWhenNotificationClickedSceneName
}

internal fun preloadAdWhenNotificationDisplayedSceneName(): String? {
    return PoseidonConfig.get()?.notificationSplashAd?.preloadAdWhenNotificationDisplayedSceneName
}

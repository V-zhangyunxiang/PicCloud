package com.poseidon.ad.main.preload.new

import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.preload.notification.isNotificationSplashAdPreloadScene
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.main.strategy.common.StrategyCfg

internal fun resolveSceneAndStrategy(sceneName: String): Pair<SceneCfg, StrategyCfg>? {
    val configuration = PoseidonConfig.get() ?: return null
    val scene = configuration.scenes?.firstOrNull { it.name == sceneName } ?: return null
    val strategy = configuration.strategies?.firstOrNull { it.id == scene.strategyId } ?: return null
    return scene to strategy
}

internal fun isNewUserEntryScene(sceneName: String, @AdTypeDef adType: Int): Boolean {
    val (scene, strategy) = resolveSceneAndStrategy(sceneName = sceneName) ?: return false
    if (scene.adType != adType) {
        return false
    }
    return strategy.description.trim().lowercase().startsWith("new_user_")
}

internal fun shouldBlockOrdinaryPreload(sceneName: String): Boolean {
    if (!NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        return false
    }
    if (isNotificationSplashAdPreloadScene(sceneName = sceneName)) {
        return true
    }
    return !isNewUserEntryScene(sceneName = sceneName, adType = AD_TYPE_INTERSTITIAL) &&
        !isNewUserEntryScene(sceneName = sceneName, adType = AD_TYPE_REWARDED)
}


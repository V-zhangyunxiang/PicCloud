package com.poseidon.ad.main.preload.new

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import com.poseidon.ad.restrict.new.NewUserFirstShowMtg
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_OPEN
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.FullscreenAdTypeDef
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.main.strategy.common.StrategyCfg

private const val NEW_USER_STRATEGY_DESCRIPTION_PREFIX = "new_user_"

internal fun StrategyCfg.isNewUserStrategy(): Boolean {
    return description.trim().lowercase().startsWith(NEW_USER_STRATEGY_DESCRIPTION_PREFIX)
}

internal fun filterEligibleNewUserScopedStrategyIds(
    strategyIds: Collection<Int>,
    excludedStrategyIds: Set<Int>,
): List<Int> {
    return strategyIds.asSequence()
        .filterNot(excludedStrategyIds::contains)
        .distinct()
        .toList()
}

private fun scopedNewUserCandidateAdTypesForFullscreen(
    @FullscreenAdTypeDef fullScreenAdType: Int,
): IntArray {
    return when (fullScreenAdType) {
        AD_TYPE_OPEN -> intArrayOf(AD_TYPE_INTERSTITIAL, AD_TYPE_OPEN)
        AD_TYPE_INTERSTITIAL -> intArrayOf(AD_TYPE_INTERSTITIAL)
        AD_TYPE_REWARDED -> intArrayOf(AD_TYPE_INTERSTITIAL, AD_TYPE_REWARDED)
        else -> intArrayOf()
    }
}

internal object NewUserScopedBidderRegistry {

    private val strategyIdsByAdType: MutableMap<Int, LinkedHashSet<Int>> = mutableMapOf()
    private val lock = reentrantLock()

    fun registerLoadedStrategy(scene: SceneCfg, strategy: StrategyCfg) {
        if (scene.strategyId != strategy.id || !strategy.isNewUserStrategy()) {
            return
        }
        lock.withLock {
            strategyIdsByAdType.getOrPut(scene.adType) { linkedSetOf() }.add(strategy.id)
        }
    }

    fun eligibleStrategyIdsForFullscreen(
        @FullscreenAdTypeDef fullScreenAdType: Int,
        excludedStrategyIds: Set<Int> = emptySet(),
    ): List<Int> {
        if (!NewUserFirstShowMtg.isNewUserFirstColdStart()) {
            return emptyList()
        }
        return lock.withLock {
            filterEligibleNewUserScopedStrategyIds(
                strategyIds = scopedNewUserCandidateAdTypesForFullscreen(fullScreenAdType)
                    .asSequence()
                    .flatMap { adType -> strategyIdsByAdType[adType].orEmpty().asSequence() }
                    .toList(),
                excludedStrategyIds = excludedStrategyIds,
            )
        }
    }
}


package com.poseidon.ad.restrict.show

import com.poseidon.ad.main.strategy.limit.show.AdLimitShowItemCfg

/**
 * Pure decision: determines whether a scene is allowed to show an ad,
 * given pre-fetched DB aggregates. No side effects, no DB or config access.
 *
 * @param sceneName       the scene requesting show permission
 * @param item            the limit configuration
 * @param todayCheckCount total checks recorded for this scene today
 * @param firstCheckPassed whether the first check of the day was pass=1 (null if no records)
 * @param firstCheckShown  whether the first check of the day was shown=1 (null if no records)
 * @param recentShownFlags list of shown flags (true=shown) for the most recent N records
 */
internal fun resolveAdShowLimitOutcome(
    sceneName: String,
    item: AdLimitShowItemCfg,
    todayCheckCount: Int,
    firstCheckPassed: Boolean?,
    firstCheckShown: Boolean?,
    recentShownFlags: List<Boolean>,
): Boolean {
    val scopedScenes = item.scenes
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
    if (!scopedScenes.isNullOrEmpty() && !scopedScenes.contains(sceneName)) {
        return true
    }

    if (todayCheckCount == 0) {
        return item.tsf
    }

    val initialWindowEnd = item.tif + 1
    if (todayCheckCount == initialWindowEnd) {
        return true
    }
    if (todayCheckCount < initialWindowEnd) {
        if (firstCheckPassed != true) return false
        if (firstCheckShown == true) return false
        return true
    }

    val rollingRemainder = todayCheckCount % (item.til + 1)
    if (rollingRemainder == 0) {
        return true
    }

    return recentShownFlags.none { it }
}

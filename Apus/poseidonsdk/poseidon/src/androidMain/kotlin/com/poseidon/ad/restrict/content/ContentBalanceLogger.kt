package com.poseidon.ad.restrict.content

import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.getAdTypeName

/**
 * 内容平衡日志工具，统一输出英文日志信息，便于排查。
 */
internal object ContentBalanceLogger {
    const val TAG: String = "AdContentBalanceManager"

    /**
     * 记录场景配置缺失的提示。
     */
    fun sceneMissing(sceneName: String, phase: ContentRestrictionPhase) {
        logInfo(TAG) { "Scene $sceneName missing while ${phase.action}, allowing operation" }
    }

    /**
     * 记录场景未配置页面的提示。
     */
    fun pageMissing(sceneName: String, phase: ContentRestrictionPhase) {
        logInfo(TAG) { "Scene $sceneName has no page configured while ${phase.action}, skipping restriction" }
    }

    /**
     * 记录首次初始化页面上下文的情况。
     */
    fun pageInitialized(pageName: String) {
        logInfo(TAG) { "Initialized content balance context with currentPage=$pageName" }
    }

    /**
     * 记录页面切换时的迁移情况。
     */
    fun pageTransition(fromPage: String, toPage: String, currentCount: Int) {
        logInfo(TAG) {
            "Page transition detected: from $fromPage to $toPage, transferring currentCount=$currentCount to lastStats"
        }
    }

    /**
     * 记录评估前的状态快照。
     */
    fun evaluationTrace(
        sceneName: String,
        pageName: String,
        adType: Int,
        lastPage: String?,
        lastCount: Int,
        currentPage: String?,
        currentCount: Int,
        threshold: Int
    ) {
        logInfo(TAG) {
            "Evaluating content balance before show - scene=$sceneName, page=$pageName, adType=${getAdTypeName(adType)}, " +
                "lastPage=$lastPage, lastCount=$lastCount, currentPage=$currentPage, currentCount=$currentCount, threshold=$threshold"
        }
    }

    /**
     * 记录超限拦截原因。
     */
    fun block(reason: String) {
        logInfo(TAG) { "Content balance check blocked - $reason" }
    }

    /**
     * 记录展示计数后的状态。
     */
    fun recordTrace(pageName: String, adType: Int, lastCount: Int, currentCount: Int, lastPage: String?, currentPage: String?) {
        logInfo(TAG) {
            "Recorded content balance display - page=$pageName, adType=${getAdTypeName(adType)}, lastPage=$lastPage, " +
                "lastCount=$lastCount, currentPage=$currentPage, currentCount=$currentCount"
        }
    }

    /**
     * 记录单次计数广告重复上报的情况。
     */
    fun singleCountAlreadyRecorded(pageName: String, adType: Int, currentCount: Int) {
        logInfo(TAG) {
            "${getAdTypeName(adType)} display already counted for page=$pageName, keeping counter at $currentCount"
        }
    }

    /**
     * 记录所有状态被清理的事件。
     */
    fun stateCleared() {
        logInfo(TAG) { "Content balance state cleared" }
    }

    /**
     * 记录未知广告类型的提示。
     */
    fun unknownAdType(adType: Int) {
        logInfo(TAG) { "Unknown adType=$adType while recording content balance display, skipping counter" }
    }
}

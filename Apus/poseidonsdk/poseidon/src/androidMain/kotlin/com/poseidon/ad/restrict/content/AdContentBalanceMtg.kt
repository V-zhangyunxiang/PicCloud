package com.poseidon.ad.restrict.content

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.restrict.FrequencyCheckResult

/**
 * “广告大于内容”限制管理器，对外暴露评估、记录与清理入口。
 */
internal object AdContentBalanceMtg {
    /**
     * 单页允许的最大广告数量。
     */
    private const val AD_CONTENT_THRESHOLD = 2

    /**
     * 页面上下文控制器，负责串行化管理计数状态。
     */
    private val pageContextController = PageContextController()

    /**
     * 展示广告前的内容占比校验。
     *
     * @return 若需阻止展示则返回 [FrequencyCheckResult.Block]，否则 [FrequencyCheckResult.Allow]
     */
    fun evaluateBeforeShow(sceneName: String, @AdTypeDef adType: Int, config: PoseidonConfig): FrequencyCheckResult {
        if (!ContentRestrictionResolver.isRestrictionEnabled(config)) {
            return FrequencyCheckResult.Allow
        }

        val pageName =
            ContentRestrictionResolver.resolvePageName(sceneName, config, ContentRestrictionPhase.Evaluate)
                ?: return FrequencyCheckResult.Allow

        return pageContextController.withPage(pageName) {
            val previousCount = lastStats.total
            val currentCount = currentStats.total
            val totalCount = previousCount + currentCount

            ContentBalanceLogger.evaluationTrace(
                sceneName = sceneName,
                pageName = pageName,
                adType = adType,
                lastPage = lastPage,
                lastCount = previousCount,
                currentPage = currentPage,
                currentCount = currentCount,
                threshold = AD_CONTENT_THRESHOLD
            )

            if (totalCount > AD_CONTENT_THRESHOLD) {
                val reason =
                    "Ad content over limit: previous=$previousCount, current=$currentCount, threshold=$AD_CONTENT_THRESHOLD, page=$pageName, adType=${
                        getAdTypeName(adType)
                    }"
                ContentBalanceLogger.block(reason)
                FrequencyCheckResult.Block(reason)
            } else {
                FrequencyCheckResult.Allow
            }
        }
    }

    /**
     * 记录广告真实展示成功事件。
     */
    fun recordDisplay(sceneName: String, @AdTypeDef adType: Int) {
        val config = PoseidonConfig.get()
        if (!ContentRestrictionResolver.isRestrictionEnabled(config)) {
            return
        }

        val safeConfig = config ?: return
        val pageName =
            ContentRestrictionResolver.resolvePageName(sceneName, safeConfig, ContentRestrictionPhase.Record)
                ?: return

        pageContextController.withPage(pageName) {
            val incremented = currentStats.increment(adType, pageName)
            if (!incremented) {
                ContentBalanceLogger.unknownAdType(adType)
            }

            ContentBalanceLogger.recordTrace(
                pageName = pageName,
                adType = adType,
                lastCount = lastStats.total,
                currentCount = currentStats.total,
                lastPage = lastPage,
                currentPage = currentPage
            )
        }
    }

    /**
     * 重置全部状态，一般用于冷启动或测试。
     */
    fun resetAll() {
        pageContextController.reset()
        ContentBalanceLogger.stateCleared()
    }
}

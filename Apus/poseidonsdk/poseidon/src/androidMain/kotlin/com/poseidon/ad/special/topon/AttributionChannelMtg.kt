package com.poseidon.ad.special.topon

import com.poseidon.ad.statistics.biz.TohungaAnalyzer
import kotlinx.atomicfu.atomic

/**
 * Tohunga 状态管理器，缓存 Adjust 渠道名称并提供降级逻辑。
 */
@Suppress("SpellCheckingInspection")
internal object AttributionChannelMtg {

    /**
     * 最新的归因渠道原子缓存。
     */
    private val attributionChannelNameAtomic = atomic<String?>(null)

    /**
     * 更新缓存，并将结果上报分析。
     */
    fun update(attributionChannelName: String?) {
        attributionChannelNameAtomic.value = attributionChannelName
        TohungaAnalyzer(attributionChannelName = attributionChannelName).analyze()
    }

    /**
     * 获取当前缓存，若为空则回落到本地持久化记录。
     */
    fun get(): String? {
        var attributionChannelName = attributionChannelNameAtomic.value
        if (attributionChannelName.isNullOrEmpty()) {
            attributionChannelName = fallbackAttributionChannelName()
        }
        return attributionChannelName
    }

    /**
     * 判断是否为空、NULL 或 ORGANIC 渠道。
     */
    fun isEmptyOrOrganic(): Boolean {
        val attributionChannelName = get().orEmpty().uppercase()
        return attributionChannelName.isEmpty() || attributionChannelName == "NULL" || attributionChannelName == "ORGANIC"
    }

    private fun fallbackAttributionChannelName(): String? {
        return AdjustAttributionNetworkStore.getAttributionNetworkSync()
    }
}

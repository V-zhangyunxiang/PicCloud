package com.poseidon.ad.special.topon

import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAttribution
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.storage.sp.Key

/**
 * Adjust 归因通用存储基类，统一处理缓存、日志与异步刷新。
 */
internal class AdjustAttributionStoreBase(
    private val preferenceKey: Key,
    private val analyzer: (from: String, value: String?) -> Unit,
    private val valueExtractor: (AdjustAttribution?) -> String?
) {
    private val lock = Any()

    /**
     * 接收到归因变更时的处理，记录日志并缓存。
     */
    fun handleAttributionChanged(origin: String, value: String?) {
        reportAndPersistIfChanged(origin, value)
    }

    /**
     * 读取缓存值。读取本身不代表 Adjust 归因发生变化，因此不触发打点。
     */
    fun readCachedValue(): String? {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        return sharedPreference.getString(preferenceKey, null)
    }

    /**
     * 触发异步刷新，将最新值写入缓存并记录来源。
     */
    fun refreshFromAdjust(origin: String) {
        Adjust.getAttribution { attribution ->
            val value = valueExtractor(attribution)
            reportAndPersistIfChanged(origin, value)
        }
    }

    private fun reportAndPersistIfChanged(origin: String, value: String?) {
        val newValue = value?.takeUnless(String::isEmpty) ?: return
        val shouldReport = synchronized(lock) {
            val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
            val cachedValue = sharedPreference.getString(preferenceKey, null)
            if (!shouldReportAttributionValue(cachedValue = cachedValue, newValue = newValue)) {
                return@synchronized false
            }
            sharedPreference.putString(preferenceKey, newValue)
            true
        }
        if (!shouldReport) {
            return
        }
        analyzer(origin, newValue)
    }
}

internal fun shouldReportAttributionValue(cachedValue: String?, newValue: String?): Boolean {
    val nonEmptyNewValue = newValue?.takeUnless(String::isEmpty) ?: return false
    return cachedValue != nonEmptyNewValue
}

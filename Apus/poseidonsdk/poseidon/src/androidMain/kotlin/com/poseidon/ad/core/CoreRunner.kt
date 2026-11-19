package com.poseidon.ad.core

import com.poseidon.ad.core.config.ConfigHub
import com.poseidon.ad.core.flow.AdFlow
import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.core.strategy.StrategyHub

/**
 * CoreRunner wires all lightweight hubs together.
 * 核心运行器用于在 Kotlin/MPP 场景下集中初始化各个 Hub，保持 API 外壳稳定。
 */
internal object CoreRunner {

    val configHub: ConfigHub = ConfigHub
    val strategyHub: StrategyHub = StrategyHub
    val adFlow: AdFlow = AdFlow
    val logHub: LogHub = LogHub

    /**
     * Ensure hubs are touched so that lazy delegates are ready.
     * 触发 Hub 的懒加载，确保在首次调用前完成初始化。
     */
    fun warmUp() {
        // Calling toString to guarantee the singleton instances are referenced once.
        configHub.toString()
        strategyHub.toString()
        adFlow.toString()
        logHub.toString()
    }
}

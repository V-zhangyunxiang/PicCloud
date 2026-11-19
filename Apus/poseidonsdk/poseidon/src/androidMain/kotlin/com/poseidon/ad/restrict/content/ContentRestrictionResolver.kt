package com.poseidon.ad.restrict.content

import com.poseidon.ad.main.strategy.common.PoseidonConfig

/**
 * 内容限制配置解析器，负责判断功能是否开启并提取页面信息。
 */
internal object ContentRestrictionResolver {

    /**
     * 判断配置中是否开启了“广告大于内容”限制。
     */
    fun isRestrictionEnabled(config: PoseidonConfig?): Boolean {
        val globalControl = config?.globalControl
        return globalControl?.adRestrictionByContent == true
    }

    /**
     * 根据场景名称解析出配置的页面名称。
     */
    fun resolvePageName(sceneName: String, config: PoseidonConfig, phase: ContentRestrictionPhase): String? {
        val scene = config.scenes?.find { it.name == sceneName }
        if (scene == null) {
            ContentBalanceLogger.sceneMissing(sceneName, phase)
            return null
        }

        val pageName = scene.page?.takeIf { it.isNotBlank() }
        if (pageName == null) {
            ContentBalanceLogger.pageMissing(sceneName, phase)
        }
        return pageName
    }
}

/**
 * 内容限制动作枚举，用于辅助日志输出含义。
 */
internal enum class ContentRestrictionPhase(val action: String) {
    Evaluate("evaluating content balance"),
    Record("recording content balance display")
}

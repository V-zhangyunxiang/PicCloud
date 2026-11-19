package com.poseidon.ad.special.topon

/**
 * TopOn 流量分组通用常量与数据模型，方便在不同组件间共享。
 */
internal const val TOPON_TRAFFIC_TAG = "TopOnTrafficGrouping"

/**
 * 原始归因信息，包含渠道与活动名称。
 */
internal data class AttributionPayload(
    val channel: String?,
    val campaign: String?
)

/**
 * 经过格式化的归因信息，适用于 TopOn 自定义参数。
 */
internal data class NormalizedAttribution(
    val channel: String?,
    val campaign: String?
)

/**
 * 将字符串转换为仅包含字母、数字与下划线的形式，空字符串则返回 null。
 */
internal fun String?.toAlphaNumeric(): String? {
    if (this.isNullOrEmpty()) {
        return null
    }
    val sanitized = replace(Regex("[^a-zA-Z0-9_]"), "")
    return sanitized.takeIf { it.isNotEmpty() }
}

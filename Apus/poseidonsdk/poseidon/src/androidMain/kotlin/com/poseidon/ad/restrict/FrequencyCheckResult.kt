package com.poseidon.ad.restrict

/**
 * 频控检查结果
 */
sealed class FrequencyCheckResult {
    /**
     * 允许展示广告
     */
    object Allow : FrequencyCheckResult() {
        override fun toString(): String = "Allow"
    }

    /**
     * 阻止展示广告
     * @param reason 阻止原因
     */
    data class Block(val reason: String) : FrequencyCheckResult() {
        override fun toString(): String = "Block($reason)"
    }
}
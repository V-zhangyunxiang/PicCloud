package com.poseidon.ad.restrict.ctr

import com.poseidon.ad.common.toolkit.logInfo

/**
 * CTR 限制日志工具，统一输出英文日志，便于追踪行为。
 */
internal object CtrLimitLogger {
    const val TAG = "CtrLimit"

    /**
     * 输出信息级别日志。
     */
    fun info(message: () -> String) {
        logInfo(TAG, message)
    }
}

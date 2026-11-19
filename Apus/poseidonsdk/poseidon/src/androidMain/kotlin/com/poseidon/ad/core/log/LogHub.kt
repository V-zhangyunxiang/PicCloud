package com.poseidon.ad.core.log

import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * LogHub standardizes logging so newly added modules have consistent format.
 * 日志中心统一英文+中文描述，便于问题排查。
 */
object LogHub {

    inline fun info(tag: String, crossinline messageProvider: () -> String) {
        logInfo(tag) { messageProvider() }
    }

    inline fun warn(tag: String, throwable: Throwable? = null, crossinline messageProvider: () -> String) {
        logWarn(throwable = throwable, tag = tag) { messageProvider() }
    }
}

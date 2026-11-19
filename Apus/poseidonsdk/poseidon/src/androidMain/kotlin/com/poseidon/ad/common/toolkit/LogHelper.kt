package com.poseidon.ad.common.toolkit

import com.poseidon.ad.BuildConfig
import io.github.aakira.napier.Napier

/**
 * 输出 INFO 级别日志，只在 Debug/QA 构建下生效，避免 Release 环境输出敏感信息。
 *
 * @param tag 业务模块的标签，会自动加上 poseidon 前缀方便定位来源。
 * @param lazyMessage 延迟求值的日志内容，只有满足条件时才会执行以避免额外开销。
 */
fun logInfo(tag: String, lazyMessage: () -> String) {
    if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
        Napier.i(message = lazyMessage, tag = "poseidon.${tag}")
    }
}

/**
 * 输出 WARN 级别日志，可附带异常堆栈，同样仅在 Debug/QA 构建时记录。
 *
 * @param throwable 可选的异常对象，便于定位问题。
 * @param tag 业务模块标签，统一加 poseidon 前缀。
 * @param lazyMessage 延迟执行的日志内容，避免在 Release 中产生无意义的字符串拼接。
 */
fun logWarn(throwable: Throwable? = null, tag: String, lazyMessage: () -> String) {
    if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
        Napier.w(throwable = throwable, message = lazyMessage, tag = "poseidon.${tag}")
    }
}

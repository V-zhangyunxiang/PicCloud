package com.poseidon.ad.common.toolkit

import android.os.SystemClock

/**
 * 时间与会话相关的工具集合。
 * 负责提供统一的时间戳、耗时计算、随机会话 ID 等方法。
 */
internal object TimeHelper {

    /**
     * 获取当前系统运行时间（毫秒），基于 SystemClock.elapsedRealtime。
     */
    fun currentTimeInMillis(): Long = SystemClock.elapsedRealtime()

    /**
     * 计算从 startTimeInMillis 到当前的耗时。
     */
    fun take(startTimeInMillis: Long): Long = currentTimeInMillis() - startTimeInMillis
}

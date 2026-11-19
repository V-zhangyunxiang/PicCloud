package com.poseidon.ad.common.toolkit

import com.poseidon.ad.common.toolkit.TimeHelper.currentTimeInMillis
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

/**
 * 字符串与 JSON 工具。
 */
internal object StringHelper {

    /**
     * 生成包含 UUID、时间戳、随机浮点数的会话 ID，便于日志跟踪。
     */
    fun newSessionId(): String = "${randomUUID()}&&${currentTimeInMillis()}&&${Random.nextFloat()}"

    /**
     * 生成随机 UUID。
     */
    fun randomUUID(): String = UUID.randomUUID().toString()
}

/**
 * Json 实例，默认忽略未知字段。
 */
@Suppress("SpellCheckingInspection")
internal val PoseidonJson: Json by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    Json {
        ignoreUnknownKeys = true
    }
}

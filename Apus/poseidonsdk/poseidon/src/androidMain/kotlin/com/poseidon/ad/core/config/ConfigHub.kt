package com.poseidon.ad.core.config

import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.restrict.traffic.TrafficLimit
import org.json.JSONObject

/**
 * ConfigHub centralizes configuration decoding and persistence.
 * ConfigHub 负责统一的配置解析与落地，避免在各个 API 外壳中重复实现。
 */
object ConfigHub {

    /**
     * Parse the backend json payload and update local configuration.
     * 解析服务端下发的 JSON，并写入本地配置。调用方仍由旧 API 触发以保持外观一致。
     */
    fun updateFromBackend(json: String?) {
        if (json.isNullOrEmpty()) {
            if (TrafficLimit.isPassable(
                    remoteKey = "poseidonConfigFromBackendParseError_error_detection",
                    defaultScale = 30
                )
            ) {
                ErrorDetectionReporter(
                    name = "poseidonConfigFromBackendParseError",
                    extra = "json is null or empty"
                ).analyze()
            }
            return
        }
        val encryptedConfig = resolveEncryptedConfig(json = json)
        if (encryptedConfig.isNullOrEmpty()) {
            if (TrafficLimit.isPassable(
                    remoteKey = "poseidonConfigFromBackendParseError_error_detection",
                    defaultScale = 30
                )
            ) {
                ErrorDetectionReporter(
                    name = "poseidonConfigFromBackendParseError",
                    extra = "encryptedPoseidonConfigString is null or empty: json=$json"
                ).analyze()
            }
            return
        }
        PoseidonConfig.updateFromBackend(encryptedPoseidonConfigString = encryptedConfig)
        LogHub.info("ConfigHub") { "updateFromBackend: configuration refreshed successfully | 配置刷新成功" }
    }

    /**
     * Extract the encrypted config string from backend payload.
     * 从服务端 JSON 中提取密文配置字符串，结构变化集中于本函数中。
     */
    fun resolveEncryptedConfig(json: String): String? {
        return runCatching {
            JSONObject(json)
                .getJSONObject("commercialization")
                .getJSONObject("poseidon_mediation")
                .getString("config")
        }.onFailure { throwable ->
            ErrorDetectionReporter(
                name = "poseidonConfigFromBackendParseError",
                extra = throwable.message.orEmpty()
            ).analyze()
        }.getOrNull()
    }
}

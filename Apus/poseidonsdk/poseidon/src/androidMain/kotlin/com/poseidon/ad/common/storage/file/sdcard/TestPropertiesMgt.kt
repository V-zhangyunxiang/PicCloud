package com.poseidon.ad.common.storage.file.sdcard

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.strategy.common.getLocalConfiguration
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * Debug 模式测试配置管理器（实时读取版），负责从外部存储读取调试所需的 key。
 */
internal object TestPropertiesMgt {

    private const val TAG = "TestPropertiesMgt"
    private const val TEST_KEYS_FILE_NAME = "poseidon_test_keys.properties"
    private const val CHANNEL_KEY = "channel"
    private const val CAMPAIGN_KEY = "campaign"

    // 平台提供者，方便未来替换或注入。
    private val platformProvider = { PoseidonSetupBaseProcessor.poseidonSetupProcessor() }
    // 实时读取器，每次调用都从文件系统拉取最新内容。
    private val loader = RealtimePropertiesLoader(TEST_KEYS_FILE_NAME)

    /**
     * 获取测试 channel，仅 Debug 模式有效。
     */
    fun getTestChannel(): String? = readWhenDebug(CHANNEL_KEY)

    /**
     * 获取测试 campaign，仅 Debug 模式有效。
     */
    fun getTestCampaign(): String? = readWhenDebug(CAMPAIGN_KEY)

    /**
     * Debug 模式下读取目标 key，非 Debug 直接返回 null。
     */
    private fun readWhenDebug(key: String): String? {
        return if (platformProvider().debug) {
            loader.readPropertyValue(key)
        } else {
            null
        }
    }

    /**
     * 负责实时拉取并解析 properties 文件的执行器。
     */
    private class RealtimePropertiesLoader(private val fileName: String) {

        /**
         * 读取指定 key，对外暴露统一的异常与日志处理。
         */
        fun readPropertyValue(key: String): String? {
            return runCatching {
                val content = loadProperties() ?: return null
                locatePropertyValue(content, key)
            }.onFailure { throwable ->
                logWarn(throwable = throwable, tag = TAG) {
                    "Failed to read test property '$key': ${throwable.message}"
                }
            }.getOrNull()
        }

        /**
         * 从外部存储加载完整内容，为 null/空串时直接返回 null。
         */
        private fun loadProperties(): String? {
            val properties = getLocalConfiguration(fileName)
            return if (properties.isNullOrEmpty()) null else properties
        }

        /**
         * 遍历文本内容，寻找目标 key 对应的 value。
         */
        private fun locatePropertyValue(content: String, key: String): String? {
            val prefix = "$key="
            return content.lineSequence()
                .map { it.trim() }
                .filterNot { it.isEmpty() || it.startsWith("#") || it.startsWith("!") }
                .firstNotNullOfOrNull { line ->
                    if (!line.startsWith(prefix)) return@firstNotNullOfOrNull null
                    val value = line.substring(prefix.length).trim()
                    if (value.isNotBlank()) {
                        logInfo(TAG) { "Read test property: $key=$value" }
                        value
                    } else {
                        null
                    }
                }
        }
    }
}

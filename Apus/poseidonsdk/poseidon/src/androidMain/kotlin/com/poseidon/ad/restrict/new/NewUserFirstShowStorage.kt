package com.poseidon.ad.restrict.new

import kotlinx.serialization.encodeToString
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 新用户首显本地存储访问层，封装 SharedPreferences 读写与集合序列化。
 */
internal object NewUserFirstShowStorage {

    /**
     * 判断首次冷启动是否已经完成。
     */
    fun isFirstColdStartCompleted(): Boolean {
        return getBoolean(SP_FIRST_START_FINISH, false)
    }

    /**
     * 记录首次冷启动已完成。
     */
    fun markFirstColdStartCompleted() {
        saveBoolean(SP_FIRST_START_FINISH, true)
    }

    /**
     * 获取旧版 first_show 应用标记。
     */
    fun isLegacyFirstShowApplied(): Boolean {
        return getBoolean(SP_FIRST_SHOW_APPLY, false)
    }

    /**
     * 更新旧版 first_show 应用标记。
     */
    fun updateLegacyFirstShowApplied(value: Boolean) {
        saveBoolean(SP_FIRST_SHOW_APPLY, value)
    }

    /**
     * 保存需要触发首显的场景集合。
     */
    fun saveTargetSceneSet(set: Set<String>) {
        val keyProvider = keyProvider(SP_FIRST_SHOW_SCENES)
        val json = PoseidonJson.encodeToString(set.toList())
        PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference.putString(keyProvider, json)
    }

    /**
     * 读取需要触发首显的场景集合。
     */
    fun loadTargetSceneSet(): Set<String> {
        val keyProvider = keyProvider(SP_FIRST_SHOW_SCENES)
        val json = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference.getString(keyProvider, "[]") ?: "[]"
        return runCatching {
            PoseidonJson.decodeFromString<List<String>>(json).toSet()
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to parse target scene set: ${throwable.message}" }
        }.getOrDefault(emptySet())
    }

    /**
     * 读取已应用首显限制的场景集合，自动迁移旧版状态。
     */
    fun loadAppliedSceneSet(): MutableSet<String> {
        migrateLegacyAppliedStateIfNeeded()
        return readAppliedSceneSet()
    }

    /**
     * 保存已应用首显限制的场景集合。
     */
    fun saveAppliedSceneSet(set: Set<String>) {
        val keyProvider = keyProvider(SP_FIRST_SHOW_APPLY_SCENES)
        val json = PoseidonJson.encodeToString(set.toList())
        PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference.putString(keyProvider, json)
    }

    /**
     * 清理全部首显相关状态，用于测试。
     */
    fun clearAllStoredState(): Boolean {
        return runCatching {
            val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
            val keyProvider1 = keyProvider(SP_FIRST_START_FINISH)
            val keyProvider2 = keyProvider(SP_FIRST_SHOW_APPLY)
            val keyProvider3 = keyProvider(SP_FIRST_SHOW_SCENES)
            val keyProvider4 = keyProvider(SP_FIRST_SHOW_APPLY_SCENES)
            sharedPreference.putBoolean(keyProvider1, false)
            sharedPreference.putBoolean(keyProvider2, false)
            sharedPreference.putString(keyProvider3, "[]")
            sharedPreference.putString(keyProvider4, "[]")
            logInfo(NEW_USER_FIRST_SHOW_TAG) { "Cleared all state for test" }
            true
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to clear state for test: ${throwable.message}" }
        }.getOrDefault(false)
    }

    private fun migrateLegacyAppliedStateIfNeeded() {
        if (isLegacyFirstShowApplied()) {
            val applied = readAppliedSceneSet()
            if (applied.add("__LEGACY__")) {
                saveAppliedSceneSet(applied)
            }
            updateLegacyFirstShowApplied(false)
            logInfo(NEW_USER_FIRST_SHOW_TAG) { "Migrated legacy first show applied flag" }
        }
    }

    private fun readAppliedSceneSet(): MutableSet<String> {
        val keyProvider = keyProvider(SP_FIRST_SHOW_APPLY_SCENES)
        val json = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference.getString(keyProvider, "[]") ?: "[]"
        return runCatching {
            PoseidonJson.decodeFromString<List<String>>(json).toMutableSet()
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to parse applied scene set: ${throwable.message}" }
        }.getOrDefault(mutableSetOf())
    }

    private fun saveBoolean(key: String, value: Boolean) {
        runCatching {
            val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
            val keyProvider = keyProvider(key)
            sharedPreference.putBoolean(keyProvider, value)
            logInfo(NEW_USER_FIRST_SHOW_TAG) { "Saved preference: $key = $value" }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to save boolean preference $key: ${throwable.message}" }
        }
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return runCatching {
            val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
            val keyProvider = keyProvider(key)
            sharedPreference.getBoolean(keyProvider, defaultValue).also { value ->
                logInfo(NEW_USER_FIRST_SHOW_TAG) { "Got preference: $key = $value (default: $defaultValue)" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                NEW_USER_FIRST_SHOW_TAG
            ) { "Failed to get boolean preference $key: ${throwable.message}" }
        }.getOrDefault(defaultValue)
    }

    private fun keyProvider(key: String): KeyProvider {
        return KeyProvider { "${SP_NEW_USER_FIRST_SHOW}_$key" }
    }
}

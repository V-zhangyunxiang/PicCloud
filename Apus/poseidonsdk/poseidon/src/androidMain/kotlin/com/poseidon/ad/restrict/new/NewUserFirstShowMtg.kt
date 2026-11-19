package com.poseidon.ad.restrict.new

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 新用户首次冷启动状态管理器。
 * 负责协调首显配置、会话态与持久化数据，提供外部可用的首显限制能力。
 */
internal object NewUserFirstShowMtg {
    private const val TAG = NEW_USER_FIRST_SHOW_TAG
    private val sessionFirstColdStart = atomic<Boolean?>(null)
    private val sessionStateInitLock = reentrantLock()

    /**
     * 提供当前会话是否属于新用户首次冷启动。
     */
    fun isNewUserFirstColdStart(): Boolean {
        return sessionFirstColdStart.value ?: ensureSessionStateInitialized()
    }

    /**
     * 确保会话内的首冷启动状态已经初始化，并立即将持久化状态标记为完成。
     */
    fun ensureSessionStateInitialized(): Boolean {
        sessionFirstColdStart.value?.let { return it }

        return sessionStateInitLock.withLock {
            sessionFirstColdStart.value?.let { return@withLock it }

            val isFirstColdStart = runCatching {
                if (!NewUserInstallInfoProvider.isNewUser()) {
                    logInfo(TAG) { "Not a new user (install_time != update_time)" }
                    return@runCatching false
                }
                if (NewUserFirstShowStorage.isFirstColdStartCompleted()) {
                    logInfo(TAG) { "First cold start already completed" }
                    return@runCatching false
                }
                if (NewUserFirstShowStorage.isLegacyFirstShowApplied()) {
                    logInfo(TAG) { "Legacy first show already applied" }
                    return@runCatching false
                }
                logInfo(TAG) { "New user first cold start detected" }
                true
            }.onFailure { throwable ->
                logWarn(
                    throwable,
                    TAG
                ) { "Failed to initialize session state: ${throwable.message}" }
            }.getOrDefault(false)

            sessionFirstColdStart.value = isFirstColdStart

            if (isFirstColdStart && !NewUserFirstShowStorage.isFirstColdStartCompleted()) {
                NewUserFirstShowStorage.markFirstColdStartCompleted()
                logInfo(TAG) { "Marked first cold start completed immediately after initialization" }
            }
            isFirstColdStart
        }
    }

    /**
     * 更新需要进行 first_show 控制的场景集合（first_show=false）。
     */
    fun updateTargetScenes(scenes: List<SceneCfg>?) {
        runCatching {
            val targetScenes = scenes.orEmpty()
                .filterNot { it.firstShow }
                .map { it.name }
                .toSet()
            NewUserFirstShowStorage.saveTargetSceneSet(targetScenes)
            logInfo(TAG) { "Updated first show target scenes: $targetScenes" }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to update target scenes: ${throwable.message}" }
        }
    }

    /**
     * 检查某个场景在首冷启动期间是否已经允许展示。
     */
    fun isSceneFirstShowApplied(sceneName: String): Boolean {
        if (!ensureSessionStateInitialized()) {
            return true
        }
        return NewUserFirstShowStorage.loadAppliedSceneSet().contains(sceneName)
    }

    /**
     * 标记某个场景的 first_show 已经被应用。
     */
    fun markSceneFirstShowApplied(sceneName: String) {
        runCatching {
            if (!ensureSessionStateInitialized()) {
                logInfo(TAG) { "Skip marking scene first show - not first cold start" }
                return@runCatching
            }
            val appliedSet = NewUserFirstShowStorage.loadAppliedSceneSet()
            if (appliedSet.add(sceneName)) {
                NewUserFirstShowStorage.saveAppliedSceneSet(appliedSet)
                logInfo(TAG) { "Marked first show applied for scene: $sceneName" }
            } else {
                logInfo(TAG) { "Scene first show already applied: $sceneName" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to mark scene first show applied: ${throwable.message}" }
        }
    }

    /**
     * 标记首次冷启动已完成。
     */
    fun markFirstColdStartCompleted() {
        sessionFirstColdStart.value = sessionFirstColdStart.value ?: false
        runCatching {
            if (isNewUserFirstColdStart()) {
                NewUserFirstShowStorage.markFirstColdStartCompleted()
                logInfo(TAG) { "Marked first cold start as completed" }
            } else {
                logInfo(TAG) { "Skip marking first cold start completed - not first cold start" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to mark first cold start completed: ${throwable.message}" }
        }
    }

    internal fun clearAllStateForTest() {
        val cleared = NewUserFirstShowStorage.clearAllStoredState()
        if (cleared) {
            sessionFirstColdStart.value = null
        }
    }

    /**
     * 检索调试信息，便于记录当前首显状态。
     */
    internal fun getDebugInfo(): String {
        val appliedScenes = NewUserFirstShowStorage.loadAppliedSceneSet()
        val targetScenes = NewUserFirstShowStorage.loadTargetSceneSet()
        val sessionState = sessionFirstColdStart.value
        return """
            NewUserFirstShowManager Debug Info:
            - sessionFirstColdStart: $sessionState
            - firstColdStartCompleted: ${NewUserFirstShowStorage.isFirstColdStartCompleted()}
            - legacyFirstShowApplied: ${NewUserFirstShowStorage.isLegacyFirstShowApplied()}
            - targetScenes: $targetScenes
            - appliedScenes: $appliedScenes
        """.trimIndent()
    }
}

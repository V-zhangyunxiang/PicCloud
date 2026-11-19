package com.poseidon.ad.core.processor

import com.poseidon.ad.common.toolkit.CompletionListener
import com.poseidon.ad.common.toolkit.TaskScheduler
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.currentThreadName
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.statistics.core.BizAdInitTracker

/**
 * 媒体渠道初始化流程协调器，统一封装日志与上报。
 */
abstract class MediationSetup : TaskScheduler<Unit, String>() {

    /**
     * 启动初始化流程，外部调用入口。
     */
    fun init(callback: MediationSetupCallback) {
        logInfo(TAG) {
            "init: callback=$callback, thread=${currentThreadName()}"
        }
        handleCompletion(createCompletionListener(callback))
    }

    @MediationDef
    protected abstract fun getMediationId(): Int

    final override fun execute(listener: CompletionListener<Unit, String>) {
        logExecuteInvocation(listener)
        MediationInitDispatcher.dispatch {
            val initContext = InitContext(
                startedAt = TimeHelper.currentTimeInMillis(),
                listener = listener
            )
            val callback = createMediationCallback(initContext)
            runCatching {
                initMediation(callback)
            }.onFailure { throwable ->
                callback.onFailure(
                    throwable.message ?: "${throwable.javaClass.simpleName} during mediation initialization"
                )
            }
        }
    }

    protected abstract fun initMediation(callback: MediationSetupCallback)

    /**
     * 记录 execute 调用信息。
     */
    private fun logExecuteInvocation(listener: CompletionListener<Unit, String>) {
        logInfo(TAG) {
            "execute: listener=$listener, thread=${currentThreadName()}"
        }
    }

    /**
     * 为 init 构建 CompletionListener，屏蔽重复代码。
     */
    private fun createCompletionListener(callback: MediationSetupCallback): CompletionListener<Unit, String> {
        return object : CompletionListener<Unit, String> {
            override fun onSuccess(data: Unit) {
                logInfo(TAG) {
                    "init.onSuccess: thread=${currentThreadName()}"
                }
                callback.onSuccess()
            }

            override fun onFailure(data: String) {
                logInfo(TAG) {
                    "init.onFailure: data=$data, thread=${currentThreadName()}"
                }
                callback.onFailure(data)
            }
        }
    }

    /**
     * 构建媒体初始化回调，负责打点与监听回传。
     */
    private fun createMediationCallback(telemetry: InitContext): MediationSetupCallback {
        return object : MediationSetupCallback {
            override fun onSuccess() {
                telemetry.reportSuccess()
                telemetry.listener.onSuccess(Unit)
            }

            override fun onFailure(message: String) {
                telemetry.reportFailure(message)
                telemetry.listener.onFailure(message)
            }
        }
    }

    /**
     * 初始化耗时、监听等上下文。
     */
    private inner class InitContext(
        val startedAt: Long,
        val listener: CompletionListener<Unit, String>
    ) {
        fun reportSuccess() {
            reportInitResult(result = "success")
        }

        fun reportFailure(message: String) {
            reportInitResult(result = message)
        }

        private fun reportInitResult(result: String) {
            BizAdInitTracker(
                mediationId = getMediationId(),
                result = result,
                take = TimeHelper.take(startedAt)
            ).analyze()
        }
    }

    companion object {
        private const val TAG = "NetworkInitializer"
    }
}

interface MediationSetupCallback {
    fun onSuccess()
    fun onFailure(message: String)

    companion object {
        val Empty: MediationSetupCallback = object : MediationSetupCallback {
            override fun onSuccess() {
            }

            override fun onFailure(message: String) {
            }
        }
    }
}

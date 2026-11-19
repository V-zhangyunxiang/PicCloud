package com.poseidon.ad.restrict.ctr

import android.app.Activity
import com.poseidon.ad.main.adinfo.ActivityLifecycleCallbacks
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import java.lang.ref.WeakReference

/**
 * CTR 活动追踪器，保存广告点击/展示对应的前台 Activity。
 */
internal object CtrActivityTracker {
    private val sessionActivityMap = mutableMapOf<String, WeakReference<Activity>>()
    private val lock = ReentrantLock()

    /**
     * 在曝光时记录当前 Activity。
     */
    fun recordImpression(sessionId: String) {
        lock.withLock {
            val activity = ActivityLifecycleCallbacks.topActivity()
            sessionActivityMap[sessionId] = WeakReference(activity)
            CtrLimitLogger.info {
                "callOnAdImpression: sessionId=$sessionId, activity=$activity, sessionIdToResumedActivityWeakReferenceMapSize=${sessionActivityMap.size}"
            }
        }
    }

    /**
     * 在关闭时清理关联 Activity。
     */
    fun clearSession(sessionId: String) {
        lock.withLock {
            sessionActivityMap.remove(sessionId)?.clear()
            CtrLimitLogger.info {
                "callOnAdImpression: sessionId=$sessionId, sessionIdToResumedActivityWeakReferenceMapSize=${sessionActivityMap.size}"
            }
        }
    }

    /**
     * 读取曝光时记录的 Activity。
     */
    fun getImpressionActivity(sessionId: String): Activity? {
        return lock.withLock {
            sessionActivityMap[sessionId]?.get()
        }
    }
}

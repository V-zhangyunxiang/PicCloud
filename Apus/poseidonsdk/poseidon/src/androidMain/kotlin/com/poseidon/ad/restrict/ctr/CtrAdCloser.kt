package com.poseidon.ad.restrict.ctr

import android.app.Activity
import com.google.android.gms.ads.AdActivity
import com.poseidon.ad.main.adinfo.ActivityLifecycleCallbacks
import com.poseidon.ad.statistics.biz.CtrLimitReporter

/**
 * CTR 广告关闭执行器，负责根据配置判断并关闭目标 Activity。
 */
internal object CtrAdCloser {

    /**
     * 尝试关闭相关广告 Activity。
     */
    fun close(sessionId: String, onAdForceClosed: () -> Unit) {
        if (!CtrRemoteConfigProvider.forceCloseEnabled()) {
            return
        }

        val activityPartialNames = CtrRemoteConfigProvider.activityPartialNames()
            .ifEmpty { listOf(AdActivity.CLASS_NAME, "com.facebook.ads", "com.mbridge.msdk") }

        val resumedActivityWhenAdClicked = ActivityLifecycleCallbacks.topActivity()
        val resumedActivityWhenImpression = CtrActivityTracker.getImpressionActivity(sessionId)
        val waitForClosedActivity = mutableSetOf<Activity>()
        val forceClose = activityPartialNames.any { name ->
            val inClicked = resumedActivityWhenAdClicked?.classNameContains(name)?.also { contains ->
                if (contains) {
                    waitForClosedActivity.add(resumedActivityWhenAdClicked)
                }
            } ?: false
            val inImpression = resumedActivityWhenImpression?.classNameContains(name)?.also { contains ->
                if (contains) {
                    waitForClosedActivity.add(resumedActivityWhenImpression)
                }
            } ?: false
            inClicked || inImpression
        }

        CtrLimitReporter(
            forceClose = forceClose,
            resumedActivityNameWhenAdClicked = resumedActivityWhenAdClicked?.javaClass?.name,
            resumedActivityNameWhenAdImpression = resumedActivityWhenImpression?.javaClass?.name,
            waitForClosedActivityNames = waitForClosedActivity.joinToString("|") { it.javaClass.name },
            activityPartialNameFromRemoteConfig = CtrRemoteConfigProvider.activityPartialNameRawForLog()
        ).analyze()

        if (forceClose) {
            waitForClosedActivity.forEach { it.finish() }
            onAdForceClosed()
        }
    }

    private fun Activity.classNameContains(partial: String): Boolean {
        return this::class.java.name.contains(partial)
    }
}

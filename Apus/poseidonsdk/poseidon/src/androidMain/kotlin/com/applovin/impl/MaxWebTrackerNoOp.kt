package com.applovin.impl

import com.applovin.impl.sdk.l
import com.applovin.impl.sdk.network.e
import com.applovin.sdk.AppLovinPostbackListener

/**
 * No-op replacement for MAX's internal WebTrackerManager ([com.applovin.impl.r8]).
 *
 * AppLovin MAX creates a real [android.webkit.WebView] on the main thread to fire
 * JavaScript postbacks and JS trackers. On contended main threads this triggers
 * Chromium's Privacy Sandbox / attribution work and has been observed to ANR
 * (stack: `org.chromium.content.browser.AttributionOsLevelManager`).
 *
 * This stub overrides the three public entry points used by the SDK so that:
 * - the init-time WebView preload is skipped,
 * - web-based postbacks are reported as successful (so callers do not retry),
 * - native-ad JS trackers are dropped.
 *
 * The superclass is tied to AppLovin 13.6.4 and must be checked on SDK upgrades.
 */
internal class MaxWebTrackerNoOp(coreSdk: l) : r8(coreSdk) {

    /**
     * Preload path: `r8.b()` -> do nothing.
     */
    override fun b() {
        // Intentionally empty: prevents the main-thread WebView creation at MAX init.
    }

    /**
     * Postback path: `r8.a(e, AppLovinPostbackListener)` -> report success.
     */
    override fun a(request: e, listener: AppLovinPostbackListener) {
        // The postback is not actually fired, but we must tell the listener it succeeded
        // so MAX does not keep retrying / logging failures for every event.
        runCatching { listener.onPostbackSuccess(request.f()) }
    }

    /**
     * JS tracker path: `r8.b(String)` -> do nothing.
     */
    override fun b(script: String) {
        // Intentionally empty: prevents one-off WebView creation for native-ad JS trackers.
    }
}

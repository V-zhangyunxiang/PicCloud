package com.poseidon.ad.mediation.max.init

import android.content.Context
import com.applovin.impl.MaxWebTrackerNoOp
import com.applovin.impl.sdk.l
import com.applovin.sdk.AppLovinSdk
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import java.util.concurrent.atomic.AtomicReference

/**
 * Guard against the AppLovin/MAX init-time ANR caused by creating a WebView on the
 * main thread for JS postbacks.
 *
 * Two-layer mitigation:
 * 1. Seed AppLovin's own SharedPreferences with `preload_postback_webview=false` before
 *    the SDK reads them, so the init-time preload is skipped even if the server has
 *    previously enabled it.
 * 2. Reflectively replace MAX's internal `WebTrackerManager` with a no-op stub after
 *    `AppLovinSdk.getInstance(...)` but before `initialize(...)`, so any later code path
 *    that would create a WebView for postbacks/JS trackers is short-circuited.
 *
 * The guard is controlled by the Firebase Remote Config / Poseidon cloud key
 * `poseidon_max_web_tracker_guard_disable`. It defaults to **enabled** (false), so the
 * ANR mitigation is active unless explicitly disabled from the cloud.
 */
internal object MaxWebTrackerGuard {

    private const val TAG = "MaxWebTrackerGuard"

    /** Remote kill-switch: when true the guard is disabled and MAX behaves normally. */
    private const val REMOTE_DISABLE_KEY = "poseidon_max_web_tracker_guard_disable"

    private const val APPLOVIN_PREFS_NAME = "com.applovin.sdk.1"
    private const val APPLOVIN_SHARED_PREFS_NAME = "com.applovin.sdk.shared"
    private const val PRELOAD_SETTING_KEY = "preload_postback_webview"
    private const val AGNOSTIC_SETTING_KEY = "com.applovin.sdk.save_settings_with_sdk_key_agnostic_name"

    /**
     * Returns whether the guard should be installed.
     *
     * Defaults to `true` when the remote config value is missing or unreadable, so fresh
     * installs and remote-config fetch failures are still protected.
     */
    fun isEnabled(): Boolean {
        return runCatching {
            val remoteConfig = PoseidonSetupProcessor.Companion.getInstance()
                .remoteConfig
            remoteConfig.getBoolean(REMOTE_DISABLE_KEY)
        }.getOrElse {
            logWarn(null,TAG) { "Unable to read remote kill-switch, defaulting guard to enabled" }
            false
        }
    }

    /**
     * Writes `preload_postback_webview=false` into AppLovin's settings SharedPreferences
     * before the SDK instantiates its settings manager.
     *
     * Must be called before the first [AppLovinSdk.getInstance] call of the process.
     */
    fun prepare(context: Context, sdkKey: String) {
        runCatching {
            val prefs = context.getSharedPreferences(APPLOVIN_PREFS_NAME, Context.MODE_PRIVATE)
            val sharedPrefs = context.getSharedPreferences(
                APPLOVIN_SHARED_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            sharedPrefs.edit()
                .putBoolean(AGNOSTIC_SETTING_KEY, true)
                .apply()

            prefs.edit().apply {
                // Disable the init-time WebView preload.
                putBoolean(PRELOAD_SETTING_KEY, false)
                // Also write the prefixed variant as a defensive fallback.
                val suffix = if (sdkKey.length > 4) sdkKey.substring(sdkKey.length - 4) else sdkKey
                putBoolean("com.applovin.sdk.$suffix.$PRELOAD_SETTING_KEY", false)
                apply()
            }
            logInfo(TAG) { "Seeded AppLovin settings to disable postback WebView preload" }
        }.onFailure {
            logWarn(tag = TAG) { "Failed to seed AppLovin settings: ${it.message}" }
        }
    }

    /**
     * Replaces MAX's internal `WebTrackerManager` (held in `com.applovin.impl.sdk.l.M`)
     * with a no-op stub.
     *
     * Must be called after [AppLovinSdk.getInstance] but before [AppLovinSdk.initialize].
     */
    fun installNoOpTrackerManager(sdk: AppLovinSdk): Boolean {
        return runCatching {
            val coreSdkField = AppLovinSdk::class.java
                .getDeclaredField("coreSdk")
                .apply { isAccessible = true }
            val coreSdk = coreSdkField.get(sdk) as l

            val sdkClass = Class.forName("com.applovin.impl.sdk.l")
            val webTrackerField = sdkClass
                .getDeclaredField("M")
                .apply { isAccessible = true }
            val atomicRef = checkNotNull(webTrackerField.get(coreSdk) as? AtomicReference<Any?>) {
                "Unexpected AppLovin WebTrackerManager holder"
            }

            atomicRef.set(MaxWebTrackerNoOp(coreSdk))
            logInfo(TAG) { "Installed no-op WebTrackerManager" }
            true
        }.getOrElse {
            logWarn(tag = TAG) { "Failed to install no-op WebTrackerManager: ${it.message}" }
            false
        }
    }
}

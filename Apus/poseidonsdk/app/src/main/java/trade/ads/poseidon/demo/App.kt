package trade.ads.poseidon.demo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import io.threadtamer.runtime.ThreadTamer
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAttribution
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import com.google.firebase.FirebaseApp
import com.poseidon.ad.special.topon.TopOnTrafficGrouping
import com.poseidon.ad.api.PoseidonAPI
import com.poseidon.ad.api.PoseidonSetupProcessor

/**
 * Demo 应用入口，负责初始化广告 SDK 及第三方依赖。
 */
class App : Application() {

    /**
     * 进程创建时初始化 Firebase、Adjust 以及 Poseidon 的其他能力。
     */
    override fun onCreate() {
        super.onCreate()
        ThreadTamer.install()
        FirebaseApp.initializeApp(this)
        val appToken = "{YourAppToken}"
        val environment = AdjustConfig.ENVIRONMENT_SANDBOX
        val config = AdjustConfig(this, appToken, environment, false)
        config.setLogLevel(LogLevel.DEBUG)
        config.setOnAttributionChangedListener { attribution: AdjustAttribution? ->
            TopOnTrafficGrouping.callOnAttributionChanged(attribution)
        }
        Adjust.initSdk(config)
        val poseidonInitConfig = PoseidonSetupProcessor(
            application = this,
            mainActivityClass = MainActivity::class.java,
        )
        PoseidonAPI.register(poseidonInitConfig)
        PoseidonAPI.initialize()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.i(TAG, "onActivityCreated: $activity")
            }

            override fun onActivityStarted(activity: Activity) {
                Log.i(TAG, "onActivityStarted: $activity")
            }

            override fun onActivityResumed(activity: Activity) {
                Log.i(TAG, "onActivityResumed: $activity")
            }

            override fun onActivityPaused(activity: Activity) {
                Log.i(TAG, "onActivityPaused: $activity")
            }

            override fun onActivityStopped(activity: Activity) {
                Log.i(TAG, "onActivityStopped: $activity")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                Log.i(TAG, "onActivitySaveInstanceState: $activity")
            }

            override fun onActivityDestroyed(activity: Activity) {
                Log.i(TAG, "onActivityDestroyed: $activity")
            }
        })
    }

    companion object {
        /** 日志 TAG，方便排查生命周期回调。 */
        const val TAG = "App"
    }
}

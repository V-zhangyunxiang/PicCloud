package com.poseidon.ad.main.adinfo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.main.preload.share.startAutomation
import com.poseidon.ad.main.preload.special.SpecificAdSceneCacheBackup
import com.poseidon.ad.restrict.gdpr.ConsentManagementPlatform
import com.poseidon.ad.restrict.gdpr.PlatformConsentFormShowParameter
import java.util.Observable
import java.util.Observer
import java.util.Stack

internal object ActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks,
    AnyActivityResumedObservable() {

    private var startedActivityCount: Int = 0


    private var isActivityStarted: Boolean = false

    private var isActivityPaused: Boolean = true

    private val activities: Stack<Activity> = Stack()


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        SpecificAdSceneCacheBackup.preload()
        startAutomation()
        activities.add(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        val activityNotStarted = !isActivityStarted
        isActivityStarted = true
        if (activityNotStarted) {
            PoseidonSetupProcessor.isAppInForeground = true
        }
    }

    override fun onActivityResumed(activity: Activity) {
        isActivityPaused = false
        notifyAnyActivityResumed(activity)
        if (PoseidonSetupProcessor.getInstance().mainActivityClass == activity::class.java) {
            ConsentManagementPlatform.showConsentForm(parameter = PlatformConsentFormShowParameter(
                activity = activity
            )
            )
        }
    }

    override fun onActivityPaused(activity: Activity) {
        isActivityPaused = true
    }

    override fun onActivityStopped(activity: Activity) {
        val nowStartedActivityCount = startedActivityCount - 1
        startedActivityCount = nowStartedActivityCount
        if (nowStartedActivityCount == 0 && isActivityStarted && isActivityPaused) {
            isActivityStarted = false
            PoseidonSetupProcessor.isAppInForeground = false
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        activities.remove(activity)
    }

    fun topActivity(): Activity? {
        return if (activities.isEmpty()) {
            null
        } else {
            activities.lastElement()
        }
    }
}

@Suppress("DEPRECATION")
internal open class AnyActivityResumedObservable : Observable() {

    fun notifyAnyActivityResumed(activity: Activity) {
        setChanged()
        notifyObservers(activity)
    }

    fun observeAnyActivityResumed(action: (Activity) -> Unit) {
        addObserver(object : Observer {
            @Deprecated("Deprecated in Java")
            override fun update(o: Observable, arg: Any) {
                o.deleteObserver(this)
                action(arg as Activity)
            }
        })
    }
}

package com.poseidon.ad.restrict.gdpr

import android.app.Activity
import com.thinkup.core.api.TUSDK
import com.thinkup.core.api.NetTrafficeCallback
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.poseidon.ad.BuildConfig
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.statistics.biz.ConsentManagementPlatformAnalyzer
import com.poseidon.ad.common.toolkit.CompletionListener
import com.poseidon.ad.common.toolkit.TaskScheduler
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.main.adinfo.ActivityLifecycleCallbacks
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.runOnMainThread
import kotlinx.atomicfu.atomic
import kotlin.Result

/**
 * GDPR 同意管理入口，负责调度 consent 表单、地理判定与各第三方 SDK。
 */
internal object ConsentManagementPlatform : TaskScheduler<Boolean, Throwable>() {

    private const val TAG = "ConsentManagementPlatform"
    private val appStartupPosted = atomic(false)
    private val stateStore = ConsentFormStateStore()
    private val consentFlow = ConsentFlowCoordinator(
        tag = TAG,
        stateStore = stateStore,
        canRequestAds = { canRequestAds() },
        loadConsentFormAction = { success, failure ->
            loadConsentForm(
                onConsentFormLoadSuccess = success,
                onConsentFormLoadFailure = failure
            )
        },
        loadAndShowConsentFormAction = { parameter, dismissed ->
            loadAndShowConsentFormIfRequired(
                parameter = parameter,
                onConsentFormDismissed = dismissed
            )
        },
        showPrivacyOptionsAction = { parameter, dismissed ->
            showPrivacyOptionsForm(
                parameter = parameter,
                onConsentFormDismissed = dismissed
            )
        }
    )
    private val europeGate = ConsentGeographyGate(
        tag = TAG,
        sharedPreferenceProvider = { PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference },
        completionHandler = { listener -> handleCompletion(listener) }
    )

    internal fun appStartup() {
        logInfo(TAG) { "[appStartup] trigger!" }
        if (!appStartupPosted.compareAndSet(expect = false, update = true)) {
            logInfo(TAG) { "[appStartup] already posted!" }
            return
        }
        warmConsentInformation()
        dispatchToBackground {
            europeGate.runIfNeeded {
                logInfo(TAG) { "[appStartup] in Europe!" }
                consentFlow.handleAppStartup()
            }
        }
    }

    internal fun showConsentForm(parameter: PlatformConsentFormShowParameter) = runOnMainThread {
        logInfo(TAG) { "[showConsentForm] trigger!" }
        europeGate.runIfNeeded {
            logInfo(TAG) { "[showConsentForm] in Europe!" }
            consentFlow.handleShowConsentForm(parameter)
        }
    }

    internal fun doOnPrivacyOptionsIsRequired(action: () -> Unit) {
        logInfo(TAG) { "[doOnPrivacyOptionsIsRequired] trigger!" }
        europeGate.runIfNeeded {
            consentFlow.handlePrivacyRequirement(action)
        }
    }

    internal fun showPrivacyOptionsForm(parameter: PlatformConsentFormShowParameter) {
        logInfo(TAG) { "[showPrivacyOptionsForm] trigger!" }
        europeGate.runIfNeeded {
            consentFlow.handleShowPrivacyOptions(parameter)
        }
    }

    override fun execute(listener: CompletionListener<Boolean, Throwable>) {
        checkIfInEurope { isInEuropeCheckingResult ->
            isInEuropeCheckingResult
                .onFailure { th ->
                    listener.onFailure(th)
                }
                .onSuccess { isInEurope ->
                    listener.onSuccess(isInEurope)
                }
        }
    }
}

internal object ConsentInfoUpdateManager : TaskScheduler<Unit, Pair<Int, String>>() {

    fun requestConsentInfoUpdate(
        onConsentInfoUpdateSuccess: () -> Unit,
        onConsentInfoUpdateFailure: (errorCode: Int, message: String) -> Unit
    ) {
        handleCompletion(object : CompletionListener<Unit, Pair<Int, String>> {
            override fun onSuccess(data: Unit) {
                onConsentInfoUpdateSuccess()
            }

            override fun onFailure(data: Pair<Int, String>) {
                onConsentInfoUpdateFailure(data.first, data.second)
            }
        })
    }

    override fun execute(listener: CompletionListener<Unit, Pair<Int, String>>) {
        ConsentManagementPlatform.requestConsentInfoUpdate(
            onConsentInfoUpdateSuccess = {
                listener.onSuccess(Unit)
            },
            onConsentInfoUpdateFailure = { errorCode, message ->
                listener.onFailure(errorCode to message)
            }
        )
    }

}

internal fun ConsentManagementPlatform.checkIfInEurope(action: (isInEuropeCheckingResult: Result<Boolean>) -> Unit) {
    if (forceInEurope()) {
        action(Result.success(true))
        return
    }

    fun checkOnWorkerThread(action: (isInEuropeCheckingResult: Result<Boolean>) -> Unit) {
        dispatchToBackground {
            val context = PoseidonSetupProcessor.getInstance().application
            val consentFlowUserGeography =
                AppLovinSdk.getInstance(context).configuration?.consentFlowUserGeography
            if (consentFlowUserGeography == AppLovinSdkConfiguration.ConsentFlowUserGeography.GDPR) {
                action(Result.success(true))
            } else {
                TUSDK.checkIsEuTraffic(context, object : NetTrafficeCallback {
                    override fun onResultCallback(isEU: Boolean) {
                        action(Result.success(isEU))
                    }

                    override fun onErrorCallback(errorMsg: String?) {
                        action(Result.failure(Exception(errorMsg)))
                    }
                })
            }
        }
    }

    checkOnWorkerThread { isInEuropeCheckingResult ->
        runOnMainThread { action(isInEuropeCheckingResult) }
    }
}

internal fun ConsentManagementPlatform.requestConsentInfoUpdate(
    onConsentInfoUpdateSuccess: () -> Unit,
    onConsentInfoUpdateFailure: (errorCode: Int, message: String) -> Unit
) = runOnMainThread {

    fun requestConsentInfoUpdate(activity: Activity) {
        if (!activity.isUsableForConsentInfoUpdate()) {
            onConsentInfoUpdateFailure(
                ERROR_CODE_ACTIVITY_UNAVAILABLE,
                "Activity is finishing or destroyed."
            )
            return
        }
        val tagForUnderAgeOfConsent =
            PoseidonSetupProcessor.getInstance().remoteConfig.getString("ump_tag_for_under_age_of_consent").toBoolean()
        ConsentManagementPlatformAnalyzer.tagForUnderAgeOfConsent(tagForUnderAgeOfConsent = tagForUnderAgeOfConsent)
            .analyze()
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(tagForUnderAgeOfConsent)
            .also { builder ->
                val testDeviceHashedId = testDeviceHashedId()
                if (!testDeviceHashedId.isNullOrEmpty()) {
                    val debugSettings = ConsentDebugSettings.Builder(activity.applicationContext)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .addTestDeviceHashedId(testDeviceHashedId)
                        .build()
                    builder.setConsentDebugSettings(debugSettings)
                }
            }
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                onConsentInfoUpdateSuccess()
            },
            { requestConsentError ->
                val errorCode = requestConsentError.errorCode
                val message = requestConsentError.message
                onConsentInfoUpdateFailure(errorCode, message)
            }
        )
    }

    val topActivity = ActivityLifecycleCallbacks.topActivity()
        ?.takeIf { it.isUsableForConsentInfoUpdate() }
    if (topActivity == null) {
        ActivityLifecycleCallbacks.observeAnyActivityResumed { anyActivity ->
            if (anyActivity.isUsableForConsentInfoUpdate()) {
                requestConsentInfoUpdate(activity = anyActivity)
            } else {
                onConsentInfoUpdateFailure(
                    ERROR_CODE_ACTIVITY_UNAVAILABLE,
                    "Activity is finishing or destroyed."
                )
            }
        }
    } else {
        requestConsentInfoUpdate(activity = topActivity)
    }
}

internal fun ConsentManagementPlatform.loadConsentForm(
    onConsentFormLoadSuccess: (form: PlatformConsentForm) -> Unit,
    onConsentFormLoadFailure: (errorCode: Int, message: String) -> Unit
) {
    val context = PoseidonSetupProcessor.getInstance().application
    UserMessagingPlatform.loadConsentForm(
        context,
        { form ->
            onConsentFormLoadSuccess(ConsentFormForAndroid(delegate = form))
        },
        { formError ->
            onConsentFormLoadFailure(formError.errorCode, formError.message)
        }
    )
}

internal interface PlatformConsentForm {
    fun show(
        parameter: PlatformConsentFormShowParameter,
        onConsentFormDismissed: (errorCode: Int?, message: String?) -> Unit
    )
}

data class PlatformConsentFormShowParameter(val activity: Activity)

internal fun ConsentManagementPlatform.loadAndShowConsentFormIfRequired(
    parameter: PlatformConsentFormShowParameter,
    onConsentFormDismissed: (errorCode: Int?, message: String?) -> Unit
) {
    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
        parameter.activity,
        { formError ->
            onConsentFormDismissed(formError?.errorCode, formError?.message)
        }
    )
}

internal fun ConsentManagementPlatform.canRequestAds(): Boolean {
    return consentInformation.canRequestAds()
}

internal fun ConsentManagementPlatform.showPrivacyOptionsForm(
    parameter: PlatformConsentFormShowParameter,
    onConsentFormDismissed: (errorCode: Int?, message: String?) -> Unit
) {
    UserMessagingPlatform.showPrivacyOptionsForm(
        parameter.activity,
        { formError ->
            onConsentFormDismissed(formError?.errorCode, formError?.message)
        }
    )
}

internal fun ConsentManagementPlatform.isPrivacyOptionsRequired(): Boolean {
    return consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
}

private fun forceInEurope(): Boolean {
    return if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
        PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig.getString("test_cmp_force_in_europe").toBoolean()
    } else {
        false
    }
}

private fun testDeviceHashedId(): String? {
    return if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
        PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig.getString("test_cmp_test_device_hashed_id")
    } else {
        null
    }
}

private fun Activity.isUsableForConsentInfoUpdate(): Boolean {
    return !isFinishing && !isDestroyed
}

private const val ERROR_CODE_ACTIVITY_UNAVAILABLE = -1

private class ConsentFormForAndroid(
    private val delegate: ConsentForm
) : PlatformConsentForm {
    override fun show(
        parameter: PlatformConsentFormShowParameter,
        onConsentFormDismissed: (errorCode: Int?, message: String?) -> Unit
    ) {
        delegate.show(parameter.activity) { formError ->
            onConsentFormDismissed(formError?.errorCode, formError?.message)
        }
    }
}

private val consentInformation: ConsentInformation by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    UserMessagingPlatform.getConsentInformation(PoseidonSetupProcessor.getInstance().application)
}

private fun warmConsentInformation() {
    dispatchToBackground {
        consentInformation
    }
}

package com.poseidon.ad.restrict.gdpr

import android.app.Activity
import com.poseidon.ad.statistics.biz.ConsentManagementPlatformAnalyzer
import com.poseidon.ad.common.toolkit.logInfo
import java.lang.ref.WeakReference

private typealias ConsentFormLoadAction = (
    onConsentFormLoadSuccess: (form: PlatformConsentForm) -> Unit,
    onConsentFormLoadFailure: (errorCode: Int, message: String) -> Unit
) -> Unit

private typealias ConsentFormShowAction = (
    parameter: PlatformConsentFormShowParameter,
    onConsentFormDismissed: (errorCode: Int?, message: String?) -> Unit
) -> Unit

/**
 * 管理 consent 表单加载、展示以及隐私选项判断的协调器。
 */
internal class ConsentFlowCoordinator(
    private val tag: String,
    private val stateStore: ConsentFormStateStore,
    private val canRequestAds: () -> Boolean,
    private val loadConsentFormAction: ConsentFormLoadAction,
    private val loadAndShowConsentFormAction: ConsentFormShowAction,
    private val showPrivacyOptionsAction: ConsentFormShowAction
) {

    fun handleAppStartup() {
        logInfo(tag) { "[appStartup] trigger!" }
        ConsentInfoUpdateManager.requestConsentInfoUpdate(
            onConsentInfoUpdateSuccess = {
                val canRequestAdsResult = canRequestAds()
                ConsentManagementPlatformAnalyzer.consentCheck(canRequestAds = canRequestAdsResult)
                    .analyze()
                logInfo(tag) { "[appStartup] consent info update success: canRequestAds=$canRequestAdsResult!" }
                if (canRequestAdsResult) return@requestConsentInfoUpdate
                loadConsentFormAction(
                    { form ->
                        logInfo(tag) { "[appStartup] consent form load success: form=$form!" }
                        stateStore.cacheForm(form)
                    },
                    { errorCode, message ->
                        logInfo(tag) { "[appStartup] consent form load failure: errorCode=$errorCode, message=$message!" }
                        ConsentManagementPlatformAnalyzer.errorType(3)
                        ConsentManagementPlatformAnalyzer.errorMessage("errorCode=$errorCode, message=$message")
                    }
                )
            },
            onConsentInfoUpdateFailure = { errorCode, message ->
                logInfo(tag) { "[appStartup] consent info update failure: errorCode=$errorCode, message=$message!" }
                ConsentManagementPlatformAnalyzer.errorType(2)
                ConsentManagementPlatformAnalyzer.errorMessage("errorCode=$errorCode, message=$message")
            }
        )
    }

    fun handleShowConsentForm(parameter: PlatformConsentFormShowParameter) {
        logInfo(tag) { "[showConsentForm] trigger!" }
        val activityRef = WeakReference(parameter.activity)

        fun currentParameter(): PlatformConsentFormShowParameter? {
            return activityRef.get()
                ?.takeIf { it.isUsableForConsentForm() }
                ?.let { PlatformConsentFormShowParameter(activity = it) }
        }

        fun showWithCache(onConsentFormDismissed: (Int?, String?) -> Unit) {
            val currentParameter = currentParameter()
            if (currentParameter == null) {
                logInfo(tag) { "[showConsentForm] drop show because activity is gone!" }
                stateStore.finishConsentForm()
                return
            }
            val cachedForm = stateStore.consumeCachedForm()
            if (cachedForm == null) {
                logInfo(tag) { "[showConsentForm] load and show consent form!" }
                ConsentManagementPlatformAnalyzer.showDialog(consentFormFromCache = false).analyze()
                loadAndShowConsentFormAction(currentParameter, onConsentFormDismissed)
            } else {
                logInfo(tag) { "[showConsentForm] show consent form loaded!" }
                ConsentManagementPlatformAnalyzer.showDialog(consentFormFromCache = true).analyze()
                cachedForm.show(currentParameter, onConsentFormDismissed)
            }
        }

        ConsentInfoUpdateManager.requestConsentInfoUpdate(
            onConsentInfoUpdateSuccess = {
                val canRequestAdsResult = canRequestAds()
                ConsentManagementPlatformAnalyzer.consentCheck(canRequestAds = canRequestAdsResult)
                    .analyze()
                logInfo(tag) { "[showConsentForm] consent info update success: canRequestAds=$canRequestAdsResult!" }
                if (canRequestAdsResult) return@requestConsentInfoUpdate

                if (stateStore.tryStartConsentForm()) {
                    logInfo(tag) { "[showConsentForm] prepare to show!" }
                    showWithCache { errorCode, message ->
                        logInfo(tag) { "[showConsentForm] dismissed: errorCode=$errorCode, message=$message!" }
                        ConsentManagementPlatformAnalyzer.consentResult(agreeConsentForm = errorCode == null)
                            .analyze()
                        stateStore.finishConsentForm()
                    }
                }
            },
            onConsentInfoUpdateFailure = { errorCode, message ->
                logInfo(tag) { "[showConsentForm] consent info update failure: errorCode=$errorCode, message=$message!" }
                ConsentManagementPlatformAnalyzer.consentCheck(canRequestAds = false).analyze()
                ConsentManagementPlatformAnalyzer.errorType(2)
                ConsentManagementPlatformAnalyzer.errorMessage("errorCode=$errorCode, message=$message")
            }
        )
    }

    fun handlePrivacyRequirement(action: () -> Unit) {
        logInfo(tag) { "[doOnPrivacyOptionsIsRequired] trigger!" }
        ConsentInfoUpdateManager.requestConsentInfoUpdate(
            onConsentInfoUpdateSuccess = {
                val required = ConsentManagementPlatform.isPrivacyOptionsRequired()
                logInfo(tag) { "[doOnPrivacyOptionsIsRequired] consent info update success: isPrivacyOptionsRequired=$required!" }
                if (required) {
                    action()
                }
            },
            onConsentInfoUpdateFailure = { errorCode, message ->
                logInfo(tag) { "[doOnPrivacyOptionsIsRequired] consent info update failure: errorCode=$errorCode, message=$message!" }
                ConsentManagementPlatformAnalyzer.errorType(2)
                ConsentManagementPlatformAnalyzer.errorMessage("errorCode=$errorCode, message=$message")
            }
        )
    }

    fun handleShowPrivacyOptions(parameter: PlatformConsentFormShowParameter) {
        logInfo(tag) { "[showPrivacyOptionsForm] trigger!" }
        if (!parameter.activity.isUsableForConsentForm()) {
            logInfo(tag) { "[showPrivacyOptionsForm] drop show because activity is gone!" }
            return
        }
        if (stateStore.tryStartPrivacyForm()) {
            logInfo(tag) { "[showPrivacyOptionsForm] prepare to show!" }
            showPrivacyOptionsAction(parameter) { errorCode, message ->
                logInfo(tag) { "[showPrivacyOptionsForm] dismissed: errorCode=$errorCode, message=$message!" }
                stateStore.finishPrivacyForm()
            }
            ConsentManagementPlatformAnalyzer.showPrivacyDialog().analyze()
        }
    }
}

private fun Activity.isUsableForConsentForm(): Boolean {
    return !isFinishing && !isDestroyed
}

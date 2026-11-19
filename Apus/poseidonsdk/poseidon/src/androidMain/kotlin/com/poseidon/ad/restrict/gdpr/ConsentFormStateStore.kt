package com.poseidon.ad.restrict.gdpr

import kotlinx.atomicfu.atomic

/**
 * 统一管理 consent 表单的缓存与显示状态，避免在主流程中直接操作原子变量。
 */
internal class ConsentFormStateStore {
    private val cachedFormAtomic = atomic<PlatformConsentForm?>(null)
    private val consentFormShowingAtomic = atomic(false)
    private val privacyOptionsShowingAtomic = atomic(false)

    fun cacheForm(form: PlatformConsentForm) {
        cachedFormAtomic.value = form
    }

    fun consumeCachedForm(): PlatformConsentForm? = cachedFormAtomic.getAndSet(null)

    fun tryStartConsentForm(): Boolean = consentFormShowingAtomic.compareAndSet(false, true)

    fun finishConsentForm() {
        consentFormShowingAtomic.compareAndSet(true, false)
    }

    fun tryStartPrivacyForm(): Boolean = privacyOptionsShowingAtomic.compareAndSet(false, true)

    fun finishPrivacyForm() {
        privacyOptionsShowingAtomic.compareAndSet(true, false)
    }
}

package com.poseidon.ad.main.preload.new

import kotlinx.atomicfu.atomic
import com.poseidon.ad.restrict.new.NewUserFirstShowMtg
import com.poseidon.ad.common.toolkit.logInfo

internal object NewUserAdPreloadGate {

    private const val TAG = "NewUserAdPreloadGate"

    private val unlockedAfterFirstShowAtomic = atomic(false)

    fun isOrdinaryPreloadBlocked(): Boolean {
        return NewUserFirstShowMtg.isNewUserFirstColdStart() &&
            !unlockedAfterFirstShowAtomic.value
    }

    fun unlockAfterFirstShowCall(): Boolean {
        if (!NewUserFirstShowMtg.isNewUserFirstColdStart()) {
            return false
        }
        val unlocked = unlockedAfterFirstShowAtomic.compareAndSet(expect = false, update = true)
        if (unlocked) {
            logInfo(TAG) { "Ordinary preload unlocked after first ad show call" }
        }
        return unlocked
    }
}


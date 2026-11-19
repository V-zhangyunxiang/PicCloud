package com.poseidon.ad.main.load

import com.poseidon.ad.main.adinfo.CodeDef

/**
 * Callback to be invoked when an ad finishes loading.
 */
interface InnerAdLoadCallback<T> {
    /**
     * Called when an ad fails to load.
     */
    fun onAdLoadFailed(@CodeDef code: Int, message: String)

    /**
     * Called when an ad successfully loads.
     */
    fun onAdLoadSuccess(ad: T)
}